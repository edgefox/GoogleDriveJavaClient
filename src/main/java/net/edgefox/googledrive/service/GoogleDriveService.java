package net.edgefox.googledrive.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.*;
import com.google.inject.name.Named;
import net.edgefox.googledrive.filesystem.FileMetadata;
import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.filesystem.change.RemoteChangePackage;
import net.edgefox.googledrive.service.util.RestrictedMimeTypes;
import net.edgefox.googledrive.util.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.lf5.util.StreamUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

/**
 * User: Ivan Lyutov
 * Date: 10/16/13
 * Time: 11:59 AM
 */
@Singleton
public class GoogleDriveService {
    public static final String ROOT_DIR_ID = "root";

    private static final Logger logger = Logger.getLogger(GoogleDriveService.class);
    @Inject
    private AuthRedirectListener authRedirectListener;
    private Drive apiClient;
    private Credential credential;
    private static final Collection<String> SCOPES = Arrays.asList(
            DriveScopes.DRIVE,
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/userinfo.email"
    );
    private final String REDIRECT_URI;
    private final String APP_KEY;
    private final String APP_SECRET;
    private String REFRESH_TOKEN;
    private GoogleAuthorizationCodeFlow authFlow;

    private static final String FILE_LIST_REQUIRED_FIELDS = "items(id,mimeType,title,md5Checksum)";
    private static final String FILE_REQUIRED_FIELDS = "id,mimeType,title,md5Checksum,etag";
    private static final String FILE_DOWNLOAD_FIELDS = "id,mimeType,title,downloadUrl,exportLinks,md5Checksum,etag";

    private static final String DELTA_FIELDS_ALL_INFO = "items(deleted,file,fileId),largestChangeId,nextPageToken";
    private static final String DELTA_FIELDS_ONLY_ID = "largestChangeId";
    private static long MAX_TIMEOUT = 60;
    private static long TIMEOUT_STEP = 5;

    @Inject
    public GoogleDriveService(@Named("REDIRECT_URI") String REDIRECT_URI,
                              @Named("APP_KEY") String APP_KEY,
                              @Named("APP_SECRET") String APP_SECRET,
                              @Named("REFRESH_TOKEN") String REFRESH_TOKEN) throws IOException {
        this.REDIRECT_URI = REDIRECT_URI;
        this.APP_KEY = APP_KEY;
        this.APP_SECRET = APP_SECRET;
        this.REFRESH_TOKEN = REFRESH_TOKEN;
    }

    @Inject
    public void init() throws IOException {
        authFlow = new GoogleAuthorizationCodeFlow.Builder(new ApacheHttpTransport(),
                                                           new JacksonFactory(),
                                                           APP_KEY, APP_SECRET, SCOPES).build();
        TokenResponse response = new TokenResponse().setRefreshToken(REFRESH_TOKEN)
                .setExpiresInSeconds(0L);
        credential = authFlow.createAndStoreCredential(response, "userId");
        apiClient = new Drive.Builder(new ApacheHttpTransport(),
                                      new JacksonFactory(),
                                      credential).setApplicationName("DriveJava").build();
    }

    public String auth() throws Exception {
        GoogleAuthorizationCodeRequestUrl authUrl = authFlow.newAuthorizationUrl();
        authUrl.setRedirectUri(REDIRECT_URI);

        return authUrl.build();
    }

    public String handleRedirect() throws Exception {
        REFRESH_TOKEN = authRedirectListener.listenForAuthComplete();
        init();
        return REFRESH_TOKEN;
    }

    public About about() throws IOException {
        try {
            Drive.About.Get about = apiClient.about().get();
            return (About) safeExecute(about.setFields(DELTA_FIELDS_ONLY_ID));
        } catch (IOException e) {
            throw new IOException("Failed to get account info", e);
        }
    }

    public FileMetadata upload(String folderId, java.io.File localFile) throws IOException {
        try {
            logger.trace(format("Trying to upload local file: %s", localFile));
    
            File file = new File();
            file.setTitle(localFile.getName());
            ParentReference parent = new ParentReference();
            parent.setId(folderId);
            file.setParents(Arrays.asList(parent));
            FileContent mediaContent = new FileContent(null, localFile);
    
            FileMetadata child = findChild(folderId, localFile.getName());
            
            if (child != null) {
                if (child.getCheckSum() == null && !child.isDir()) {
                    return child;
                }
                File updatedFile = (File) safeExecute(apiClient.files().update(child.getId(), file, mediaContent).setFields(FILE_REQUIRED_FIELDS));
                logger.trace(format("File has been successfully uploaded: '%s'", localFile));
                return new FileMetadata(updatedFile);
            }
    
            Drive.Files.Insert insertedFile = apiClient.files().insert(file, mediaContent).setFields(FILE_REQUIRED_FIELDS);
            File uploadedFile = (File) safeExecute(insertedFile);
            logger.trace(format("File has been successfully uploaded: '%s'", localFile));
    
            return new FileMetadata(uploadedFile);
        } catch (IOException e) {
            throw new IOException(format("Failed to upload file %s to remote directory '%s'", localFile, folderId), e);
        }
    }

    public void delete(String id) throws IOException {
        try {
            logger.trace(format("Trying to delete remote resource with id '%s'", id));
            Drive.Files.Delete delete = apiClient.files().delete(id);
            safeExecute(delete);
            logger.trace(format("Resource with id '%s' has been successfully deleted from remote storage", id));
        } catch (IOException e) {
            throw new IOException(format("Failed to delete remote file '%s'", id), e);
        }
    }

    public FileMetadata getFileMetadata(String id) throws IOException {
        try {
            logger.trace(format("Trying to get metadata for remote resource with id '%s'", id));
            Drive.Files.Get get = apiClient.files().get(id);
            File file = (File) safeExecute(get);
            logger.trace(format("Metadata for resource with id '%s' has been successfully retrieved", id));
    
            return new FileMetadata(file);
        } catch (IOException e) {
            throw new IOException(format("Failed to get metadata for remote file '%s'", id), e);
        }
    }

    public FileMetadata createDirectory(String parentId, String name) throws IOException {
        try {
            logger.trace(format("Trying to create remote directory '%s'", name));
            File dirToCreate = new File();
            dirToCreate.setTitle(name);
            dirToCreate.setMimeType("application/vnd.google-apps.folder");
            ParentReference parent = new ParentReference();
            parent.setId(parentId);
            dirToCreate.setParents(Arrays.asList(parent));
    
            Drive.Files.Insert insert = apiClient.files().insert(dirToCreate).setFields(FILE_REQUIRED_FIELDS);
            File createdDirectory = (File) safeExecute(insert);
    
            logger.trace(format("Remote directory '%s' has been successfully created", name));
    
            return new FileMetadata(createdDirectory);
        } catch (IOException e) {
            throw new IOException(format("Failed to create new directory '%s' at '%s'", name, parentId), e);
        }
    }

    public FileMetadata createOrGetDirectory(String id, String title) throws IOException {
        FileMetadata child = findChild(id, title);
        if (child != null) {
            return child;
        }

        return createDirectory(id, title);
    }

    public FileMetadata findChild(String folderId, String title) throws IOException {
        try {
            String format = format("title = '%s' and trashed = false and '%s' in parents", title, folderId);
            Drive.Files.List list = apiClient.files().list()
                    .setFields(FILE_LIST_REQUIRED_FIELDS)
                    .setQ(format);
            FileList children = (FileList) safeExecute(list);
            if (children.getItems().isEmpty()) {
                return null;
            }
            String childId = children.getItems().get(0).getId();
            Drive.Files.Get get = apiClient.files().get(childId).setFields(FILE_REQUIRED_FIELDS);
            File file = (File) safeExecute(get);
            return new FileMetadata(file);
        } catch (IOException e) {
            throw new IOException(format("Failed to get child entry '%s' in remote folder '%s'", title, folderId), e);
        }
    }

    public Set<String> getAllChildrenIds(String folderId) throws IOException {
        try {
            Set<String> result = new HashSet<>();
            Drive.Children.List request = apiClient.children().list(folderId);
            ChildList childList = (ChildList) safeExecute(request);
            for (ChildReference childReference : childList.getItems()) {
                result.add(childReference.getId());
                result.addAll(getAllChildrenIds(childReference.getId()));
            }
    
            return result;
        } catch (IOException e) {
            throw new IOException(format("Failed to get all child entries of remote folder '%s'", folderId), e);
        }
    }

    public List<FileMetadata> listDirectory(String folderId) throws IOException {
        Drive.Children.List list = apiClient.children().list(folderId)
                .setQ(format("trashed = false and '%s' in parents", folderId));
        ChildList children = (ChildList) safeExecute(list);
        List<FileMetadata> resultList = new ArrayList<>(children.getItems().size());
        for (ChildReference childReference : children.getItems()) {
            Drive.Files.Get get = apiClient.files().get(childReference.getId()).setFields(FILE_REQUIRED_FIELDS);
            File file = (File) safeExecute(get);
            if (!RestrictedMimeTypes.isRestricted(file.getMimeType())) {
                resultList.add(new FileMetadata(file));
            }
        }

        return resultList;
    }

    public FileMetadata downloadFile(String id, java.io.File localFile) throws InterruptedException, IOException {
        try {
            logger.trace(format("Trying to download remote file with id '%s' to %s", id, localFile));
            Drive.Files.Get get = apiClient.files().get(id).setFields(FILE_DOWNLOAD_FIELDS);
            File file = (File) safeExecute(get);
            GenericUrl downloadUrl = getGenericUrl(file);
    
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            try {
                HttpResponse resp = safeDownload(downloadUrl);
                inputStream = resp.getContent();
    
                FileUtils.forceMkdir(localFile.getParentFile());
    
                outputStream = new FileOutputStream(localFile);
                StreamUtils.copy(inputStream, outputStream);
                outputStream.close();
            } finally {
                IOUtils.safeClose(inputStream);
                IOUtils.safeClose(outputStream);
            }
            logger.trace(format("File has been successfully downloaded: %s", localFile));
    
            return new FileMetadata(file);
        } catch (IOException e) {
            throw new IOException(format("Failed to download remote file '%s' to '%s'", id, localFile), e);
        }
    }

    public RemoteChangePackage getChanges(long revisionNumber) throws IOException {
        try {
            Set<FileSystemChange<String>> resultChanges = new LinkedHashSet<>();
            Drive.Changes.List request = apiClient.changes().list().setFields(DELTA_FIELDS_ALL_INFO).setIncludeSubscribed(false);
            request.setStartChangeId(++revisionNumber);
            do {
                ChangeList changes = (ChangeList) safeExecute(request);
                revisionNumber = changes.getLargestChangeId();
                for (Change change : changes.getItems()) {
                    String title = change.getDeleted() ? null : change.getFile().getTitle();
                    boolean isDir = !change.getDeleted() && change.getFile().getMimeType().equals("application/vnd.google-apps.folder");
                    String md5CheckSum = getMd5CheckSum(change);
                    FileSystemChange<String> fileSystemChange = new FileSystemChange<>(change.getFileId(),
                                                                                       getParentId(change),
                                                                                       title,
                                                                                       isDir,
                                                                                       md5CheckSum);
                    resultChanges.add(fileSystemChange);
                }
                request.setPageToken(changes.getNextPageToken());
                request.setPageToken(null);
            } while (request.getPageToken() != null &&
                    request.getPageToken().length() > 0);
    
            return new RemoteChangePackage(revisionNumber, resultChanges);
        } catch (IOException e) {
            throw new IOException(format("Failed to get remote changes with revision '%s'", revisionNumber), e);
        }
    }

    String requestRefreshToken(String code) throws IOException {
        GoogleAuthorizationCodeTokenRequest tokenRequest = authFlow.newTokenRequest(code);
        tokenRequest.setRedirectUri(REDIRECT_URI);
        GoogleTokenResponse googleTokenResponse = tokenRequest.execute();
        return googleTokenResponse.getRefreshToken();
    }

    private String getParentId(Change change) {
        String parentId = null;
        if (change.getDeleted() || change.getFile().getLabels().getTrashed()) {
            return parentId;
        }

        List<ParentReference> parents = change.getFile().getParents();
        ParentReference parentReference = parents.get(0);

        return parentReference.getIsRoot() ? ROOT_DIR_ID : parentReference.getId();
    }

    private Object safeExecute(AbstractGoogleClientRequest request) throws IOException {
        long timeout = 0;
        Object result = null;
        while (result == null && timeout < MAX_TIMEOUT) {
            try {
                result = request.execute();
                if (request instanceof Drive.Files.Delete) {
                    result = "deletion succeeded";
                }
            } catch (SocketTimeoutException | GoogleJsonResponseException e) {
                try {
                    timeout += TIMEOUT_STEP;
                    logger.warn(format("Request timeout. Retrying in %d seconds", timeout), e);
                    TimeUnit.SECONDS.sleep(timeout);
                } catch (InterruptedException e1) {
                    logger.warn("Request execution was interrupted", e1);
                }
            }
        }

        if (result == null) {
            throw new IOException("Connection timeout");
        }

        return result;
    }

    private HttpResponse safeDownload(GenericUrl downloadUrl) throws IOException {
        long timeout = 0;
        HttpResponse response = null;
        while (response == null && timeout < MAX_TIMEOUT) {
            try {
                response = apiClient.getRequestFactory()
                        .buildGetRequest(downloadUrl)
                        .execute();
            } catch (SocketTimeoutException | GoogleJsonResponseException e) {
                try {
                    timeout += TIMEOUT_STEP;
                    logger.warn(format("Request timeout. Retrying in %d seconds", timeout), e);
                    TimeUnit.SECONDS.sleep(timeout);
                } catch (InterruptedException e1) {
                    logger.warn("Request execution was interrupted", e1);
                }
            }
        }

        if (response == null) {
            throw new IOException("Connection timeout");
        }

        return response;
    }

    private GenericUrl getGenericUrl(File file) {
        String downloadUrl;
        if (isSupportedGoogleApp(file)) {
            downloadUrl = file.getExportLinks().get("application/pdf");
        } else {
            downloadUrl = file.getDownloadUrl();
        }
        return new GenericUrl(downloadUrl);
    }

    private boolean isSupportedGoogleApp(File file) {
        return file.getMimeType().equals("application/vnd.google-apps.document") ||
            file.getMimeType().equals("application/vnd.google-apps.spreadsheet") ||
            file.getMimeType().equals("application/vnd.google-apps.presentation");
    }

    private String getMd5CheckSum(Change change) {
        if (change.getDeleted()) {
            return null;
        } else if (isSupportedGoogleApp(change.getFile())) {
            return change.getFile().getEtag();
        } else {
            return change.getFile().getMd5Checksum();
        }
    }
}

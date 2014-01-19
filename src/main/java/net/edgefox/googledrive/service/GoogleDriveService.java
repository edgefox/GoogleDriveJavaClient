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
import com.google.api.services.drive.model.File;
import com.google.inject.name.Named;
import net.edgefox.googledrive.filesystem.FileMetadata;
import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.filesystem.change.RemoteChangePackage;
import net.edgefox.googledrive.util.GoogleDriveUtils;
import net.edgefox.googledrive.util.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.NoHttpResponseException;
import org.apache.log4j.Logger;
import org.apache.log4j.lf5.util.StreamUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.net.SocketTimeoutException;
import java.util.*;

import static java.lang.String.format;

/**
 * User: Ivan Lyutov
 * Date: 10/16/13
 * Time: 11:59 AM
 */
@Singleton
public class GoogleDriveService {
    public static final String ROOT_DIR_ID = "root";

    private static Logger logger = Logger.getLogger(GoogleDriveService.class);
    @Inject
    private AuthRedirectListener authRedirectListener;
    private Drive apiClient;
    private static final Collection<String> SCOPES = Arrays.asList(
            DriveScopes.DRIVE,
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/userinfo.email"
    );
    private final String redirectUri;
    private final String appKey;
    private final String appSecret;
    private String refreshToken;
    private GoogleAuthorizationCodeFlow authFlow;

    private static final String FILE_LIST_REQUIRED_FIELDS = "nextPageToken, items(id,mimeType,title,md5Checksum,parents)";
    private static final String FILE_REQUIRED_FIELDS = "id,mimeType,title,md5Checksum,etag";
    private static final String FILE_DOWNLOAD_FIELDS = "id,mimeType,title,downloadUrl,exportLinks,md5Checksum,etag";

    private static final String DELTA_FIELDS_ALL_INFO = "items(deleted,file,fileId),largestChangeId,nextPageToken";
    private static final String DELTA_FIELDS_ONLY_ID = "largestChangeId";
    private static long MAX_TIMEOUT = 30;
    private static long TIMEOUT_STEP = 5;

    @Inject
    public GoogleDriveService(@Named("redirectUri") String redirectUri,
                              @Named("appKey") String appKey,
                              @Named("appSecret") String appSecret,
                              @Named("refreshToken") String refreshToken) throws IOException {
        this.redirectUri = redirectUri;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.refreshToken = refreshToken;
    }

    @Inject
    public void init() throws IOException {
        authFlow = new GoogleAuthorizationCodeFlow.Builder(new ApacheHttpTransport(),
                                                           new JacksonFactory(),
                                                           appKey, appSecret, SCOPES).build();
        TokenResponse response = new TokenResponse().setRefreshToken(refreshToken)
                .setExpiresInSeconds(0L);
        Credential credential = authFlow.createAndStoreCredential(response, "userId");
        apiClient = new Drive.Builder(new ApacheHttpTransport(),
                                      new JacksonFactory(),
                                      credential).setApplicationName("DriveJava").build();
    }

    public String auth() throws Exception {
        GoogleAuthorizationCodeRequestUrl authUrl = authFlow.newAuthorizationUrl();
        authUrl.setRedirectUri(redirectUri);

        return authUrl.build();
    }

    public String handleRedirect() throws Exception {
        refreshToken = authRedirectListener.listenForAuthComplete();
        init();
        return refreshToken;
    }

    public About about() throws IOException {
        try {
            Drive.About.Get about = apiClient.about().get();
            return safeExecute(about.setFields(DELTA_FIELDS_ONLY_ID));
        } catch (Exception e) {
            throw new IOException("Failed to get account info", e);
        }
    }

    public FileMetadata upload(String folderId, java.io.File localFile) throws IOException {
        try {
            logger.trace(format("Trying to upload local file: %s", localFile));

            File file = findChildFile(folderId, localFile.getName());
            file = file == null ? new File() : file;
            
            file.setTitle(localFile.getName());
            ParentReference parent = new ParentReference();
            parent.setId(folderId);
            file.setParents(Arrays.asList(parent));
            FileContent mediaContent = new FileContent(null, localFile);

            if (file.getId() != null) {
                if (!isUploadRequired(localFile, file)) {
                    return new FileMetadata(file);
                }

                Drive.Files.Update updateRequest = apiClient.files().update(file.getId(), file, mediaContent)
                                                                    .setFields(FILE_REQUIRED_FIELDS);
                File updatedFile = safeExecute(updateRequest);
                logger.trace(format("File has been successfully uploaded: '%s'", localFile));
                return new FileMetadata(updatedFile);
            }

            Drive.Files.Insert insertRequest = apiClient.files().insert(file, mediaContent).setFields(FILE_REQUIRED_FIELDS);
            File uploadedFile = safeExecute(insertRequest);
            logger.trace(format("File has been successfully uploaded: '%s'", localFile));

            return new FileMetadata(uploadedFile);
        } catch (Exception e) {
            throw new IOException(format("Failed to upload file %s to remote directory '%s'", localFile, folderId), e);
        }
    }

    public void delete(String id) throws IOException {
        try {
            logger.trace(format("Trying to delete remote resource with id '%s'", id));
            Drive.Files.Delete delete = apiClient.files().delete(id);
            safeExecute(delete);
            logger.trace(format("Resource with id '%s' has been successfully deleted from remote storage", id));
        } catch (Exception e) {
            throw new IOException(format("Failed to delete remote file '%s'", id), e);
        }
    }

    public FileMetadata getFileMetadata(String id) throws IOException {
        try {
            logger.trace(format("Trying to get metadata for remote resource with id '%s'", id));
            Drive.Files.Get get = apiClient.files().get(id);
            File file = safeExecute(get);
            logger.trace(format("Metadata for resource with id '%s' has been successfully retrieved", id));

            return new FileMetadata(file);
        } catch (Exception e) {
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
            File createdDirectory = safeExecute(insert);

            logger.trace(format("Remote directory '%s' has been successfully created", name));

            return new FileMetadata(createdDirectory);
        } catch (Exception e) {
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
        File childFile = findChildFile(folderId, title);
        return childFile == null ? null : new FileMetadata(childFile);
    }

    public Set<String> getAllChildrenIds(String folderId) throws IOException {
        try {
            Set<String> result = new HashSet<>();
            Drive.Children.List request = apiClient.children().list(folderId);
            ChildList childList = safeExecute(request);
            for (ChildReference childReference : childList.getItems()) {
                result.add(childReference.getId());
                result.addAll(getAllChildrenIds(childReference.getId()));
            }

            return result;
        } catch (Exception e) {
            throw new IOException(format("Failed to get all child entries ids of remote folder '%s'", folderId), e);
        }
    }

    public List<FileMetadata> listDirectory(String folderId) throws IOException {
        try {
            String query = format("trashed=false and '%s' in parents", folderId);
            return convertToFileMetadata(search(query));
        } catch (Exception e) {
            throw new IOException(format("Failed to list child entries in remote folder '%s'", folderId), e);
        }
    }

    public FileMetadata downloadFile(String id, java.io.File localFile) throws IOException {
        try {
            logger.trace(format("Trying to download remote file with id '%s' to %s", id, localFile));
            Drive.Files.Get get = apiClient.files().get(id).setFields(FILE_DOWNLOAD_FIELDS);
            File file = safeExecute(get);
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
        } catch (Exception e) {
            throw new IOException(format("Failed to download remote file '%s' to '%s'", id, localFile), e);
        }
    }

    public RemoteChangePackage getChanges(long revisionNumber) throws IOException {
        try {
            Set<FileSystemChange<String>> resultChanges = new LinkedHashSet<>();
            Drive.Changes.List request = apiClient.changes()
                                                  .list()
                                                  .setFields(DELTA_FIELDS_ALL_INFO)
                                                  .setIncludeSubscribed(true);
            request.setStartChangeId(++revisionNumber);
            do {
                ChangeList changes = safeExecute(request);
                revisionNumber = changes.getLargestChangeId();
                for (Change change : changes.getItems()) {
                    resultChanges.add(new FileSystemChange<String>(change));
                }
                request.setPageToken(changes.getNextPageToken());
            } while (request.getPageToken() != null &&
                    request.getPageToken().length() > 0);

            return new RemoteChangePackage(revisionNumber, resultChanges);
        } catch (Exception e) {
            throw new IOException(format("Failed to get remote changes with revision '%s'", revisionNumber), e);
        }
    }

    String requestRefreshToken(String code) throws IOException {
        try {
            GoogleAuthorizationCodeTokenRequest tokenRequest = authFlow.newTokenRequest(code);
            tokenRequest.setRedirectUri(redirectUri);
            GoogleTokenResponse googleTokenResponse = tokenRequest.execute();
            return googleTokenResponse.getRefreshToken();
        } catch (Exception e) {
            throw new IOException(format("Failed to get refresh token with code '%s'", code), e);
        }
    }

    Map<String, File> listAllFiles() throws IOException {
        Map<String, File> fileSystem = new HashMap<>();
        Drive.Files.List request = apiClient.files()
                .list()
                .setFields(FILE_LIST_REQUIRED_FIELDS)
                .setQ("trashed=false")
                .setMaxResults(1000);
        do {
            FileList response = safeExecute(request);
            for (File file : response.getItems()) {
                fileSystem.put(file.getId(), file);
            }
            request.setPageToken(response.getNextPageToken());
        } while (request.getPageToken() != null && 
                 !request.getPageToken().isEmpty());

        return fileSystem;
    }

    private static <T> T safeExecute(AbstractGoogleClientRequest<T> request) throws IOException {
        long timeout = 0;
        T result = null;
        while (result == null && timeout < MAX_TIMEOUT) {
            try {
                if (request.getResponseClass().equals(Void.class)) {
                    request.execute();
                    return null;
                }

                result = request.execute();
            } catch (SocketTimeoutException | GoogleJsonResponseException e) {
                timeout += TIMEOUT_STEP;
                logger.warn("Request timeout. Retrying...", e);
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
            } catch (SocketTimeoutException | GoogleJsonResponseException | NoHttpResponseException e) {
                timeout += TIMEOUT_STEP;
                logger.warn("Request timeout. Retrying...", e);
            }
        }

        if (response == null) {
            throw new IOException("Connection timeout");
        }

        return response;
    }

    private FileList search(String searchQuery) throws IOException {
        Drive.Files.List list = apiClient.files().list()
                                                 .setQ(searchQuery)
                                                 .setFields(FILE_LIST_REQUIRED_FIELDS);
        return safeExecute(list);
    }

    private File findChildFile(String folderId, String title) throws IOException {
        try {
            String format = format("title = '%s' and trashed = false and '%s' in parents", title, folderId);
            Drive.Files.List list = apiClient.files().list()
                    .setFields(FILE_LIST_REQUIRED_FIELDS)
                    .setQ(format);
            FileList children = safeExecute(list);
            if (children.getItems().isEmpty()) {
                return null;
            }
            String childId = children.getItems().get(0).getId();
            Drive.Files.Get get = apiClient.files().get(childId).setFields(FILE_REQUIRED_FIELDS);
            return safeExecute(get);
        } catch (Exception e) {
            throw new IOException(format("Failed to get child entry '%s' in remote folder '%s'", title, folderId), e);
        }
    }

    private GenericUrl getGenericUrl(File file) {
        String downloadUrl;
        if (GoogleDriveUtils.isSupportedGoogleApp(file)) {
            downloadUrl = file.getExportLinks().get("application/pdf");
        } else {
            downloadUrl = file.getDownloadUrl();
        }
        return new GenericUrl(downloadUrl);
    }

    private List<FileMetadata> convertToFileMetadata(FileList children) {
        List<FileMetadata> resultList = new ArrayList<>(children.getItems().size());
        for (File file : children.getItems()) {
            if (!GoogleDriveUtils.isRestrictedGoogleApp(file)) {
                resultList.add(new FileMetadata(file));
            }
        }

        return resultList;
    }

    private boolean isUploadRequired(java.io.File localFile, File file) throws IOException {
        if (GoogleDriveUtils.isSupportedGoogleApp(file)) {
            return false;
        }
        if (localFile.exists()) {
            String md5CheckSum = IOUtils.getFileMd5CheckSum(localFile.toPath());
            if (md5CheckSum.equals(file.getMd5Checksum())) {
                return false;
            }
        }
        return true;
    }
}

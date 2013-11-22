import com.google.inject.Singleton;
import filesystem.FileMetadata;
import filesystem.FileSystem;
import filesystem.change.ChangesApplier;
import filesystem.change.local.LocalChangesWatcher;
import filesystem.change.remote.RemoteChangesWatcher;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import service.GoogleDriveService;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * User: Ivan Lyutov
 * Date: 11/18/13
 * Time: 6:44 PM
 */
@Singleton
public class FileSystemManager {
    @Inject
    private Path trackedPath;
    @Inject    
    private GoogleDriveService googleDriveService;
    @Inject
    private FileSystem fileSystem;
    @Inject
    private LocalChangesWatcher localChangesWatcher;
    @Inject
    private RemoteChangesWatcher remoteChangesWatcher;
    @Inject
    private ChangesApplier changesApplier;
    @Inject
    private ConfigurationManager configurationManager;

    @Inject
    public void start() throws Exception {
        prepareGoogleDriveAuth();

        if (fileSystem.getFileSystemRevision() == 0) {
            reflectRemoteStorage(GoogleDriveService.ROOT_DIR_ID, trackedPath.toString());
        }
        localChangesWatcher.start();
        remoteChangesWatcher.start();
        changesApplier.start();
    }

    private void prepareGoogleDriveAuth() throws Exception {
        if (StringUtils.isEmpty(configurationManager.getProperty("REFRESH_TOKEN"))) {
            String authUrl = googleDriveService.auth();
            System.out.println(String.format("Please follow the url to authorize the application: '%s'", authUrl));
            String newRefreshToken = googleDriveService.handleRedirect();
            configurationManager.updateProperties("REFRESH_TOKEN", newRefreshToken);
            System.out.println("Authorization succeeded. Starting application...");
        }
    }

    private void reflectRemoteStorage(String id, String path) throws IOException, InterruptedException {
        List<FileMetadata> root = googleDriveService.listDirectory(id);
        for (FileMetadata fileMetadata : root) {
            File file = new File(path, fileMetadata.getTitle());
            Path imagePath = trackedPath.relativize(Paths.get(file.getAbsolutePath()));
            if (fileMetadata.isDir()) {
                FileUtils.forceMkdir(file);
                fileSystem.update(imagePath, fileMetadata);
                reflectRemoteStorage(fileMetadata.getId(), file.getAbsolutePath());
            } else {
                googleDriveService.downloadFile(fileMetadata.getId(), file);
                fileSystem.update(imagePath, fileMetadata);
            }
        }
    }
}

package service;

import com.google.inject.Singleton;
import com.google.inject.name.Named;
import filesystem.FileMetadata;
import filesystem.FileSystem;
import filesystem.change.ChangesApplier;
import filesystem.change.local.LocalChangesWatcher;
import filesystem.change.remote.RemoteChangesWatcher;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

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
    @Named("REFRESH_TOKEN")
    private String refreshToken;

    public void start() throws Exception {
        if (StringUtils.isEmpty(refreshToken)) {
            googleDriveService.auth();
        }
        
        if (fileSystem.getFileSystemRevision() == 0) {
            reflectRemoteStorage(GoogleDriveService.ROOT_DIR_ID, trackedPath.toString());
        }
        localChangesWatcher.start();
        remoteChangesWatcher.start();
        changesApplier.start();
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

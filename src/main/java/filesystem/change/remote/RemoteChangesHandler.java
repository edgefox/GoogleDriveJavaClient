package filesystem.change.remote;

import filesystem.FileMetadata;
import filesystem.FileSystem;
import filesystem.Trie;
import filesystem.change.FileSystemChange;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import service.GoogleDriveService;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * User: Ivan Lyutov
 * Date: 11/21/13
 * Time: 2:28 PM
 */
public class RemoteChangesHandler {
    private static final Logger logger = Logger.getLogger(RemoteChangesWatcher.class);
    @Inject
    private Path trackedPath;
    @Inject
    private volatile FileSystem fileSystem;
    @Inject
    private RemoteChangesWatcher remoteChangesWatcher;
    @Inject
    private GoogleDriveService googleDriveService;
    
    public void handle() {
        Set<FileSystemChange<String>> remoteChanges = remoteChangesWatcher.getChangesCopy();
        logger.info(String.format("Trying to apply remote changes: %s", remoteChanges));
        for (FileSystemChange<String> change : remoteChanges) {
            tryHandleRemoteChange(change, 3);
        }
    }

    private void tryHandleRemoteChange(FileSystemChange<String> change, int triesLeft) {
        boolean success = true;
        try {
            Trie<String, FileMetadata> imageFile = fileSystem.get(change.getId());
            if (imageFile == null && !change.isRemoved()) {
                if (change.isDir()) {
                    Path parentPath = convertRemoteIdToLocal(change.getParentId());

                    if (parentPath == null) {
                        throw new IllegalStateException(String.format("Unable to handle change: %s", change));
                    }

                    File directory = new File(parentPath.toFile(), change.getTitle());
                    FileUtils.forceMkdir(directory);
                    FileMetadata fileMetadata = new FileMetadata(change.getId(), change.getTitle(), change.isDir(), null);
                    fileSystem.update(Paths.get(directory.toURI()), fileMetadata);
                } else {
                    downloadNewFile(change);
                }
            } else if (imageFile != null) {
                File localFile = fileSystem.getPath(imageFile).toFile();
                if (change.isRemoved()) {
                    deleteLocalFile(imageFile, localFile);
                }

                if (!change.getParentId().equals(imageFile.getModel().getId())) {
                    moveLocalFile(change, imageFile, localFile);
                } else {
                    updateLocalFile(change, imageFile, localFile);
                }
            }
        } catch (Throwable e) {
            logger.warn(String.format("Failed to apply change: %s", change), e);
            success = false;
            if (triesLeft == 0) {
                remoteChangesWatcher.changeHandled(change);
            } else {
                tryHandleRemoteChange(change, --triesLeft);
            }
        } finally {
            if (success) {
                logger.info(String.format("Сhange has been successfully applied: %s", change));
                remoteChangesWatcher.changeHandled(change);
            }
        }
    }

    private void moveLocalFile(FileSystemChange<String> change, Trie<String, FileMetadata> imageFile, File localFile) throws IOException {
        Path parentPath = convertRemoteIdToLocal(change.getParentId());
        File moveTo = parentPath.toFile();
        FileUtils.copyFileToDirectory(localFile, moveTo);
        localFile.delete();
        fileSystem.delete(imageFile);
        fileSystem.get(parentPath).addChild(imageFile);
    }

    private Path convertRemoteIdToLocal(String id) {
        return fileSystem.getPath(fileSystem.get(id));
    }

    private void updateLocalFile(FileSystemChange<String> change,
                                 Trie<String, FileMetadata> imageFile,
                                 File localFile) throws InterruptedException, IOException {
        FileMetadata fileMetadata = googleDriveService.downloadFile(change.getId(), localFile);
        imageFile.setModel(fileMetadata);
    }

    private void deleteLocalFile(Trie<String, FileMetadata> imageFile, File localFile) {
        localFile.delete();
        fileSystem.delete(imageFile);
    }

    private void downloadNewFile(FileSystemChange<String> change) throws InterruptedException, IOException {
        Trie<String, FileMetadata> parent = fileSystem.get(change.getParentId());

        if (parent == null) {
            throw new IllegalStateException(String.format("Unable to handle change: %s", change));
        }

        File parentFile = fileSystem.getPath(parent).toFile();
        File localFile = new File(parentFile, change.getTitle());
        FileMetadata fileMetadata = googleDriveService.downloadFile(change.getId(), localFile);
        fileSystem.update(Paths.get(localFile.toURI()), fileMetadata);
    }
}

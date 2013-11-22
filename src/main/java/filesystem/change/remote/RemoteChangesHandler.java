package filesystem.change.remote;

import com.google.inject.Singleton;
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
import java.util.HashSet;
import java.util.Set;

/**
 * User: Ivan Lyutov
 * Date: 11/21/13
 * Time: 2:28 PM
 */
@Singleton
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
    private Set<Path> handledPaths = new HashSet<>();

    public Set<Path> handle() {
        handledPaths.clear();
        Set<FileSystemChange<String>> remoteChanges = remoteChangesWatcher.getChangesCopy();
        logger.info(String.format("Trying to apply remote changes: %s", remoteChanges));
        for (FileSystemChange<String> change : remoteChanges) {
            tryHandleRemoteChange(change, 3);
        }
        return handledPaths;
    }

    private void tryHandleRemoteChange(FileSystemChange<String> change, int triesLeft) {
        boolean success = true;
        try {
            Trie<String, FileMetadata> imageFile = fileSystem.get(change.getId());
            if (imageFile == null && !change.isRemoved()) {
                if (change.isDir()) {
                    createDirectory(change);
                } else {
                    downloadNewFile(change);
                }
            } else if (imageFile != null) {
                File localFile = fileSystem.getPath(imageFile).toFile();
                if (change.isRemoved()) {
                    deleteLocalFile(imageFile, localFile);
                }

                if (!change.getParentId().equals(imageFile.getParent().getModel().getId())) {
                    moveLocalFile(change, imageFile, localFile);
                } else {
                    if (!change.isDir()) {
                        updateLocalFile(change, imageFile, localFile);
                    }
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

    private void createDirectory(FileSystemChange<String> change) throws IOException {
        Path parentPath = convertRemoteIdToLocal(change.getParentId());

        if (parentPath == null) {
            throw new IllegalStateException(String.format("Unable to handle change: %s", change));
        }

        Path fullParentPath = fileSystem.getPath(fileSystem.get(change.getParentId()));
        File directory = new File(fullParentPath.toFile(), change.getTitle());
        FileUtils.forceMkdir(directory);
        FileMetadata fileMetadata = new FileMetadata(change.getId(), change.getTitle(), change.isDir(), null);
        Path newDirectoryPath = Paths.get(directory.getAbsolutePath());
        fileSystem.update(trackedPath.relativize(newDirectoryPath), fileMetadata);
        handledPaths.add(newDirectoryPath);
    }

    private void moveLocalFile(FileSystemChange<String> change, Trie<String, FileMetadata> imageFile, File localFile) throws IOException {
        Path fullParentPath = fileSystem.getPath(fileSystem.get(change.getParentId()));
        File moveTo = fullParentPath.toFile();        
        FileUtils.copyFileToDirectory(localFile, moveTo);
        localFile.delete();
        fileSystem.move(imageFile, fileSystem.get(change.getParentId()));
        handledPaths.add(Paths.get(localFile.toURI()));
    }

    private void updateLocalFile(FileSystemChange<String> change,
                                 Trie<String, FileMetadata> imageFile,
                                 File localFile) throws InterruptedException, IOException {
        FileMetadata fileMetadata = googleDriveService.downloadFile(change.getId(), localFile);
        imageFile.setModel(fileMetadata);
        handledPaths.add(Paths.get(localFile.toURI()));
    }

    private void deleteLocalFile(Trie<String, FileMetadata> imageFile, File localFile) {
        localFile.delete();
        fileSystem.delete(imageFile);
        handledPaths.add(Paths.get(localFile.toURI()));
    }

    private void downloadNewFile(FileSystemChange<String> change) throws InterruptedException, IOException {
        Trie<String, FileMetadata> parent = fileSystem.get(change.getParentId());

        if (parent == null) {
            throw new IllegalStateException(String.format("Unable to handle change: %s", change));
        }

        File parentFile = fileSystem.getPath(parent).toFile();
        File localFile = new File(parentFile, change.getTitle());
        FileMetadata fileMetadata = googleDriveService.downloadFile(change.getId(), localFile);
        Path newFilePath = Paths.get(localFile.toURI());
        fileSystem.update(trackedPath.relativize(newFilePath), fileMetadata);
        handledPaths.add(newFilePath);        
    }

    private Path convertRemoteIdToLocal(String id) {
        return trackedPath.relativize(fileSystem.getPath(fileSystem.get(id)));
    }
}

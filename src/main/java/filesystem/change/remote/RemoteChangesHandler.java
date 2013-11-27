package filesystem.change.remote;

import javax.inject.Singleton;
import filesystem.FileMetadata;
import filesystem.FileSystem;
import filesystem.Trie;
import filesystem.change.FileSystemChange;
import filesystem.change.local.LocalChangesWatcher;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import service.GoogleDriveService;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    private Path trackedPath;
    @Inject
    private volatile FileSystem fileSystem;
    @Inject
    private volatile RemoteChangesWatcher remoteChangesWatcher;
    @Inject
    private volatile LocalChangesWatcher localChangesWatcher;
    @Inject
    private GoogleDriveService googleDriveService;
    private Set<Path> handledPaths = new HashSet<>();

    @Inject
    public RemoteChangesHandler(Path trackedPath) {
        this.trackedPath = trackedPath;
    }

    public void handle() {
        handledPaths.clear();
        Set<FileSystemChange<String>> remoteChanges = remoteChangesWatcher.getChangesCopy();
        logger.info(String.format("Trying to apply remote changes: %s", remoteChanges));
        for (FileSystemChange<String> change : remoteChanges) {
            tryHandleRemoteChange(change, 3);
        }
        localChangesWatcher.ignoreChanges(handledPaths);
    }

    void tryHandleRemoteChange(FileSystemChange<String> change, int triesLeft) {
        boolean success = true;
        try {
            Trie<String, FileMetadata> imageFile = fileSystem.get(change.getId());
            boolean isNewEntry = imageFile == null;
            if (isNewEntry) {
                handleNewEntry(change);
            } else {
                handleExistingEntry(change, imageFile);
            }
        } catch (Exception e) {
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

    void handleNewEntry(FileSystemChange<String> change) throws IOException, InterruptedException {
        if (!change.isRemoved()) {
            if (change.isDir()) {
                createDirectory(change);
            } else {
                downloadNewFile(change);
            }
        }
    }

    void handleExistingEntry(FileSystemChange<String> change, Trie<String, FileMetadata> imageFile) throws IOException, InterruptedException {
        File localFile = fileSystem.getFullPath(imageFile).toFile();
        if (change.isRemoved()) {
            deleteLocalFile(imageFile, localFile);
        } else if (isMoved(change, imageFile)) {
            moveLocalFile(change, imageFile);
        } else if (!change.isDir()) {
            updateLocalFile(change, imageFile, localFile);
        }
    }

    boolean isMoved(FileSystemChange<String> change, Trie<String, FileMetadata> imageFile) {
        return !change.getTitle().equals(imageFile.getKey()) ||
                !change.getParentId().equals(imageFile.getParent().getModel().getId());
    }

    void createDirectory(FileSystemChange<String> change) throws IOException {
        Path parentPath = convertRemoteIdToLocal(change.getParentId());

        if (parentPath == null) {
            throw new IllegalStateException(String.format("Unable to handle change: %s", change));
        }

        Path fullParentPath = fileSystem.getFullPath(fileSystem.get(change.getParentId()));
        File directory = new File(fullParentPath.toFile(), change.getTitle());
        FileUtils.forceMkdir(directory);
        FileMetadata fileMetadata = new FileMetadata(change.getId(), change.getTitle(), change.isDir(), null);
        Path newDirectoryPath = Paths.get(directory.getAbsolutePath());
        fileSystem.update(trackedPath.relativize(newDirectoryPath), fileMetadata);
        handledPaths.add(newDirectoryPath);
    }

    void moveLocalFile(FileSystemChange<String> change,
                       Trie<String, FileMetadata> imageFile) throws IOException {
        Path source = fileSystem.getFullPath(imageFile);
        Trie<String, FileMetadata> parentImageFile = fileSystem.get(change.getParentId());
        Path destination = fileSystem.getFullPath(parentImageFile).resolve(change.getTitle());
        Files.move(source, destination);
        if (!imageFile.getKey().equals(change.getTitle())) {
            imageFile.setKey(change.getTitle());
        }
        fileSystem.move(imageFile, parentImageFile);
        handledPaths.add(destination);
        if (change.isDir()) {
            Set<String> allChildrenIds = googleDriveService.getAllChildrenIds(change.getId());
            for (String childId : allChildrenIds) {
                Path path = fileSystem.getFullPath(fileSystem.get(childId));
                handledPaths.add(path);
            }

        }
    }

    void updateLocalFile(FileSystemChange<String> change,
                         Trie<String, FileMetadata> imageFile,
                         File localFile) throws InterruptedException, IOException {
        FileMetadata fileMetadata = googleDriveService.downloadFile(change.getId(), localFile);
        imageFile.setModel(fileMetadata);
        handledPaths.add(Paths.get(localFile.toURI()));
    }

    void deleteLocalFile(Trie<String, FileMetadata> imageFile, File localFile) {
        localFile.delete();
        fileSystem.delete(imageFile);
        handledPaths.add(Paths.get(localFile.toURI()));
    }

    void downloadNewFile(FileSystemChange<String> change) throws InterruptedException, IOException {
        Trie<String, FileMetadata> parent = fileSystem.get(change.getParentId());

        if (parent == null) {
            throw new IllegalStateException(String.format("Unable to handle change: %s", change));
        }

        File parentFile = fileSystem.getFullPath(parent).toFile();
        File localFile = new File(parentFile, change.getTitle());
        FileMetadata fileMetadata = googleDriveService.downloadFile(change.getId(), localFile);
        Path newFilePath = Paths.get(localFile.toURI());
        fileSystem.update(trackedPath.relativize(newFilePath), fileMetadata);
        handledPaths.add(newFilePath);
    }

    private Path convertRemoteIdToLocal(String id) {
        return trackedPath.relativize(fileSystem.getFullPath(fileSystem.get(id)));
    }
}

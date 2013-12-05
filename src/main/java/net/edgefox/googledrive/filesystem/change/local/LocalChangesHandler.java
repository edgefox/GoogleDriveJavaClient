package net.edgefox.googledrive.filesystem.change.local;

import javax.inject.Singleton;
import net.edgefox.googledrive.filesystem.FileMetadata;
import net.edgefox.googledrive.filesystem.FileSystem;
import net.edgefox.googledrive.filesystem.Trie;
import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.filesystem.change.remote.RemoteChangesWatcher;
import net.edgefox.googledrive.util.Notifier;
import org.apache.log4j.Logger;
import net.edgefox.googledrive.service.GoogleDriveService;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * User: Ivan Lyutov
 * Date: 11/21/13
 * Time: 2:28 PM
 */
@Singleton
public class LocalChangesHandler {
    private static Logger logger = Logger.getLogger(LocalChangesHandler.class);
    @Inject
    private volatile FileSystem fileSystem;
    @Inject
    private volatile LocalChangesWatcher localChangesWatcher;
    @Inject
    private volatile RemoteChangesWatcher remoteChangesWatcher; 
    @Inject
    private GoogleDriveService googleDriveService;
    private Set<String> handledIds = new HashSet<>();

    public void handle() {
        handledIds.clear();
        Set<FileSystemChange<Path>> localChanges = localChangesWatcher.getChangesCopy();
        logger.info(String.format("Trying to apply local changes: %s", localChanges));
        for (FileSystemChange<Path> change : localChanges) {
            tryHandleLocalChange(change, 3);
        }
        logger.info(String.format("Local changes have been applied: %s", localChanges));

        remoteChangesWatcher.ignoreChanges(handledIds);
    }

    void tryHandleLocalChange(FileSystemChange<Path> change, int triesLeft) {
        boolean success = true;
        try {
            logger.info(String.format("Trying to apply change: %s", change));
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
                localChangesWatcher.changeHandled(change);
            } else {
                tryHandleLocalChange(change, --triesLeft);
            }
        } finally {
            if (success) {
                logger.info(String.format("Сhange has been successfully applied: %s", change));
                localChangesWatcher.changeHandled(change);
            }
        }
    }

    void handleNewEntry(FileSystemChange<Path> change) throws IOException {
        if (!change.isRemoved()) {
            if (change.isDir()) {
                createDirectory(change);
            } else {
                uploadLocalFile(change);
            }
        }
    }

    void handleExistingEntry(FileSystemChange<Path> change, Trie<String, FileMetadata> imageFile) throws IOException {
        if (change.isRemoved()) {
            deleteRemoteFile(imageFile);
        } else {
            if (change.isDir()) {
                createDirectory(change);
            } else {
                uploadLocalFile(change);
            }
        }
    }

    void createDirectory(FileSystemChange<Path> change) throws IOException {
        Trie<String, FileMetadata> parentImageFile = fileSystem.get(change.getParentId());
        String parentId = parentImageFile.getModel().getId();
        FileMetadata fileMetadata = googleDriveService.createOrGetDirectory(parentId, change.getTitle());
        fileSystem.update(change.getId(), fileMetadata);
        handledIds.add(fileMetadata.getId());
        Notifier.showMessage("Local update", String.format("Created directory %s", change.getId()));
    }

    void deleteRemoteFile(Trie<String, FileMetadata> imageFile) throws IOException {
        Path pathToDelete = fileSystem.getFullPath(imageFile);
        googleDriveService.delete(imageFile.getModel().getId());
        fileSystem.delete(imageFile);
        handledIds.add(imageFile.getModel().getId());
        Notifier.showMessage("Local update", String.format("Deleted %s", pathToDelete));
    }

    void uploadLocalFile(FileSystemChange<Path> change) throws IOException {
        Trie<String, FileMetadata> parent = fileSystem.get(change.getParentId());
        if (parent == null) {
            throw new IllegalStateException(String.format("Unable to handle change: %s", change));
        }

        FileMetadata fileMetadata = googleDriveService.upload(parent.getModel().getId(),
                                                              change.getId().toFile());
        fileSystem.update(change.getId(), fileMetadata);
        handledIds.add(fileMetadata.getId());
        Notifier.showMessage("Local update", String.format("File update %s", change.getId()));
    }
}

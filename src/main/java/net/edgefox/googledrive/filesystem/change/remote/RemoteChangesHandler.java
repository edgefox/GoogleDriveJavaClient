package net.edgefox.googledrive.filesystem.change.remote;

import javax.inject.Singleton;
import net.edgefox.googledrive.filesystem.FileMetadata;
import net.edgefox.googledrive.filesystem.FileSystem;
import net.edgefox.googledrive.filesystem.Trie;
import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.filesystem.change.local.LocalChangesWatcher;
import net.edgefox.googledrive.util.IOUtils;
import net.edgefox.googledrive.util.Notifier;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import net.edgefox.googledrive.service.GoogleDriveService;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static java.lang.String.*;
import static net.edgefox.googledrive.util.IOUtils.*;

/**
 * User: Ivan Lyutov
 * Date: 11/21/13
 * Time: 2:28 PM
 */
@Singleton
public class RemoteChangesHandler {
    private static Logger logger = Logger.getLogger(RemoteChangesWatcher.class);
    @Inject
    private volatile FileSystem fileSystem;
    @Inject
    private volatile RemoteChangesWatcher remoteChangesWatcher;
    @Inject
    private volatile LocalChangesWatcher localChangesWatcher;
    @Inject
    private GoogleDriveService googleDriveService;
    private Set<Path> handledPaths = new HashSet<>();

    public void handle() {
        handledPaths.clear();
        Set<FileSystemChange<String>> remoteChanges = remoteChangesWatcher.getChangesCopy();
        logger.info(format("Trying to apply remote changes: %s", remoteChanges));
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
            logger.warn(format("Failed to apply change: %s", change), e);
            success = false;
            if (triesLeft == 0) {
                remoteChangesWatcher.changeHandled(change);
            } else {
                tryHandleRemoteChange(change, --triesLeft);
            }
        } finally {
            if (success) {
                logger.info(format("Сhange has been successfully applied: %s", change));
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
        } else if (!change.isDir() && 
                   !change.getMd5CheckSum().equals(imageFile.getModel().getCheckSum())) { 
            updateLocalFile(change, imageFile, localFile);
        }
    }

    boolean isMoved(FileSystemChange<String> change, Trie<String, FileMetadata> imageFile) {
        return !change.getTitle().equals(imageFile.getKey()) ||
                !change.getParentId().equals(imageFile.getParent().getModel().getId());
    }

    void createDirectory(FileSystemChange<String> change) throws IOException {
        Trie<String, FileMetadata> parentImage = fileSystem.get(change.getParentId());
        Path fullParentPath = fileSystem.getFullPath(parentImage);

        if (fullParentPath == null) {
            throw new IllegalStateException(format("Unable to handle change: %s", change));
        }

        Path newDirectoryPath = fullParentPath.resolve(change.getTitle());
        safeCreateDirectory(newDirectoryPath);
        FileMetadata fileMetadata = new FileMetadata(change.getId(), change.getTitle(), change.isDir(), null);
        fileSystem.update(newDirectoryPath, fileMetadata);
        handledPaths.add(newDirectoryPath);
        Notifier.showRemoteChangeMessage(format("Added new directory %s", newDirectoryPath.toString()));
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
        handledPaths.add(source);
        handledPaths.add(destination);
        Notifier.showRemoteChangeMessage(format("Moved %s to %s", source, destination));
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
        Notifier.showRemoteChangeMessage(format("Updated file %s", localFile.getAbsolutePath()));
    }

    void deleteLocalFile(Trie<String, FileMetadata> imageFile, File localFile) throws IOException {
        if (localFile.exists()) {
            FileUtils.forceDelete(localFile);
            fileSystem.delete(imageFile);        
            Notifier.showRemoteChangeMessage(format("Deleted file %s", localFile.getAbsolutePath()));
            handledPaths.add(Paths.get(localFile.toURI()));
            if (imageFile.getModel().isDir()) {
                Set<String> allChildrenIds = googleDriveService.getAllChildrenIds(imageFile.getModel().getId());
                for (String childId : allChildrenIds) {
                    Path path = fileSystem.getFullPath(fileSystem.get(childId));
                    handledPaths.add(path);
                }
            }
        }
    }

    void downloadNewFile(FileSystemChange<String> change) throws InterruptedException, IOException {
        Trie<String, FileMetadata> parent = fileSystem.get(change.getParentId());

        if (parent == null) {
            throw new IllegalStateException(format("Unable to handle change: %s", change));
        }

        File parentFile = fileSystem.getFullPath(parent).toFile();
        File localFile = new File(parentFile, change.getTitle());
        FileMetadata fileMetadata = googleDriveService.downloadFile(change.getId(), localFile);
        Path newFilePath = Paths.get(localFile.toURI());
        fileSystem.update(newFilePath, fileMetadata);
        handledPaths.add(newFilePath);
        Notifier.showRemoteChangeMessage(format("Added new file %s", localFile.getAbsolutePath()));
    }
}

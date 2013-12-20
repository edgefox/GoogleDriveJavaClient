package net.edgefox.googledrive.service;

import com.google.api.services.drive.model.ParentReference;
import com.google.inject.Singleton;
import net.edgefox.googledrive.filesystem.FileMetadata;
import net.edgefox.googledrive.filesystem.FileSystem;
import net.edgefox.googledrive.filesystem.Trie;
import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.filesystem.change.RemoteChangePackage;
import net.edgefox.googledrive.util.GoogleDriveUtils;
import net.edgefox.googledrive.util.Notifier;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static net.edgefox.googledrive.util.GoogleDriveUtils.*;
import static net.edgefox.googledrive.util.IOUtils.getFileMd5CheckSum;
import static net.edgefox.googledrive.util.IOUtils.safeCreateDirectory;

/**
 * User: Ivan Lyutov
 * Date: 12/1/13
 * Time: 12:43 PM
 */
@Singleton
public class Storage {
    @Inject
    private Path trackedPath;
    @Inject
    private FileSystem fileSystem;
    @Inject
    private GoogleDriveService googleDriveService;
    private Path sharedRootPath;

    public void checkout() throws IOException, InterruptedException {
        Notifier.showSystemMessage("Trying to perform initial sync");
        initSharedStorage();
        Map<String, com.google.api.services.drive.model.File> remoteFileRegistry = googleDriveService.listAllFiles();
        Set<File> handledFiles = checkoutRemote(remoteFileRegistry);
        if (fileSystem.getFileSystemRevision() > 0L) {
            handleDeletedRemotely();
        }
        handledFiles.add(sharedRootPath.toFile());
        checkoutLocal(trackedPath.toFile(), handledFiles);
        Notifier.showSystemMessage("Initial sync is completed");
    }

    void handleDeletedRemotely() throws IOException {
        RemoteChangePackage changes = googleDriveService.getChanges(fileSystem.getFileSystemRevision());
        for (FileSystemChange<String> change : changes.getChanges()) {
            if (change.isRemoved()) {
                Trie<String, FileMetadata> entry = fileSystem.get(change.getId());
                if (entry != null) {
                    Path path = fileSystem.getPath(entry);
                    if (Files.exists(path)) {
                        FileUtils.forceDelete(path.toFile());
                    }
                    fileSystem.delete(path);
                }
            }
        }
    }

    Set<File> checkoutRemote(Map<String, com.google.api.services.drive.model.File> fileRegistry) throws IOException {
        Set<File> processedFiles = new HashSet<>();
        for (Map.Entry<String, com.google.api.services.drive.model.File> remoteEntry : fileRegistry.entrySet()) {
            com.google.api.services.drive.model.File remoteFile = remoteEntry.getValue();
            Trie<String, FileMetadata> entry = ensureEntry(remoteFile, fileRegistry);
            Path fullPath = fileSystem.getFullPath(entry);
            processedFiles.add(fullPath.toFile());
            if (isGoogleDir(remoteFile)) {
                safeCreateDirectory(fullPath);
            } else {
                if (!isRestrictedGoogleApp(remoteFile) && 
                    (Files.notExists(fullPath) || !getFileMd5CheckSum(fullPath).equals(remoteFile.getMd5Checksum()))) {
                    googleDriveService.downloadFile(remoteFile.getId(), fullPath.toFile());
                    entry.setModel(new FileMetadata(remoteFile));
                }
            }
        }

        return processedFiles;
    }

    void checkoutLocal(File parentFile, Set<File> handledFiles) throws IOException {
        File[] files = parentFile.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (handledFiles.contains(file) || file.toPath().startsWith(sharedRootPath)) {
                if (file.isDirectory()) {
                    checkoutLocal(file, handledFiles);
                }
            } else {
                Trie<String, FileMetadata> imageFile = fileSystem.get(file.toPath());
                if (imageFile != null) {
                    String parentId = imageFile.getParent().getModel().getId();
                    if (file.isDirectory()) {
                        FileMetadata remoteMetadata = googleDriveService.createOrGetDirectory(parentId, file.getName());
                        imageFile.setModel(remoteMetadata);
                        fileSystem.addRemoteId(remoteMetadata.getId(), imageFile);
                        checkoutLocal(file, handledFiles);
                    } else {
                        FileMetadata remoteMetadata = googleDriveService.upload(parentId, file);
                        imageFile.setModel(remoteMetadata);
                        fileSystem.addRemoteId(remoteMetadata.getId(), imageFile);
                    }
                } else {
                    String parentId = fileSystem.get(file.toPath().getParent()).getModel().getId();
                    if (file.isDirectory()) {
                        FileMetadata remoteMetadata = googleDriveService.createOrGetDirectory(parentId, file.getName());
                        fileSystem.update(file.toPath(), remoteMetadata);
                        checkoutLocal(file, handledFiles);
                    } else {
                        FileMetadata remoteMetadata = googleDriveService.upload(parentId, file);
                        fileSystem.update(file.toPath(), remoteMetadata);
                    }
                }
            }
        }
    }

    private Trie<String, FileMetadata> ensureEntry(com.google.api.services.drive.model.File remoteFile,
                                                   Map<String, com.google.api.services.drive.model.File> fileRegistry) throws IOException {
        Trie<String, FileMetadata> entry = fileSystem.get(remoteFile.getId());
        String parentId;
        if (remoteFile.getParents().isEmpty()) {
            parentId = "shared";
        } else {
            ParentReference parentReference = remoteFile.getParents().get(0);
            parentId = parentReference.getIsRoot() ?
                       GoogleDriveService.ROOT_DIR_ID :
                       parentReference.getId();
        }

        Trie<String, FileMetadata> parentEntry;
        if (GoogleDriveService.ROOT_DIR_ID.equals(parentId) || 
            "shared".equals(parentId)) {
            parentEntry = fileSystem.get(parentId);
        } else {
            if (fileRegistry.containsKey(parentId)) {
                parentEntry = ensureEntry(fileRegistry.get(parentId), fileRegistry);
            } else {
                parentEntry = fileSystem.get("shared");
            }
        }
        if (entry == null) {
            Path fullParentPath = fileSystem.getFullPath(parentEntry);
            fileSystem.update(fullParentPath.resolve(remoteFile.getTitle()), new FileMetadata(remoteFile));
            entry = fileSystem.get(remoteFile.getId());
        }

        Path fullPath = fileSystem.getFullPath(entry);
        if (!entry.getParent().getModel().getId().equals(parentId)) {
            fileSystem.move(entry, parentEntry);
            Path newPath = fileSystem.getFullPath(parentEntry).resolve(remoteFile.getTitle());
            if (Files.exists(fullPath)) {
                Files.move(fullPath, newPath);
            }
        }

        return entry;
    }

    private void initSharedStorage() throws IOException {
        sharedRootPath = trackedPath.resolve("Shared with me");
        Trie<String, FileMetadata> sharedRoot = fileSystem.get("shared");
        if (sharedRoot == null) {
            fileSystem.update(sharedRootPath, new FileMetadata("shared", "Shared with me", true, null));
        }

        safeCreateDirectory(sharedRootPath);
    }
}

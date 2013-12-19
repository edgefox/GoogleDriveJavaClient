package net.edgefox.googledrive.filesystem;

import com.google.inject.Singleton;
import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.filesystem.change.RemoteChangePackage;
import net.edgefox.googledrive.service.GoogleDriveService;
import net.edgefox.googledrive.util.Notifier;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        sharedRootPath = trackedPath.resolve("Shared with me");
        Set<File> handledFiles = checkoutRemote(GoogleDriveService.ROOT_DIR_ID, trackedPath);
        checkoutShared(handledFiles);
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

    Set<File> checkoutRemote(String id, Path path) throws IOException, InterruptedException {
        Set<File> handledFiles = new HashSet<>();
        if (Files.isSameFile(sharedRootPath, path)) {
            return handledFiles;
        }
        List<FileMetadata> root = googleDriveService.listDirectory(id);
        for (FileMetadata remoteMetadata : root) {
            Path childPath = path.resolve(remoteMetadata.getTitle());
            Trie<String,FileMetadata> imageFile = fileSystem.get(remoteMetadata.getId());
            FileMetadata localMetadata;
            if (imageFile == null) {
                localMetadata = remoteMetadata;
                fileSystem.update(childPath, remoteMetadata);
            } else {
                localMetadata = imageFile.getModel();
                imageFile.setModel(remoteMetadata);
                fileSystem.addRemoteId(remoteMetadata.getId(), imageFile);
            }

            if (remoteMetadata.isDir()) {
                safeCreateDirectory(childPath);
                handledFiles.addAll(checkoutRemote(remoteMetadata.getId(), childPath));
            } else if (!Files.exists(childPath) ||
                    localMetadata.getCheckSum() == null ||
                    !localMetadata.getCheckSum().equals(remoteMetadata.getCheckSum())) {
                googleDriveService.downloadFile(remoteMetadata.getId(), childPath.toFile());
            }
            handledFiles.add(childPath.toFile());
        }

        return handledFiles;
    }
    
    void checkoutShared(Set<File> handledFiles) throws IOException, InterruptedException {
        initSharedStorage();

        List<FileMetadata> sharedFiles = googleDriveService.listSharedOrphanFiles();
        for (FileMetadata sharedFile : sharedFiles) {
            Path filePath = sharedRootPath.resolve(sharedFile.getTitle());
            Trie<String, FileMetadata> imageFile = fileSystem.get(sharedFile.getId());
            if (imageFile == null) {
                fileSystem.update(filePath, sharedFile);
            }
            if (sharedFile.isDir()) {
                safeCreateDirectory(filePath);
                handledFiles.addAll(checkoutRemote(sharedFile.getId(), filePath));
            } else if (!Files.exists(filePath) ||
                       sharedFile.getCheckSum() == null ||
                       !getFileMd5CheckSum(filePath).equals(sharedFile.getCheckSum())) {
                googleDriveService.downloadFile(sharedFile.getId(), filePath.toFile());
            }
            handledFiles.add(filePath.toFile());
        }
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

    private void initSharedStorage() throws IOException {
        Trie<String, FileMetadata> sharedRoot = fileSystem.get("shared");
        if (sharedRoot == null) {
            fileSystem.update(sharedRootPath, new FileMetadata("shared", "Shared with me", true, null));
        }

        safeCreateDirectory(sharedRootPath);
    }
}

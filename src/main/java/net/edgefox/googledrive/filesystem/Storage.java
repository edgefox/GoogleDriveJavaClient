package net.edgefox.googledrive.filesystem;

import com.google.inject.Singleton;
import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.filesystem.change.RemoteChangePackage;
import net.edgefox.googledrive.service.GoogleDriveService;
import net.edgefox.googledrive.util.IOUtils;
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

import static net.edgefox.googledrive.util.IOUtils.*;

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
    
    public void checkout() throws IOException, InterruptedException {
        Notifier.showMessage("System notification", "Trying to perform initial sync");
        Set<File> handledFiles = checkoutRemote(GoogleDriveService.ROOT_DIR_ID, trackedPath);
        if (fileSystem.getFileSystemRevision() > 0L) {
            handleDeletedRemotely();
        }
        checkoutLocal(trackedPath.toFile(), handledFiles);
        Notifier.showMessage("System notification", "Initial sync is completed");
    }

    void handleDeletedRemotely() throws IOException {
        RemoteChangePackage changes = googleDriveService.getChanges(fileSystem.getFileSystemRevision());
        for (FileSystemChange<String> change : changes.getChanges()) {
            if (change.isRemoved()) {
                Path path = fileSystem.getPath(fileSystem.get(change.getId()));
                if (Files.exists(path)) {
                    FileUtils.forceDelete(path.toFile());
                }
                fileSystem.delete(path);
            }
        }
    }

    Set<File> checkoutRemote(String id, Path path) throws IOException, InterruptedException {
        List<FileMetadata> root = googleDriveService.listDirectory(id);
        Set<File> handledFiles = new HashSet<>();
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
                checkoutRemote(remoteMetadata.getId(), childPath);
            } else if (!Files.exists(childPath) ||
                    localMetadata.getCheckSum() == null ||
                    !localMetadata.getCheckSum().equals(remoteMetadata.getCheckSum())) {
                googleDriveService.downloadFile(remoteMetadata.getId(), childPath.toFile());
            }
            handledFiles.add(childPath.toFile());
        }

        return handledFiles;
    }       

    void checkoutLocal(File parentFile, Set<File> handledFiles) throws IOException {
        File[] files = parentFile.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (handledFiles.contains(file)) {
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
}

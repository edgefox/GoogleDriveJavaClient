package net.edgefox.googledrive.filesystem;

import com.google.inject.Singleton;
import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.filesystem.change.RemoteChangePackage;
import net.edgefox.googledrive.service.GoogleDriveService;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        Set<File> handledFiles = checkoutRemote(GoogleDriveService.ROOT_DIR_ID, trackedPath);
        if (fileSystem.getFileSystemRevision() > 0L) {
            handleDeletedRemotely();
        }
        checkoutLocal(trackedPath.toFile(), handledFiles);
    }

    void handleDeletedRemotely() throws IOException {
        RemoteChangePackage changes = googleDriveService.getChanges(fileSystem.getFileSystemRevision());
        for (FileSystemChange<String> change : changes.getChanges()) {
            if (change.isRemoved()) {
                Path path = fileSystem.getPath(fileSystem.get(change.getId()));
                FileUtils.forceDelete(path.toFile());
                fileSystem.delete(path);
            }
        }
    }

    Set<File> checkoutRemote(String id, Path path) throws IOException, InterruptedException {
        List<FileMetadata> root = googleDriveService.listDirectory(id);
        Set<File> handledFiles = new HashSet<>();
        for (FileMetadata remoteMetadata : root) {
            Path childPath = path.resolve(remoteMetadata.getTitle());
            Path imagePath = trackedPath.relativize(childPath);
            FileMetadata localMetadata;
            if (!Files.exists(childPath)) {
                localMetadata = remoteMetadata;
                fileSystem.update(imagePath, remoteMetadata);
            } else {
                Trie<String,FileMetadata> imageFile = fileSystem.get(imagePath);
                localMetadata = imageFile.getModel();
                imageFile.setModel(remoteMetadata);
                fileSystem.addRemoteId(remoteMetadata.getId(), imageFile);
            }

            if (remoteMetadata.isDir()) {
                if (!Files.exists(childPath)) {
                    Files.createDirectory(childPath);
                }
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
                Trie<String, FileMetadata> imageFile = fileSystem.get(trackedPath.relativize(file.toPath()));
                String parentId = imageFile.getParent().getModel().getId();
                FileMetadata remoteMetadata;
                if (file.isDirectory()) {
                    remoteMetadata = googleDriveService.createOrGetDirectory(parentId, file.getName());
                    checkoutLocal(file, handledFiles);
                } else {
                    remoteMetadata = googleDriveService.upload(parentId, file);
                }
                imageFile.setModel(remoteMetadata);
                fileSystem.addRemoteId(remoteMetadata.getId(), imageFile);
            }
        }
    }
}

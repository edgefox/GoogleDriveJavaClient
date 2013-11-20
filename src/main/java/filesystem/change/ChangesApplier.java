package filesystem.change;

import filesystem.FileMetadata;
import filesystem.FileSystem;
import filesystem.Trie;
import filesystem.change.watcher.LocalChangesWatcher;
import filesystem.change.watcher.RemoteChangesWatcher;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import service.GoogleDriveService;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * User: Ivan Lyutov
 * Date: 10/15/13
 * Time: 5:33 PM
 */
public class ChangesApplier {
    private static final Logger logger = Logger.getLogger(ChangesApplier.class);
    @Inject
    private LocalChangesWatcher localChangesWatcher;
    @Inject
    private RemoteChangesWatcher remoteChangesWatcher;
    @Inject
    private FileSystem fileSystem;
    @Inject
    private GoogleDriveService googleDriveService;
    @Inject
    private Path trackedPath;

    private ScheduledExecutorService executorService;

    public ChangesApplier() {
        executorService = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        executorService.scheduleWithFixedDelay(new MergeTask(), 0, 15, TimeUnit.SECONDS);
        logger.info("changes applier has been successfully started");
    }

    public void stop() {
        executorService.shutdownNow();
        logger.info("changes applier has been successfully stopped");
    }

    class MergeTask implements Runnable {

        @Override
        public void run() {
            logger.info("Change merge iteration started");
            handleLocalChanges(localChangesWatcher.getChangesCopy());
            handleRemoteChanges(remoteChangesWatcher.getChangesCopy());
            logger.info("Change merge iteration ended");
        }

        private void handleLocalChanges(Queue<FileSystemChange<Path>> localChanges) {
            logger.info(String.format("Trying to apply local changes: %s", localChanges));
            for (FileSystemChange<Path> change : localChanges) {
                boolean success = true;
                try {
                    logger.info(String.format("Trying to apply change: %s", change));
                    Trie<String, FileMetadata> imageFile = fileSystem.get(trackedPath.relativize(change.getId()));
                    if (imageFile == null && !change.isRemoved()) {
                        if (change.isDir()) {
                            Trie<String, FileMetadata> parentImageFile = fileSystem.get(trackedPath.relativize(change.getParentId()));
                            String parentId = parentImageFile.getModel().getId();
                            FileMetadata fileMetadata = googleDriveService.createDirectory(parentId, change.getTitle());
                            fileSystem.update(change.getId(), fileMetadata);
                        } else {
                            uploadLocalFile(change);
                        }
                    } else if (imageFile != null) {
                        if (change.isRemoved()) {
                            deleteRemoteFile(imageFile);
                        } else if (!change.getParentId().equals(convertRemoteIdToLocal(imageFile.getModel().getId()))) {
                            //TODO: implement move event handling for local changes
                        } else {
                            uploadLocalFile(change);
                        }
                    }
                } catch (Throwable e) {
                    logger.warn(String.format("Failed to apply change: %s", change), e);
                    success = false;
                } finally {
                    if (success) {
                        logger.info(String.format("Сhange has been successfully applied: %s", change));
                        localChangesWatcher.changeHandled(change);
                    }
                }
            }
        }

        private void deleteRemoteFile(Trie<String, FileMetadata> imageFile) throws IOException {
            googleDriveService.delete(imageFile.getModel().getId());
            fileSystem.delete(imageFile);
        }

        private void uploadLocalFile(FileSystemChange<Path> change) throws IOException {
            Trie<String, FileMetadata> parent = fileSystem.get(trackedPath.relativize(change.getParentId()));

            if (parent == null) {
                throw new IllegalStateException(String.format("Unable to handle change: %s", change));
            }

            FileMetadata fileMetadata = googleDriveService.upload(parent.getModel().getId(),
                                                                  change.getId().toFile());
            fileSystem.update(trackedPath.relativize(change.getId()), fileMetadata);
        }

        private void handleRemoteChanges(Queue<FileSystemChange<String>> remoteChanges) {
            logger.info(String.format("Trying to apply remote changes: %s", remoteChanges));
            for (FileSystemChange<String> change : remoteChanges) {
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
                } finally {
                    if (success) {
                        logger.info(String.format("Сhange has been successfully applied: %s", change));
                        remoteChangesWatcher.changeHandled(change);
                    }
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
}

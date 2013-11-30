package net.edgefox.googledrive;

import net.edgefox.googledrive.config.ConfigurationManager;
import net.edgefox.googledrive.filesystem.FileMetadata;
import net.edgefox.googledrive.filesystem.FileSystem;
import net.edgefox.googledrive.filesystem.Trie;
import net.edgefox.googledrive.filesystem.change.ChangesApplier;
import net.edgefox.googledrive.filesystem.change.local.LocalChangesWatcher;
import net.edgefox.googledrive.filesystem.change.remote.RemoteChangesWatcher;
import net.edgefox.googledrive.service.GoogleDriveService;
import net.edgefox.googledrive.util.Notifier;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * User: Ivan Lyutov
 * Date: 11/18/13
 * Time: 6:44 PM
 */
@Singleton
public class Application {
    private static final Logger logger = Logger.getLogger(Application.class);
    @Inject
    private Path trackedPath;
    @Inject
    private GoogleDriveService googleDriveService;
    @Inject
    private volatile FileSystem fileSystem;
    @Inject
    private LocalChangesWatcher localChangesWatcher;
    @Inject
    private RemoteChangesWatcher remoteChangesWatcher;
    @Inject
    private ChangesApplier changesApplier;
    @Inject
    private ConfigurationManager configurationManager;

    @Inject
    public void start() throws Exception {
        prepareGoogleDriveAuth();

        Notifier.showMessage("System Notification",
                             "Initial sync started");
        initStorage(GoogleDriveService.ROOT_DIR_ID, trackedPath.toString());
        Notifier.showMessage("System Notification",
                             "Initial sync completed");

        remoteChangesWatcher.start();
        localChangesWatcher.start();
        changesApplier.start();
    }

    private void prepareGoogleDriveAuth() throws Exception {
        if (StringUtils.isEmpty(configurationManager.getProperty("REFRESH_TOKEN"))) {
            String authUrl = googleDriveService.auth();
            System.out.println(String.format("Please follow the url to authorize the application: '%s'", authUrl));
            String newRefreshToken = googleDriveService.handleRedirect();
            configurationManager.updateProperties("REFRESH_TOKEN", newRefreshToken);
            Notifier.showMessage("System Notification", "Authorization succeeded. Starting application...");
        }
    }

    private void initStorage(String id, String path) throws IOException, InterruptedException {
        List<FileMetadata> root = googleDriveService.listDirectory(id);
        for (FileMetadata remoteMetadata : root) {
            File file = new File(path, remoteMetadata.getTitle());
            Path imagePath = trackedPath.relativize(Paths.get(file.getAbsolutePath()));
            FileMetadata localMetadata;
            if (!file.exists()) {
                localMetadata = remoteMetadata;
                fileSystem.update(imagePath, remoteMetadata);
            } else {
                Trie<String,FileMetadata> imageFile = fileSystem.get(imagePath);
                localMetadata = imageFile.getModel();
                imageFile.setModel(remoteMetadata);
            }

            if (remoteMetadata.isDir()) {
                if (!file.exists()) {
                    FileUtils.forceMkdir(file);
                }
                initStorage(remoteMetadata.getId(), file.getAbsolutePath());
            } else if (!file.exists() || 
                       localMetadata.getCheckSum() == null || 
                       !localMetadata.getCheckSum().equals(remoteMetadata.getCheckSum())) {
                googleDriveService.downloadFile(remoteMetadata.getId(), file);
            }
        }
    }
}

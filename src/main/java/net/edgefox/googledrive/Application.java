package net.edgefox.googledrive;

import net.edgefox.googledrive.config.ConfigurationManager;
import net.edgefox.googledrive.filesystem.Storage;
import net.edgefox.googledrive.filesystem.change.ChangesApplier;
import net.edgefox.googledrive.filesystem.change.local.LocalChangesWatcher;
import net.edgefox.googledrive.filesystem.change.remote.RemoteChangesWatcher;
import net.edgefox.googledrive.service.GoogleDriveService;
import net.edgefox.googledrive.util.Notifier;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * User: Ivan Lyutov
 * Date: 11/18/13
 * Time: 6:44 PM
 */
@Singleton
public class Application {
    private static Logger logger = Logger.getLogger(Application.class);
    @Inject
    private GoogleDriveService googleDriveService;
    @Inject
    private LocalChangesWatcher localChangesWatcher;
    @Inject
    private RemoteChangesWatcher remoteChangesWatcher;
    @Inject
    private ChangesApplier changesApplier;
    @Inject
    private ConfigurationManager configurationManager;
    @Inject
    private Storage storage;

    @Inject
    public void start() throws Exception {
        prepareGoogleDriveAuth();

        storage.checkout();
        
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
}

package net.edgefox.googledrive;

import net.edgefox.googledrive.config.ConfigurationManager;
import net.edgefox.googledrive.filesystem.Storage;
import net.edgefox.googledrive.filesystem.change.ChangesApplier;
import net.edgefox.googledrive.filesystem.change.local.LocalChangesWatcher;
import net.edgefox.googledrive.filesystem.change.remote.RemoteChangesWatcher;
import net.edgefox.googledrive.gui.DriveTray;
import net.edgefox.googledrive.service.GoogleDriveService;
import net.edgefox.googledrive.util.Notifier;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.Semaphore;

/**
 * User: Ivan Lyutov
 * Date: 11/18/13
 * Time: 6:44 PM
 */
@Singleton
public class Application {
    private final Logger logger = Logger.getLogger(Application.class); 
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
    private ShutdownHook shutdownHook;
    @Inject
    private Semaphore applicationSemaphore;
    @Inject
    private DriveTray driveTray;
    private boolean paused;

    @Inject
    public void start() throws Exception {
        configureGracefulShutdown();

        prepareGoogleDriveAuth();

        storage.checkout();
        
        remoteChangesWatcher.start();
        localChangesWatcher.start();
        changesApplier.start();

        driveTray.start();
    }
    
    public void pause() {
        try {
            applicationSemaphore.acquire(3);
            paused = true;
            String message = "Sync has been paused";
            Notifier.showSystemMessage(message);
            logger.info(message);
        } catch (InterruptedException e) {
            logger.warn(e);
        }
    }
    
    public void resume() {
        applicationSemaphore.release(3);
        paused = false;
        String message = "Sync has been resumed";
        Notifier.showSystemMessage(message);
        logger.info(message);
    }

    public boolean isPaused() {
        return paused;
    }

    private void configureGracefulShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(shutdownHook));
    }

    private void prepareGoogleDriveAuth() throws Exception {
        if (StringUtils.isEmpty(configurationManager.getProperty("refreshToken"))) {
            String authUrl = googleDriveService.auth();
            System.out.println(String.format("Please follow the url to authorize the application: '%s'", authUrl));
            String newRefreshToken = googleDriveService.handleRedirect();
            configurationManager.updateProperties("refreshToken", newRefreshToken);
            Notifier.showSystemMessage("Authorization succeeded. Starting application...");
        }
    }
}

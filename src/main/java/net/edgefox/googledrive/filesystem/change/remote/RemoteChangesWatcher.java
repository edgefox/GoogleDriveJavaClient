package net.edgefox.googledrive.filesystem.change.remote;

import javax.inject.Singleton;

import net.edgefox.googledrive.filesystem.FileSystem;
import net.edgefox.googledrive.filesystem.change.ChangesWatcher;
import net.edgefox.googledrive.filesystem.change.RemoteChangePackage;
import org.apache.log4j.Logger;
import net.edgefox.googledrive.service.GoogleDriveService;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * User: Ivan Lyutov
 * Date: 11/20/13
 * Time: 1:07 PM
 */
@Singleton
public class RemoteChangesWatcher extends ChangesWatcher<String> {
    private static final Logger logger = Logger.getLogger(RemoteChangesWatcher.class);
    @Inject
    private GoogleDriveService googleDriveService;
    @Inject
    private volatile FileSystem fileSystem;

    public void start() throws IOException {
        logger.info("Trying to start RemoteChangesWatcher");
        if (fileSystem.getFileSystemRevision() == 0L) {
            logger.info("Setting initial revision number");
            Long revisionNumber = googleDriveService.about().getLargestChangeId();
            fileSystem.updateFileSystemRevision(revisionNumber);
            logger.info(String.format("Initial revision number has been set to %d", revisionNumber));
        }
        executorService.scheduleWithFixedDelay(new PollTask(), 0, 10, TimeUnit.SECONDS);
        logger.info("RemoteChangesWatcher has been successfully started");
    }

    private class PollTask implements Runnable {

        @Override
        public void run() {
            try {
                RemoteChangePackage remoteChangePackage = googleDriveService.getChanges(fileSystem.getFileSystemRevision());
                changes.addAll(remoteChangePackage.getChanges());
                fileSystem.updateFileSystemRevision(remoteChangePackage.getRevisionNumber());
            } catch (IOException e) {
                logger.error("Error occurred while fetching new changes from GoogleDrive service", e);
            }
        }
    }
}

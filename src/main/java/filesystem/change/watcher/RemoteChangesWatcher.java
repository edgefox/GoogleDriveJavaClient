package filesystem.change.watcher;

import com.google.inject.Singleton;
import filesystem.FileSystemRevision;
import filesystem.change.RemoteChangePackage;
import org.apache.log4j.Logger;
import service.GoogleDriveService;

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
    private volatile FileSystemRevision fileSystemRevision;

    public void start() throws IOException {
        if (fileSystemRevision.getRevisionNumber() == 0) {
            Long revisionNumber = googleDriveService.about().getLargestChangeId();
            fileSystemRevision.setRevisionNumber(revisionNumber);
        }
        executorService.scheduleWithFixedDelay(new PollTask(), 0, 10, TimeUnit.SECONDS);
    }

    private class PollTask implements Runnable {

        @Override
        public void run() {
            try {
                RemoteChangePackage remoteChangePackage = googleDriveService.getChanges(fileSystemRevision.getRevisionNumber());
                changes.addAll(remoteChangePackage.getChanges());
                fileSystemRevision.setRevisionNumber(remoteChangePackage.getRevisionNumber());
                logger.info(changes);
            } catch (IOException e) {
                logger.error(e);
            }
        }
    }
}

package net.edgefox.googledrive.filesystem.change;

import net.edgefox.googledrive.filesystem.change.local.LocalChangesHandler;
import net.edgefox.googledrive.filesystem.change.remote.RemoteChangesHandler;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * User: Ivan Lyutov
 * Date: 10/15/13
 * Time: 5:33 PM
 */
@Singleton
public class ChangesApplier {
    private static Logger logger = Logger.getLogger(ChangesApplier.class);
    @Inject
    private LocalChangesHandler localChangesHandler;
    @Inject
    private RemoteChangesHandler remoteChangesHandler;
    @Inject
    private ScheduledExecutorService executorService;
    @Inject
    private Semaphore applicationSemaphore;

    public void start() {
        logger.info("Trying to start ChangesApplier");
        executorService.scheduleWithFixedDelay(new MergeTask(), 0, 15, TimeUnit.SECONDS);
        logger.info("ChangesApplier has been successfully started");
    }

    class MergeTask implements Runnable {

        @Override
        public void run() {
            try {
                applicationSemaphore.acquire();
                logger.info("Change merge iteration started");
                localChangesHandler.handle();
                remoteChangesHandler.handle();
                logger.info("Change merge iteration ended");
            } catch (InterruptedException e) {
                logger.warn(e);
            } finally {
                applicationSemaphore.release();
            }
        }
    }
}

package net.edgefox.googledrive;

import com.google.inject.Singleton;
import net.edgefox.googledrive.filesystem.FileSystem;
import net.edgefox.googledrive.util.Notifier;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;

/**
 * User: Ivan Lyutov
 * Date: 12/3/13
 * Time: 4:43 PM
 */
@Singleton
public class ShutdownHook implements Runnable {
    @Inject
    private ScheduledExecutorService applicationThreadPool;
    @Inject
    private FileSystem fileSystem;

    @Override
    public void run() {
        Notifier.showMessage("System notification", "Application is going down");
        fileSystem.lockForShutdown();
        applicationThreadPool.shutdownNow();
    }
}

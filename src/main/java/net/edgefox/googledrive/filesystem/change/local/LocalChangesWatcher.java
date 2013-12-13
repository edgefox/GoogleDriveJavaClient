package net.edgefox.googledrive.filesystem.change.local;

import net.edgefox.googledrive.filesystem.change.ChangesWatcher;
import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.util.IOUtils;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

/**
 * User: Ivan Lyutov
 * Date: 11/20/13
 * Time: 11:53 AM
 */
@Singleton
public class LocalChangesWatcher extends ChangesWatcher<Path> {
    private static Logger logger = Logger.getLogger(LocalChangesWatcher.class);

    private Path trackedPath;
    private WatchService watchService;
    private Map<WatchKey, Path> watchKeyToPath = new HashMap<>();
    @Inject
    private Semaphore applicationSemaphore;

    @Inject
    public LocalChangesWatcher(Path trackedPath) {
        this.trackedPath = trackedPath;
        watchService = null;
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void start() throws IOException {
        logger.info("Trying to start LocalChangesWatcher");
        registerAll(trackedPath);
        executorService.execute(new PollTask());
        logger.info("LocalChangesWatcher has been successfully started");
    }

    private void registerAll(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isHidden(dir)) {
                    return FileVisitResult.CONTINUE;
                }
                
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    void register(Path path) throws IOException {
        WatchKey watchKey = path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        watchKeyToPath.put(watchKey, path);
    }

    class PollTask implements Runnable {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WatchKey key;
                    try {
                        key = watchService.take();
                        applicationSemaphore.acquire();
                    } catch (InterruptedException e) {
                        logger.info("LocalChangesWatcher has been interrupted", e);
                        return;
                    }
    
                    Path filePath = watchKeyToPath.get(key);
                    if (filePath == null) {
                        logger.warn("WatchKey not recognized!!");
                        continue;
                    }
    
                    List<WatchEvent<?>> watchEvents = key.pollEvents();
                    for (WatchEvent<?> watchEvent : watchEvents) {
                        handleEvent(filePath, (WatchEvent<Path>) watchEvent);
                    }
                    
                    resetKey(key);
                } finally {
                    applicationSemaphore.release();
                }
            }
        }

        private void handleEvent(Path filePath, WatchEvent<Path> event) {
            Path child = filePath.resolve(event.context());
            WatchEvent.Kind kind = event.kind();
            try {
                if ((kind == ENTRY_MODIFY && Files.isDirectory(child)) ||
                    Files.isHidden(child)) {
                    return;
                }
                String md5CheckSum = kind == ENTRY_DELETE || Files.isDirectory(filePath) ? null : IOUtils.getFileMd5CheckSum(child);
                changes.add(new FileSystemChange<>(child,
                                                   kind == ENTRY_DELETE ? null : child.getParent(),
                                                   child.getFileName().toString(),
                                                   child.toFile().isDirectory(), md5CheckSum));
            } catch (IOException e) {
                logger.error(String.format("Unable to handle event: %s", event), e);
            }

            if (kind == ENTRY_CREATE) {
                try {
                    if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                        registerNewDirectory(child);
                    }
                } catch (IOException e) {
                    logger.error(String.format("Unable to register child elements of folder: %s", child), e);
                }
            }
        }

        private void registerNewDirectory(Path child) throws IOException {
            Files.walkFileTree(child, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (Files.isHidden(dir)) {
                        return FileVisitResult.CONTINUE;
                    }

                    changes.add(new FileSystemChange<>(dir,
                                                       dir.getParent(),
                                                       dir.getFileName().toString(),
                                                       true, null));
                    register(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (Files.isHidden(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    String md5CheckSum = IOUtils.getFileMd5CheckSum(file);
                    changes.add(new FileSystemChange<>(file,
                                                       file.getParent(),
                                                       file.getFileName().toString(),
                                                       false, 
                                                       md5CheckSum));
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        private void resetKey(WatchKey key) {
            boolean valid = key.reset();
            if (!valid) {
                watchKeyToPath.remove(key);
                if (watchKeyToPath.isEmpty()) {
                    logger.info("LocalChangesWatcher has been stopped");
                }
            }
        }
    }
}

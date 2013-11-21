package filesystem.change.local;

import com.google.inject.Singleton;
import filesystem.FileMetadata;
import filesystem.Trie;
import filesystem.change.ChangesWatcher;
import filesystem.change.FileSystemChange;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private static final Logger logger = Logger.getLogger(LocalChangesWatcher.class);
    static  {
        watchService = null;
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            logger.error(e);
        }
    }
    private static WatchService watchService;
    private static Map<WatchKey, Path> watchKeyToPath = new HashMap<>();
    @Inject
    private Path trackedPath;
    @Inject
    private filesystem.FileSystem fileSystem;

    public void start() throws IOException {
        logger.info("Trying to start LocalChangesWatcher");
        registerAll(trackedPath);
        executorService.scheduleWithFixedDelay(new PollTask(), 0, 10, TimeUnit.SECONDS);
        logger.info("LocalChangesWatcher has been successfully started");
    }

    private void registerAll(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path path) throws IOException {
        WatchKey watchKey = path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        watchKeyToPath.put(watchKey, path);
    }

    class PollTask implements Runnable {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.poll(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    return;
                }
                
                if (key == null) {
                    break;
                }

                Path filePath = watchKeyToPath.get(key);
                if (filePath == null) {
                    logger.warn("WatchKey not recognized!!");
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path name = ev.context();
                    Path child = filePath.resolve(name);
                    
                    if (handledEntries.remove(child)) {
                        continue;
                    }
                    
                    changes.add(new FileSystemChange<>(child,
                                                       kind == ENTRY_DELETE ? null : child.getParent(),
                                                       child.getFileName().toString(),
                                                       child.toFile().isDirectory()));
                    logger.info(String.format("%s: %s\n", event.kind().name(), child));

                    if (kind == ENTRY_CREATE) {
                        try {
                            if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                                registerAll(child);
                            }
                        } catch (IOException e) {
                            logger.warn(e);
                        }
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    watchKeyToPath.remove(key);
                    if (watchKeyToPath.isEmpty()) {
                        break;
                    }
                }
            }
        }
    }
}

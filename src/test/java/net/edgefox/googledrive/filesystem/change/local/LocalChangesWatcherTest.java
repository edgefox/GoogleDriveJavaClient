package net.edgefox.googledrive.filesystem.change.local;

import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.util.IOUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;

/**
 * User: Ivan Lyutov
 * Date: 11/25/13
 * Time: 12:17 PM
 */

public class LocalChangesWatcherTest {
    private Path trackedPath = Paths.get("/tmp/GoogleDrive");
    @Spy
    private ScheduledExecutorService scheduledExecutorService;
    @Spy
    private Semaphore semaphore = new Semaphore(3);
    @InjectMocks
    private LocalChangesWatcher localChangesWatcher;

    @Before
    public void setUp() throws Exception {
        localChangesWatcher = new LocalChangesWatcher(trackedPath);
        scheduledExecutorService = new ScheduledThreadPoolExecutor(1);
        MockitoAnnotations.initMocks(this);
        if (Files.exists(trackedPath)) {
            FileUtils.forceDelete(trackedPath.toFile());
        }
        Files.createDirectory(trackedPath);
        localChangesWatcher.start();
    }

    @Test
    public void testGetChangesAfterFilesCreate() throws Exception {
        Set<FileSystemChange<Path>> changes = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Path path = trackedPath.resolve(String.format("file_%d", i));
            Files.createFile(path);
            changes.add(new FileSystemChange<>(path, path.getParent(),
                                               path.getFileName().toString(),
                                               false,
                                               IOUtils.getFileMd5CheckSum(path)));
        }

        TimeUnit.SECONDS.sleep(3);

        assertEquals(localChangesWatcher.getChangesCopy(), changes);
    }

    @Test
    public void testGetChangesAfterFilesCreateWithExclusion() throws Exception {
        Set<Path> pathsToIgnore = new HashSet<>();
        pathsToIgnore.add(trackedPath.resolve("file_1"));
        localChangesWatcher.ignoreChanges(pathsToIgnore);
        Set<FileSystemChange<Path>> changes = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Path path = trackedPath.resolve(String.format("file_%d", i));
            Files.createFile(path);
            if (!pathsToIgnore.contains(path)) {
                changes.add(new FileSystemChange<>(path, path.getParent(),
                                                   path.getFileName().toString(),
                                                   false,
                                                   IOUtils.getFileMd5CheckSum(path)));
            }
        }

        TimeUnit.SECONDS.sleep(3);

        assertEquals(localChangesWatcher.getChangesCopy(), changes);
        assertEquals(changes.size(), localChangesWatcher.getChangesCopy().size());
    }

    @Test
    public void testGetChangesAfterNewDirectoryWithFilesCreate() throws Exception {
        Set<FileSystemChange<Path>> changes = new HashSet<>();
        Path dirPath = trackedPath.resolve(Paths.get("one/two/three/four"));
        Files.createDirectories(dirPath);

        Path current = trackedPath;
        for (Path path : trackedPath.relativize(dirPath)) {
            current = current.resolve(path);
            changes.add(new FileSystemChange<>(current, current.getParent(),
                                               current.getFileName().toString(),
                                               true,
                                               null));
        }

        TimeUnit.SECONDS.sleep(3);

        assertEquals(localChangesWatcher.getChangesCopy(), changes);

        for (int i = 0; i < 10; i++) {
            Path path = dirPath.resolve(String.format("file_%d", i));
            Files.createFile(path);
            changes.add(new FileSystemChange<>(path, path.getParent(),
                                               path.getFileName().toString(),
                                               false,
                                               IOUtils.getFileMd5CheckSum(path)));
        }

        TimeUnit.SECONDS.sleep(3);

        assertEquals(localChangesWatcher.getChangesCopy(), changes);
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.forceDelete(trackedPath.toFile());
        scheduledExecutorService.shutdownNow();
    }
}

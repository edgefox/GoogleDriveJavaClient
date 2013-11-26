package filesystem.change.local;

import filesystem.change.FileSystemChange;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * User: Ivan Lyutov
 * Date: 11/25/13
 * Time: 12:17 PM
 */

@Ignore
//TODO: Seems unreliable. Think how to rework it.
public class LocalChangesWatcherTest {
    private Path trackedPath = Paths.get("/tmp/GoogleDrive");
    @Spy
    private ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1);
    @InjectMocks
    private LocalChangesWatcher localChangesWatcher;

    @Before
    public void setUp() throws Exception {
        localChangesWatcher = new LocalChangesWatcher(trackedPath);
        MockitoAnnotations.initMocks(this);
        Files.createDirectory(trackedPath);
        localChangesWatcher.start();
    }

    @Test
    public void testGetChangesAfterFilesCreate() throws Exception {
        TimeUnit.SECONDS.sleep(3);
        Set<Path> paths = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Path path = trackedPath.resolve(String.format("file_%d", i));
            paths.add(path);
            Files.createFile(path);
        }
        
        TimeUnit.SECONDS.sleep(10);
        
        for (FileSystemChange<Path> change : localChangesWatcher.getChangesCopy()) {
            paths.remove(change.getId());
        }

        assertTrue("Created files were not handled",
                   paths.isEmpty());
    }

    @Test
    public void testGetChangesAfterFilesCreateWithExclusion() throws Exception {
        TimeUnit.SECONDS.sleep(3);
        Set<Path> pathsToIgnore = new HashSet<>();
        pathsToIgnore.add(trackedPath.resolve("file_1"));
        localChangesWatcher.ignoreChanges(pathsToIgnore);
        Set<Path> paths = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Path path = trackedPath.resolve(String.format("file_%d", i));
            paths.add(path);
            Files.createFile(path);
        }

        TimeUnit.SECONDS.sleep(10);

        for (FileSystemChange<Path> change : localChangesWatcher.getChangesCopy()) {
            paths.remove(change.getId());
        }

        assertTrue("Created files were not handled",
                   paths.size() == 1);
        assertTrue(paths.containsAll(pathsToIgnore));
    }

    @Test
    public void testGetChangesAfterNewDirectoryWithFilesCreate() throws Exception {
        TimeUnit.SECONDS.sleep(3);
        Path dirPath = trackedPath.resolve(Paths.get("one/two/three/four"));
        Files.createDirectories(dirPath);
        
        TimeUnit.SECONDS.sleep(10);
        
        assertEquals("New directories were not handled",
                     trackedPath.relativize(dirPath).getNameCount(), 
                     localChangesWatcher.getChangesCopy().size());

        Set<Path> paths = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Path path = dirPath.resolve(String.format("file_%d", i));
            paths.add(path);
            Files.createFile(path);
        }

        TimeUnit.SECONDS.sleep(10);

        for (FileSystemChange<Path> change : localChangesWatcher.getChangesCopy()) {
            paths.remove(change.getId());
        }

        assertTrue("Files in new folder were not handled", paths.isEmpty());
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.forceDelete(trackedPath.toFile());
    }
}

package net.edgefox.googledrive.filesystem.change.remote;

import net.edgefox.googledrive.filesystem.FileSystem;
import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.filesystem.change.RemoteChangePackage;
import net.edgefox.googledrive.service.GoogleDriveService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.*;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.when;

/**
 * User: Ivan Lyutov
 * Date: 11/25/13
 * Time: 12:17 PM
 */
public class RemoteChangesWatcherTest {
    @Mock
    private GoogleDriveService googleDriveService;
    @Mock
    private FileSystem fileSystem;
    @Spy
    private ScheduledExecutorService scheduledExecutorService;
    @InjectMocks
    private RemoteChangesWatcher remoteChangesWatcher;
    private FileSystemChange<String> file1 = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                    GoogleDriveService.ROOT_DIR_ID,
                                                                    "file1.txt",
                                                                    false, null);
    private FileSystemChange<String> file2 = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                    GoogleDriveService.ROOT_DIR_ID,
                                                                    "file2.txt",
                                                                    false, null);
    private FileSystemChange<String> directory = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                        GoogleDriveService.ROOT_DIR_ID,
                                                                        "directory",
                                                                        true, null);
    private FileSystemChange<String> file3 = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                    directory.getParentId(),
                                                                    "file3.txt",
                                                                    false, null);

    @Before
    public void setUp() throws Exception {
        scheduledExecutorService = new ScheduledThreadPoolExecutor(1);
        initMocks();
        remoteChangesWatcher.start();
    }

    @Test
    public void testGetChanges() throws Exception {
        TimeUnit.SECONDS.sleep(5);
        Set<FileSystemChange<String>> changesCopy = remoteChangesWatcher.getChangesCopy();
        assertNotNull(changesCopy);
        assertEquals(4, changesCopy.size());
    }

    @Test
    public void testGetChangesWithExclusion() throws Exception {
        TimeUnit.SECONDS.sleep(5);
        HashSet<String> handledEntries = new HashSet<>();
        handledEntries.add(file2.getId());
        remoteChangesWatcher.ignoreChanges(handledEntries);
        Set<FileSystemChange<String>> changesCopy = remoteChangesWatcher.getChangesCopy();
        assertNotNull(changesCopy);
        assertEquals(3, changesCopy.size());
        assertFalse(changesCopy.contains(file2));
    }

    private void initMocks() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(fileSystem.getFileSystemRevision()).thenReturn(999L);
        RemoteChangePackage changePackage = new RemoteChangePackage(1000, new LinkedHashSet<FileSystemChange<String>>() {
            {
                add(file1);
                add(file2);
                add(directory);
                add(file3);
            }

        });
        when(googleDriveService.getChanges(anyLong())).thenReturn(changePackage);
    }

    @After
    public void tearDown() throws Exception {
        scheduledExecutorService.shutdownNow();
    }
}

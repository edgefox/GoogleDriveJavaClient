package filesystem.change.remote;

import filesystem.FileSystem;
import filesystem.change.FileSystemChange;
import filesystem.change.RemoteChangePackage;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;
import service.GoogleDriveService;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Mockito.*;

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
    private ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1);
    @InjectMocks
    private RemoteChangesWatcher remoteChangesWatcher;

    @Before
    public void setUp() throws Exception {
        initMocks();
        remoteChangesWatcher.start();
    }

    @Test
    public void testHandledChanges() throws Exception {
        TimeUnit.SECONDS.sleep(5);
        Set<FileSystemChange<String>> changesCopy = remoteChangesWatcher.getChangesCopy();
        assertNotNull(changesCopy);
        assertEquals(4, changesCopy.size());
    }

    private void initMocks() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(fileSystem.getFileSystemRevision()).thenReturn(999L);
        RemoteChangePackage changePackage = new RemoteChangePackage(1000, new ArrayList<FileSystemChange<String>>() {
            {
                FileSystemChange<String> file1 = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                        GoogleDriveService.ROOT_DIR_ID,
                                                                        "file1.txt",
                                                                        false);
                FileSystemChange<String> file2 = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                        GoogleDriveService.ROOT_DIR_ID,
                                                                        "file2.txt",
                                                                        false);
                FileSystemChange<String> directory = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                            GoogleDriveService.ROOT_DIR_ID,
                                                                            "directory",
                                                                            true);
                FileSystemChange<String> file3 = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                        directory.getParentId(),
                                                                        "file3.txt",
                                                                        false);
                add(file1);
                add(file2);
                add(directory);
                add(file3);
            }
        });
        when(googleDriveService.getChanges(anyLong())).thenReturn(changePackage);
    }
}

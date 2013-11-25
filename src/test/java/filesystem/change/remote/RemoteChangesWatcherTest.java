package filesystem.change.remote;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import service.GoogleDriveService;

/**
 * User: Ivan Lyutov
 * Date: 11/25/13
 * Time: 12:17 PM
 */
public class RemoteChangesWatcherTest {
    @Mock
    private GoogleDriveService googleDriveService;
    private RemoteChangesWatcher remoteChangesWatcher;    
    
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        remoteChangesWatcher.start();
    }

    @Test
    public void testStart() throws Exception {

    }
}

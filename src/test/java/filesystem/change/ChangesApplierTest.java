package filesystem.change;

import filesystem.change.local.LocalChangesHandler;
import filesystem.change.remote.RemoteChangesHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * User: Ivan Lyutov
 * Date: 11/25/13
 * Time: 12:15 PM
 */
@RunWith(MockitoJUnitRunner.class)
public class ChangesApplierTest {
    @Mock
    private LocalChangesHandler localChangesHandler;
    @Mock
    private RemoteChangesHandler remoteChangesHandler;
    @Spy
    private ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1);
    @InjectMocks
    @Spy
    private ChangesApplier changesApplier;

    @Test
    public void testStart() throws Exception {
        changesApplier.start();                
        
        verify(localChangesHandler, times(1)).handle();
        verify(remoteChangesHandler, times(1)).handle();
    }
}

package service;

import junit.framework.Assert;
import org.eclipse.jetty.server.Server;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * User: Ivan Lyutov
 * Date: 11/26/13
 * Time: 6:56 PM
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthRedirectListenerTest {
    @Mock
    private GoogleDriveService googleDriveService;
    @InjectMocks
    private AuthRedirectListener authRedirectListener;
    
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    
    private String code = "code";
    private String refreshToken = "refreshToken";

    @Before
    public void setUp() throws Exception {
        Mockito.when(request.getParameter("code")).thenReturn(code);
        Mockito.when(googleDriveService.requestRefreshToken("code")).thenReturn(refreshToken);
    }

    @Test
    public void testListenForAuthComplete() throws Exception {
        simulateRedirect();        
        Assert.assertEquals(refreshToken, authRedirectListener.listenForAuthComplete());
    }

    private void simulateRedirect() throws InterruptedException {
        Thread authThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TimeUnit.SECONDS.sleep(3);
                    authRedirectListener.getRedirectServlet().doGet(request, response);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        authThread.start();
    }
}

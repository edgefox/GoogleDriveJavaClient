package service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import org.apache.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * User: Ivan Lyutov
 * Date: 11/25/13
 * Time: 12:38 PM
 */
@Singleton
public class AuthRedirectListener extends Server {
    @Inject
    @Named("REDIRECT_URI")
    private String REDIRECT_URI;
    @Inject
    private GoogleDriveService googleDriveService;
    private String refreshToken;

    private static final int DEFAULT_LISTENER_PORT = 9999;

    public AuthRedirectListener() {
        this(DEFAULT_LISTENER_PORT);
    }

    public AuthRedirectListener(int port) {
        super(port);
        ServletContextHandler handler = new ServletContextHandler();
        handler.addServlet(new ServletHolder(new AuthRedirectServlet()), "/");
        setHandler(handler);
    }

    synchronized public String listenForAuthComplete() throws Exception {
        start();
        wait();
        stop();
        
        return refreshToken;
    }

    class AuthRedirectServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            synchronized (AuthRedirectListener.this) {
                try {
                    String code = req.getParameterMap().get("code")[0];
                    GoogleAuthorizationCodeTokenRequest tokenRequest = googleDriveService.authFlow.newTokenRequest(code);
                    tokenRequest.setRedirectUri(REDIRECT_URI);
                    GoogleTokenResponse googleTokenResponse = tokenRequest.execute();
                    refreshToken = googleTokenResponse.getRefreshToken();
                    resp.setStatus(HttpStatus.SC_OK);
                } finally {
                    AuthRedirectListener.this.notifyAll();
                }
            }
        }
    }
}

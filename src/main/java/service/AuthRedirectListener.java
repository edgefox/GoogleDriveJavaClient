package service;

import org.apache.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.inject.Inject;
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
public class AuthRedirectListener {
    @Inject
    private GoogleDriveService googleDriveService;
    private String refreshToken;
    private Server server;

    private static final int DEFAULT_LISTENER_PORT = 9999;
    private AuthRedirectListener.AuthRedirectServlet redirectServlet;

    public AuthRedirectListener() {
        this(DEFAULT_LISTENER_PORT);
    }

    public AuthRedirectListener(int port) {
        server = new Server(port);
        ServletContextHandler handler = new ServletContextHandler();
        redirectServlet = new AuthRedirectServlet();
        handler.addServlet(new ServletHolder(redirectServlet), "/");
        server.setHandler(handler);
    }

    synchronized public String listenForAuthComplete() throws Exception {
        server.start();
        wait();
        server.stop();
        
        return refreshToken;
    }

    AuthRedirectServlet getRedirectServlet() {
        return redirectServlet;
    }

    class AuthRedirectServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            synchronized (AuthRedirectListener.this) {
                try {
                    String code = req.getParameter("code");                    
                    refreshToken = googleDriveService.requestRefreshToken(code);
                    resp.setStatus(HttpStatus.SC_OK);
                } finally {
                    AuthRedirectListener.this.notifyAll();
                }
            }
        }
    }
}

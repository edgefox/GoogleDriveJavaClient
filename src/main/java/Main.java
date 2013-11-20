import com.google.inject.*;
import com.google.inject.name.Names;
import filesystem.FileSystem;
import filesystem.FileSystemProvider;
import filesystem.FileSystemRevision;
import org.apache.log4j.Logger;
import service.FileSystemManager;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * User: Ivan Lyutov
 * Date: 11/18/13
 * Time: 2:06 PM
 */
@Singleton
public class Main extends AbstractModule {
    private static final Logger logger = Logger.getLogger(Main.class);
    private static final Path DEFAULT_PATH = Paths.get("/home/ilyutov/GoogleDrive");
    @Inject
    private FileSystemManager fileSystemManager;

    public void start() throws Exception {
        fileSystemManager.start();
    }

    @Override
    protected void configure() {
        loadProperties(binder());
        bind(Path.class).toInstance(DEFAULT_PATH);
        bind(FileSystem.class).toProvider(new FileSystemProvider());
        bind(FileSystemRevision.class).toInstance(new FileSystemRevision(0));
    }

    private void loadProperties(Binder binder) {
        InputStream stream = Main.class.getResourceAsStream("application.properties");
        Properties appProperties = new Properties();
        try {
            appProperties.load(stream);
            Names.bindProperties(binder, appProperties);
        } catch (IOException e) {
            binder.addError(e);
        }
    }

    public static void main(String[] args) {
        try {
            Main main = new Main();
            Injector injector = Guice.createInjector(main);
            injector.injectMembers(main);
            main.start();
        } catch (IllegalStateException e) {
            logger.error("Failed to init filesystem", e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

import com.google.inject.*;
import com.google.inject.name.Names;
import filesystem.FileSystem;
import filesystem.FileSystemProvider;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * User: Ivan Lyutov
 * Date: 11/18/13
 * Time: 2:06 PM
 */
@Singleton
public class Main extends AbstractModule {
    private static final Logger logger = Logger.getLogger(Main.class);
    private final String configLocation;
    @Inject
    private FileSystemManager fileSystemManager;
    private ScheduledExecutorService applicationThreadPool = initApplicationThreadPool();
    private ConfigurationManager configManager;

    public Main() {
        String customConfigLocation = System.getProperty("config.location");
        configLocation = customConfigLocation == null ? "application.properties" : customConfigLocation;
    }

    @Override
    protected void configure() {
        try {
            if (configLocation == null) {
                configManager = new ConfigurationManager();
            } else {
                configManager = new ConfigurationManager(configLocation);
            }
            bind(ConfigurationManager.class).toInstance(configManager);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        bind(Path.class).toProvider(new TrackedPathProvider());
        loadProperties(binder());
        bind(FileSystem.class).toProvider(new FileSystemProvider());
        bind(ScheduledExecutorService.class).toInstance(applicationThreadPool);
    }

    private ScheduledExecutorService initApplicationThreadPool() {
        return Executors.newScheduledThreadPool(3, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r.getClass().getName());
            }
        });
    }

    private void loadProperties(Binder binder) {
        Properties appProperties = configManager.getAppProperties();
        Names.bindProperties(binder, appProperties);
    }

    public static void main(String[] args) {
        try {
            Main main = new Main();
            Injector injector = Guice.createInjector(main);
            injector.injectMembers(main);
            System.out.println("Application is up and active.");
            main.applicationThreadPool.awaitTermination(365, TimeUnit.DAYS);
        } catch (IllegalStateException e) {
            logger.error("Failed to init filesystem", e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

import com.google.inject.*;
import com.google.inject.name.Names;
import config.ConfigurationManager;
import config.TrackedPathProvider;
import filesystem.FileSystem;
import filesystem.FileSystemProvider;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: Ivan Lyutov
 * Date: 11/18/13
 * Time: 2:06 PM
 */
@Singleton
public class Main extends AbstractModule {
    private static final Logger logger = Logger.getLogger(Main.class);
    
    @Inject
    private FileSystemManager fileSystemManager;
    private ScheduledExecutorService applicationThreadPool = initApplicationThreadPool();
    private ConfigurationManager configManager;

    @Override
    protected void configure() {
        try {
            configManager = new ConfigurationManager(System.getProperty("config.location"));
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
        ThreadFactory threadFactory = new BasicThreadFactory.Builder().namingPattern("Service - %d")
                                                                      .daemon(false)
                                                                      .build();
        return Executors.newScheduledThreadPool(3, threadFactory);
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
        } catch (IllegalStateException e) {
            logger.error("Failed to init filesystem", e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

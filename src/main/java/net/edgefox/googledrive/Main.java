package net.edgefox.googledrive;

import com.google.inject.*;
import com.google.inject.name.Names;
import net.edgefox.googledrive.config.ConfigurationManager;
import net.edgefox.googledrive.config.TrackedPathProvider;
import net.edgefox.googledrive.filesystem.FileSystem;
import net.edgefox.googledrive.filesystem.FileSystemProvider;
import net.edgefox.googledrive.util.Notifier;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

/**
 * User: Ivan Lyutov
 * Date: 11/18/13
 * Time: 2:06 PM
 */
@Singleton
public class Main extends AbstractModule {
    private static Logger logger = Logger.getLogger(Main.class);
    @Inject
    private Application application;

    @Override
    protected void configure() {
        System.out.println("test");
        ConfigurationManager configManager = initConfigurationManager();
        bind(ConfigurationManager.class).toInstance(configManager);
        bind(Path.class).toProvider(TrackedPathProvider.class);
        loadProperties(binder(), configManager);
        bind(FileSystem.class).toProvider(FileSystemProvider.class);
        bind(ScheduledExecutorService.class).toInstance(initApplicationThreadPool());
        bind(Semaphore.class).toInstance(new Semaphore(3));
    }

    private ConfigurationManager initConfigurationManager() {
        ConfigurationManager configManager;
        try {
            configManager = new ConfigurationManager(System.getProperty("config.location"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return configManager;
    }

    private ScheduledExecutorService initApplicationThreadPool() {
        ThreadFactory threadFactory = new BasicThreadFactory.Builder().namingPattern("Service - %d")
                .daemon(false)
                .build();
        return Executors.newScheduledThreadPool(3, threadFactory);
    }

    private void loadProperties(Binder binder, ConfigurationManager configManager) {
        Properties appProperties = configManager.getAppProperties();
        Names.bindProperties(binder, appProperties);
    }

    public static void main(String[] args) throws Exception{
        try {
            Main main = new Main();
            Injector injector = Guice.createInjector(main);
            injector.injectMembers(main);
            Notifier.showSystemMessage("Application is up and active.");
        } catch (Exception e) {
            logger.error("Application crashed", e);
            throw e;
        }
    }
}

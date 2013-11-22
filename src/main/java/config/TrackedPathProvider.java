package config;

import com.google.inject.Provider;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * User: Ivan Lyutov
 * Date: 11/22/13
 * Time: 3:40 PM
 */
public class TrackedPathProvider implements Provider<Path> {
    private static Path trackedPath;
    @Inject
    private ConfigurationManager configurationManager;
    
    @Override
    public Path get() {
        if (trackedPath == null) {
            if (StringUtils.isEmpty(configurationManager.getProperty("TRACKED_PATH"))) {
                trackedPath = setUpPath();
            } else {
                trackedPath = Paths.get(configurationManager.getProperty("TRACKED_PATH"));
            }
        }
        
        if (!Files.exists(trackedPath)) {
            try {
                Files.createDirectories(trackedPath);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        
        return trackedPath;
    }

    private Path setUpPath() {
        System.out.print("Please enter the path you want to track: ");
        Scanner scanner = new Scanner(System.in);
        String newTrackedPath = scanner.nextLine();
        try {
            configurationManager.updateProperties("TRACKED_PATH", newTrackedPath);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return Paths.get(newTrackedPath);
    }
}

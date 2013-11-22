package config;

import com.google.inject.Singleton;

import java.io.*;
import java.net.URL;
import java.util.Properties;

/**
 * User: Ivan Lyutov
 * Date: 11/22/13
 * Time: 2:24 PM
 */
@Singleton
public class ConfigurationManager {
    private static final String DEFAULT_CONFIG_PATH = "application.properties";
    private String configPath;
    private Properties appProperties;

    public ConfigurationManager() throws IOException {
        this.configPath = DEFAULT_CONFIG_PATH;
        try (InputStream stream = ConfigurationManager.class.getResourceAsStream(configPath)) {
            appProperties = new Properties();
            appProperties.load(stream);
        }
    }

    public ConfigurationManager(String configPath) throws IOException {
        this.configPath = configPath;
        try (InputStream stream = new FileInputStream(configPath)) {
            appProperties = new Properties();
            appProperties.load(stream);
        }
    }
    
    public String getProperty(String key) {
        return appProperties.getProperty(key);
    }
    
    public void updateProperties(String key, String value) throws IOException {
        appProperties.setProperty(key, value);
        URL resource;
        if (configPath.equals(DEFAULT_CONFIG_PATH)) {
            resource = ConfigurationManager.class.getResource(configPath);
        } else {
            resource = new File(configPath).toURL();
        }
        try (FileOutputStream out = new FileOutputStream(resource.getFile())) {
            appProperties.store(out, null);
        }
    }

    public Properties getAppProperties() {
        return appProperties;
    }
}

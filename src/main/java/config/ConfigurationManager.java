package config;

import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    public ConfigurationManager(String configPath) throws IOException {
        init(configPath);
        try (InputStream stream = new FileInputStream(this.configPath)) {
            appProperties = new Properties();
            appProperties.load(stream);
        }
    }

    private void init(String configPath) throws IOException {
        if (StringUtils.isEmpty(configPath)) {
            this.configPath = DEFAULT_CONFIG_PATH;
            if (!Files.exists(Paths.get(DEFAULT_CONFIG_PATH))) {
                InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(DEFAULT_CONFIG_PATH);
                Files.copy(in, Paths.get(DEFAULT_CONFIG_PATH));
            }
        } else {
            this.configPath = configPath;
        }
    }

    public String getProperty(String key) {
        return appProperties.getProperty(key);
    }
    
    public void updateProperties(String key, String value) throws IOException {
        appProperties.setProperty(key, value);
        try (FileOutputStream out = new FileOutputStream(configPath)) {
            appProperties.store(out, null);
        }
    }

    public Properties getAppProperties() {
        return appProperties;
    }
}

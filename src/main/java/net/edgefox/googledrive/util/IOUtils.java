package net.edgefox.googledrive.util;

import com.twmacinta.util.MD5;
import org.apache.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User: Ivan Lyutov
 * Date: 11/19/13
 * Time: 1:39 PM
 */
public class IOUtils {
    private static Logger logger = Logger.getLogger(IOUtils.class);
    
    private IOUtils() {        
    }

    public static void safeClose(InputStream inputStream) {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            logger.error("Unable to close the stream", e);
        }
    }

    public static void safeClose(FileOutputStream outputStream) {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            logger.error("Unable to close the stream", e);
        }
    }
    
    public static String getFileMd5CheckSum(Path path) throws IOException {
        return MD5.asHex(MD5.getHash(path.toFile()));
    }

    public static void safeCreateDirectory(Path newDirectoryPath) throws IOException {
        if (Files.notExists(newDirectoryPath)) {
            Files.createDirectories(newDirectoryPath);
        }
    }
}
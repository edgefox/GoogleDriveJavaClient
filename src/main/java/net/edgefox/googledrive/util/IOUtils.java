package net.edgefox.googledrive.util;

import org.apache.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: Ivan Lyutov
 * Date: 11/19/13
 * Time: 1:39 PM
 */
public class IOUtils {
    private static final Logger logger = Logger.getLogger(IOUtils.class);
    
    private IOUtils() {}

    public static void safeClose(InputStream inputStream) {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            logger.error("Unable to close the stream.", e);
        }
    }

    public static void safeClose(FileOutputStream outputStream) {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            logger.error("Unable to close the stream.", e);
        }
    }
}
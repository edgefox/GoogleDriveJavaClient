package net.edgefox.googledrive.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

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
        byte[] digestBytes = DigestUtils.md5(Files.readAllBytes(path));
        StringBuilder sb = new StringBuilder("");
        for (byte digestByte : digestBytes) {
            sb.append(Integer.toString((digestByte & 0xff) + 0x100, 16).substring(1));
        }

        return sb.toString();
    }

    public static void safeCreateDirectory(Path newDirectoryPath) throws IOException {
        if (!Files.exists(newDirectoryPath)) {
            Files.createDirectory(newDirectoryPath);
        }
    }
}
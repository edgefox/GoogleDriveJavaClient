package net.edgefox.googledrive.filesystem;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import net.edgefox.googledrive.config.ConfigurationManager;
import net.edgefox.googledrive.util.IOUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * User: Ivan Lyutov
 * Date: 10/2/13
 * Time: 4:24 PM
 */
@Singleton
public class FileSystemProvider implements Provider<FileSystem> {
    @Inject
    private Path trackedPath;
    @Inject
    private ConfigurationManager configurationManager;
    private static FileSystem fileSystem = null;

    public net.edgefox.googledrive.filesystem.FileSystem get() {
        if (fileSystem == null) {
            synchronized (FileSystemProvider.class) {
                fileSystem = createNewFileSystem();
            }
        }

        return fileSystem;
    }

    private FileSystem createNewFileSystem() {
        final  FileSystem result = new FileSystem(trackedPath, configurationManager);
        try {
            Files.walkFileTree(trackedPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (trackedPath.equals(dir) || Files.isHidden(dir)) return FileVisitResult.CONTINUE;

                    result.update(trackedPath.relativize(dir), getPathMetadata(dir, attrs));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (Files.isHidden(file)) return FileVisitResult.CONTINUE;

                    result.update(trackedPath.relativize(file), getPathMetadata(file, attrs));
                    return FileVisitResult.CONTINUE;
                }

                FileMetadata getPathMetadata(Path path, BasicFileAttributes attrs) throws IOException {
                    String id = null;
                    String title = path.getFileName().toString();
                    boolean isDir = attrs.isDirectory();
                    String checkSum;
                    checkSum = Files.isDirectory(path) ? null : IOUtils.getFileMd5CheckSum(path);

                    return new FileMetadata(id, title, isDir, checkSum);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize filesystem", e);
        }
        
        return result;
    }
}

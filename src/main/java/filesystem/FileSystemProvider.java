package filesystem;

import com.google.inject.Provider;
import org.apache.commons.codec.digest.DigestUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

/**
 * User: Ivan Lyutov
 * Date: 10/2/13
 * Time: 4:24 PM
 */
public class FileSystemProvider implements Provider<FileSystem> {
    @Inject
    private Path basePath;
    @Inject
    private FileSystemRevision fileSystemRevision;

    public filesystem.FileSystem get() {
        final filesystem.FileSystem fileSystem = new filesystem.FileSystem(fileSystemRevision, basePath);
        try {
            Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    fileSystem.update(basePath.relativize(dir), getPathMetadata(dir, attrs));
                    return FileVisitResult.CONTINUE;
                }
    
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    fileSystem.update(basePath.relativize(file), getPathMetadata(file, attrs));
                    return FileVisitResult.CONTINUE;
                }
                
                FileMetadata getPathMetadata(Path path, BasicFileAttributes attrs) {
                    String id = null;
                    String title = path.getFileName().toString();
                    boolean isDir = attrs.isDirectory();
                    String checkSum = Arrays.toString(DigestUtils.md5(path.toString()));
                    
                    return new FileMetadata(id, title, isDir, checkSum);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize filesystem");
        }
        
        return fileSystem;
    }
}

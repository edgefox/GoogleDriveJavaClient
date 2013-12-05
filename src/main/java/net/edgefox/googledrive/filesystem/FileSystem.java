package net.edgefox.googledrive.filesystem;

import net.edgefox.googledrive.service.GoogleDriveService;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Image of tracked filesystem tree backed by {@link Trie}
 * It stores {@link FileMetadata} for each entry.
 *
 * @author Ivan Lyutov
 */
public class FileSystem implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private static Logger logger = Logger.getLogger(FileSystem.class);
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Trie<String, FileMetadata> trie;
    private final Map<String, Trie<String, FileMetadata>> idToTrie = new HashMap<String, Trie<String, FileMetadata>>();
    private transient Path basePath;
    private long fileSystemRevision = 0L;
    public static final Path DB_FILE_PATH = Paths.get(String.format("%s%s%s%s%s",
                                                                    System.getProperty("user.home"),
                                                                    File.separator,
                                                                    ".googledrive",
                                                                    File.separator,
                                                                    "data.db"));

    FileSystem(Path basePath) {
        this.basePath = basePath;
        trie = new Trie<>();
        trie.setModel(new FileMetadata(GoogleDriveService.ROOT_DIR_ID,
                                       GoogleDriveService.ROOT_DIR_ID,
                                       true,
                                       null));
    }

    public void addRemoteId(String id, Trie<String, FileMetadata> trie) {
        idToTrie.put(id, trie);
    }

    public long getFileSystemRevision() {
        return fileSystemRevision;
    }

    public void updateFileSystemRevision(long fileSystemRevision) throws IOException {
        lock.writeLock().lock();
        try {
            this.fileSystemRevision = fileSystemRevision;
            persistFileSystem();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Path getBasePath() {
        return basePath;
    }

    public void setBasePath(Path basePath) {
        this.basePath = basePath;
    }

    public void update(Path path, FileMetadata metadata) {
        path = relativizePathIfNeeded(path);
        lock.writeLock().lock();
        logger.info(String.format("Updating fileSystem with: %s", path));
        try {
            Trie<String, FileMetadata> current = trie;
            for (Iterator<Path> iterator = path.iterator(); iterator.hasNext(); ) {
                String key = iterator.next().getFileName().toString();
                if (iterator.hasNext()) {
                    if (current == null) {
                        throw new IllegalArgumentException(String.format("Incorrect path: %s", path));
                    }
                    current = current.getChild(key);
                } else {
                    Trie<String, FileMetadata> child = new Trie<String, FileMetadata>(key, metadata);
                    idToTrie.put(child.getModel().getId(), child);
                    current = current.addChild(child);
                }
            }
            persistFileSystem();
        } catch (IOException e) {
            logger.error("Failed to save fileSystem", e);
        } finally {
            logger.info(String.format("FileSystem has been updated with: %s", path));
            lock.writeLock().unlock();
        }
    }

    public void delete(Path path) {
        path = relativizePathIfNeeded(path);
        lock.writeLock().lock();
        try {
            Trie<String, FileMetadata> foundEntry = get(path);
            if (foundEntry != null) {
                foundEntry.detachFromParent();
                idToTrie.remove(foundEntry.getModel().getId());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void delete(Trie<String, FileMetadata> trie) {
        lock.writeLock().lock();
        try {
            trie.detachFromParent();
            idToTrie.remove(trie.getModel().getId());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void move(Trie<String, FileMetadata> source, Trie<String, FileMetadata> dest) {
        lock.writeLock().lock();
        try {
            source.detachFromParent();
            dest.addChild(source);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Trie<String, FileMetadata> get(Path path) {
        path = relativizePathIfNeeded(path);
        lock.readLock().lock();
        try {
            if (path.toFile().getName().isEmpty()) {
                return trie;
            }
            Trie<String, FileMetadata> current = trie;
            for (Path entry : path) {
                String key = entry.getFileName().toString();
                current = current.getChild(key);
                if (current == null) {
                    return null;
                }
            }

            return current;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Trie<String, FileMetadata> get(String id) {
        lock.readLock().lock();
        try {
            if (id.equals(GoogleDriveService.ROOT_DIR_ID)) {
                return trie;
            }
            return idToTrie.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Path getFullPath(Trie<String, FileMetadata> entry) {
        return basePath.resolve(getPath(entry));
    }

    public Path getPath(Trie<String, FileMetadata> entry) {
        if (entry.getParent() == null) {
            return Paths.get("");
        }

        return getPath(entry.getParent()).resolve(entry.getKey());
    }
    
    public void lockForShutdown() {
        lock.writeLock().lock();
    }

    private Path relativizePathIfNeeded(Path path) {
        if (path.startsWith(basePath)) {
            path = basePath.relativize(path);
        }
        return path;
    }

    private void persistFileSystem() throws IOException {
        if (Files.exists(DB_FILE_PATH)) {
            Files.delete(DB_FILE_PATH);
        }
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(Files.newOutputStream(DB_FILE_PATH))) {
            objectOutputStream.writeObject(this);
        }
    }
}

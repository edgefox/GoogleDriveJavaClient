package filesystem;

import org.apache.log4j.Logger;
import service.GoogleDriveService;

import java.io.*;
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
    private static final Logger logger = Logger.getLogger(FileSystem.class);
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Trie<String, FileMetadata> trie;
    private final Map<String, Trie<String, FileMetadata>> idToTrie = new HashMap<String, Trie<String, FileMetadata>>();
    private volatile Long fileSystemRevision = 0L;
    private transient Path basePath;

    FileSystem(Path basePath) {
        this.basePath = basePath;
        trie = new Trie<>();
        trie.setModel(new FileMetadata(GoogleDriveService.ROOT_DIR_ID, 
                                       GoogleDriveService.ROOT_DIR_ID, 
                                       true, 
                                       null));
    }

    public long getFileSystemRevision() {
        return fileSystemRevision;
    }

    public void updateFileSystemRevision(long fileSystemRevision) throws IOException {
        this.fileSystemRevision = fileSystemRevision;
        persistFileSystem();
    }

    public void setBasePath(Path basePath) {
        this.basePath = basePath;
    }

    public void update(Path path, FileMetadata metadata) {
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

    private void persistFileSystem() throws IOException {
        File database = new File("data.db");
        database.delete();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(database))) {
            objectOutputStream.writeObject(this);
        }
    }

    public void delete(Path path) {
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
    
    public Path getPath(Trie<String, FileMetadata> entry) {
        return Paths.get(basePath + getFullPath(entry));
    }
    
    private String getFullPath(Trie<String, FileMetadata> entry) {
        if (entry.getParent() == null) return "";
        
        return getFullPath(entry.getParent()) + "/" + entry.getKey();
    }
}

package net.edgefox.googledrive.filesystem;

import net.edgefox.googledrive.config.ConfigurationManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

/**
 * User: Ivan Lyutov
 * Date: 11/25/13
 * Time: 12:12 PM
 */
public class FileSystemTest {
    private FileSystem fileSystem;
    private Path basePath = Paths.get("/tmp/GoogleDrive");
    @Mock
    private ConfigurationManager configurationManager;

    @Before
    public void setUp() throws Exception {
        fileSystem = new FileSystem(basePath, configurationManager);
    }

    @Test
    public void testGetFileSystemRevision() throws Exception {
        assertNotNull(fileSystem.getFileSystemRevision());
    }

    @Test
    public void testUpdateFileSystemRevision() throws Exception {
        fileSystem.updateFileSystemRevision(1000);
        assertEquals(1000, fileSystem.getFileSystemRevision());
    }

    @Test
    public void testGetBasePath() {
        assertEquals(basePath, fileSystem.getBasePath());
    }
    
    @Test
    public void testSetBasePath() throws Exception {
        Path newBasePath = Paths.get("/trackedPath");
        fileSystem.setBasePath(newBasePath);
        assertEquals(newBasePath, fileSystem.getBasePath());
    }

    @Test
    public void testUpdate() throws Exception {
        Path pathToInsert = Paths.get("one/two/three/four");
        Path currentPath = Paths.get("");
        for (Path pathItem : pathToInsert) {
            currentPath = currentPath.resolve(pathItem);
            FileMetadata metadata = generateFileMetadata(pathItem);
            fileSystem.update(currentPath, metadata);
            assertNotNull(fileSystem.get(currentPath));
            assertNotNull(fileSystem.get(metadata.getId()));
            assertEquals(fileSystem.get(currentPath), fileSystem.get(metadata.getId()));
        }
    }

    @Test
    public void testDelete() throws Exception {
        testUpdate();
        Path pathToDelete = Paths.get("one/two/three");
        fileSystem.delete(pathToDelete);
        assertNull(fileSystem.get(pathToDelete));
        assertNotNull(fileSystem.get(Paths.get("one/two")));
    }

    @Test
    public void testMove() throws Exception {
        testUpdate();
        Path whatPath = Paths.get("one/two/three/four");
        Trie<String, FileMetadata> what = fileSystem.get(whatPath);
        Path wherePath = Paths.get("one");
        Trie<String, FileMetadata> where = fileSystem.get(wherePath);
        fileSystem.move(what, where);

        assertEquals(what, fileSystem.get(wherePath.resolve(whatPath.getFileName())));
        assertEquals(what, fileSystem.get(what.getModel().getId()));
    }

    @Test
    public void testGet() throws Exception {
        testUpdate();
        Trie<String, FileMetadata> child = fileSystem.get(Paths.get("one/two"));
        assertNotNull(child);
        assertEquals(child, fileSystem.get(child.getModel().getId()));
    }

    @Test
    public void testGetFullPath() throws Exception {
        testUpdate();
        Path path = Paths.get("one");
        Trie<String, FileMetadata> entry = fileSystem.get(path);
        assertEquals(basePath.resolve(path), fileSystem.getFullPath(entry));        
    }

    @Test
    public void testGetPath() throws Exception {
        testUpdate();
        Path path = Paths.get("one");
        Trie<String, FileMetadata> entry = fileSystem.get(path);
        assertEquals(path, fileSystem.getPath(entry));
    }

    private FileMetadata generateFileMetadata(Path pathItem) {
        return new FileMetadata(UUID.randomUUID().toString(),
                                pathItem.getFileName().toString(),
                                true,
                                UUID.randomUUID().toString());
    }
}

package filesystem;

import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;

/**
 * User: Ivan Lyutov
 * Date: 11/25/13
 * Time: 12:12 PM
 */
public class FileSystemTest {
    private FileSystem fileSystem;
    
    @Before
    public void setUp() throws Exception {
        fileSystem = new FileSystem(Paths.get("/tmp/GoogleDrive"));
    }

    @Test
    public void testGetFileSystemRevision() throws Exception {

    }

    @Test
    public void testUpdateFileSystemRevision() throws Exception {

    }

    @Test
    public void testSetBasePath() throws Exception {

    }

    @Test
    public void testUpdate() throws Exception {

    }

    @Test
    public void testDelete() throws Exception {

    }

    @Test
    public void testMove() throws Exception {

    }

    @Test
    public void testGet() throws Exception {

    }

    @Test
    public void testGetPath() throws Exception {

    }
}

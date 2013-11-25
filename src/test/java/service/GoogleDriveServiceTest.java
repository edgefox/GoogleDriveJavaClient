package service;

import com.google.api.services.drive.model.About;
import filesystem.FileMetadata;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static junit.framework.Assert.*;

/**
 * User: Ivan Lyutov
 * Date: 11/19/13
 * Time: 12:21 PM
 */
public class GoogleDriveServiceTest {
    @Mock
    private AuthRedirectListener authRedirectListener;
    @InjectMocks
    private GoogleDriveService googleDriveService;
    private static final File serviceDirectory = new File("/tmp/GoogleDrive");
    private File file1;
    private File file2;

    @Before
    public void setUp() throws Exception {
        initGoogleDrive();
        initMocks();
        file1 = new File(serviceDirectory, UUID.randomUUID().toString());
        file2 = new File(serviceDirectory, UUID.randomUUID().toString());
        FileUtils.forceMkdir(serviceDirectory);
        FileUtils.touch(file1);
        FileUtils.touch(file2);
    }

    private void initMocks() throws Exception {
        MockitoAnnotations.initMocks(this);
        Mockito.when(authRedirectListener.listenForAuthComplete()).thenReturn("REFRESH_TOKEN");
    }

    private void initGoogleDrive() throws IOException {
        Properties properties = new Properties();
        InputStream resource = Thread.currentThread().getContextClassLoader()
                                                     .getResourceAsStream("application.properties");
        properties.load(resource);
        googleDriveService = new GoogleDriveService(properties.getProperty("REDIRECT_URI"), 
                                                    properties.getProperty("APP_KEY"), 
                                                    properties.getProperty("APP_SECRET"), 
                                                    properties.getProperty("REFRESH_TOKEN"));
        googleDriveService.init();
    }

    @Test
    public void testAbout() throws Exception {
        About about = googleDriveService.about();
        assertNotNull(about);
    }

    @Test
    public void testUpload() throws Exception {
        FileMetadata uploadedFile1 = googleDriveService.upload(GoogleDriveService.ROOT_DIR_ID, file1);
        assertNotNull(uploadedFile1);
        FileMetadata uploadedFile2 = googleDriveService.upload(GoogleDriveService.ROOT_DIR_ID, file2);
        assertNotNull(uploadedFile2);
    }

    @Test
    public void testDelete() throws Exception {
        FileMetadata file = googleDriveService.upload(GoogleDriveService.ROOT_DIR_ID, file1);
        googleDriveService.delete(file.getId());
        FileMetadata found = googleDriveService.findChild(GoogleDriveService.ROOT_DIR_ID, file.getId());
        assertNull(found);
    }

    @Test
    public void testGetFileMetadata() throws Exception {
        FileMetadata fileMetadata = googleDriveService.getFileMetadata(GoogleDriveService.ROOT_DIR_ID);
        assertNotNull(fileMetadata);
    }

    @Test
    public void testCreateDirectory() throws Exception {
        String dirName = UUID.randomUUID().toString();
        FileMetadata createdDir = googleDriveService.createDirectory(GoogleDriveService.ROOT_DIR_ID, dirName);
        assertNotNull(createdDir);
    }

    @Test
    public void testCreateOrGetDirectory() throws Exception {
        String dirName = UUID.randomUUID().toString();
        FileMetadata createdDir = googleDriveService.createOrGetDirectory(GoogleDriveService.ROOT_DIR_ID, dirName);
        FileMetadata existingDir = googleDriveService.createOrGetDirectory(GoogleDriveService.ROOT_DIR_ID, dirName);
        assertEquals(createdDir.getId(), existingDir.getId());
    }

    @Test
    public void testFindChild() throws Exception {
        String dirName = UUID.randomUUID().toString();
        googleDriveService.createDirectory(GoogleDriveService.ROOT_DIR_ID, dirName);
        FileMetadata child = googleDriveService.findChild(GoogleDriveService.ROOT_DIR_ID, dirName);
        assertNotNull(child);
    }

    @Test
    public void testListDirectory() throws Exception {
        List<FileMetadata> children = googleDriveService.listDirectory(GoogleDriveService.ROOT_DIR_ID);
        assertNotNull(children);
        assertTrue(children.size() > 0);
    }

    @Test
    public void testDownloadFile() throws Exception {
        FileMetadata uploadedFile = googleDriveService.upload(GoogleDriveService.ROOT_DIR_ID, file1);
        FileUtils.forceDelete(file1);
        googleDriveService.downloadFile(uploadedFile.getId(), file1);
        assertTrue(file1.exists());
    }
    
    @Test
    public void testAuth() throws Exception {
        String redirectUrl = googleDriveService.auth();
        assertNotNull(redirectUrl);
    }
    
    @Test
    public void testHandleRedirect() throws Exception {
        String refreshToken = googleDriveService.handleRedirect();
        assertEquals("REFRESH_TOKEN", refreshToken);
    }
    
    @Test
    public void testGetAllChildrenIds() throws Exception{
        Set<String> allChildrenIds = googleDriveService.getAllChildrenIds(GoogleDriveService.ROOT_DIR_ID);
        assertNotNull(allChildrenIds);
        assertFalse(allChildrenIds.isEmpty());
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(serviceDirectory);
    }
}

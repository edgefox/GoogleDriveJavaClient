package filesystem.change.local;

import filesystem.FileMetadata;
import filesystem.FileSystem;
import filesystem.Trie;
import filesystem.change.FileSystemChange;
import filesystem.change.remote.RemoteChangesWatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import service.GoogleDriveService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static junit.framework.Assert.*;
import static org.mockito.Mockito.*;

/**
 * User: Ivan Lyutov
 * Date: 11/25/13
 * Time: 12:16 PM
 */
@RunWith(MockitoJUnitRunner.class)
public class LocalChangesHandlerTest {
    private Path trackedPath = Paths.get("/tmp/GoogleDrive");
    @Mock
    private FileSystem fileSystem;
    @Mock
    private LocalChangesWatcher localChangesWatcher;
    @Mock
    private RemoteChangesWatcher remoteChangesWatcher;
    @Mock
    private GoogleDriveService googleDriveService;
    @Spy
    private Set<String> handledIds = new HashSet<>();
    @InjectMocks
    @Spy
    private LocalChangesHandler localChangesHandler = new LocalChangesHandler(trackedPath);

    private Trie<String, FileMetadata> rootTrie;
    private FileSystemChange<Path> directoryChange;
    private FileSystemChange<Path> fileChange;
    private FileSystemChange<Path> deletedChange;
    private Path directoryPath;
    private Path filePath;
    private Path deletedPath;
    private Trie<String, FileMetadata> directoryImage;
    private Trie<String, FileMetadata> fileImage;
    private Trie<String, FileMetadata> deletedImage;

    @Before
    public void setUp() throws Exception {
        rootTrie = new Trie<>(GoogleDriveService.ROOT_DIR_ID, new FileMetadata(GoogleDriveService.ROOT_DIR_ID,
                                                                               GoogleDriveService.ROOT_DIR_ID,
                                                                               true,
                                                                               null));

        directoryPath = trackedPath.resolve("directory");
        directoryChange = new FileSystemChange<>(directoryPath,
                                                 trackedPath,
                                                 directoryPath.getFileName().toString(),
                                                 true);
        directoryImage = new Trie<>(directoryPath.getFileName().toString(),
                                    new FileMetadata(UUID.randomUUID().toString(),
                                                     directoryPath.getFileName().toString(),
                                                     true,
                                                     UUID.randomUUID().toString()),
                                    rootTrie);
        filePath = trackedPath.resolve("filePath");
        fileChange = new FileSystemChange<>(filePath,
                                            trackedPath,
                                            filePath.getFileName().toString(),
                                            false);
        fileImage = new Trie<>(filePath.getFileName().toString(),
                               new FileMetadata(UUID.randomUUID().toString(),
                                                filePath.getFileName().toString(),
                                                false,
                                                UUID.randomUUID().toString()),
                               rootTrie);
        deletedPath = trackedPath.resolve("deleted");
        deletedChange = new FileSystemChange<>(deletedPath,
                                               null,
                                               deletedPath.getFileName().toString(),
                                               false);
        deletedImage = new Trie<>(deletedPath.getFileName().toString(),
                                  new FileMetadata(UUID.randomUUID().toString(),
                                                   deletedPath.getFileName().toString(),
                                                   true,
                                                   UUID.randomUUID().toString()),
                                  directoryImage);
        initMocks();
    }

    private void initMocks() throws IOException {
        when(fileSystem.get(Paths.get(""))).thenReturn(rootTrie);
        when(fileSystem.get(filePath)).thenReturn(fileImage);
        when(fileSystem.get(deletedPath)).thenReturn(deletedImage);

        when(googleDriveService.upload(GoogleDriveService.ROOT_DIR_ID, filePath.toFile()))
                .thenReturn(fileImage.getModel());
        when(googleDriveService.createOrGetDirectory(GoogleDriveService.ROOT_DIR_ID, directoryImage.getKey()))
                .thenReturn(directoryImage.getModel());
    }

    @Test
    public void testHandleNewDirectory() throws Exception {
        Set<FileSystemChange<Path>> changes = new HashSet<>();
        changes.add(directoryChange);

        when(localChangesWatcher.getChangesCopy()).thenReturn(changes);
        when(fileSystem.get(directoryPath)).thenReturn(null);

        localChangesHandler.handle();

        verify(localChangesHandler, times(1)).handleNewEntry(directoryChange);
        verify(localChangesHandler, times(1)).createDirectory(directoryChange);
        verify(googleDriveService, times(1)).createOrGetDirectory(GoogleDriveService.ROOT_DIR_ID, directoryImage.getKey());
        verify(fileSystem, times(1)).update(trackedPath.relativize(directoryPath), directoryImage.getModel());
        verify(localChangesWatcher, times(1)).changeHandled(directoryChange);
        verify(remoteChangesWatcher, times(1)).ignoreChanges(handledIds);

        assertTrue(handledIds.contains(directoryImage.getModel().getId()));
    }

    @Test
    public void testHandleNewFile() throws Exception {
        Set<FileSystemChange<Path>> changes = new HashSet<>();
        changes.add(fileChange);

        when(localChangesWatcher.getChangesCopy()).thenReturn(changes);
        when(fileSystem.get(filePath)).thenReturn(null);

        localChangesHandler.handle();

        verify(localChangesHandler, times(1)).handleNewEntry(fileChange);
        verify(localChangesHandler, times(1)).uploadLocalFile(fileChange);
        verify(googleDriveService, times(1)).upload(GoogleDriveService.ROOT_DIR_ID, filePath.toFile());
        verify(fileSystem, times(1)).update(trackedPath.relativize(filePath), fileImage.getModel());
        verify(localChangesWatcher, times(1)).changeHandled(fileChange);
        verify(remoteChangesWatcher, times(1)).ignoreChanges(handledIds);

        assertTrue(handledIds.contains(fileImage.getModel().getId()));
    }

    @Test
    public void testHandleExistingDirectory() throws Exception {
        Set<FileSystemChange<Path>> changes = new HashSet<>();
        changes.add(directoryChange);

        when(localChangesWatcher.getChangesCopy()).thenReturn(changes);
        when(fileSystem.get(trackedPath.relativize(directoryPath))).thenReturn(directoryImage);

        localChangesHandler.handle();

        verify(localChangesHandler, times(1)).handleExistingEntry(directoryChange, directoryImage);
        verify(localChangesHandler, times(1)).createDirectory(directoryChange);
        verify(googleDriveService, times(1)).createOrGetDirectory(GoogleDriveService.ROOT_DIR_ID, directoryImage.getKey());
        verify(fileSystem, times(1)).update(trackedPath.relativize(directoryPath), directoryImage.getModel());
        verify(localChangesWatcher, times(1)).changeHandled(directoryChange);
        verify(remoteChangesWatcher, times(1)).ignoreChanges(handledIds);

        assertTrue(handledIds.contains(directoryImage.getModel().getId()));
    }

    @Test
    public void testHandleExistingFile() throws Exception {
        Set<FileSystemChange<Path>> changes = new HashSet<>();
        changes.add(fileChange);

        when(localChangesWatcher.getChangesCopy()).thenReturn(changes);
        when(fileSystem.get(trackedPath.relativize(filePath))).thenReturn(fileImage);

        localChangesHandler.handle();

        verify(localChangesHandler, times(1)).handleExistingEntry(fileChange, fileImage);
        verify(localChangesHandler, times(1)).uploadLocalFile(fileChange);
        verify(googleDriveService, times(1)).upload(GoogleDriveService.ROOT_DIR_ID, filePath.toFile());
        verify(fileSystem, times(1)).update(trackedPath.relativize(filePath), fileImage.getModel());
        verify(localChangesWatcher, times(1)).changeHandled(fileChange);
        verify(remoteChangesWatcher, times(1)).ignoreChanges(handledIds);

        assertTrue(handledIds.contains(fileImage.getModel().getId()));
    }

    @Test
    public void testHandleDeletedEntry() throws Exception {
        Set<FileSystemChange<Path>> changes = new HashSet<>();
        changes.add(deletedChange);

        when(localChangesWatcher.getChangesCopy()).thenReturn(changes);
        when(fileSystem.get(trackedPath.relativize(deletedPath))).thenReturn(deletedImage);

        localChangesHandler.handle();

        verify(localChangesHandler, times(1)).handleExistingEntry(deletedChange, deletedImage);
        verify(localChangesHandler, times(1)).deleteRemoteFile(deletedImage);
        verify(googleDriveService, times(1)).delete(deletedImage.getModel().getId());
        verify(fileSystem, times(1)).delete(deletedImage);
        verify(localChangesWatcher, times(1)).changeHandled(deletedChange);
        verify(remoteChangesWatcher, times(1)).ignoreChanges(handledIds);

        assertTrue(handledIds.contains(deletedImage.getModel().getId()));
    }

    @Test
    public void testHandleNewFileUploadFail() throws Exception {
        Set<FileSystemChange<Path>> changes = new HashSet<>();
        changes.add(fileChange);

        when(googleDriveService.upload(GoogleDriveService.ROOT_DIR_ID, filePath.toFile())).
                thenThrow(new IOException("Failed"));
        when(localChangesWatcher.getChangesCopy()).thenReturn(changes);
        when(fileSystem.get(filePath)).thenReturn(null);

        localChangesHandler.handle();

        verify(localChangesHandler, times(4)).handleNewEntry(fileChange);
        verify(localChangesHandler, times(4)).uploadLocalFile(fileChange);
        verify(googleDriveService, times(4)).upload(GoogleDriveService.ROOT_DIR_ID, filePath.toFile());
        verify(fileSystem, never()).update(trackedPath.relativize(filePath), fileImage.getModel());
        verify(localChangesWatcher, times(1)).changeHandled(fileChange);
        verify(remoteChangesWatcher, times(1)).ignoreChanges(handledIds);

        assertFalse(handledIds.contains(fileImage.getModel().getId()));
    }

    @Test
    public void testHandleNewFileFailWithoutParentEntry() throws Exception {
        Set<FileSystemChange<Path>> changes = new HashSet<>();
        changes.add(fileChange);

        when(localChangesWatcher.getChangesCopy()).thenReturn(changes);
        when(fileSystem.get(filePath)).thenReturn(fileImage);
        when(fileSystem.get(trackedPath.relativize(fileChange.getParentId()))).thenReturn(null);

        localChangesHandler.handle();

        verify(localChangesHandler, times(4)).handleNewEntry(fileChange);
        verify(localChangesHandler, times(4)).uploadLocalFile(fileChange);
        verify(googleDriveService, never()).upload(GoogleDriveService.ROOT_DIR_ID, filePath.toFile());
        verify(fileSystem, never()).update(trackedPath.relativize(filePath), fileImage.getModel());
        verify(localChangesWatcher, times(1)).changeHandled(fileChange);
        verify(remoteChangesWatcher, times(1)).ignoreChanges(handledIds);

        assertFalse(handledIds.contains(fileImage.getModel().getId()));
    }
}

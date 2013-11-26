package filesystem.change.remote;

import filesystem.FileMetadata;
import filesystem.FileSystem;
import filesystem.Trie;
import filesystem.change.FileSystemChange;
import filesystem.change.local.LocalChangesWatcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;
import service.GoogleDriveService;

import java.io.File;
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
public class RemoteChangesHandlerTest {
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
    private Set<Path> handledPaths = new HashSet<>();
    @InjectMocks
    @Spy
    private RemoteChangesHandler remoteChangesHandler;

    private FileSystemChange<String> directory = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                        GoogleDriveService.ROOT_DIR_ID,
                                                                        UUID.randomUUID().toString(),
                                                                        true);
    private FileSystemChange<String> file = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                   GoogleDriveService.ROOT_DIR_ID,
                                                                   UUID.randomUUID().toString(),
                                                                   false);
    private FileSystemChange<String> deletedEntry = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                           null,
                                                                           UUID.randomUUID().toString(),
                                                                           false);
    private Trie<String, FileMetadata> rootTrie = new Trie<>();

    @Before
    public void setUp() throws Exception {
        remoteChangesHandler = new RemoteChangesHandler(trackedPath);
        MockitoAnnotations.initMocks(this);
        when(fileSystem.get(GoogleDriveService.ROOT_DIR_ID)).thenReturn(rootTrie);
    }

    @Test
    public void testHandleNewDirectory() throws Exception {
        HashSet<FileSystemChange<String>> changesToHandle = new HashSet<>();
        changesToHandle.add(directory);
        when(remoteChangesWatcher.getChangesCopy()).thenReturn(changesToHandle);
        when(fileSystem.getFullPath(rootTrie)).thenReturn(trackedPath);

        remoteChangesHandler.handle();

        verify(remoteChangesHandler, times(1)).handleNewEntry(directory);
        verify(remoteChangesHandler, times(1)).createDirectory(directory);
        verify(fileSystem, times(1)).update(any(Path.class), any(FileMetadata.class));
        verify(remoteChangesWatcher, times(1)).changeHandled(directory);
        verify(localChangesWatcher, times(1)).ignoreChanges(handledPaths);

        assertTrue(handledPaths.contains(trackedPath.resolve(directory.getTitle())));
    }

    @Test
    public void testHandleNewFile() throws Exception {
        HashSet<FileSystemChange<String>> changesToHandle = new HashSet<>();
        changesToHandle.add(file);
        when(remoteChangesWatcher.getChangesCopy()).thenReturn(changesToHandle);
        when(fileSystem.getFullPath(rootTrie)).thenReturn(trackedPath);
        FileMetadata newFileMetadata = new FileMetadata(file.getId(), file.getTitle(), file.isDir(), null);
        when(googleDriveService.downloadFile(anyString(), any(File.class))).thenReturn(newFileMetadata);

        remoteChangesHandler.handle();

        verify(remoteChangesHandler, times(1)).handleNewEntry(file);
        verify(remoteChangesHandler, times(1)).downloadNewFile(file);
        verify(fileSystem, times(1)).update(Paths.get(file.getTitle()), newFileMetadata);
        verify(remoteChangesWatcher, times(1)).changeHandled(file);
        verify(localChangesWatcher, times(1)).ignoreChanges(handledPaths);

        assertTrue(handledPaths.contains(trackedPath.resolve(file.getTitle())));
    }

    @Test
    public void testHandleNewDeletedEntry() throws Exception {
        HashSet<FileSystemChange<String>> changesToHandle = new HashSet<>();
        changesToHandle.add(deletedEntry);
        when(remoteChangesWatcher.getChangesCopy()).thenReturn(changesToHandle);
        when(fileSystem.get(deletedEntry.getId())).thenReturn(null);

        remoteChangesHandler.handle();

        verify(remoteChangesHandler, times(1)).handleNewEntry(deletedEntry);
        verify(fileSystem, never()).delete(Mockito.<Trie<String, FileMetadata>>any());
        verify(remoteChangesWatcher, times(1)).changeHandled(deletedEntry);
        verify(localChangesWatcher, times(1)).ignoreChanges(handledPaths);

        assertTrue(handledPaths.isEmpty());
    }
}

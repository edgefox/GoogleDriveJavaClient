package net.edgefox.googledrive.filesystem.change.remote;

import net.edgefox.googledrive.filesystem.FileMetadata;
import net.edgefox.googledrive.filesystem.FileSystem;
import net.edgefox.googledrive.filesystem.Trie;
import net.edgefox.googledrive.filesystem.change.FileSystemChange;
import net.edgefox.googledrive.filesystem.change.local.LocalChangesWatcher;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.runners.MockitoJUnitRunner;
import net.edgefox.googledrive.service.GoogleDriveService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * User: Ivan Lyutov
 * Date: 11/25/13
 * Time: 12:16 PM
 */
@RunWith(MockitoJUnitRunner.class)
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
    private RemoteChangesHandler remoteChangesHandler = new RemoteChangesHandler();

    private FileSystemChange<String> directoryChange = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                              GoogleDriveService.ROOT_DIR_ID,
                                                                              UUID.randomUUID().toString(),
                                                                              true, null);
    private FileSystemChange<String> fileChange = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                         GoogleDriveService.ROOT_DIR_ID,
                                                                         UUID.randomUUID().toString(),
                                                                         false, UUID.randomUUID().toString());
    private FileSystemChange<String> deletedEntryChange = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                                 null,
                                                                                 UUID.randomUUID().toString(),
                                                                                 false, null);
    private FileSystemChange<String> movedChange = new FileSystemChange<>(UUID.randomUUID().toString(),
                                                                          directoryChange.getId(),
                                                                          UUID.randomUUID().toString(),
                                                                          true, UUID.randomUUID().toString());
    private Trie<String, FileMetadata> rootTrie;

    @Before
    public void setUp() throws Exception {
        Files.createDirectories(trackedPath);
        rootTrie = new Trie<>(GoogleDriveService.ROOT_DIR_ID, new FileMetadata(GoogleDriveService.ROOT_DIR_ID,
                                                                               GoogleDriveService.ROOT_DIR_ID,
                                                                               true,
                                                                               null));
        when(fileSystem.get(GoogleDriveService.ROOT_DIR_ID)).thenReturn(rootTrie);
    }

    @Test
    public void testHandleNewDirectory() throws Exception {
        HashSet<FileSystemChange<String>> changesToHandle = new HashSet<>();
        changesToHandle.add(directoryChange);
        when(remoteChangesWatcher.getChangesCopy()).thenReturn(changesToHandle);
        when(fileSystem.getFullPath(rootTrie)).thenReturn(trackedPath);

        remoteChangesHandler.handle();

        verify(remoteChangesHandler, times(1)).handleNewEntry(directoryChange);
        verify(remoteChangesHandler, times(1)).createDirectory(directoryChange);
        verify(fileSystem, times(1)).update(any(Path.class), any(FileMetadata.class));
        verify(remoteChangesWatcher, times(1)).changeHandled(directoryChange);
        verify(localChangesWatcher, times(1)).ignoreChanges(handledPaths);

        assertTrue(handledPaths.contains(trackedPath.resolve(directoryChange.getTitle())));
    }

    @Test
    public void testHandleNewFile() throws Exception {
        HashSet<FileSystemChange<String>> changesToHandle = new HashSet<>();
        changesToHandle.add(fileChange);
        when(remoteChangesWatcher.getChangesCopy()).thenReturn(changesToHandle);
        when(fileSystem.getFullPath(rootTrie)).thenReturn(trackedPath);
        FileMetadata newFileMetadata = new FileMetadata(fileChange.getId(),
                                                        fileChange.getTitle(),
                                                        fileChange.isDir(),
                                                        null);
        Path filePath = trackedPath.resolve(fileChange.getTitle());
        when(googleDriveService.downloadFile(anyString(), any(File.class))).thenReturn(newFileMetadata);

        remoteChangesHandler.handle();

        verify(remoteChangesHandler, times(1)).handleNewEntry(fileChange);
        verify(remoteChangesHandler, times(1)).downloadNewFile(fileChange);
        verify(googleDriveService, times(1)).downloadFile(fileChange.getId(), filePath.toFile());
        verify(fileSystem, times(1)).update(trackedPath.resolve(fileChange.getTitle()), newFileMetadata);
        verify(remoteChangesWatcher, times(1)).changeHandled(fileChange);
        verify(localChangesWatcher, times(1)).ignoreChanges(handledPaths);

        assertTrue(handledPaths.contains(filePath));
    }

    @Test
    public void testHandleNewDeletedEntry() throws Exception {
        HashSet<FileSystemChange<String>> changesToHandle = new HashSet<>();
        changesToHandle.add(deletedEntryChange);
        when(remoteChangesWatcher.getChangesCopy()).thenReturn(changesToHandle);
        when(fileSystem.get(deletedEntryChange.getId())).thenReturn(null);

        remoteChangesHandler.handle();

        verify(remoteChangesHandler, times(1)).handleNewEntry(deletedEntryChange);
        verify(fileSystem, never()).delete(Mockito.<Trie<String, FileMetadata>>any());
        verify(remoteChangesWatcher, times(1)).changeHandled(deletedEntryChange);
        verify(localChangesWatcher, times(1)).ignoreChanges(handledPaths);

        assertTrue(handledPaths.isEmpty());
    }

    @Test
    public void testHandleExistingFileUpdate() throws Exception {
        HashSet<FileSystemChange<String>> changesToHandle = new HashSet<>();
        changesToHandle.add(fileChange);
        when(remoteChangesWatcher.getChangesCopy()).thenReturn(changesToHandle);

        FileMetadata remoteMetadata = new FileMetadata(fileChange.getId(), fileChange.getTitle(), fileChange.isDir(), UUID.randomUUID().toString());
        FileMetadata fileMetadata = new FileMetadata(fileChange.getId(), fileChange.getTitle(), fileChange.isDir(), UUID.randomUUID().toString());
        Trie<String, FileMetadata> fileImage = new Trie<>(fileChange.getTitle(), fileMetadata, rootTrie);
        Path filePath = trackedPath.resolve(fileChange.getTitle());
        when(fileSystem.get(fileChange.getId())).thenReturn(fileImage);
        when(fileSystem.getFullPath(fileImage)).thenReturn(filePath);
        when(googleDriveService.downloadFile(anyString(), any(File.class))).thenReturn(remoteMetadata);

        remoteChangesHandler.handle();

        verify(remoteChangesHandler, times(1)).handleExistingEntry(fileChange, fileImage);
        verify(remoteChangesHandler, times(1)).updateLocalFile(fileChange, fileImage, filePath.toFile());
        verify(googleDriveService, times(1)).downloadFile(fileMetadata.getId(), filePath.toFile());
        verify(remoteChangesWatcher, times(1)).changeHandled(fileChange);
        verify(localChangesWatcher, times(1)).ignoreChanges(handledPaths);

        assertTrue(handledPaths.contains(filePath));
    }

    @Test
    public void testHandleExistingDirectory() throws Exception {
        HashSet<FileSystemChange<String>> changesToHandle = new HashSet<>();
        changesToHandle.add(directoryChange);
        when(remoteChangesWatcher.getChangesCopy()).thenReturn(changesToHandle);

        FileMetadata fileMetadata = new FileMetadata(directoryChange.getId(),
                                                     directoryChange.getTitle(),
                                                     directoryChange.isDir(),
                                                     null);
        Trie<String, FileMetadata> fileImage = new Trie<>(directoryChange.getTitle(),
                                                          fileMetadata,
                                                          rootTrie);
        Path filePath = trackedPath.resolve(directoryChange.getTitle());
        when(fileSystem.get(directoryChange.getId())).thenReturn(fileImage);
        when(fileSystem.getFullPath(fileImage)).thenReturn(filePath);

        remoteChangesHandler.handle();

        verify(remoteChangesHandler, times(1)).handleExistingEntry(directoryChange, fileImage);
        verify(remoteChangesHandler, never()).createDirectory(directoryChange);
        verify(remoteChangesWatcher, times(1)).changeHandled(directoryChange);
        verify(localChangesWatcher, times(1)).ignoreChanges(handledPaths);

        assertFalse(handledPaths.contains(filePath));
    }

    @Test
    public void testHandleExistingDeletedEntry() throws Exception {
        HashSet<FileSystemChange<String>> changesToHandle = new HashSet<>();
        changesToHandle.add(deletedEntryChange);
        when(remoteChangesWatcher.getChangesCopy()).thenReturn(changesToHandle);

        FileMetadata fileMetadata = new FileMetadata(deletedEntryChange.getId(),
                                                     deletedEntryChange.getTitle(),
                                                     deletedEntryChange.isDir(),
                                                     null);
        Trie<String, FileMetadata> fileImage = new Trie<>(deletedEntryChange.getTitle(), fileMetadata, rootTrie);
        Path filePath = trackedPath.resolve(deletedEntryChange.getTitle());
        Files.createFile(filePath);
        when(fileSystem.get(deletedEntryChange.getId())).thenReturn(fileImage);
        when(fileSystem.getFullPath(fileImage)).thenReturn(filePath);

        remoteChangesHandler.handle();

        verify(remoteChangesHandler, times(1)).handleExistingEntry(deletedEntryChange, fileImage);
        verify(remoteChangesHandler, times(1)).deleteLocalFile(fileImage, filePath.toFile());
        verify(fileSystem, times(1)).delete(Mockito.<Trie<String, FileMetadata>>any());
        verify(remoteChangesWatcher, times(1)).changeHandled(deletedEntryChange);
        verify(localChangesWatcher, times(1)).ignoreChanges(handledPaths);

        assertTrue(handledPaths.contains(filePath));
        assertFalse(Files.exists(filePath));
    }

    @Test
    public void testHandleMove() throws Exception {
        HashSet<FileSystemChange<String>> changesToHandle = new HashSet<>();
        changesToHandle.add(movedChange);
        when(remoteChangesWatcher.getChangesCopy()).thenReturn(changesToHandle);

        FileMetadata fileMetadata = new FileMetadata(movedChange.getId(),
                                                     movedChange.getTitle(),
                                                     movedChange.isDir(),
                                                     null);
        Trie<String, FileMetadata> fileImage = new Trie<>(movedChange.getTitle(), fileMetadata, rootTrie);
        Path filePath = trackedPath.resolve(movedChange.getTitle());

        FileMetadata parentFileMetadata = new FileMetadata(directoryChange.getId(),
                                                           directoryChange.getTitle(),
                                                           directoryChange.isDir(),
                                                           null);
        Trie<String, FileMetadata> newParentImage = new Trie<>(directoryChange.getTitle(), parentFileMetadata, rootTrie);
        Path newParentPath = trackedPath.resolve(deletedEntryChange.getTitle());

        Files.createDirectory(newParentPath);
        Files.createFile(filePath);

        when(fileSystem.get(movedChange.getId())).thenReturn(fileImage);
        when(fileSystem.get(movedChange.getParentId())).thenReturn(newParentImage);
        when(fileSystem.getFullPath(fileImage)).thenReturn(filePath);
        when(fileSystem.getFullPath(newParentImage)).thenReturn(newParentPath);

        remoteChangesHandler.handle();
        
        verify(remoteChangesHandler, times(1)).handleExistingEntry(movedChange, fileImage);
        verify(remoteChangesHandler, times(1)).moveLocalFile(movedChange, fileImage);
        verify(fileSystem, times(1)).move(fileImage, newParentImage);
        verify(googleDriveService, times(1)).getAllChildrenIds(movedChange.getId());
        verify(remoteChangesWatcher, times(1)).changeHandled(movedChange);
        verify(localChangesWatcher, times(1)).ignoreChanges(handledPaths);

        Path newFilePath = newParentPath.resolve(movedChange.getTitle());
        assertTrue(handledPaths.contains(newFilePath));
        assertTrue(Files.exists(newFilePath));
        assertFalse(Files.exists(filePath));
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.forceDelete(trackedPath.toFile());
    }
}

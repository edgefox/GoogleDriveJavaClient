package filesystem;

import com.google.inject.Provider;

import javax.inject.Inject;

/**
 * User: Ivan Lyutov
 * Date: 11/21/13
 * Time: 4:44 PM
 */
public class FileSystemRevisionProvider implements Provider<FileSystemRevision> {
    @Inject
    private FileSystem fileSystem;
    
    @Override
    public FileSystemRevision get() {
        return fileSystem.getFileSystemRevision();
    }
}

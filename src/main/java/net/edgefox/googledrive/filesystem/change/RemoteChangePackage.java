package net.edgefox.googledrive.filesystem.change;

import java.util.List;

/**
 * User: Ivan Lyutov
 * Date: 11/20/13
 * Time: 1:16 PM
 */
public class RemoteChangePackage {
    private long revisionNumber;
    private volatile List<FileSystemChange<String>> changes;

    public RemoteChangePackage(long revisionNumber, List<FileSystemChange<String>> changes) {
        this.revisionNumber = revisionNumber;
        this.changes = changes;
    }

    public long getRevisionNumber() {
        return revisionNumber;
    }

    public List<FileSystemChange<String>> getChanges() {
        return changes;
    }
}

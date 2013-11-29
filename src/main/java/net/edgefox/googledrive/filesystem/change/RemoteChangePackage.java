package net.edgefox.googledrive.filesystem.change;

import java.util.Set;

/**
 * User: Ivan Lyutov
 * Date: 11/20/13
 * Time: 1:16 PM
 */
public class RemoteChangePackage {
    private long revisionNumber;
    private volatile Set<FileSystemChange<String>> changes;

    public RemoteChangePackage(long revisionNumber, Set<FileSystemChange<String>> changes) {
        this.revisionNumber = revisionNumber;
        this.changes = changes;
    }

    public long getRevisionNumber() {
        return revisionNumber;
    }

    public Set<FileSystemChange<String>> getChanges() {
        return changes;
    }
}

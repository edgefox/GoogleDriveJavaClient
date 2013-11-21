package filesystem.change;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: Ivan Lyutov
 * Date: 11/20/13
 * Time: 2:21 PM
 */
public abstract class ChangesWatcher<T> {
    protected final Set<FileSystemChange<T>> changes = new LinkedHashSet<>();
    protected Set<T> handledEntries = new HashSet<>();
    
    public abstract void start() throws Exception;

    public Set<FileSystemChange<T>> getChangesCopy() {
        return new LinkedHashSet<>(changes);
    }

    public void changeHandled(FileSystemChange<T> changeToHandle) {
        changes.remove(changeToHandle);
    }

    public void ignoreChanges(Set<T> handledEntries) {
        this.handledEntries.addAll(handledEntries);
    }
}

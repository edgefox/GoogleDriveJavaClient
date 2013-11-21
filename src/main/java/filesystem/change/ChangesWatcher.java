package filesystem.change;

import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * User: Ivan Lyutov
 * Date: 11/20/13
 * Time: 2:21 PM
 */
public abstract class ChangesWatcher<T> {
    protected final Set<FileSystemChange<T>> changes;
    @Inject
    protected ScheduledExecutorService executorService;

    public ChangesWatcher() {
        changes = new LinkedHashSet<>();
    }
    
    public abstract void start() throws Exception;

    public Set<FileSystemChange<T>> getChangesCopy() {
        return new LinkedHashSet<>(changes);
    }

    public void changeHandled(FileSystemChange<T> changeToHandle) {
        changes.remove(changeToHandle);
    }
}

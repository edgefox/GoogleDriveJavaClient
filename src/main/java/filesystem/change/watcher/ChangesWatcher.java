package filesystem.change.watcher;

import filesystem.change.FileSystemChange;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * User: Ivan Lyutov
 * Date: 11/20/13
 * Time: 2:21 PM
 */
public abstract class ChangesWatcher<T> {
    final Queue<FileSystemChange<T>> changes;
    final ScheduledExecutorService executorService;

    public ChangesWatcher() {
        executorService = Executors.newSingleThreadScheduledExecutor();
        changes = new LinkedList<FileSystemChange<T>>();
    }
    
    public abstract void start() throws Exception;
    
    public void stop() {
        executorService.shutdownNow();
    }

    public Queue<FileSystemChange<T>> getChangesCopy() {
        return new LinkedList<FileSystemChange<T>>(changes);
    }

    public void changeHandled(FileSystemChange<T> changeToHandle) {
        changes.remove(changeToHandle);
    }
}

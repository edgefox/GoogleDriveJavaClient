package filesystem.change;

/**
 * User: Ivan Lyutov
 * Date: 10/15/13
 * Time: 5:32 PM
 */
public class FileSystemChange<T> {
    private T id;
    private T parentId;
    private String title;
    private boolean dir;

    public FileSystemChange(T id, T parentId, String title, boolean dir) {
        this.id = id;
        this.parentId = parentId;
        this.title = title;
        this.dir = dir;
    }

    public T getId() {
        return id;
    }

    public T getParentId() {
        return parentId;
    }

    public String getTitle() {
        return title;
    }

    public boolean isDir() {
        return dir;
    }

    public boolean isRemoved() {
        return parentId == null;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("FileSystemChange{");
        sb.append("id='").append(id).append('\'');
        sb.append("parentId='").append(parentId).append('\'');
        sb.append("title='").append(title).append('\'');
        sb.append("dir='").append(dir).append('\'');
        sb.append(", removed=").append(isRemoved());
        sb.append('}');
        return sb.toString();
    }
}

package net.edgefox.googledrive.filesystem.change;

import com.google.api.services.drive.model.Change;
import net.edgefox.googledrive.service.util.GoogleDriveUtils;

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
    private String md5CheckSum;

    public FileSystemChange(T id, T parentId, String title, boolean dir, String md5CheckSum) {
        this.id = id;
        this.parentId = parentId;
        this.title = title;
        this.dir = dir;
        this.md5CheckSum = md5CheckSum;
    }

    @SuppressWarnings("unchecked")
    public FileSystemChange(Change change) {
        id = (T) change.getId();
        parentId = (T) GoogleDriveUtils.getParentId(change);
        title = change.getDeleted() ? null : change.getFile().getTitle();
        dir = GoogleDriveUtils.getIsGoogleDir(change);
        md5CheckSum = GoogleDriveUtils.getMd5CheckSumFromChange(change);
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

    public String getMd5CheckSum() {
        return md5CheckSum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileSystemChange)) return false;

        FileSystemChange that = (FileSystemChange) o;

        if (dir != that.dir) return false;
        if (!id.equals(that.id)) return false;
        if (parentId != null ? !parentId.equals(that.parentId) : that.parentId != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (parentId != null ? parentId.hashCode() : 0);
        result = 31 * result + (dir ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("FileSystemChange{");
        sb.append("id='").append(id).append('\'');
        sb.append("parentId='").append(parentId).append('\'');
        sb.append("title='").append(title).append('\'');
        sb.append("dir='").append(dir).append('\'');
        sb.append(", removed=").append(isRemoved());
        sb.append(", md5CheckSum=").append(md5CheckSum);
        sb.append('}');
        return sb.toString();
    }
}

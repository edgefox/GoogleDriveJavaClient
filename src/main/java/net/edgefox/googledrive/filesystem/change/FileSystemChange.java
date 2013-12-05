package net.edgefox.googledrive.filesystem.change;

import com.google.api.services.drive.model.Change;
import net.edgefox.googledrive.service.GoogleDriveUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

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
        id = (T) change.getFileId();
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof FileSystemChange)) {
            return false;
        }

        FileSystemChange that = (FileSystemChange) o;

        return new EqualsBuilder().append(dir, that.dir).
                                   append(id, that.id).
                                   append(parentId, that.parentId).build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(id).
                                     append(parentId).
                                     append(dir).build();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE).build();
    }
}

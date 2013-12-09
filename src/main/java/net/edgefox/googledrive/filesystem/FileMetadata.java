package net.edgefox.googledrive.filesystem;

import com.google.api.services.drive.model.File;
import net.edgefox.googledrive.util.GoogleDriveUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;

/**
 * User: Ivan Lyutov
 * Date: 10/2/13
 * Time: 1:22 AM
 */
public class FileMetadata implements Serializable {
    private String id;
    private String title;
    private boolean dir;
    private String checkSum;

    public FileMetadata(String id, String title, boolean dir, String checkSum) {
        this.id = id;
        this.title = title;
        this.dir = dir;
        this.checkSum = checkSum;
    }

    public FileMetadata(File driveFile) {
        id = driveFile.getId();
        title = driveFile.getTitle();
        dir = GoogleDriveUtils.isGoogleDir(driveFile);
        checkSum = driveFile.getMd5Checksum();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isDir() {
        return dir;
    }

    public String getCheckSum() {
        return checkSum;
    }

    public void setCheckSum(String checkSum) {
        this.checkSum = checkSum;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE).append(id)
                                                                        .append(title)
                                                                        .append(dir)
                                                                        .append(checkSum).build();
    }
}

package filesystem;

/**
 * User: Ivan Lyutov
 * Date: 11/20/13
 * Time: 1:26 PM
 */
public class FileSystemRevision {
    private long revisionNumber;

    public FileSystemRevision(long revisionNumber) {
        this.revisionNumber = revisionNumber;
    }

    public long getRevisionNumber() {
        return revisionNumber;
    }

    public void setRevisionNumber(long revisionNumber) {
        this.revisionNumber = revisionNumber;
    }
}

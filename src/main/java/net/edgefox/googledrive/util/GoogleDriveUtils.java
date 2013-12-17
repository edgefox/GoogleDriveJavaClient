package net.edgefox.googledrive.util;

import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;
import net.edgefox.googledrive.service.GoogleDriveService;

import java.util.List;

/**
 * User: Ivan Lyutov
 * Date: 12/2/13
 * Time: 5:57 PM
 */
public class GoogleDriveUtils {
    private static final String GOOGLE_DOC = "application/vnd.google-apps.document";
    private static final String SPREADSHEET = "application/vnd.google-apps.spreadsheet";
    private static final String PRESENTATION = "application/vnd.google-apps.presentation";
    private static final String GOOGLE_DRAWING = "application/vnd.google-apps.drawing";
    private static final String GOOGLE_SCRIPT = "application/vnd.google-apps.script";
    private static final String GOOGLE_SITE = "application/vnd.google-apps.sites";
    private static final String GOOGLE_FUSION_TABLE = "application/vnd.google-apps.fusiontable";
    private static final String GOOGLE_FORM = "application/vnd.google-apps.form";
    private static final String GOOGLE_FOLDER = "application/vnd.google-apps.folder";

    private GoogleDriveUtils() {
    }

    public static String getMd5CheckSumFromChange(Change change) {
        if (change.getDeleted()) {
            return null;
        } else if (isSupportedGoogleApp(change.getFile())) {
            return change.getFile().getEtag();
        } else {
            return change.getFile().getMd5Checksum();
        }
    }

    public static boolean isGoogleDir(Change change) {
        return !change.getDeleted() && GOOGLE_FOLDER.equals(change.getFile().getMimeType());
    }

    public static boolean isGoogleDir(File file) {
        return GOOGLE_FOLDER.equals(file.getMimeType());
    }

    public static boolean isSupportedGoogleApp(File file) {
        return GOOGLE_DOC.equals(file.getMimeType()) ||
               SPREADSHEET.equals(file.getMimeType()) ||
               PRESENTATION.equals(file.getMimeType());
    }
    
    public static boolean isRestrictedGoogleApp(File file) {
        return GOOGLE_DRAWING.equals(file.getMimeType()) ||
               GOOGLE_SCRIPT.equals(file.getMimeType()) ||
               GOOGLE_SITE.equals(file.getMimeType()) ||
               GOOGLE_FUSION_TABLE.equals(file.getMimeType()) ||
               GOOGLE_FORM.equals(file.getMimeType());

    }

    public static String getParentId(Change change) {
        String parentId = null;
        if (change.getDeleted() || change.getFile().getLabels().getTrashed()) {
            return parentId;
        }

        List<ParentReference> parents = change.getFile().getParents();
        ParentReference parentReference;
        if (parents.isEmpty()) {
            parentReference = new ParentReference();
            parentReference.setId("shared");
            parentReference.setIsRoot(false);
        }
        else {
            parentReference = parents.get(0);
        }

        return parentReference.getIsRoot() ? GoogleDriveService.ROOT_DIR_ID : parentReference.getId();
    }
}

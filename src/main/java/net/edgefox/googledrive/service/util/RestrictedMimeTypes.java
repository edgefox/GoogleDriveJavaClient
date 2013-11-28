package net.edgefox.googledrive.service.util;

/**
 * User: Ivan Lyutov
 * Date: 11/18/13
 * Time: 8:01 PM
 */
public enum RestrictedMimeTypes {    
    DRAWING("application/vnd.google-apps.drawing"),
    SCRIPT("application/vnd.google-apps.script"),
    SITES("application/vnd.google-apps.sites"),
    FUSIONTABLE("application/vnd.google-apps.fusiontable"),
    FORM("application/vnd.google-apps.form");
    
    private String value;

    private RestrictedMimeTypes(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
    
    public static boolean isRestricted(String rawMimeType) {
        for (RestrictedMimeTypes mimeType : RestrictedMimeTypes.values()) {
            if (mimeType.getValue().equals(rawMimeType)) {
                return true;
            }
        }

        return false;
    }
}

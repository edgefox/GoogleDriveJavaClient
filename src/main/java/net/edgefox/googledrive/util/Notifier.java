package net.edgefox.googledrive.util;

import ch.swingfx.twinkle.NotificationBuilder;
import ch.swingfx.twinkle.style.INotificationStyle;
import ch.swingfx.twinkle.style.theme.DarkDefaultNotification;
import ch.swingfx.twinkle.window.Positions;

import javax.swing.*;

/**
 * User: Ivan Lyutov
 * Date: 11/28/13
 * Time: 12:56 PM
 */
public class Notifier {
    
    private Notifier() {        
    }
    
    public static void showRemoteChangeMessage(String message) {
        showMessage("Remote update", message);
    }

    public static void showLocalChangeMessage(String message) {
        showMessage("Local update", message);
    }

    public static void showSystemMessage(String message) {
        showMessage("System notification", message);
    }

    private static void showMessage(String title, String message) {
        System.setProperty("swing.aatext", "true");

        INotificationStyle style = new DarkDefaultNotification()
                .withWidth(400)
                .withAlpha(0.9f);

        new NotificationBuilder()
                .withStyle(style)
                .withTitle(title)
                .withMessage(message)
                .withIcon(new ImageIcon(Notifier.class.getResource("/drive-icon.png")))
                .withDisplayTime(5000)
                .withPosition(Positions.NORTH_EAST)
                .showNotification();

    }

}
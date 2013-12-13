package net.edgefox.googledrive.gui;

import com.google.inject.Singleton;
import net.edgefox.googledrive.Application;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * User: Ivan Lyutov
 * Date: 12/13/13
 * Time: 3:33 PM
 */
@Singleton
public class DriveTray {
    @Inject
    private Application application;
    
    public void start() throws AWTException, MalformedURLException {
        if (SystemTray.isSupported()) {
            final SystemTray systemTray = SystemTray.getSystemTray();
            final TrayIcon trayIcon = getTrayIcon();
            final PopupMenu popupMenu = getPopupMenu(systemTray, trayIcon);

            trayIcon.setPopupMenu(popupMenu);
            systemTray.add(trayIcon);
        }
    }

    private PopupMenu getPopupMenu(final SystemTray systemTray, final TrayIcon trayIcon) {
        final PopupMenu popupMenu = new PopupMenu();
        MenuItem pauseItem = new MenuItem("Pause/Resume sync");
        MenuItem exitItem = new MenuItem("Exit");
        popupMenu.add(pauseItem);
        popupMenu.add(exitItem);
        pauseItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (application.isPaused()) {
                    application.resume();
                } else {
                    application.pause();
                }
            }
        });
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                systemTray.remove(trayIcon);
                System.exit(0);
            }
        });
        return popupMenu;
    }

    private TrayIcon getTrayIcon() throws MalformedURLException {
        TrayIcon trayIcon = new TrayIcon(createImage("drive-icon.png", "Google Drive"));
        trayIcon.setImageAutoSize(true);
        return trayIcon;
    }

    private Image createImage(String path, String description) {
        URL imageURL = Thread.currentThread().getContextClassLoader().getResource(path);
        if (imageURL == null) {
            System.err.println("Resource not found: " + path);
            return null;
        } else {
            return (new ImageIcon(imageURL, description)).getImage();
        }
    }
}

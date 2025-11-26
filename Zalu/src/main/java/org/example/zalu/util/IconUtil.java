package org.example.zalu.util;

import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignV;

/**
 * Utility class để tạo các icon đẹp sử dụng Ikonli
 */
public class IconUtil {
    
    /**
     * Tạo FontIcon với style đẹp
     */
    public static FontIcon createIcon(Ikon icon, double size, Color color) {
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize((int) size);
        fontIcon.setIconColor(color);
        return fontIcon;
    }
    
    /**
     * Tạo Label chứa icon với style đẹp
     */
    public static Label createIconLabel(Ikon icon, double size, Color color) {
        Label label = new Label();
        FontIcon fontIcon = createIcon(icon, size, color);
        label.setGraphic(fontIcon);
        label.getStyleClass().add("icon-label");
        return label;
    }
    
    /**
     * Tạo Label chứa icon với màu mặc định
     */
    public static Label createIconLabel(Ikon icon, double size) {
        return createIconLabel(icon, size, Color.web("#65676b"));
    }
    
    // ========== File Icons ==========
    
    public static Label getFileIcon(String fileName, double size, Color color) {
        String lowerName = fileName.toLowerCase();
        Ikon icon = getFileIkon(lowerName);
        return createIconLabel(icon, size, color);
    }
    
    public static Label getFileIcon(String fileName, double size) {
        return getFileIcon(fileName, size, Color.web("#65676b"));
    }
    
    public static Ikon getFileIkon(String lowerFileName) {
        if (lowerFileName.endsWith(".pdf")) return MaterialDesignF.FILE_DOCUMENT;
        if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx")) return MaterialDesignF.FILE_DOCUMENT;
        if (lowerFileName.endsWith(".xls") || lowerFileName.endsWith(".xlsx")) return MaterialDesignF.FILE_DOCUMENT;
        if (lowerFileName.endsWith(".ppt") || lowerFileName.endsWith(".pptx")) return MaterialDesignF.FILE_DOCUMENT;
        if (lowerFileName.endsWith(".zip") || lowerFileName.endsWith(".rar") || lowerFileName.endsWith(".7z")) {
            return MaterialDesignF.FILE;
        }
        if (lowerFileName.endsWith(".mp4") || lowerFileName.endsWith(".avi") || lowerFileName.endsWith(".mkv")) {
            return MaterialDesignV.VIDEO;
        }
        if (lowerFileName.endsWith(".mp3") || lowerFileName.endsWith(".wav") || lowerFileName.endsWith(".m4a")) {
            return FontAwesomeSolid.MUSIC;
        }
        if (lowerFileName.endsWith(".txt")) return MaterialDesignF.FILE_DOCUMENT;
        return MaterialDesignF.FILE; // Default attachment icon
    }
    
    // ========== Common Icons ==========
    
    public static Label getPinIcon(double size) {
        return createIconLabel(MaterialDesignP.PIN, size, Color.web("#e53e3e"));
    }
    
    public static Label getPinIcon() {
        return getPinIcon(18);
    }
    
    public static Label getAttachmentIcon(double size, Color color) {
        return createIconLabel(MaterialDesignP.PAPERCLIP, size, color);
    }
    
    public static Label getAttachmentIcon(double size) {
        return getAttachmentIcon(size, Color.web("#65676b"));
    }
    
    public static Label getAttachmentIcon() {
        return getAttachmentIcon(20);
    }
    
    public static Label getCheckIcon(double size, Color color) {
        return createIconLabel(FontAwesomeSolid.CHECK_CIRCLE, size, color);
    }
    
    public static Label getCheckIcon(double size) {
        return getCheckIcon(size, Color.web("#31d559"));
    }
    
    public static Label getCheckIcon() {
        return getCheckIcon(18);
    }
    
    public static Label getPlayIcon(double size, Color color) {
        return createIconLabel(FontAwesomeSolid.PLAY, size, color);
    }
    
    public static Label getPlayIcon(double size) {
        return getPlayIcon(size, Color.WHITE);
    }
    
    public static Label getPlayIcon() {
        return getPlayIcon(24);
    }
    
    public static Label getPauseIcon(double size, Color color) {
        return createIconLabel(FontAwesomeSolid.PAUSE, size, color);
    }
    
    public static Label getPauseIcon(double size) {
        return getPauseIcon(size, Color.WHITE);
    }
    
    public static Label getPauseIcon() {
        return getPauseIcon(24);
    }
    
    public static Label getCloseIcon(double size, Color color) {
        return createIconLabel(FontAwesomeSolid.TIMES, size, color);
    }
    
    public static Label getCloseIcon(double size) {
        return getCloseIcon(size, Color.web("#666"));
    }
    
    public static Label getCloseIcon() {
        return getCloseIcon(16);
    }
    
    // ========== Media Icons ==========
    
    public static Label getVideoIcon(double size) {
        return createIconLabel(FontAwesomeSolid.VIDEO, size, Color.web("#e53e3e"));
    }
    
    public static Label getImageIcon(double size) {
        return createIconLabel(FontAwesomeSolid.IMAGE, size, Color.web("#0d8bff"));
    }
    
    public static Label getAudioIcon(double size) {
        return createIconLabel(FontAwesomeSolid.VOLUME_UP, size, Color.web("#31d559"));
    }
}


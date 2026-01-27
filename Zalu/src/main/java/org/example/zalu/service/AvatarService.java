package org.example.zalu.service;

import javafx.scene.image.Image;
import org.example.zalu.model.User;

import java.io.ByteArrayInputStream;
import java.net.URL;

/**
 * Service để xử lý avatar images
 */
public class AvatarService {
    private static Image defaultAvatar;

    /**
     * Resolve avatar image từ User object
     */
    public static Image resolveAvatar(User user) {
        if (user == null) {
            return getDefaultAvatar();
        }

        // 1. Check ClientCache trước (avatar đã download)
        byte[] cachedAvatarData = org.example.zalu.client.ClientCache.getInstance().getAvatar(user.getId());
        if (cachedAvatarData != null && cachedAvatarData.length > 0) {
            try {
                Image cachedImage = new Image(new ByteArrayInputStream(cachedAvatarData), 48, 48, true, true);
                if (!cachedImage.isError()) {
                    System.out.println("AvatarService: Using cached avatar for user " + user.getId());
                    return cachedImage;
                }
            } catch (Exception e) {
                System.err.println("Error loading cached avatar: " + e.getMessage());
            }
        }

        // 2. Fallback về avatarData hoặc avatarPath
        return resolveAvatar(user.getAvatarData(), user.getAvatarUrlRaw());
    }

    /**
     * Resolve avatar image từ avatarData hoặc avatarPath
     */
    public static Image resolveAvatar(byte[] avatarData, String avatarPath) {
        Image image = null;

        // Debug log
        System.out.println("AvatarService.resolveAvatar called:");
        System.out.println("  - avatarData: " + (avatarData != null ? avatarData.length + " bytes" : "null"));
        System.out.println("  - avatarPath: " + (avatarPath != null ? "'" + avatarPath + "'" : "null"));

        // Ưu tiên avatarData (BLOB)
        if (avatarData != null && avatarData.length > 0) {
            try {
                image = new Image(new ByteArrayInputStream(avatarData), 48, 48, true, true);
                System.out.println("  → Loaded from avatarData successfully");
            } catch (Exception e) {
                System.err.println("Error loading avatar from data: " + e.getMessage());
            }
        }

        // Fallback về avatarPath
        if (image == null && avatarPath != null && !avatarPath.isEmpty()) {
            image = loadImageFromPath(avatarPath);
            if (image != null) {
                System.out.println("  → Loaded from avatarPath successfully");
            }
        }

        // Fallback về default
        if (image == null) {
            image = getDefaultAvatar();
            System.out.println("  → Using default avatar");
        }

        return image;
    }

    /**
     * Load image từ path (URL, file path, hoặc resource path)
     */
    public static Image loadImageFromPath(String path) {
        if (path == null || path.isBlank())
            return null;

        try {
            if (path.startsWith("http")) {
                return new Image(path, 48, 48, true, true, false);
            } else if (path.startsWith("file:") || path.matches("^[a-zA-Z]:\\\\.*")) {
                String fileUrl = path.startsWith("file:") ? path : "file:" + path;
                return new Image(fileUrl, 48, 48, true, true, false);
            } else {
                String normalized = path.startsWith("/") ? path : "/" + path;
                URL resource = AvatarService.class.getResource(normalized);
                if (resource != null) {
                    return new Image(resource.toExternalForm(), 48, 48, true, true, false);
                }
            }
        } catch (Exception e) {
            System.err.println("Không load được ảnh từ " + path + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Get default avatar
     */
    public static Image getDefaultAvatar() {
        if (defaultAvatar == null) {
            defaultAvatar = loadImageFromPath("/images/default-avatar.jpg");
        }
        return defaultAvatar;
    }

    /**
     * Get default avatar với kích thước tùy chỉnh
     */
    public static Image getDefaultAvatar(double width, double height) {
        Image img = loadImageFromPath("/images/default-avatar.jpg");
        if (img != null && !img.isError()) {
            return new Image(img.getUrl(), width, height, true, true);
        }
        return img;
    }

    /**
     * Load avatar với kích thước tùy chỉnh
     */
    public static Image resolveAvatar(byte[] avatarData, String avatarPath, double width, double height) {
        Image image = null;

        if (avatarData != null && avatarData.length > 0) {
            try {
                image = new Image(new ByteArrayInputStream(avatarData), width, height, true, true);
            } catch (Exception e) {
                System.err.println("Error loading avatar from data: " + e.getMessage());
            }
        }

        if (image == null && avatarPath != null && !avatarPath.isEmpty()) {
            try {
                if (avatarPath.startsWith("http")) {
                    image = new Image(avatarPath, width, height, true, true, false);
                } else if (avatarPath.startsWith("file:") || avatarPath.matches("^[a-zA-Z]:\\\\.*")) {
                    String fileUrl = avatarPath.startsWith("file:") ? avatarPath : "file:" + avatarPath;
                    image = new Image(fileUrl, width, height, true, true, false);
                } else {
                    String normalized = avatarPath.startsWith("/") ? avatarPath : "/" + avatarPath;
                    URL resource = AvatarService.class.getResource(normalized);
                    if (resource != null) {
                        image = new Image(resource.toExternalForm(), width, height, true, true, false);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error loading avatar from path: " + e.getMessage());
            }
        }

        if (image == null || image.isError()) {
            image = getDefaultAvatar(width, height);
        }

        return image;
    }
}

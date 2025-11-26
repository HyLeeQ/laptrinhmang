package org.example.zalu.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;

public class AvatarUtils {
    public static void loadAvatar(ImageView avatarView, String avatarPath, double width, double height) {
        try (InputStream stream = AvatarUtils.class.getResourceAsStream(avatarPath)){
            if (stream != null) {
                Image image = new Image(stream);
                avatarView.setImage(image);
            } else {
                avatarView.setImage(createPlaceholderImage(width, height));
            }
        } catch (Exception e) {
            System.err.println("Avatar load error: " + e.getMessage() + " for path: " + avatarPath);
            avatarView.setImage(createPlaceholderImage(width, height));
        }
    }
    private static Image createPlaceholderImage(double width, double height) {
        return null;
    }
}


package org.example.zalu.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import org.example.zalu.model.User;

import java.io.ByteArrayInputStream;
import java.util.Map;

public class FriendListCell extends ListCell<User> {
    private final Map<Integer, Boolean> onlineFriends;
    private final Map<Integer, String> lastMessages;
    private static final String DEFAULT_AVATAR = "/images/default-avatar.jpg";

    public FriendListCell(Map<Integer, Boolean> onlineFriends, Map<Integer, String> lastMessages) {
        this.onlineFriends = onlineFriends;
        this.lastMessages = lastMessages;
    }

    @Override
    protected void updateItem(User user, boolean empty) {
        super.updateItem(user, empty);
        if (empty || user == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        HBox itemBox = new HBox(10);
        itemBox.setAlignment(Pos.CENTER_LEFT);
        itemBox.setPadding(new Insets(10, 5, 10, 5));
        itemBox.setStyle("-fx-background-color: transparent;");

        Circle avatar = createAvatarCircle(user);

        boolean isOnline = onlineFriends.getOrDefault(user.getId(), false);
        Circle statusDot = new Circle(8, isOnline ? Color.GREEN : Color.GRAY);

        VBox infoBox = new VBox(2);
        String displayName = (user.getFullName() != null && !user.getFullName().trim().isEmpty()) 
                ? user.getFullName() 
                : user.getUsername();
        Label nameLabel = new Label(displayName);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        String preview = lastMessages != null ? lastMessages.getOrDefault(user.getId(), "Bắt đầu trò chuyện...") : "Bắt đầu trò chuyện...";
        Label previewLabel = new Label(preview);
        previewLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 12; -fx-max-width: 150;");
        previewLabel.setWrapText(true);
        infoBox.getChildren().addAll(nameLabel, previewLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        itemBox.getChildren().addAll(avatar, statusDot, infoBox, spacer);

        itemBox.setOnMouseEntered(e -> itemBox.setStyle("-fx-background-color: #f5f7fb;"));
        itemBox.setOnMouseExited(e -> itemBox.setStyle("-fx-background-color: transparent;"));

        setGraphic(itemBox);
    }

    private Circle createAvatarCircle(User user) {
        Circle circle = new Circle(25, Color.web("#1a73e8"));
        circle.setStroke(Color.rgb(255, 255, 255, 0.9));
        circle.setStrokeWidth(2);

        Image avatarImage = null;
        if (user.getAvatarData() != null && user.getAvatarData().length > 0) {
            avatarImage = new Image(new ByteArrayInputStream(user.getAvatarData()), 50, 50, true, true);
        } else {
            avatarImage = loadImageFromPath(user.getAvatarUrlRaw());
        }

        if (avatarImage == null || avatarImage.isError()) {
            avatarImage = loadImageFromPath(DEFAULT_AVATAR);
        }

        if (avatarImage != null && !avatarImage.isError()) {
            circle.setFill(new ImagePattern(avatarImage));
        }
        return circle;
    }

    private Image loadImageFromPath(String path) {
        try {
            String resolved = path;
            if (resolved == null || resolved.trim().isEmpty() || resolved.equals("/default-avatar.jpg")) {
                resolved = DEFAULT_AVATAR;
            }
            if (resolved.startsWith("http") || resolved.startsWith("file:")) {
                return new Image(resolved, 50, 50, true, true, false);
            }
            String normalized = resolved.startsWith("/") ? resolved : "/" + resolved;
            var stream = getClass().getResourceAsStream(normalized);
            if (stream != null) {
                Image img = new Image(stream, 50, 50, true, true);
                stream.close();
                return img;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}


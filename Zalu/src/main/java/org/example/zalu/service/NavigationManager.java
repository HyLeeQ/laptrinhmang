package org.example.zalu.service;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import org.example.zalu.model.User;

/**
 * Manager để quản lý navigation avatar và menu
 */
public class NavigationManager {
    private final ImageView navAvatarImage;
    private ContextMenu navAccountMenu;
    private Label navMenuNameLabel;
    private Label navMenuStatusLabel;
    private Runnable onProfileClick;
    private Runnable onLogoutClick;
    private Runnable onSettingsClick;
    private Runnable onUpgradeClick;

    public NavigationManager(ImageView navAvatarImage) {
        this.navAvatarImage = navAvatarImage;
    }

    /**
     * Setup navigation account menu
     */
    public void setupMenu(Runnable onProfileClick, Runnable onLogoutClick,
            Runnable onSettingsClick, Runnable onUpgradeClick) {
        this.onProfileClick = onProfileClick;
        this.onLogoutClick = onLogoutClick;
        this.onSettingsClick = onSettingsClick;
        this.onUpgradeClick = onUpgradeClick;

        if (navAvatarImage == null)
            return;

        navAccountMenu = new ContextMenu();

        navMenuNameLabel = new Label("Tài khoản của bạn");
        navMenuNameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 600;");

        navMenuStatusLabel = new Label("Đang hoạt động");
        navMenuStatusLabel.setStyle("-fx-text-fill: #7a839b; -fx-font-size: 12px;");

        VBox headerBox = new VBox(2, navMenuNameLabel, navMenuStatusLabel);
        headerBox.setPadding(new Insets(10, 14, 6, 14));

        CustomMenuItem headerItem = new CustomMenuItem(headerBox, false);

        MenuItem upgradeItem = new MenuItem("Nâng cấp tài khoản");
        upgradeItem.setOnAction(e -> {
            if (onUpgradeClick != null)
                onUpgradeClick.run();
        });

        MenuItem profileItem = new MenuItem("Hồ sơ của bạn");
        profileItem.setOnAction(e -> {
            if (onProfileClick != null)
                onProfileClick.run();
        });

        MenuItem settingsItem = new MenuItem("Cài đặt");
        settingsItem.setOnAction(e -> {
            if (onSettingsClick != null)
                onSettingsClick.run();
        });

        MenuItem logoutItem = new MenuItem("Đăng xuất");
        logoutItem.setOnAction(e -> {
            if (onLogoutClick != null)
                onLogoutClick.run();
        });

        navAccountMenu.getItems().addAll(headerItem, new SeparatorMenuItem(),
                upgradeItem, profileItem, settingsItem, new SeparatorMenuItem(), logoutItem);

        navAvatarImage.setOnMouseClicked(event -> {
            if (navAccountMenu == null)
                return;
            if (navAccountMenu.isShowing()) {
                navAccountMenu.hide();
            } else {
                navAccountMenu.show(navAvatarImage, Side.RIGHT, 8, 0);
            }
        });
    }

    private int currentUserId = -1;

    /**
     * Set current user ID
     */
    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
    }

    /**
     * Update navigation info (avatar & menu) from User object
     */
    public void updateUserInfo(User user) {
        if (user == null)
            return;
        this.currentUserId = user.getId();

        Platform.runLater(() -> {
            // Update Menu Header
            if (navMenuNameLabel != null) {
                String displayName = (user.getFullName() != null && !user.getFullName().isBlank())
                        ? user.getFullName()
                        : user.getUsername();
                navMenuNameLabel.setText(displayName != null ? displayName : "Tài khoản của bạn");
                navMenuStatusLabel.setText("Đang hoạt động");
            }

            // Update Avatar
            if (navAvatarImage != null) {
                try {
                    Image avatar = AvatarService.resolveAvatar(user);
                    if (avatar != null) {
                        navAvatarImage.setImage(avatar);
                    }
                } catch (Exception e) {
                    System.err.println("Cannot load avatar: " + e.getMessage());
                    Image fallback = AvatarService.getDefaultAvatar();
                    if (fallback != null) {
                        navAvatarImage.setImage(fallback);
                    }
                }
            }
        });
    }
}

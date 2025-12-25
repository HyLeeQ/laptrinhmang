package org.example.zalu.controller.profile;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.example.zalu.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class BioViewController {
    private static final Logger logger = LoggerFactory.getLogger(BioViewController.class);
    @FXML
    private ImageView avatarImageView;
    @FXML
    private Label fullNameLabel;
    @FXML
    private Label usernameLabel;
    @FXML
    private Label emailLabel;
    @FXML
    private TextArea bioTextArea;
    @FXML
    private Label genderLabel;
    @FXML
    private Label birthdateLabel;
    @FXML
    private Label phoneLabel;
    @FXML
    private Button editButton;

    private Stage stage;

    private int currentUserId = -1;
    private int displayedUserId = -1;
    private static final String DEFAULT_AVATAR = "/images/default-avatar.jpg";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        updateEditButtonVisibility();
    }

    public void setUserId(int userId) {
        this.displayedUserId = userId;

        // Register callback to update UI when data arrives
        org.example.zalu.client.ChatEventManager.getInstance().registerGetUserByIdCallback(users -> {
            if (users != null) {
                for (User u : users) {
                    if (u.getId() == this.displayedUserId) {
                        javafx.application.Platform.runLater(() -> loadUserBio(u));
                        break;
                    }
                }
            }
        });

        // Request data
        org.example.zalu.client.ChatClient.sendRequest("GET_USER_BY_ID|" + userId);
        updateEditButtonVisibility();
    }

    public void setUser(User user) {
        if (user != null) {
            this.displayedUserId = user.getId();
            loadUserBio(user);
            updateEditButtonVisibility();
        }
    }

    private void updateEditButtonVisibility() {
        if (editButton != null) {
            boolean isOwnProfile = (currentUserId > 0 && displayedUserId > 0 && currentUserId == displayedUserId);
            editButton.setVisible(isOwnProfile);
            editButton.setManaged(isOwnProfile);
        }
    }

    private void loadUserBio(User user) {
        // Avatar
        loadAvatar(avatarImageView, user.getAvatarData(), user.getAvatarUrl(), 120, 120);

        // Tên
        String displayName = (user.getFullName() != null && !user.getFullName().trim().isEmpty())
                ? user.getFullName()
                : user.getUsername();
        fullNameLabel.setText(displayName);

        // Username
        usernameLabel.setText("@" + user.getUsername());

        // Email
        emailLabel.setText(user.getEmail() != null ? user.getEmail() : "Chưa cập nhật");

        // Bio
        String bio = user.getBio();
        if (bio == null || bio.trim().isEmpty()) {
            bioTextArea.setText("Chưa có giới thiệu.");
            bioTextArea.setStyle("-fx-text-fill: #8d96b2; -fx-font-style: italic;");
        } else {
            bioTextArea.setText(bio);
            bioTextArea.setStyle("-fx-text-fill: #0f1c2e;");
        }

        // Giới tính
        String gender = user.getGender();
        if (gender != null && !gender.trim().isEmpty()) {
            switch (gender.toLowerCase()) {
                case "male":
                    genderLabel.setText("Nam");
                    break;
                case "female":
                    genderLabel.setText("Nữ");
                    break;
                case "other":
                    genderLabel.setText("Khác");
                    break;
                default:
                    genderLabel.setText(gender);
            }
        } else {
            genderLabel.setText("Chưa cập nhật");
        }

        // Ngày sinh
        LocalDate birthdate = user.getBirthdate();
        if (birthdate != null) {
            birthdateLabel.setText(birthdate.format(DATE_FORMATTER));
        } else {
            birthdateLabel.setText("Chưa cập nhật");
        }

        // Điện thoại
        String phone = user.getPhone();
        if (phone != null && !phone.trim().isEmpty()) {
            phoneLabel.setText(phone);
        } else {
            phoneLabel.setText("Chưa cập nhật");
        }
    }

    private void loadAvatar(ImageView view, byte[] data, String path, double width, double height) {
        Image image = null;
        if (data != null && data.length > 0) {
            image = new Image(new ByteArrayInputStream(data), width, height, true, true);
        } else {
            image = loadAvatarFromPath(path, width, height);
        }
        if (image == null) {
            image = loadAvatarFromPath(DEFAULT_AVATAR, width, height);
        }
        if (image != null) {
            view.setImage(image);
            view.setFitWidth(width);
            view.setFitHeight(height);
            view.setPreserveRatio(true);
        }
    }

    private Image loadAvatarFromPath(String path, double width, double height) {
        try {
            if (path == null || path.trim().isEmpty()) {
                path = DEFAULT_AVATAR;
            }
            if (path.startsWith("http") || path.startsWith("file:")) {
                return new Image(path, width, height, true, true, false);
            }
            String normalized = path.startsWith("/") ? path : "/" + path;
            var stream = getClass().getResourceAsStream(normalized);
            if (stream != null) {
                Image image = new Image(stream, width, height, true, true);
                stream.close();
                return image;
            }
        } catch (Exception e) {
            logger.error("Không thể load avatar từ path {}: {}", path, e.getMessage(), e);
        }
        return null;
    }

    private Runnable onProfileUpdateCallback;

    public void setOnProfileUpdateCallback(Runnable callback) {
        this.onProfileUpdateCallback = callback;
    }

    @FXML
    private void openEditProfile() {
        if (displayedUserId <= 0) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/zalu/views/profile/profile-view.fxml"));
            Parent root = loader.load();
            ProfileController profileCtrl = loader.getController();
            profileCtrl.setCurrentUserId(displayedUserId);

            profileCtrl.setOnProfileUpdated(updatedUser -> {
                // Refresh profile data locally
                if (updatedUser != null) {
                    javafx.application.Platform.runLater(() -> loadUserBio(updatedUser));
                }

                if (onProfileUpdateCallback != null) {
                    onProfileUpdateCallback.run();
                }
            });

            Stage profileStage = new Stage();
            profileCtrl.setStage(profileStage);
            profileStage.setScene(new Scene(root, 800, 600));
            profileStage.setTitle("Chỉnh sửa hồ sơ");
            profileStage.show();

        } catch (IOException e) {
            logger.error("Không thể mở profile view: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void closeBioView() {
        if (stage != null) {
            stage.close();
        }
    }
}

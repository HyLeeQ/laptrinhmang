package org.example.zalu.controller.profile;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.User;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ProfileController {
    @FXML private TextField fullNameField;
    @FXML private TextField phoneField;
    @FXML private TextArea bioField;
    @FXML private ImageView avatarImageView;
    @FXML private Label statusLabel;
    @FXML private Label usernameLabel;
    @FXML private Label emailLabel;
    @FXML private DatePicker birthdatePicker;
    @FXML private PasswordField newPasswordField;
    @FXML private RadioButton maleRadio;
    @FXML private RadioButton femaleRadio;
    @FXML private RadioButton otherRadio;
    @FXML private Button changePasswordButton;
    private ToggleGroup genderGroup;

    private Stage stage;
    private int currentUserId = -1;
    private UserDAO userDAO;
    private User currentUser;
    private static final String DEFAULT_AVATAR = "/images/default-avatar.jpg";
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;  // 2MB

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        loadUserProfile();
    }

    @FXML
    public void initialize() {
        try {
            userDAO = new UserDAO();
        } catch (Exception e) {
            System.err.println("Error init UserDAO: " + e.getMessage());
        }
        genderGroup = new ToggleGroup();
        if (maleRadio != null) maleRadio.setToggleGroup(genderGroup);
        if (femaleRadio != null) femaleRadio.setToggleGroup(genderGroup);
        if (otherRadio != null) {
            otherRadio.setToggleGroup(genderGroup);
            otherRadio.setSelected(true);
        }
        newPasswordField.setVisible(false);
        newPasswordField.setManaged(false);
    }

    private void loadUserProfile() {
        if (currentUserId <= 0 || userDAO == null) return;
        try {
            currentUser = userDAO.getUserById(currentUserId);
            if (currentUser != null) {
                if (usernameLabel != null) {
                    usernameLabel.setText(currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getUsername());
                }
                if (emailLabel != null) {
                    emailLabel.setText("Email: " + (currentUser.getEmail() != null ? currentUser.getEmail() : ""));
                }
                fullNameField.setText(currentUser.getFullName());
                phoneField.setText(currentUser.getPhone() != null ? currentUser.getPhone() : "");
                bioField.setText(currentUser.getBio() != null ? currentUser.getBio() : "");
                if (birthdatePicker != null) {
                    birthdatePicker.setValue(currentUser.getBirthdate());
                }

                if (genderGroup != null) {
                    String gender = currentUser.getGender();
                    if ("male".equalsIgnoreCase(gender) && maleRadio != null) {
                        maleRadio.setSelected(true);
                    } else if ("female".equalsIgnoreCase(gender) && femaleRadio != null) {
                        femaleRadio.setSelected(true);
                    } else if (otherRadio != null) {
                        otherRadio.setSelected(true);
                    }
                }
                loadAvatar(avatarImageView, currentUser.getAvatarData(), currentUser.getAvatarUrl(), 100, 100);
                if (statusLabel != null) {
                    statusLabel.setText("");
                }
            }
        } catch (org.example.zalu.exception.auth.UserNotFoundException e) {
            showAlert("Không tìm thấy người dùng: " + e.getMessage());
        } catch (org.example.zalu.exception.database.DatabaseException | org.example.zalu.exception.database.DatabaseConnectionException e) {
            showAlert("Lỗi tải profile: " + e.getMessage());
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
            System.err.println("Không thể load avatar từ path " + path + ": " + e.getMessage());
        }
        return null;
    }

    @FXML
    private void chooseAvatar() {
        if (currentUserId == -1) {
            showAlert("Chưa đăng nhập!");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh đại diện");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif")
        );
        File selected = chooser.showOpenDialog(stage);
        if (selected != null && selected.exists()) {
            try {
                byte[] bytes = Files.readAllBytes(selected.toPath());
                if (bytes.length > MAX_FILE_SIZE) {
                    showAlert("File quá lớn (>2MB) ! Chọn ảnh nhỏ hơn.");
                    return;
                }
                currentUser.setAvatarData(bytes);

                Image newImage = new Image(selected.toURI().toString());
                avatarImageView.setImage(resizeImage(newImage, 100, 100));
                statusLabel.setText("Đã chọn ảnh mới. Nhấn Lưu để cập nhật.");
            } catch (IOException e) {
                showAlert("Lỗi đọc ảnh: " + e.getMessage());
            }
        }
    }

    private Image resizeImage(Image source, double targetWidth, double targetHeight) {
        if (source == null || targetWidth <= 0 || targetHeight <= 0) {
            return source;
        }
        WritableImage resized = new WritableImage((int) targetWidth, (int) targetHeight);
        PixelWriter writer = resized.getPixelWriter();
        PixelReader reader = source.getPixelReader();

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                double sx = (x / targetWidth) * source.getWidth();
                double sy = (y / targetHeight) * source.getHeight();
                int px = Math.min((int) Math.floor(sx), (int) source.getWidth() - 1);
                int py = Math.min((int) Math.floor(sy), (int) source.getHeight() - 1);
                writer.setArgb(x, y, reader.getArgb(px, py));
            }
        }
        return resized;
    }

    @FXML
    private void changePassword() {
        boolean visible = !newPasswordField.isVisible();
        newPasswordField.setVisible(visible);
        newPasswordField.setManaged(visible);
        changePasswordButton.setText(visible ? "Ẩn" : "Đổi mật khẩu");
        if (visible) {
            newPasswordField.requestFocus();
            newPasswordField.clear();
        } else {
            newPasswordField.clear();
        }
    }

    @FXML
    private void saveProfile() {
        if (currentUser == null) {
            showAlert("Không tải được thông tin người dùng!");
            return;
        }
        try {
            currentUser.setFullName(fullNameField.getText().trim());
            currentUser.setPhone(phoneField.getText().trim());
            currentUser.setBio(bioField.getText().trim());
            if (birthdatePicker != null) {
                currentUser.setBirthdate(birthdatePicker.getValue());
            }

            if (genderGroup != null) {
                String gender = "other";
                if (genderGroup.getSelectedToggle() == maleRadio) {
                    gender = "male";
                } else if (genderGroup.getSelectedToggle() == femaleRadio) {
                    gender = "female";
                }
                currentUser.setGender(gender);
            }

            if (!newPasswordField.getText().trim().isEmpty()) {
                currentUser.setPassword(newPasswordField.getText().trim());
            }

            if (userDAO.updateUser(currentUser)) {
                showAlert("Cập nhật profile thành công!");
                statusLabel.setText("Profile đã lưu.");
                if (stage != null) {
                    stage.close();
                }
            } else {
                showAlert("Lỗi cập nhật profile! (Kiểm tra kết nối DB)");
            }
        } catch (Exception e) {
            showAlert("Lỗi save: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void viewBio() {
        if (currentUser == null) {
            showAlert("Không tải được thông tin người dùng!");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/profile/bio-view.fxml"));
            Parent root = loader.load();
            BioViewController bioController = loader.getController();
            bioController.setUser(currentUser);
            bioController.setCurrentUserId(currentUserId);
            
            Stage bioStage = new Stage();
            bioController.setStage(bioStage);
            bioStage.setScene(new Scene(root, 650, 700));
            String displayName = (currentUser.getFullName() != null && !currentUser.getFullName().trim().isEmpty())
                    ? currentUser.getFullName() : currentUser.getUsername();
            bioStage.setTitle("Hồ sơ của tôi - " + displayName);
            bioStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Không thể mở hồ sơ: " + e.getMessage());
        }
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
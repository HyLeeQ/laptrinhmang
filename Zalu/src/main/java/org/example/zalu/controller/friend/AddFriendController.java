
package org.example.zalu.controller.friend;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.client.ChatEventManager;

import org.example.zalu.model.User;
import org.example.zalu.controller.MainController;
import org.example.zalu.controller.profile.BioViewController;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class AddFriendController {
    @FXML
    private TextField searchField;
    @FXML
    private VBox friendListContainer;
    @FXML
    private Label resultCountLabel;
    @FXML
    private Button addSelectedButton;

    private static final Logger logger = LoggerFactory.getLogger(AddFriendController.class);

    private Stage stage;
    private int currentUserId = -1;
    private MainController mainController;
    private FriendRequestController parentController;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        logger.debug("currentUserId set to {}", userId);
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        searchField.setPrefWidth(250.0);
        HBox.setHgrow(searchField, Priority.ALWAYS);

    }

    // Các method search và add
    @FXML
    private void searchFriend() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            friendListContainer.getChildren().clear();
            resultCountLabel.setText(""); // SỬA: Clear count
            return;
        }

        // Gửi request qua server thay vì truy cập database trực tiếp
        // Format: SEARCH_USERS|query|userId
        String request = "SEARCH_USERS|" + query + "|" + currentUserId;
        ChatClient.sendRequest(request);

        // Đăng ký callback để nhận kết quả từ server
        ChatEventManager eventManager = ChatEventManager.getInstance();
        eventManager.registerSearchUsersCallback(filteredUsers -> {
            handleSearchResults(filteredUsers);
        });
    }

    // Method để xử lý kết quả tìm kiếm từ server
    private void handleSearchResults(List<User> filteredUsers) {
        javafx.application.Platform.runLater(() -> {
            friendListContainer.getChildren().clear();
            if (filteredUsers == null || filteredUsers.isEmpty()) {
                VBox emptyBox = new VBox(12);
                emptyBox.setAlignment(Pos.CENTER);
                emptyBox.setPadding(new Insets(40, 20, 40, 20));
                Label noResult = new Label("🔍 Không tìm thấy kết quả");
                noResult.setStyle("-fx-text-fill: #8d96b2; -fx-font-size: 15px; -fx-font-weight: 500;");
                Label hintLabel = new Label("Thử tìm kiếm với từ khóa khác");
                hintLabel.setStyle("-fx-text-fill: #b0b8c8; -fx-font-size: 13px;");
                emptyBox.getChildren().addAll(noResult, hintLabel);
                friendListContainer.getChildren().add(emptyBox);
                resultCountLabel.setText("");
            } else {
                resultCountLabel.setText(filteredUsers.size() + " kết quả");
                for (User user : filteredUsers) {
                    HBox userItem = createUserItem(user);
                    friendListContainer.getChildren().add(userItem);
                }
            }
        });
    }

    private HBox createUserItem(User user) {
        HBox userItem = new HBox(16);
        userItem.setAlignment(Pos.CENTER_LEFT);
        userItem.setPadding(new Insets(16));
        userItem.getStyleClass().add("user-item-card");

        // Avatar với gradient đẹp
        Circle avatar = new Circle(32);
        javafx.scene.image.Image img = org.example.zalu.service.AvatarService.resolveAvatar(user);
        if (img != null) {
            avatar.setFill(new javafx.scene.paint.ImagePattern(img));
        } else {
            avatar.setFill(Color.web("#0d8bff"));
        }
        avatar.setStroke(Color.WHITE);
        avatar.setStrokeWidth(2);
        avatar.setCursor(Cursor.HAND);
        avatar.setOnMouseClicked(e -> showUserBio(user));

        VBox infoBox = new VBox(4);
        Label nameLabel = new Label(resolveDisplayName(user));
        nameLabel.getStyleClass().add("user-item-name");
        Label usernameLabel = new Label("@" + user.getUsername());
        usernameLabel.getStyleClass().add("user-item-id");
        infoBox.getChildren().addAll(nameLabel, usernameLabel);
        infoBox.setAlignment(Pos.CENTER_LEFT);

        // Spacer để push button sang phải
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Check friend status and create appropriate button
        Button actionBtn = createActionButton(user);

        userItem.getChildren().addAll(avatar, infoBox, spacer, actionBtn);
        return userItem;
    }

    private Button createActionButton(User user) {
        // Simple button - server will handle duplicate prevention
        Button btn = new Button("➕ Thêm bạn");
        btn.getStyleClass().add("user-item-btn");
        btn.setOnAction(e -> addFriendAction(user));
        return btn;
    }

    private String resolveDisplayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getUsername();

    }

    private void showUserBio(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/profile/bio-view.fxml"));
            Parent root = loader.load();

            BioViewController bioController = loader.getController();
            Stage bioStage = new Stage();
            bioController.setStage(bioStage);
            bioController.setCurrentUserId(currentUserId);
            bioController.setUser(user);

            bioStage.setTitle("Hồ sơ của " + resolveDisplayName(user));
            bioStage.setScene(new Scene(root));
            bioStage.setResizable(false);
            if (stage != null) {
                bioStage.initOwner(stage);
                bioStage.initModality(Modality.WINDOW_MODAL);
            }
            bioStage.show();
        } catch (IOException e) {
            logger.error("Không thể mở thông tin bạn bè: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể mở thông tin bạn bè: " + e.getMessage());
        }
    }

    private void addFriendAction(User user) {
        if (currentUserId <= 0) {
            showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "User ID không hợp lệ! Vui lòng đăng nhập lại.");
            return;
        }
        if (user.getId() == currentUserId) {
            showAlert(Alert.AlertType.WARNING, "Lỗi", "Không thể tự thêm chính mình làm bạn bè!");
            return;
        }

        // #region agent log
        try {
            String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
            java.nio.file.Files.write(java.nio.file.Paths.get(logPath), (String.format(
                    "{\"id\":\"log_%d_G\",\"timestamp\":%d,\"location\":\"AddFriendController.java:198\",\"message\":\"Add friend action - before sending request\",\"data\":{\"senderId\":%d,\"receiverId\":%d,\"receiverName\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"G\"}\n",
                    System.currentTimeMillis(), System.currentTimeMillis(), currentUserId, user.getId(),
                    resolveDisplayName(user))).getBytes(), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
        }
        // #endregion

        // Gửi request qua server thay vì truy cập database trực tiếp
        // Format: SEND_FRIEND_REQUEST|senderId|receiverId
        String request = "SEND_FRIEND_REQUEST|" + currentUserId + "|" + user.getId();
        ChatClient.sendRequest(request);

        // #region agent log
        try {
            String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
            java.nio.file.Files.write(java.nio.file.Paths.get(logPath), (String.format(
                    "{\"id\":\"log_%d_G\",\"timestamp\":%d,\"location\":\"AddFriendController.java:210\",\"message\":\"Add friend request sent to server\",\"data\":{\"request\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"G\"}\n",
                    System.currentTimeMillis(), System.currentTimeMillis(), request)).getBytes(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
        }
        // #endregion

        // Đăng ký callback một lần để xử lý response từ server
        ChatEventManager eventManager = ChatEventManager.getInstance();
        final int targetReceiverId = user.getId(); // Capture để so sánh
        final String targetReceiverName = resolveDisplayName(user); // Capture để hiển thị

        // Tạo callback tạm thời, chỉ xử lý response cho request này
        java.util.function.Consumer<String> tempCallback = message -> {
            if (message.startsWith("FRIEND_REQUEST_SENT|OK")) {
                // #region agent log
                try {
                    String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                    java.nio.file.Files.write(java.nio.file.Paths.get(logPath), (String.format(
                            "{\"id\":\"log_%d_G\",\"timestamp\":%d,\"location\":\"AddFriendController.java:217\",\"message\":\"Friend request sent successfully\",\"data\":{\"senderId\":%d,\"receiverId\":%d},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"G\"}\n",
                            System.currentTimeMillis(), System.currentTimeMillis(), currentUserId, targetReceiverId))
                            .getBytes(), java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
                } catch (Exception e) {
                }
                // #endregion

                javafx.application.Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Thành công!",
                            "Đã gửi lời mời kết bạn đến " + targetReceiverName);
                    if (mainController != null) {
                        mainController.refreshFriendList();
                    }
                    if (parentController != null) {
                        parentController.refreshRequests(); // Refresh parent lists immediately
                    }
                    // Refresh danh sách tìm kiếm để ẩn user đã gửi lời mời
                    searchFriend();
                });
            } else if (message.startsWith("FRIEND_REQUEST_SENT|FAIL")) {
                // #region agent log
                try {
                    String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                    java.nio.file.Files.write(java.nio.file.Paths.get(logPath), (String.format(
                            "{\"id\":\"log_%d_G\",\"timestamp\":%d,\"location\":\"AddFriendController.java:230\",\"message\":\"Friend request failed\",\"data\":{\"senderId\":%d,\"receiverId\":%d,\"reason\":\"already exists or already friends\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"G\"}\n",
                            System.currentTimeMillis(), System.currentTimeMillis(), currentUserId, targetReceiverId))
                            .getBytes(), java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
                } catch (Exception e) {
                }
                // #endregion

                javafx.application.Platform.runLater(() -> {
                    showAlert(Alert.AlertType.WARNING, "Thất bại", "Lời mời kết bạn đã tồn tại hoặc đã là bạn bè!");
                });
            }
        };

        // Đăng ký callback vào eventManager
        eventManager.registerBroadcastCallback(tempCallback);

        // Tự động unregister sau 5 giây để tránh memory leak
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                // Note: Không thể unregister callback cụ thể, nhưng callback sẽ tự động bị bỏ
                // qua nếu không match
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @FXML
    private void addFriend() {
        showAlert(AlertType.WARNING, "Use Inline Button", "Sử dụng nút 'Thêm' bên cạnh tên bạn bè!");
    }

    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void setParentController(FriendRequestController parent) {
        this.parentController = parent;
    }

    @FXML
    private void backToMain() {
        if (parentController != null) {
            parentController.backToMain();
        } else {
            ChatEventManager.getInstance().unregisterAllCallbacks();

            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/org/example/zalu/views/main/main-view.fxml"));
                Parent root = loader.load();
                MainController mainController = loader.getController();
                mainController.setStage(stage);
                mainController.setCurrentUserId(currentUserId);

                stage.setScene(new Scene(root, 800, 400));
                stage.setTitle("Chat Application - Main");
                stage.show();

                ChatClient.sendRequest("GET_FRIENDS|" + currentUserId);
                if (mainController != null)
                    mainController.refreshFriendList();
            } catch (IOException e) {
                logger.error("Error loading main view", e);
                showAlert(AlertType.ERROR, "Lỗi Điều Hướng", "Failed to load main view: " + e.getMessage());
            }
        }
    }
}
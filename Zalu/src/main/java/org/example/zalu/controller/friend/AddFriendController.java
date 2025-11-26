

package org.example.zalu.controller.friend;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.client.ChatEventManager;
import org.example.zalu.dao.FriendDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.User;
import org.example.zalu.controller.MainController;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class AddFriendController {
    @FXML
    private TextField searchField;
    @FXML
    private VBox friendListContainer;
    @FXML
    private Label resultCountLabel;
    @FXML
    private Button addSelectedButton;

    private Stage stage;
    private FriendDAO friendDAO;
    private UserDAO userDAO;
    private int currentUserId =-1;
    private MainController mainController;
    private FriendRequestController parentController;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        System.out.println("AddFriendController: currentUserId set to " + userId);
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        searchField.setPrefWidth(250.0);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        try {
            friendDAO = new FriendDAO();
            userDAO = new UserDAO();
        } catch (Exception e) {
            System.out.println("Error initializing DAOs: " + e.getMessage());
            showAlert(AlertType.ERROR, "Initialization Error", "Failed to connect to database: " + e.getMessage());
        }
    }

    // Các method search và add
    @FXML
    private void searchFriend() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            friendListContainer.getChildren().clear();
            resultCountLabel.setText("");  // SỬA: Clear count
            return;
        }
        try {
            List<User> allUsers = userDAO.searchUsers(query);

            List<User> filteredUsers = allUsers.stream()
                    .filter(user -> user.getId() != currentUserId)
                    .filter(user -> {
                        try {
                            return !friendDAO.isExistingFriendOrRequest(currentUserId, user.getId());
                        } catch (SQLException e) {
                            System.out.println("Error checking existing: " + e.getMessage());
                            return true;  // Nếu lỗi, vẫn show (fallback)
                        }
                    })
                    .collect(Collectors.toList());

            // SỬA: Render dynamic HBox items thay vì setItems ListView
            friendListContainer.getChildren().clear();
            if (filteredUsers.isEmpty()) {
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
        } catch (org.example.zalu.exception.database.DatabaseException | org.example.zalu.exception.database.DatabaseConnectionException e) {
            System.out.println("Error searching users: " + e.getMessage());
            showAlert(AlertType.ERROR, "Search Error", "Failed to search users: " + e.getMessage());
            friendListContainer.getChildren().clear();
            resultCountLabel.setText("");
        }
    }

    private HBox createUserItem(User user) {
        HBox userItem = new HBox(16);
        userItem.setAlignment(Pos.CENTER_LEFT);
        userItem.setPadding(new Insets(16));
        userItem.getStyleClass().add("user-item-card");

        // Avatar với gradient đẹp
        Circle avatar = new Circle(32);
        avatar.setFill(Color.web("#0d8bff"));
        avatar.setStroke(Color.WHITE);
        avatar.setStrokeWidth(2);

        VBox infoBox = new VBox(4);
        Label nameLabel = new Label(user.getUsername());
        nameLabel.getStyleClass().add("user-item-name");
        Label idLabel = new Label("ID: " + user.getId());
        idLabel.getStyleClass().add("user-item-id");
        infoBox.getChildren().addAll(nameLabel, idLabel);
        infoBox.setAlignment(Pos.CENTER_LEFT);

        // Spacer để push button sang phải
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Button thêm bạn đẹp hơn
        Button addBtn = new Button("➕ Thêm bạn");
        addBtn.getStyleClass().add("user-item-btn");
        addBtn.setOnAction(e -> addFriendAction(user));

        userItem.getChildren().addAll(avatar, infoBox, spacer, addBtn);
        return userItem;
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

        try {
            boolean success = new FriendDAO().sendFriendRequest(currentUserId, user.getId());
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công!", "Đã gửi lời mời kết bạn đến " + user.getUsername() + " (ID: " + user.getId() + ")");
                if (mainController != null) {
                    mainController.refreshFriendList();
                }
            } else {
                showAlert(Alert.AlertType.WARNING, "Thất bại", "Lời mời kết bạn đã tồn tại hoặc đã là bạn bè!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi cơ sở dữ liệu", "Không thể gửi lời mời: " + e.getMessage());
        }
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
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/main/main-view.fxml"));
                Parent root = loader.load();
                MainController mainController = loader.getController();
                mainController.setStage(stage);
                mainController.setCurrentUserId(currentUserId);

                stage.setScene(new Scene(root, 800, 400));
                stage.setTitle("Chat Application - Main");
                stage.show();

                ChatClient.sendRequest("GET_FRIENDS|" + currentUserId);
                if (mainController != null) mainController.refreshFriendList();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Error loading main view: " + e.getMessage());
                showAlert(AlertType.ERROR, "Lỗi Điều Hướng", "Failed to load main view: " + e.getMessage());
            }
        }
    }
}
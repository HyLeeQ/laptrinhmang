package org.example.zalu.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.zalu.dao.FriendDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.DBConnection;
import org.example.zalu.model.Friend;
import org.example.zalu.model.User;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class AddFriendController {
    @FXML
    private TextField searchField;
    @FXML
    private ListView<User> friendList; // Thay đổi kiểu thành ListView<User>

    private Stage stage;
    private FriendDAO friendDAO;
    private UserDAO userDAO;
    private int currentUserId;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
    }

    @FXML
    public void initialize() {
        try {
            Connection connection = DBConnection.getConnection();
            if (connection == null) throw new Exception("Database connection failed");
            friendDAO = new FriendDAO(connection);
            userDAO = new UserDAO(connection);
        } catch (Exception e) {
            System.out.println("Error initializing DAOs: " + e.getMessage());
        }

        // Cấu hình ListView để hiển thị tên User (sử dụng cell factory)
        friendList.setCellFactory(param -> new ListCell<User>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getUsername() + " (ID: " + item.getId() + ")");
                }
            }
        });
    }

    @FXML
    private void searchFriend() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            friendList.setItems(FXCollections.observableArrayList()); // Xóa danh sách
            return;
        }
        try {
            List<User> users = userDAO.searchUsers(query);
            friendList.setItems(FXCollections.observableArrayList(users));
        } catch (SQLException e) {
            System.out.println("Error searching users: " + e.getMessage());
        }
    }

    @FXML
    private void addFriend() {
        User selected = friendList.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getId() != currentUserId) {
            try {
                Friend friendRequest = new Friend(currentUserId, selected.getId(), "pending");
                if (friendDAO.saveFriend(friendRequest)) {
                    System.out.println("Lời mời kết bạn đã gửi đến " + selected.getUsername());
                    // Cập nhật danh sách hoặc thông báo thành công
                    friendList.getItems().remove(selected);
                } else {
                    System.out.println("Không thể gửi lời mời (đã tồn tại)");
                }
            } catch (SQLException e) {
                System.out.println("Error sending friend request: " + e.getMessage());
            }
        }
    }

    @FXML
    private void backToMain() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/main-view.fxml"));
            Parent root = loader.load();
            MainController mainController = loader.getController();
            mainController.setStage(stage);
            mainController.setCurrentUserId(currentUserId);
            stage.setScene(new Scene(root, 800, 400));
            stage.setTitle("Chat Application - Main");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error loading main view: " + e.getMessage());
        }
    }
}
package org.example.zalu.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.example.zalu.dao.FriendDAO;
import org.example.zalu.model.DBConnection;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class FriendRequestController {
    @FXML
    private ListView<String> requestList;
    @FXML
    private Button acceptButton;
    @FXML
    private Button rejectButton;

    private Stage stage;
    private FriendDAO friendDAO;
    private int currentUserId = -1;
    private int selectedSenderId = -1;
    private boolean dataLoaded = false; // Flag để tránh load trùng lặp

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        System.out.println("FriendRequestController currentUserId set to: " + userId);
        if (!dataLoaded) {
            initData(); // Load dữ liệu ngay sau khi set userId, chỉ gọi một lần
        }
    }

    @FXML
    public void initialize() {
        System.out.println("FriendRequestController initialize for userId: " + currentUserId);
        // Khởi tạo DAO luôn, không phụ thuộc ID
        try {
            Connection connection = DBConnection.getConnection();
            if (connection == null) {
                throw new Exception("Database connection failed");
            }
            friendDAO = new FriendDAO(connection);
            System.out.println("friendDAO initialized");
        } catch (Exception e) {
            System.out.println("Error initializing FriendRequestController: " + e.getMessage());
        }

        // Load dữ liệu chỉ nếu userId đã set
        if (currentUserId != -1) {
            initData();
        } else {
            System.out.println("Warning: currentUserId is not set before initialize");
        }
    }

    private void initData() {
        if (currentUserId == -1) {
            System.out.println("Warning: currentUserId is not set before initData");
            return;
        }
        if (dataLoaded) return; // Tránh load trùng lặp
        try {
            loadPendingRequests();
            dataLoaded = true;
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Error loading pending requests: " + e.getMessage());
        }
    }

    private void loadPendingRequests() throws SQLException {
        if (friendDAO == null) {
            System.out.println("friendDAO is null in loadPendingRequests, skipping");
            return;
        }
        System.out.println("Loading pending requests for userId: " + currentUserId);
        List<Integer> senderIds = friendDAO.getPendingRequests(currentUserId);
        System.out.println("Found " + senderIds.size() + " pending requests: " + senderIds);
        requestList.setItems(FXCollections.observableArrayList());
        for (int senderId : senderIds) {
            requestList.getItems().add("User" + senderId + " wants to be friends");
        }
        if (requestList.getItems().isEmpty()) {
            requestList.getItems().add("No pending requests");
        }
    }

    @FXML
    private void onRequestSelected() {
        String selected = requestList.getSelectionModel().getSelectedItem();
        if (selected != null && selected.contains("wants to be friends")) {
            selectedSenderId = Integer.parseInt(selected.replace("User", "").replace(" wants to be friends", ""));
            System.out.println("Selected senderId: " + selectedSenderId);
            if (acceptButton != null) acceptButton.setDisable(false);
            if (rejectButton != null) rejectButton.setDisable(false);
        }
    }

    @FXML
    private void acceptRequest() {
        if (selectedSenderId != -1) {
            try {
                System.out.println("Attempting to accept request from senderId: " + selectedSenderId + " to currentUserId: " + currentUserId);
                if (friendDAO.acceptFriendRequest(currentUserId, selectedSenderId)) {
                    System.out.println("Accepted friend request from User" + selectedSenderId);
                    loadPendingRequests();
                } else {
                    System.out.println("Failed to accept request - no pending row found");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                System.out.println("Error accepting request: " + e.getMessage());
            }
        }
    }

    @FXML
    private void rejectRequest() {
        if (selectedSenderId != -1) {
            try {
                if (friendDAO.rejectFriendRequest(currentUserId, selectedSenderId)) {
                    System.out.println("Rejected friend request from User" + selectedSenderId);
                    loadPendingRequests();
                } else {
                    System.out.println("Failed to reject request");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                System.out.println("Error rejecting request: " + e.getMessage());
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
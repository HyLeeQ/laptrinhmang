package org.example.zalu.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;

public class FriendRequestController {
    @FXML
    private ListView<String> requestList;
    @FXML
    private Button acceptButton;
    @FXML
    private Button rejectButton;

    private Stage stage;
    private int currentUserId = -1;
    private int selectedSenderId = -1;
    private List<Integer> pendingRequests = null; // Lưu list senderIds từ MainController

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        System.out.println("FriendRequestController currentUserId set to: " + userId);
        // Không load data ở đây nữa - dùng setPendingRequests
    }

    // THÊM method này để nhận list từ MainController
    public void setPendingRequests(List<Integer> pendingRequests) {
        this.pendingRequests = pendingRequests;
        loadRequestList(); // Load UI ngay
        System.out.println("Pending requests set: " + pendingRequests.size());
    }

    @FXML
    public void initialize() {
        System.out.println("FriendRequestController initialize for userId: " + currentUserId);
        // Disable buttons ban đầu
        if (acceptButton != null) acceptButton.setDisable(true);
        if (rejectButton != null) rejectButton.setDisable(true);

        // Nếu pendingRequests đã set trước, load lại
        if (pendingRequests != null) {
            loadRequestList();
        } else {
            requestList.getItems().add("No pending requests (waiting for data...)");
        }
    }

    private void loadRequestList() {
        if (requestList == null || pendingRequests == null) return;
        requestList.setItems(FXCollections.observableArrayList());
        for (int senderId : pendingRequests) {
            requestList.getItems().add("User" + senderId + " wants to be friends");
        }
        if (pendingRequests.isEmpty()) {
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
                ObjectOutputStream out = ChatClient.getOut();
                if (out == null) {
                    System.out.println("Server not connected");
                    return;
                }
                String req = "ACCEPT_FRIEND|" + currentUserId + "|" + selectedSenderId;
                out.writeObject(req);
                out.flush();
                System.out.println("Sent ACCEPT_FRIEND request for senderId: " + selectedSenderId);

                // Sau khi gửi, reload list (response success sẽ đến listener ở Main, nhưng ở đây reload tạm)
                // Hoặc quay về main để reload toàn bộ
                backToMain(); // Quay về main để reload friends/requests
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Error sending accept request: " + e.getMessage());
            }
        }
    }

    @FXML
    private void rejectRequest() {
        if (selectedSenderId != -1) {
            try {
                ObjectOutputStream out = ChatClient.getOut();
                if (out == null) {
                    System.out.println("Server not connected");
                    return;
                }
                String req = "REJECT_FRIEND|" + currentUserId + "|" + selectedSenderId;
                out.writeObject(req);
                out.flush();
                System.out.println("Sent REJECT_FRIEND request for senderId: " + selectedSenderId);

                // Tương tự, quay về main để reload
                backToMain();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Error sending reject request: " + e.getMessage());
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
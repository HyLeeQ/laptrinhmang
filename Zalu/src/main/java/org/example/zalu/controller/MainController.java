package org.example.zalu.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.zalu.dao.FriendDAO;
import org.example.zalu.dao.MessageDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.dao.VoiceMessageDAO;
import org.example.zalu.model.DBConnection;
import org.example.zalu.model.Message;
import org.example.zalu.model.VoiceMessage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class MainController {
    @FXML
    private ListView<String> chatList;
    @FXML
    private TextField messageField;
    @FXML
    private TextArea chatArea;

    private Stage stage;
    private ObjectOutputStream out;
    private Thread listenThread;
    private Socket socket;
    private UserDAO userDAO;
    private FriendDAO friendDAO;
    private MessageDAO messageDAO;
    private VoiceMessageDAO voiceMessageDAO;
    private int currentUserId = -1;
    private int currentFriendId = -1;
    private volatile boolean isRunning = true;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        System.out.println("Current userId set to: " + userId);
        initData(); // Load dữ liệu ngay sau khi set userId
    }

    @FXML
    public void initialize() {
        System.out.println("MainController initialized for userId: " + currentUserId);
        // Chỉ khởi tạo cơ bản, không load dữ liệu ở đây
        try {
            Connection connection = DBConnection.getConnection();
            if (connection == null) {
                throw new SQLException("Failed to establish database connection");
            }
            userDAO = new UserDAO(connection);
            friendDAO = new FriendDAO(connection);
            messageDAO = new MessageDAO(connection);
            voiceMessageDAO = new VoiceMessageDAO(connection);
            chatArea.appendText("Database connection successful\n");
        } catch (SQLException e) {
            e.printStackTrace();
            chatArea.appendText("Database error: " + e.getMessage() + "\n");
        }

        try {
            socket = new Socket("localhost", 5000);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject("LOGIN:" + currentUserId);
            out.flush();
            startListening();
            chatArea.appendText("Connected to chat server\n");
        } catch (IOException e) {
            e.printStackTrace();
            chatArea.appendText("Server connection error: " + e.getMessage() + "\n");
        }

        // Nếu userId chưa set, in cảnh báo
        if (currentUserId == -1) {
            chatArea.appendText("Warning: User ID not set. Data will be loaded after login.\n");
        }
    }

    private void initData() {
        if (currentUserId == -1) {
            System.out.println("Warning: currentUserId is not set before initData");
            chatArea.appendText("Error: User ID not set. Please login again.\n");
            return;
        }

        try {
            loadFriends();
            if (!chatList.getItems().isEmpty()) {
                currentFriendId = Integer.parseInt(chatList.getItems().get(0).replace("User", ""));
                loadMessagesAndVoices();
            }
            userDAO.updateStatus(currentUserId, "online");
            chatArea.appendText("Loaded data successfully\n");
        } catch (SQLException e) {
            e.printStackTrace();
            chatArea.appendText("Error loading data: " + e.getMessage() + "\n");
        }
    }

    private void loadFriends() throws SQLException {
        if (friendDAO == null) throw new SQLException("FriendDAO not initialized");
        List<Integer> friendIds = friendDAO.getFriendsByUserId(currentUserId);
        chatList.getItems().clear();
        for (int friendId : friendIds) {
            chatList.getItems().add("User" + friendId);
        }
    }

    private void loadMessagesAndVoices() throws SQLException {
        if (currentFriendId == -1) return;
        if (messageDAO == null || voiceMessageDAO == null) throw new SQLException("DAO not initialized");
        chatArea.clear();
        List<Message> messages = messageDAO.getMessagesByUserAndFriend(currentUserId, currentFriendId);
        for (Message message : messages) {
            String displayText = String.format("[%s] %s -> %s: %s %s",
                    message.getCreatedAt(),
                    message.getSenderId() == currentUserId ? "You" : "User" + message.getSenderId(),
                    message.getReceiverId() == currentUserId ? "You" : "User" + message.getReceiverId(),
                    message.getContent(),
                    message.getIsRead() ? "(Read)" : "(Unread)");
            chatArea.appendText(displayText + "\n");
        }
        List<VoiceMessage> voiceMessages = voiceMessageDAO.getVoiceMessagesByUserAndFriend(currentUserId, currentFriendId);
        for (VoiceMessage voice : voiceMessages) {
            String displayText = String.format("[%s] %s sent voice: %s %s",
                    voice.getCreatedAt(),
                    voice.getSenderId() == currentUserId ? "You" : "User" + voice.getSenderId(),
                    voice.getFilePath(),
                    voice.getIsRead() ? "(Read)" : "(Unread)");
            chatArea.appendText(displayText + "\n");
        }
    }

    private void startListening() {
        listenThread = new Thread(() -> {
            try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                while (isRunning) {
                    String message = (String) in.readObject();
                    if (message != null) {
                        javafx.application.Platform.runLater(() -> {
                            chatArea.appendText(message + "\n");
                            try {
                                loadMessagesAndVoices();
                            } catch (SQLException e) {
                                e.printStackTrace();
                                chatArea.appendText("Error loading messages: " + e.getMessage() + "\n");
                            }
                        });
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                if (isRunning) {
                    e.printStackTrace();
                    System.out.println("Error receiving message: " + e.getMessage());
                }
            }
        });
        listenThread.setDaemon(true);
        listenThread.start();
    }

    @FXML
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && currentFriendId != -1) {
            try {
                Message newMessage = new Message(0, currentUserId, currentFriendId, message, false, LocalDateTime.now());
                if (messageDAO.saveMessage(newMessage)) {
                    String fullMessage = currentUserId + ":" + currentFriendId + ":" + message;
                    out.writeObject(fullMessage);
                    out.flush();
                    messageField.clear();
                    loadMessagesAndVoices();
                } else {
                    chatArea.appendText("Failed to send message\n");
                }
            } catch (SQLException | IOException e) {
                e.printStackTrace();
                chatArea.appendText("Error sending message: " + e.getMessage() + "\n");
            }
        }
    }

    @FXML
    private void logout() {
        System.out.println("Đăng xuất");
        isRunning = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (userDAO != null) {
                try {
                    userDAO.updateStatus(currentUserId, "offline");
                    chatArea.appendText("Logged out successfully\n");
                } catch (SQLException e) {
                    e.printStackTrace();
                    chatArea.appendText("Database error during logout: " + e.getMessage() + "\n");
                }
            }
            switchScene("/org/example/zalu/views/login-view.fxml");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error logging out: " + e.getMessage());
        }
    }

    private void switchScene(String fxmlPath) throws IOException {
        if (stage == null) throw new IllegalStateException("Stage is not initialized");
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = loader.load();
        Scene scene = new Scene(root, 800, 400);
        stage.setScene(scene);
        stage.setTitle("Chat Application - " + fxmlPath.replace("/views/", "").replace(".fxml", ""));
        stage.show();
    }

    @FXML
    private void onFriendSelected() {
        String selected = chatList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            currentFriendId = Integer.parseInt(selected.replace("User", ""));
            try {
                loadMessagesAndVoices();
            } catch (SQLException e) {
                e.printStackTrace();
                chatArea.appendText("Error loading messages: " + e.getMessage() + "\n");
            }
        }
    }

    @FXML
    private void sendVoiceMessage() {
        if (currentFriendId != -1) {
            try {
                String filePath = "/path/to/sample_voice.mp3"; // Thay bằng logic chọn file
                VoiceMessage voiceMessage = new VoiceMessage(0, currentUserId, currentFriendId, filePath, LocalDateTime.now(), false);
                if (voiceMessageDAO.saveVoiceMessage(voiceMessage)) {
                    String fullMessage = currentUserId + ":" + currentFriendId + ":VOICE:" + filePath;
                    out.writeObject(fullMessage);
                    out.flush();
                    loadMessagesAndVoices();
                } else {
                    chatArea.appendText("Failed to send voice message\n");
                }
            } catch (SQLException | IOException e) {
                e.printStackTrace();
                chatArea.appendText("Error sending voice message: " + e.getMessage() + "\n");
            }
        }
    }

    @FXML
    private void addFriend() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/add-friend-view.fxml"));
            Parent root = loader.load();
            AddFriendController addFriendController = loader.getController();
            addFriendController.setStage(stage);
            addFriendController.setCurrentUserId(currentUserId);
            stage.setScene(new Scene(root, 400, 300));
            stage.setTitle("Chat Application - Add Friend");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error loading add friend view: " + e.getMessage());
        }
    }

    @FXML
    private void viewFriendRequests() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/friend-request-view.fxml"));
            Parent root = loader.load();
            FriendRequestController requestController = loader.getController();
            if (requestController == null) {
                throw new IOException("FriendRequestController not loaded from FXML");
            }
            requestController.setStage(stage);
            requestController.setCurrentUserId(currentUserId); // Gọi ngay sau load, trước set scene
            stage.setScene(new Scene(root, 400, 300));
            stage.setTitle("Chat Application - Friend Requests");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error loading friend request view: " + e.getMessage());
        }
    }
}
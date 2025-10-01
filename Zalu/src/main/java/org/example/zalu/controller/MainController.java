package org.example.zalu.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.model.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MainController {
    @FXML
    private ListView<String> chatList;
    @FXML
    private TextField messageField;
    @FXML
    private TextArea chatArea;
    @FXML
    private TextField friendIdField; // THÊM: TextField để nhập ID bạn bè

    private Stage stage;
    private int currentUserId = -1;
    private int currentFriendId = -1;
    private volatile boolean isRunning = true;
    private Thread listenThread;
    private boolean listenerStarted = false;
    private List<Integer> pendingRequests = new ArrayList<>();
    private boolean waitingForRequests = false;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        System.out.println("Current userId set to: " + userId);
        initData(); // Load dữ liệu ngay sau khi set userId (sync cho nhanh)
        // startListening() được gọi SAU khi load initial xong, tránh concurrent read
    }

    @FXML
    public void initialize() {
        System.out.println("MainController initialized for userId: " + currentUserId);
        chatArea.appendText("Connected! Loading data...\n"); // Update message ngay, không "Waiting for login"
    }

    // SỬA: initData() dùng Task sync read response (nhanh, không freeze UI)
    private void initData() {
        if (currentUserId == -1) return;

        // Update UI ngay khi bắt đầu load
        Platform.runLater(() -> chatArea.appendText("Loading friends...\n"));

        Task<Object> loadTask = new Task<Object>() {
            @Override
            protected Object call() throws Exception {
                ObjectOutputStream out = ChatClient.getOut();
                if (out == null) throw new IOException("Server not connected");

                // BƯỚC 1: Load friends sync
                String reqFriends = "GET_FRIENDS|" + currentUserId;
                out.writeObject(reqFriends);
                out.flush();
                System.out.println("Sent GET_FRIENDS for user " + currentUserId);

                ObjectInputStream in = ChatClient.getIn();
                if (in == null) throw new IOException("Cannot read from server");
                Object objFriends = in.readObject();
                if (!(objFriends instanceof List && ((List<?>) objFriends).get(0) instanceof Integer)) {
                    throw new IOException("Invalid friends response: " + objFriends.getClass().getSimpleName());
                }
                @SuppressWarnings("unchecked")
                final List<Integer> friendIds = (List<Integer>) objFriends;

                // BƯỚC 2: Load messages cho friend đầu tiên (nếu có) sync
                List<Message> messages = new ArrayList<>();
                if (!friendIds.isEmpty()) {
                    currentFriendId = friendIds.get(0); // Set ngay trong task
                    String reqMessages = "GET_MESSAGES|" + currentUserId + "|" + currentFriendId;
                    out.writeObject(reqMessages);
                    out.flush();
                    System.out.println("Sent GET_MESSAGES for friend " + currentFriendId);

                    Object objMessages = in.readObject();
                    if (!(objMessages instanceof List && ((List<?>) objMessages).get(0) instanceof Message)) {
                        throw new IOException("Invalid messages response: " + objMessages.getClass().getSimpleName());
                    }
                }

                // Trả về pair (friends, messages) để onSucceeded dùng
                return new Object[]{friendIds, messages};
            }
        };

        loadTask.setOnSucceeded(event -> {
            @SuppressWarnings("unchecked")
            Object[] result = (Object[]) loadTask.getValue();
            List<Integer> friendIds = (List<Integer>) result[0];
            List<Message> messages = (List<Message>) result[1];

            // Update UI với friends và messages
            Platform.runLater(() -> {
                chatList.getItems().clear();
                for (int id : friendIds) {
                    chatList.getItems().add("User" + id);
                }
                chatArea.appendText("Loaded " + friendIds.size() + " friends!\n");

                // Update messages ngay
                chatArea.clear();
                for (Message msg : messages) {
                    String display = String.format("[%s] %s -> %s: %s %s",
                            msg.getCreatedAt(),
                            msg.getSenderId() == currentUserId ? "You" : "User" + msg.getSenderId(),
                            msg.getReceiverId() == currentUserId ? "You" : "User" + msg.getReceiverId(),
                            msg.getContent(),
                            msg.getIsRead() ? "(Read)" : "(Unread)");
                    chatArea.appendText(display + "\n");
                }
                chatArea.appendText("Messages loaded!\n");
            });
            System.out.println("Initial load complete");

            // Start listener SAU khi sync load xong hoàn toàn, tránh concurrent read
            startListening();
        });

        loadTask.setOnFailed(event -> {
            Throwable ex = loadTask.getException();
            ex.printStackTrace();
            Platform.runLater(() -> chatArea.appendText("Error loading data: " + ex.getMessage() + "\n"));
            // Fallback: Start listener dù lỗi
            startListening();
        });

        // Chạy task ở background thread
        new Thread(loadTask).start();
    }

    private void loadMessagesForFriend() {
        // Không làm gì - listener sẽ handle response GET_MESSAGES
        System.out.println("Requesting messages for friend " + currentFriendId + " (async via listener)");
    }

    private void startListening() {
        if (listenerStarted || currentUserId == -1) return;
        listenerStarted = true;

        listenThread = new Thread(() -> {
            ObjectInputStream in = ChatClient.getIn();
            if (in == null) return;
            try {
                while (isRunning) {
                    Object obj = in.readObject();
                    if (obj != null) {
                        Platform.runLater(() -> handleResponse(obj));
                    }
                }
            } catch (Exception e) {
                if (isRunning) System.out.println("Error receiving: " + e.getMessage());
            } finally {
                listenerStarted = false;
            }
        });
        listenThread.setDaemon(true);
        listenThread.start();
        System.out.println("Listener started for user " + currentUserId);
    }

    private void handleResponse(Object obj) {
        try {
            if (obj instanceof List && !((List<?>) obj).isEmpty() && ((List<?>) obj).get(0) instanceof Integer) {
                @SuppressWarnings("unchecked")
                List<Integer> ids = (List<Integer>) obj;
                if (waitingForRequests) {
                    pendingRequests.clear();
                    pendingRequests.addAll(ids);
                    chatArea.appendText("Loaded " + ids.size() + " pending requests\n");
                    waitingForRequests = false;
                } else {
                    chatList.getItems().clear();
                    for (int id : ids) {
                        chatList.getItems().add("User" + id);
                    }
                    if (!ids.isEmpty()) {
                        currentFriendId = ids.get(0);
                        loadMessagesForFriend();
                    }
                    chatArea.appendText("Loaded " + ids.size() + " friends\n");
                }
            } else if (obj instanceof List && !((List<?>) obj).isEmpty() && ((List<?>) obj).get(0) instanceof Message) {
                @SuppressWarnings("unchecked")
                List<Message> messages = (List<Message>) obj;
                chatArea.clear();
                for (Message msg : messages) {
                    String display = String.format("[%s] %s -> %s: %s %s",
                            msg.getCreatedAt(),
                            msg.getSenderId() == currentUserId ? "You" : "User" + msg.getSenderId(),
                            msg.getReceiverId() == currentUserId ? "You" : "User" + msg.getReceiverId(),
                            msg.getContent(),
                            msg.getIsRead() ? "(Read)" : "(Unread)");
                    chatArea.appendText(display + "\n");
                }
            } else if (obj instanceof Boolean) {
                boolean success = (Boolean) obj;
                chatArea.appendText(success ? "Action successful\n" : "Action failed\n");
            } else if (obj instanceof String) {
                chatArea.appendText((String) obj + "\n");
                loadMessagesForFriend();
            } else {
                chatArea.appendText("Unknown response: " + obj.getClass().getSimpleName() + "\n");
            }
        } catch (Exception e) {
            chatArea.appendText("Error handling response: " + e.getMessage() + "\n");
        }
    }

    @FXML
    private void sendMessage() {
        String content = messageField.getText().trim();
        if (!content.isEmpty() && currentFriendId != -1) {
            try {
                ObjectOutputStream out = ChatClient.getOut();
                if (out == null) return;
                String req = "SEND_MESSAGE|" + currentUserId + "|" + currentFriendId + "|" + content;
                out.writeObject(req);
                out.flush();
                messageField.clear();
            } catch (IOException e) {
                e.printStackTrace();
                chatArea.appendText("Error sending: " + e.getMessage() + "\n");
            }
        }
    }

    @FXML
    private void onFriendSelected() {
        String selected = chatList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            currentFriendId = Integer.parseInt(selected.replace("User", ""));
            try {
                ObjectOutputStream out = ChatClient.getOut();
                if (out == null) return;
                String req = "GET_MESSAGES|" + currentUserId + "|" + currentFriendId;
                out.writeObject(req);
                out.flush();
                System.out.println("Sent GET_MESSAGES for friend " + currentFriendId);
            } catch (IOException e) {
                e.printStackTrace();
                chatArea.appendText("Error loading messages: " + e.getMessage() + "\n");
            }
        }
    }

    @FXML
    private void addFriend() {
        String input = friendIdField.getText().trim(); // LẤY INPUT TỪ TEXTFIELD
        if (input.isEmpty()) {
            chatArea.appendText("Please enter friend ID\n");
            return;
        }
        try {
            int targetId = Integer.parseInt(input); // Parse input thành int
            ObjectOutputStream out = ChatClient.getOut();
            if (out == null) return;
            String req = "SEND_FRIEND_REQUEST|" + currentUserId + "|" + targetId;
            out.writeObject(req);
            out.flush();
            chatArea.appendText("Friend request sent to User" + targetId + "\n");
            friendIdField.clear(); // Clear field sau khi gửi
        } catch (NumberFormatException e) {
            chatArea.appendText("Invalid ID format\n");
        } catch (IOException e) {
            e.printStackTrace();
            chatArea.appendText("Error sending friend request: " + e.getMessage() + "\n");
        }
    }

    @FXML
    private void viewFriendRequests() {
        if (currentUserId == -1) {
            chatArea.appendText("Please login first\n");
            return;
        }

        waitingForRequests = true;

        try {
            ObjectOutputStream out = ChatClient.getOut();
            if (out == null) {
                chatArea.appendText("Server not connected\n");
                return;
            }
            String req = "GET_PENDING_REQUESTS|" + currentUserId;
            out.writeObject(req);
            out.flush();
            System.out.println("Sent GET_PENDING_REQUESTS for user " + currentUserId);

            Platform.runLater(() -> {
                try {
                    Thread.sleep(200);
                    try{
                        switchToFriendRequestsView();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (InterruptedException e) {

                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            chatArea.appendText("Error requesting pending requests: " + e.getMessage() + "\n");
            waitingForRequests = false;
        }
    }

    private void switchToFriendRequestsView() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/friend-request-view.fxml"));
        Parent root = loader.load();
        FriendRequestController requestController = loader.getController();
        if (requestController != null) {
            requestController.setStage(stage);
            requestController.setCurrentUserId(currentUserId);
            requestController.setPendingRequests(pendingRequests);
        }
        stage.setScene(new Scene(root, 400, 300));
        stage.setTitle("Chat Application - Friend Requests");
        stage.show();
    }

    @FXML
    private void logout() {
        System.out.println("Đăng xuất");
        isRunning = false;
        listenerStarted = false;
        try {
            ChatClient.disconnect();
            chatArea.appendText("Logged out successfully\n");
            switchScene("/org/example/zalu/views/login-view.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            chatArea.appendText("Error during logout: " + e.getMessage() + "\n");
            Platform.exit();
        }
    }

    private void switchScene(String fxmlPath) throws IOException {
        if (stage == null) throw new IllegalStateException("Stage is not initialized");

        java.net.URL fxmlUrl = getClass().getResource(fxmlPath);
        if (fxmlUrl == null) {
            throw new IOException("FXML file not found: " + fxmlPath);
        }
        System.out.println("Found FXML at: " + fxmlUrl);

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        Object controller = loader.getController();
        if (controller instanceof LoginController) {
            ((LoginController) controller).setStage(stage);
        }

        Scene scene = new Scene(root, 800, 400);
        stage.setScene(scene);
        stage.setTitle("Chat Application - Login");
        stage.show();
    }

    @FXML
    private void sendVoiceMessage() {
        if (currentFriendId != -1) {
            try {
                String filePath = "/path/to/sample_voice.mp3"; // Thay bằng logic chọn file
                ObjectOutputStream out = ChatClient.getOut();
                if (out == null) {
                    chatArea.appendText("Server not connected\n");
                    return;
                }
                String req = "SEND_VOICE_MESSAGE|" + currentUserId + "|" + currentFriendId + "|" + filePath;
                out.writeObject(req);
                out.flush();
                chatArea.appendText("Voice message sent: " + filePath + "\n");
                loadMessagesForFriend();
            } catch (IOException e) {
                e.printStackTrace();
                chatArea.appendText("Error sending voice message: " + e.getMessage() + "\n");
            }
        }
    }
}
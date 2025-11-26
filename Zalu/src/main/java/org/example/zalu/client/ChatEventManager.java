package org.example.zalu.client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.example.zalu.ZaluApplication;
import org.example.zalu.controller.MainController;
import org.example.zalu.dao.FriendDAO;
import org.example.zalu.dao.MessageDAO;
import org.example.zalu.model.Message;
import org.example.zalu.util.database.DBConnection;
import org.example.zalu.util.AppConstants;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

public class ChatEventManager {
    private static ChatEventManager instance = null;
    private static final BlockingDeque<Object> eventQueue = new LinkedBlockingDeque<>();
    private static volatile boolean listening = false;

    private static Consumer<List<Integer>> friendCallback = null;
    private static Consumer<List<Message>> messageCallback = null;
    private static Consumer<String> broadcastCallback = null;
    private static Consumer<String> errorCallback = null;
    private static Consumer<List<Integer>> pendingRequestsCallback = null;
    private static Consumer<List<org.example.zalu.model.GroupInfo>> groupsCallback = null;
    private static Consumer<List<Integer>> onlineUsersCallback = null;
    private static Consumer<FileDownloadInfo> fileDownloadCallback = null;

    private static final ObservableList<Integer> sharedFriends = FXCollections.observableArrayList();
    private static final ObservableList<Message> sharedMessages = FXCollections.observableArrayList();
    private static int currentUserId = -1;
    // Lưu metadata file tạm thời để gắn vào file data
    private static Message pendingFileMessage = null;
    // Lưu thông tin file download đang chờ
    private static FileDownloadInfo pendingFileDownload = null;
    
    // Class để lưu thông tin file download
    public static class FileDownloadInfo {
        private int messageId;
        private String fileName;
        private long fileSize;
        private byte[] fileData;
        
        public FileDownloadInfo(int messageId, String fileName, long fileSize) {
            this.messageId = messageId;
            this.fileName = fileName;
            this.fileSize = fileSize;
        }
        
        public FileDownloadInfo(int messageId, String fileName, long fileSize, byte[] fileData) {
            this.messageId = messageId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileData = fileData;
        }
        
        public int getMessageId() { return messageId; }
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public byte[] getFileData() { return fileData; }
    }
    // Counter để phân biệt các loại List<Integer> sau khi login
    private static int integerListCounter = 0;
    private static boolean hasReceivedGroups = false;

    public static void setCurrentUserId(int id) {
        currentUserId = id;
        System.out.println("ChatEventManager: Current userId set to " + id);
    }

    private ChatEventManager() {
    }

    public static ChatEventManager getInstance() {
        if (instance == null) {
            instance = new ChatEventManager();
        }
        return instance;
    }

    public void startListening(ObjectInputStream in) {
        if (listening) return;
        listening = true;
        new Thread(() -> {
            System.out.println("Listener started for user");
            while (listening) {
                try {
                    Object obj = in.readObject();
                    if (obj != null) {
                        eventQueue.offer(obj);
                        processEvent(obj);  // Gọi processEvent (giữ nguyên)
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Listener timeout - continuing to listen...");
                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Error receiving in listener: " + e.getMessage());
                    if (e instanceof java.io.StreamCorruptedException) {
                        System.out.println("Stream corrupted - attempting reconnect...");
                        ChatClient.reconnect();
                        break;
                    }
                    break;
                }
            }
            listening = false;
        }, "ChatListener").start();
    }

    public void stopListening() {
        listening = false;
        eventQueue.clear();
    }

    // SỬA: Thay private thành public để ChatClient có thể gọi
    public void processEvent(Object obj) {
        if (obj == null) {
            System.out.println("Received null object - ignoring");
            return;  // SỬA: Check null
        }
        System.out.println("Processing event: " + obj + " (type: " + (obj != null ? obj.getClass().getSimpleName() : "null") + ")");

        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (list.isEmpty()) {
                System.out.println("Empty List received - ignoring (e.g., no friends)");
                return;
            }
        }

        if (obj instanceof Boolean) {
            boolean success = (Boolean) obj;
            System.out.println("Boolean event (success?): " + success);
            if (errorCallback != null) {
                Platform.runLater(() -> errorCallback.accept(success ? "SUCCESS|Operation OK" : "FAIL|Operation failed"));
            }
            return;
        }

        if (obj instanceof List && !((List<?>) obj).isEmpty()) {
            List<?> list = (List<?>) obj;
            if (list.get(0) instanceof Integer) {
                List<Integer> intList = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Integer) intList.add((Integer) o);
                }
                integerListCounter++;
                System.out.println("Received List<Integer> #" + integerListCounter + ": " + intList + " (friends, pending, or online users?)");
                
                // Phân biệt dựa trên thứ tự và flag:
                // 1. List đầu tiên sau login = friends
                // 2. List thứ hai (nếu có) = pending requests
                // 3. List sau khi đã nhận groups = online users
                if (hasReceivedGroups && onlineUsersCallback != null) {
                    // Đã nhận groups, nên đây là online users list
                    System.out.println("Treating as online users list");
                    Platform.runLater(() -> onlineUsersCallback.accept(intList));
                } else if (integerListCounter == 1 && friendCallback != null) {
                    // List đầu tiên = friends
                    System.out.println("Treating as friends list");
                    Platform.runLater(() -> friendCallback.accept(intList));
                } else if (integerListCounter == 2 && pendingRequestsCallback != null) {
                    // List thứ hai = pending requests
                    System.out.println("Treating as pending requests list");
                    Platform.runLater(() -> pendingRequestsCallback.accept(intList));
                } else {
                    // Fallback: gọi tất cả callbacks (để tương thích ngược)
                    if (friendCallback != null) {
                        Platform.runLater(() -> friendCallback.accept(intList));
                    }
                    if (pendingRequestsCallback != null) {
                        Platform.runLater(() -> pendingRequestsCallback.accept(intList));
                    }
                    if (onlineUsersCallback != null) {
                        Platform.runLater(() -> onlineUsersCallback.accept(intList));
                    }
                }
                return;
            } else if (list.get(0) instanceof Message) {
                List<Message> messages = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Message) messages.add((Message) o);
                }
                System.out.println("Received List<Message>: " + messages.size() + " messages");
                if (messageCallback != null) {
                    Platform.runLater(() -> messageCallback.accept(messages));
                }
                return;
            } else if (list.get(0) instanceof org.example.zalu.model.GroupInfo) {
                List<org.example.zalu.model.GroupInfo> groups = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof org.example.zalu.model.GroupInfo) groups.add((org.example.zalu.model.GroupInfo) o);
                }
                System.out.println("Received List<GroupInfo>: " + groups.size() + " groups");
                hasReceivedGroups = true; // Đánh dấu đã nhận groups
                if (groupsCallback != null) {
                    Platform.runLater(() -> groupsCallback.accept(groups));
                }
                return;
            }
        }
        if (obj instanceof String) {
            String message = (String) obj;
            System.out.println("Received string message: " + message);

            // ===== 1. XỬ LÝ ĐĂNG NHẬP THÀNH CÔNG (CHỈ CẦN BẤM 1 LẦN) =====
            if (message.startsWith("LOGIN_RESPONSE|SUCCESS|")) {
                String[] parts = message.split("\\|");
                if (parts.length < 3) {
                    // Gọi callback onFail nếu format không đúng
                    ChatClient.triggerLoginCallback(false, -1, "Lỗi định dạng response từ server");
                    return;
                }

                int userId = Integer.parseInt(parts[2]);
                ChatClient.userId = userId;
                setCurrentUserId(userId);
                // Reset counter khi login thành công
                integerListCounter = 0;
                hasReceivedGroups = false;

                // Gọi callback onSuccess TRƯỚC khi chuyển màn hình
                // LoginController sẽ tự xử lý việc chuyển màn hình
                ChatClient.triggerLoginCallback(true, userId, null);

                // Giữ lại logic tự động chuyển màn hình cho tương thích ngược
                // (nếu có code khác dựa vào behavior này)
                Platform.runLater(() -> {
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/main/main-view.fxml"));
                        Parent root = loader.load();
                        MainController controller = loader.getController();

                        Stage stage = LoginSession.getStage();  // Lấy stage đã lưu
                        String username = LoginSession.getUsername(); // Lấy username đã lưu

                        if (stage != null && username != null) {
                            controller.setStage(stage);
                            controller.setCurrentUserId(userId);
                            controller.setWelcomeUsername(username);

                            Scene scene = new Scene(root, AppConstants.MAIN_WIDTH, AppConstants.MAIN_HEIGHT);
                            stage.setScene(scene);
                            stage.setTitle("Zalu - " + username);
                            stage.setWidth(AppConstants.MAIN_WIDTH);
                            stage.setHeight(AppConstants.MAIN_HEIGHT);
                            stage.setResizable(true);
                            stage.setMinWidth(800);
                            stage.setMinHeight(600);
                            stage.centerOnScreen();
                            stage.show();

                            System.out.println("✓ Đăng nhập thành công! UserID = " + userId);

                            // Dọn dẹp
                            LoginSession.clear();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Không hiển thị alert ở đây vì LoginController đã xử lý
                    }
                });
                return;
            }

            // ===== 2bis. XỬ LÝ KẾT QUẢ ĐĂNG KÝ =====
            if (message.startsWith("REGISTER_RESPONSE|")) {
                if (errorCallback != null) {
                    Platform.runLater(() -> errorCallback.accept(message));
                }
                return;
            }

            // ===== 2. XỬ LÝ ĐĂNG NHẬP THẤT BẠI =====
            if (message.startsWith("LOGIN_RESPONSE|FAIL|") || message.startsWith("LOGIN_RESPONSE|ERROR|")) {
                String error = message.contains("|") ? message.substring(message.indexOf("|", 20) + 1) : "Lỗi không xác định";
                // Gọi callback onFail
                ChatClient.triggerLoginCallback(false, -1, error);
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.ERROR, "Đăng nhập thất bại: " + error, ButtonType.OK).show();
                });
                return;
            }

            if (message.startsWith("RESUME_SESSION|")) {
                if (message.contains("|OK")) {
                    System.out.println("✓ Khôi phục session thành công, yêu cầu tải lại nhóm");
                    if (currentUserId > 0) {
                        ChatClient.sendRequest("GET_GROUPS|" + currentUserId);
                    }
                } else {
                    System.out.println("✗ Khôi phục session thất bại: " + message);
                }
                return;
            }

            // ===== 3. CÁC XỬ LÝ CŨ GIỮ NGUYÊN (chỉ dời xuống dưới) =====
            if (message.startsWith("NEW_MESSAGE|")) {
                String[] parts = message.split("\\|");
                if (parts.length >= 4) {
                    int senderId = Integer.parseInt(parts[1]);
                    int receiverId = Integer.parseInt(parts[2]);
                    String content = parts[3];
                    Message newMsg = new Message(0, senderId, receiverId, content, false, LocalDateTime.now());
                    // Check if this is a reply
                    if (parts.length >= 7 && "REPLY_TO".equals(parts[4])) {
                        try {
                            int repliedToMessageId = Integer.parseInt(parts[5]);
                            String repliedToContent = parts[6];
                            newMsg.setRepliedToMessageId(repliedToMessageId);
                            newMsg.setRepliedToContent(repliedToContent);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid repliedToMessageId in NEW_MESSAGE: " + e.getMessage());
                        }
                    }
                    if (messageCallback != null) {
                        List<Message> singleMsg = new ArrayList<>();
                        singleMsg.add(newMsg);
                        Platform.runLater(() -> messageCallback.accept(singleMsg));
                    }
                }
            }
            else if (message.startsWith("NEW_FILE|")) {
                // Format: NEW_FILE|senderId|receiverId|fileName|fileSize
                String[] parts = message.split("\\|");
                if (parts.length >= 5) {
                    int senderId = Integer.parseInt(parts[1]);
                    int receiverId = Integer.parseInt(parts[2]);
                    String fileName = parts[3];
                    long fileSize = Long.parseLong(parts[4]);
                    Message newFileMsg = new Message(0, senderId, receiverId, null, true, LocalDateTime.now());
                    newFileMsg.setFileName(fileName);
                    // Lưu tạm để gắn file data sau
                    pendingFileMessage = newFileMsg;
                    // Chờ nhận file data (byte[]) tiếp theo
                }
            }
            else if (message.startsWith("NEW_GROUP_MESSAGE|")) {
                String[] parts = message.split("\\|");
                if (parts.length >= 4) {
                    int groupId = Integer.parseInt(parts[1]);
                    int senderId = Integer.parseInt(parts[2]);
                    String content = parts[3];
                    Message newMsg = new Message(0, senderId, 0, content, false, LocalDateTime.now(), groupId);
                    // Check if this is a reply
                    if (parts.length >= 7 && "REPLY_TO".equals(parts[4])) {
                        try {
                            int repliedToMessageId = Integer.parseInt(parts[5]);
                            String repliedToContent = parts[6];
                            newMsg.setRepliedToMessageId(repliedToMessageId);
                            newMsg.setRepliedToContent(repliedToContent);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid repliedToMessageId in NEW_GROUP_MESSAGE: " + e.getMessage());
                        }
                    }
                    if (messageCallback != null) {
                        List<Message> singleMsg = new ArrayList<>();
                        singleMsg.add(newMsg);
                        Platform.runLater(() -> messageCallback.accept(singleMsg));
                    }
                }
            }
            else if (message.startsWith("NEW_GROUP_FILE|")) {
                // Format: NEW_GROUP_FILE|groupId|senderId|fileName|fileSize
                String[] parts = message.split("\\|");
                if (parts.length >= 5) {
                    int groupId = Integer.parseInt(parts[1]);
                    int senderId = Integer.parseInt(parts[2]);
                    String fileName = parts[3];
                    long fileSize = Long.parseLong(parts[4]);
                    Message newFileMsg = new Message(0, senderId, 0, null, true, LocalDateTime.now(), groupId);
                    newFileMsg.setFileName(fileName);
                    // Lưu tạm để gắn file data sau
                    pendingFileMessage = newFileMsg;
                    // Chờ nhận file data (byte[]) tiếp theo
                }
            }
            else if (message.equals("GROUPS_UPDATE")) {
                // Yêu cầu client reload danh sách nhóm
                if (currentUserId > 0) {
                    ChatClient.sendRequest("GET_GROUPS|" + currentUserId);
                }
            }
            else if (message.startsWith("ERROR|") || message.startsWith("FAIL|")) {
                if (errorCallback != null) {
                    Platform.runLater(() -> errorCallback.accept(message));
                }
            }
            else if (message.startsWith("MESSAGE_SENT|") || message.startsWith("FILE_SENT|") || 
                     message.startsWith("GROUP_MESSAGE_SENT|") || message.startsWith("GROUP_FILE_SENT|")) {
                if (errorCallback != null) {
                    Platform.runLater(() -> errorCallback.accept(message));
                }
            }
            else if (message.startsWith("FILE_DATA|")) {
                // Format: FILE_DATA|messageId|fileName|fileSize hoặc FILE_DATA|FAIL|error
                String[] parts = message.split("\\|");
                if (parts.length >= 2 && !parts[1].equals("FAIL")) {
                    // Success: lưu metadata và chờ file data
                    try {
                        int messageId = Integer.parseInt(parts[1]);
                        String fileName = parts.length >= 3 ? parts[2] : "file";
                        long fileSize = parts.length >= 4 ? Long.parseLong(parts[3]) : 0;
                        pendingFileDownload = new FileDownloadInfo(messageId, fileName, fileSize);
                        System.out.println("FILE_DATA metadata received: messageId=" + messageId + ", fileName=" + fileName + ", size=" + fileSize);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid FILE_DATA format: " + message);
                        pendingFileDownload = null;
                    }
                } else {
                    // Error
                    String error = parts.length >= 3 ? parts[2] : "Unknown error";
                    System.err.println("FILE_DATA error: " + error);
                    if (fileDownloadCallback != null) {
                        Platform.runLater(() -> fileDownloadCallback.accept(null));
                    }
                    pendingFileDownload = null;
                }
            }
            else if (message.startsWith("MESSAGES_READ|")) {
                // Format: MESSAGES_READ|receiverId
                // Người nhận (receiverId) đã đọc tin nhắn của người gửi (currentUserId)
                String[] parts = message.split("\\|");
                if (parts.length >= 2) {
                    int readerId = Integer.parseInt(parts[1]);
                    System.out.println("ChatEventManager: Tin nhắn đã được đọc bởi user " + readerId);
                    // Gọi callback để cập nhật UI
                    if (broadcastCallback != null) {
                        Platform.runLater(() -> broadcastCallback.accept(message));
                    }
                }
                return;
            }
            else if (message.startsWith("BROADCAST|")) {
                if (broadcastCallback != null) {
                    Platform.runLater(() -> broadcastCallback.accept(message.substring(10)));
                }
            }
            else if (message.startsWith("USER_ONLINE|")) {
                // Format: USER_ONLINE|userId
                String[] parts = message.split("\\|");
                if (parts.length >= 2) {
                    try {
                        int onlineUserId = Integer.parseInt(parts[1]);
                        if (broadcastCallback != null) {
                            Platform.runLater(() -> broadcastCallback.accept(message));
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Lỗi parse userId từ USER_ONLINE: " + message);
                    }
                }
            }
            else if (message.startsWith("USER_OFFLINE|")) {
                // Format: USER_OFFLINE|userId
                String[] parts = message.split("\\|");
                if (parts.length >= 2) {
                    try {
                        int offlineUserId = Integer.parseInt(parts[1]);
                        if (broadcastCallback != null) {
                            Platform.runLater(() -> broadcastCallback.accept(message));
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Lỗi parse userId từ USER_OFFLINE: " + message);
                    }
                }
            }
            else if (message.startsWith("TYPING_INDICATOR|")) {
                // Format: TYPING_INDICATOR|senderId
                String[] parts = message.split("\\|");
                if (parts.length >= 2) {
                    try {
                        int typingUserId = Integer.parseInt(parts[1]);
                        if (broadcastCallback != null) {
                            Platform.runLater(() -> broadcastCallback.accept(message));
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Lỗi parse userId từ TYPING_INDICATOR: " + message);
                    }
                }
            }
            else if (message.startsWith("TYPING_STOP|")) {
                // Format: TYPING_STOP|senderId
                String[] parts = message.split("\\|");
                if (parts.length >= 2) {
                    try {
                        int typingUserId = Integer.parseInt(parts[1]);
                        if (broadcastCallback != null) {
                            Platform.runLater(() -> broadcastCallback.accept(message));
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Lỗi parse userId từ TYPING_STOP: " + message);
                    }
                }
            }
            else if (message.startsWith("MESSAGE_DELETED|")) {
                // Format: MESSAGE_DELETED|messageId
                String[] parts = message.split("\\|");
                if (parts.length >= 2) {
                    try {
                        int messageId = Integer.parseInt(parts[1]);
                        if (broadcastCallback != null) {
                            Platform.runLater(() -> broadcastCallback.accept(message));
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Lỗi parse messageId từ MESSAGE_DELETED: " + message);
                    }
                }
            }
            else if (message.startsWith("MESSAGE_RECALLED|")) {
                // Format: MESSAGE_RECALLED|messageId
                String[] parts = message.split("\\|");
                if (parts.length >= 2) {
                    try {
                        int messageId = Integer.parseInt(parts[1]);
                        if (broadcastCallback != null) {
                            Platform.runLater(() -> broadcastCallback.accept(message));
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Lỗi parse messageId từ MESSAGE_RECALLED: " + message);
                    }
                }
            }
            else if (message.startsWith("MESSAGE_EDITED|")) {
                // Format: MESSAGE_EDITED|messageId|newContent
                String[] parts = message.split("\\|", 3);
                if (parts.length >= 3) {
                    try {
                        int messageId = Integer.parseInt(parts[1]);
                        String newContent = parts[2];
                        if (broadcastCallback != null) {
                            Platform.runLater(() -> broadcastCallback.accept(message));
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Lỗi parse messageId từ MESSAGE_EDITED: " + message);
                    }
                }
            }
            else {
                // Các thông báo khác
                if (broadcastCallback != null) {
                    Platform.runLater(() -> broadcastCallback.accept(message));
                }
            }
        }

        // Handle byte[] for file data (follow after NEW_FILE, NEW_GROUP_FILE, or FILE_DATA)
        if (obj instanceof byte[]) {
            byte[] fileData = (byte[]) obj;
            System.out.println("Received file data: " + fileData.length + " bytes");
            
            // Kiểm tra xem có phải file download không
            if (pendingFileDownload != null) {
                // Đây là file download response
                FileDownloadInfo info = pendingFileDownload;
                pendingFileDownload = null;
                
                if (fileDownloadCallback != null) {
                    // Tạo FileDownloadInfo với file data
                    FileDownloadInfo downloadInfo = new FileDownloadInfo(info.getMessageId(), info.getFileName(), fileData.length, fileData);
                    Platform.runLater(() -> fileDownloadCallback.accept(downloadInfo));
                }
                return;
            }
            
            // Gắn file data vào message đã lưu tạm (cho NEW_FILE/NEW_GROUP_FILE)
            if (pendingFileMessage != null) {
                pendingFileMessage.setFileData(fileData);
                
                // Gửi message với file data đầy đủ
                if (messageCallback != null) {
                    List<Message> singleMsg = new ArrayList<>();
                    singleMsg.add(pendingFileMessage);
                    Platform.runLater(() -> {
                        messageCallback.accept(singleMsg);
                        pendingFileMessage = null; // Reset sau khi gửi
                    });
                } else {
                    pendingFileMessage = null;
                }
            } else {
                System.err.println("Nhận file data nhưng không có metadata!");
            }
            return;
        }

        System.out.println("Unknown event type: " + (obj != null ? obj.getClass().getName() : "null"));
    }

    // Các method khác giữ nguyên (getUpdatedFriends, reloadMessagesForUpdatedFriends, getMessagesForFriend, register/unregister...)
    private List<Integer> getUpdatedFriends(int userId) {
        List<Integer> friends = new ArrayList<>();
        try {
            FriendDAO dao = new FriendDAO();
            friends = dao.getFriendsByUserId(userId);
            System.out.println("Reloaded " + friends.size() + " friends for user " + userId);
        } catch (SQLException e) {
            System.out.println("Error reloading friends: " + e.getMessage());
            e.printStackTrace();
        }
        return friends;
    }

    private List<Message> reloadMessagesForUpdatedFriends(int userId) {
        List<Message> allPreviewMessages = new ArrayList<>();
        try {
            MessageDAO messageDAO = new MessageDAO();
            List<Integer> friends = getUpdatedFriends(userId);
            for (int friendId : friends) {
                try {
                    List<Message> previews = messageDAO.getLastMessagesPerFriend(userId, friendId, 1);
                    allPreviewMessages.addAll(previews);
                } catch (org.example.zalu.exception.message.MessageException | 
                         org.example.zalu.exception.database.DatabaseException | 
                         org.example.zalu.exception.database.DatabaseConnectionException e) {
                    System.out.println("Error reloading messages for friend " + friendId + ": " + e.getMessage());
                }
            }
            System.out.println("Reloaded preview messages for " + friends.size() + " friends");
        } catch (Exception e) {
            System.out.println("Error reloading messages for friends: " + e.getMessage());
        }
        return allPreviewMessages;
    }

    private List<Message> getMessagesForFriend(int userId, int friendId) {
        List<Message> messages = new ArrayList<>();
        try {
            MessageDAO messageDAO = new MessageDAO();
            messages = messageDAO.getLastMessagesPerFriend(userId, friendId, 1);
            System.out.println("Fetched " + messages.size() + " messages for friend " + friendId);
        } catch (org.example.zalu.exception.message.MessageException | 
                 org.example.zalu.exception.database.DatabaseException | 
                 org.example.zalu.exception.database.DatabaseConnectionException e) {
            System.out.println("Error fetching messages for friend: " + e.getMessage());
        }
        return messages;
    }

    public void registerFriendsCallback(Consumer<List<Integer>> callback) {
        friendCallback = callback;
    }

    public void registerMessagesCallback(Consumer<List<Message>> callback) {
        messageCallback = callback;
    }

    public void registerBroadcastCallback(Consumer<String> callback) {
        broadcastCallback = callback;
    }

    public void registerErrorCallback(Consumer<String> callback) {
        errorCallback = callback;
    }

    public void registerPendingRequestsCallback(Consumer<List<Integer>> callback) {
        pendingRequestsCallback = callback;
    }

    public void registerGroupsCallback(Consumer<List<org.example.zalu.model.GroupInfo>> callback) {
        groupsCallback = callback;
    }

    public void registerOnlineUsersCallback(Consumer<List<Integer>> callback) {
        onlineUsersCallback = callback;
    }
    
    public void registerFileDownloadCallback(Consumer<FileDownloadInfo> callback) {
        fileDownloadCallback = callback;
    }

    public static ObservableList<Integer> getSharedFriends() {
        return sharedFriends;
    }

    public static ObservableList<Message> getSharedMessages() {
        return sharedMessages;
    }

    public void unregisterAllCallbacks() {
        friendCallback = null;
        messageCallback = null;
        broadcastCallback = null;
        errorCallback = null;
        pendingRequestsCallback = null;
        groupsCallback = null;
        onlineUsersCallback = null;
        fileDownloadCallback = null;
    }

    public BlockingDeque<Object> getEventQueue() {
        return eventQueue;
    }
}
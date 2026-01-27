package org.example.zalu.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import org.example.zalu.controller.MainController;
import org.example.zalu.model.Message;
import org.example.zalu.util.AppConstants;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ChatEventManager {
    private static final Logger logger = LoggerFactory.getLogger(ChatEventManager.class);
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
    private static final java.util.Map<Integer, Consumer<FileDownloadInfo>> fileDownloadCallbacks = new java.util.concurrent.ConcurrentHashMap<>();
    private static Consumer<List<org.example.zalu.model.User>> searchUsersCallback = null;
    private static Consumer<List<org.example.zalu.model.User>> getUserByIdCallback = null;
    private static Consumer<java.util.Map<String, List<org.example.zalu.model.User>>> pendingRequestsMapCallback = null; // Changed
                                                                                                                         // to
                                                                                                                         // User
    private static Consumer<List<org.example.zalu.model.User>> friendsListFullCallback = null; // New callback
    private static BiConsumer<Integer, List<Message>> getMessagesCallback = null;
    private static Consumer<String[]> messageSentCallback = null; // New: [status, realId, tempId]

    private static final ObservableList<Integer> sharedFriends = FXCollections.observableArrayList();
    private static final ObservableList<Message> sharedMessages = FXCollections.observableArrayList();
    private static int currentUserId = -1;
    private static Consumer<String> updateProfileCallback = null;
    private static Consumer<org.example.zalu.model.GroupInfo> groupInfoCallback = null;
    private static Consumer<List<org.example.zalu.model.User>> friendsNotInGroupCallback = null;
    private static Consumer<String> addGroupMemberCallback = null;
    private static Consumer<String> memberRoleCallback = null;
    private static Consumer<List<Message>> searchMessagesCallback = null;
    private static Consumer<List<Message>> pinnedMessagesCallback = null;
    private static Consumer<java.util.Map<Integer, Integer>> unreadCountCallback = null; // New: unread count map
    // Lưu metadata file tạm thời để gắn vào file data
    private static Message pendingFileMessage = null;
    // Lưu thông tin file download đang chờ
    private static FileDownloadInfo pendingFileDownload = null;
    private static int pendingAvatarUserId = -1;
    private static final java.util.Map<Integer, Consumer<byte[]>> userAvatarCallbacks = new java.util.concurrent.ConcurrentHashMap<>();

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

        public int getMessageId() {
            return messageId;
        }

        public String getFileName() {
            return fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public byte[] getFileData() {
            return fileData;
        }
    }

    // Counter để phân biệt các loại List<Integer> sau khi login
    private static int integerListCounter = 0;
    private static boolean hasReceivedGroups = false;

    public static void setCurrentUserId(int id) {
        currentUserId = id;
        logger.info("ChatEventManager: Current userId set to {}", id);
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
        if (listening)
            return;
        listening = true;
        new Thread(() -> {
            logger.info("Listener started for user");
            while (listening) {
                try {
                    Object obj = in.readObject();
                    if (obj != null) {
                        eventQueue.offer(obj);
                        processEvent(obj); // Gọi processEvent (giữ nguyên)
                    }
                } catch (SocketTimeoutException e) {
                    logger.debug("Listener timeout - continuing to listen...");
                } catch (IOException | ClassNotFoundException e) {
                    logger.error("Error receiving in listener: {}", e.getMessage());
                    if (e instanceof java.io.StreamCorruptedException) {
                        logger.warn("Stream corrupted - attempting reconnect...");
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
            logger.warn("Received null object - ignoring");
            return; // SỬA: Check null
        }
        logger.debug("Processing event: {} (type: {})", obj, (obj != null ? obj.getClass().getSimpleName() : "null"));

        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (list.isEmpty()) {
                logger.debug("Empty List received - ignoring (e.g., no friends)");
                return;
            }
        }

        if (obj instanceof Boolean) {
            boolean success = (Boolean) obj;
            logger.info("Boolean event (success?): {}", success);
            if (errorCallback != null) {
                Platform.runLater(
                        () -> errorCallback.accept(success ? "SUCCESS|Operation OK" : "FAIL|Operation failed"));
            }
            return;
        }

        if (obj instanceof List && !((List<?>) obj).isEmpty()) {
            List<?> list = (List<?>) obj;
            if (list.get(0) instanceof Integer) {
                List<Integer> intList = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Integer)
                        intList.add((Integer) o);
                }
                integerListCounter++;
                logger.info("Received List<Integer> #{}: {} (friends, pending, or online users?)", integerListCounter,
                        intList);

                // Phân biệt dựa trên thứ tự và flag:
                // 1. List đầu tiên sau login = friends
                // 2. List thứ hai (nếu có) = pending requests
                // 3. List sau khi đã nhận groups = online users
                if (hasReceivedGroups && onlineUsersCallback != null) {
                    // Đã nhận groups, nên đây là online users list
                    logger.debug("Treating as online users list");
                    Platform.runLater(() -> onlineUsersCallback.accept(intList));
                } else if (integerListCounter == 1 && friendCallback != null) {
                    // List đầu tiên = friends
                    logger.debug("Treating as friends list");
                    Platform.runLater(() -> friendCallback.accept(intList));
                } else if (integerListCounter == 2 && pendingRequestsCallback != null) {
                    // List thứ hai = pending requests
                    logger.debug("Treating as pending requests list");
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
                List<Message> allMessages = new ArrayList<>();
                Map<Integer, List<Message>> groupedByChat = new HashMap<>();

                for (Object o : list) {
                    if (o instanceof Message) {
                        Message m = (Message) o;
                        allMessages.add(m);

                        int chatId = m.getGroupId() > 0 ? -m.getGroupId()
                                : (m.getSenderId() == currentUserId ? m.getReceiverId() : m.getSenderId());
                        groupedByChat.computeIfAbsent(chatId, k -> new ArrayList<>()).add(m);
                    }
                }

                logger.info("Received {} messages total, grouped into {} conversations",
                        allMessages.size(), groupedByChat.size());

                // Cache each conversation separately
                for (Map.Entry<Integer, List<Message>> entry : groupedByChat.entrySet()) {
                    ClientCache.getInstance().cacheMessages(entry.getKey(), entry.getValue());
                }

                if (messageCallback != null) {
                    Platform.runLater(() -> messageCallback.accept(allMessages));
                }
                if (getMessagesCallback != null) {
                    // ChatId might be mixed in batch, but usually it's login batch.
                    // For batch, we'll just pass 0 or not call it if it's meant for history only.
                    // But let's pass 0 to indicate unknown/mixed batch.
                    Platform.runLater(() -> getMessagesCallback.accept(0, allMessages));
                }
                return;
            } else if (list.get(0) instanceof org.example.zalu.model.GroupInfo) {
                List<org.example.zalu.model.GroupInfo> groups = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof org.example.zalu.model.GroupInfo)
                        groups.add((org.example.zalu.model.GroupInfo) o);
                }
                logger.info("Received List<GroupInfo>: {} groups", groups.size());
                hasReceivedGroups = true; // Đánh dấu đã nhận groups
                if (groupsCallback != null) {
                    Platform.runLater(() -> groupsCallback.accept(groups));
                }
                return;
            } else if (list.get(0) instanceof org.example.zalu.model.User) {
                List<org.example.zalu.model.User> users = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof org.example.zalu.model.User)
                        users.add((org.example.zalu.model.User) o);
                }
                logger.info("Received List<User>: {} users (search results or user info)", users.size());
                // Có thể là search results hoặc user info
                if (searchUsersCallback != null) {
                    Platform.runLater(() -> searchUsersCallback.accept(users));
                }
                return;
            }
        }
        // Handle single GroupInfo object
        if (obj instanceof org.example.zalu.model.GroupInfo) {
            org.example.zalu.model.GroupInfo group = (org.example.zalu.model.GroupInfo) obj;
            // Note: We don't have a specific group cache yet in ClientCache but we could
            // add it
            if (groupInfoCallback != null) {
                Platform.runLater(() -> groupInfoCallback.accept(group));
            }
            return;
        }

        // Xử lý Map (cho GET_PENDING_REQUESTS, GET_FRIENDS_LIST_FULL, và UNREAD_COUNT)
        if (obj instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) obj;

            // Check if this is an unread count map (Integer -> Integer)
            if (!map.isEmpty() && map.keySet().iterator().next() instanceof Integer) {
                boolean isUnreadCountMap = true;
                for (Object value : map.values()) {
                    if (!(value instanceof Integer)) {
                        isUnreadCountMap = false;
                        break;
                    }
                }

                if (isUnreadCountMap && unreadCountCallback != null) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<Integer, Integer> unreadMap = (java.util.Map<Integer, Integer>) map;
                    logger.info("Received unread count map: {} entries", unreadMap.size());
                    Platform.runLater(() -> unreadCountCallback.accept(unreadMap));
                    return;
                }
            }

            if (map.containsKey("type") && "FRIENDS_NOT_IN_GROUP".equals(map.get("type"))) {
                Object data = map.get("data");
                if (data instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<org.example.zalu.model.User> users = (List<org.example.zalu.model.User>) data;
                    if (friendsNotInGroupCallback != null) {
                        Platform.runLater(() -> friendsNotInGroupCallback.accept(users));
                    }
                }
                return;
            }

            if (map.containsKey("type") && "FRIENDS_LIST_FULL".equals(map.get("type"))) {
                Object data = map.get("data");
                if (data instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<org.example.zalu.model.User> friends = (List<org.example.zalu.model.User>) data;
                    for (org.example.zalu.model.User friend : friends) {
                        ClientCache.getInstance().cacheUser(friend);
                    }
                    if (friendsListFullCallback != null) {
                        Platform.runLater(() -> friendsListFullCallback.accept(friends));
                    }
                }
                return;
            }

            if (map.containsKey("type") && "CONVERSATION_HISTORY".equals(map.get("type"))) {
                Object data = map.get("data");
                if (data instanceof List) {
                    List<Message> messages = new ArrayList<>();
                    for (Object o : (List<?>) data) {
                        if (o instanceof Message)
                            messages.add((Message) o);
                    }

                    // Xác định chatId từ echo context
                    int chatId = 0;
                    if (map.containsKey("groupId")) {
                        chatId = -((Number) map.get("groupId")).intValue();
                    } else if (map.containsKey("friendId")) {
                        chatId = ((Number) map.get("friendId")).intValue();
                    }

                    if (chatId != 0) {
                        logger.info("Caching history for chatId {}: {} messages", chatId, messages.size());
                        ClientCache.getInstance().cacheMessages(chatId, messages);
                    }

                    if (getMessagesCallback != null) {
                        int finalChatId = chatId;
                        Platform.runLater(() -> getMessagesCallback.accept(finalChatId, messages));
                    }
                }
                return;
            }

            if (map.containsKey("incoming") && map.containsKey("outgoing")) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, List<org.example.zalu.model.User>> pendingMap = (java.util.Map<String, List<org.example.zalu.model.User>>) map;
                if (pendingRequestsMapCallback != null) {
                    Platform.runLater(() -> pendingRequestsMapCallback.accept(pendingMap));
                }
                return;
            }

            if (map.containsKey("type") && "SEARCH_MESSAGES_RESULT".equals(map.get("type"))) {
                Object data = map.get("data");
                if (data instanceof List) {
                    List<?> list = (List<?>) data;
                    List<Message> messages = new ArrayList<>();
                    for (Object o : list) {
                        if (o instanceof Message)
                            messages.add((Message) o);
                    }
                    if (searchMessagesCallback != null) {
                        Platform.runLater(() -> searchMessagesCallback.accept(messages));
                    }
                }
                return;
            }

            if (map.containsKey("type") && "PINNED_MESSAGES_RESULT".equals(map.get("type"))) {
                Object data = map.get("data");
                if (data instanceof List) {
                    List<?> list = (List<?>) data;
                    List<Message> messages = new ArrayList<>();
                    for (Object o : list) {
                        if (o instanceof Message)
                            messages.add((Message) o);
                    }
                    if (pinnedMessagesCallback != null) {
                        Platform.runLater(() -> pinnedMessagesCallback.accept(messages));
                    }
                }
                return;
            }
        }
        // Xử lý User đơn lẻ (cho GET_USER_BY_ID)
        if (obj instanceof org.example.zalu.model.User) {
            org.example.zalu.model.User user = (org.example.zalu.model.User) obj;
            ClientCache.getInstance().cacheUser(user);
            if (getUserByIdCallback != null) {
                List<org.example.zalu.model.User> userList = new ArrayList<>();
                userList.add(user);
                Platform.runLater(() -> getUserByIdCallback.accept(userList));
            }
            return;
        }

        if (obj instanceof String) {
            String message = (String) obj;
            logger.info("Received string message: {}", message);

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
                        FXMLLoader loader = new FXMLLoader(
                                getClass().getResource("/org/example/zalu/views/main/main-view.fxml"));
                        Parent root = loader.load();
                        MainController controller = loader.getController();

                        Stage stage = LoginSession.getStage(); // Lấy stage đã lưu
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

                            logger.info("✓ Đăng nhập thành công! UserID = {}", userId);

                            // Dọn dẹp
                            LoginSession.clear();
                        }
                    } catch (Exception e) {
                        logger.error("Error switching to main view", e);
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
                String error = message.contains("|") ? message.substring(message.indexOf("|", 20) + 1)
                        : "Lỗi không xác định";
                // Gọi callback onFail
                ChatClient.triggerLoginCallback(false, -1, error);
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.ERROR, "Đăng nhập thất bại: " + error, ButtonType.OK).show();
                });
                return;
            }

            if (message.startsWith("RESUME_SESSION|")) {
                if (message.contains("|OK")) {
                    logger.info("✓ Khôi phục session thành công, yêu cầu tải lại nhóm");
                    if (currentUserId > 0) {
                        ChatClient.sendRequest("GET_GROUPS|" + currentUserId);
                    }
                } else {
                    logger.warn("✗ Khôi phục session thất bại: {}", message);
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
                    // Parsing optional parts
                    for (int i = 4; i < parts.length; i++) {
                        if ("REPLY_TO".equals(parts[i]) && i + 2 < parts.length) {
                            try {
                                newMsg.setRepliedToMessageId(Integer.parseInt(parts[i + 1]));
                                newMsg.setRepliedToContent(parts[i + 2]);
                            } catch (NumberFormatException ignored) {
                            }
                            i += 2;
                        } else if ("TEMP_ID".equals(parts[i]) && i + 1 < parts.length) {
                            newMsg.setTempId(parts[i + 1]);
                            i += 1;
                        }
                    }
                    if (messageCallback != null) {
                        List<Message> singleMsg = new ArrayList<>();
                        singleMsg.add(newMsg);
                        Platform.runLater(() -> messageCallback.accept(singleMsg));
                    }
                }
            } else if (message.startsWith("NEW_FILE|")) {
                // Format: NEW_FILE|senderId|receiverId|fileName|fileSize
                String[] parts = message.split("\\|");
                if (parts.length >= 5) {
                    int senderId = Integer.parseInt(parts[1]);
                    int receiverId = Integer.parseInt(parts[2]);
                    String fileName = parts[3];
                    Message newFileMsg = new Message(0, senderId, receiverId, null, true, LocalDateTime.now());
                    newFileMsg.setFileName(fileName);
                    // Lưu tạm để gắn file data sau
                    pendingFileMessage = newFileMsg;
                    // Chờ nhận file data (byte[]) tiếp theo
                }
            } else if (message.startsWith("NEW_GROUP_MESSAGE|")) {
                String[] parts = message.split("\\|");
                if (parts.length >= 4) {
                    int groupId = Integer.parseInt(parts[1]);
                    int senderId = Integer.parseInt(parts[2]);
                    String content = parts[3];
                    Message newMsg = new Message(0, senderId, 0, content, false, LocalDateTime.now(), groupId);
                    // Parsing optional parts
                    for (int i = 4; i < parts.length; i++) {
                        if ("REPLY_TO".equals(parts[i]) && i + 2 < parts.length) {
                            try {
                                newMsg.setRepliedToMessageId(Integer.parseInt(parts[i + 1]));
                                newMsg.setRepliedToContent(parts[i + 2]);
                            } catch (NumberFormatException ignored) {
                            }
                            i += 2;
                        } else if ("TEMP_ID".equals(parts[i]) && i + 1 < parts.length) {
                            newMsg.setTempId(parts[i + 1]);
                            i += 1;
                        }
                    }
                    if (messageCallback != null) {
                        List<Message> singleMsg = new ArrayList<>();
                        singleMsg.add(newMsg);
                        Platform.runLater(() -> messageCallback.accept(singleMsg));
                    }
                }
            } else if (message.startsWith("NEW_GROUP_FILE|")) {
                // Format: NEW_GROUP_FILE|groupId|senderId|fileName|fileSize
                String[] parts = message.split("\\|");
                if (parts.length >= 5) {
                    int groupId = Integer.parseInt(parts[1]);
                    int senderId = Integer.parseInt(parts[2]);
                    String fileName = parts[3];
                    Message newFileMsg = new Message(0, senderId, 0, null, true, LocalDateTime.now(), groupId);
                    newFileMsg.setFileName(fileName);
                    // Lưu tạm để gắn file data sau
                    pendingFileMessage = newFileMsg;
                    // Chờ nhận file data (byte[]) tiếp theo
                }
            } else if (message.equals("GROUPS_UPDATE")) {
                // Yêu cầu client reload danh sách nhóm
                if (currentUserId > 0) {
                    ChatClient.sendRequest("GET_GROUPS|" + currentUserId);
                }
            } else if (message.startsWith("LEAVE_GROUP|")) {
                // Handle LEAVE_GROUP response
                if (broadcastCallback != null) {
                    Platform.runLater(() -> broadcastCallback.accept(message));
                }
                // If success, groups will be updated via GROUPS_UPDATE broadcast
            } else if (message.startsWith("ERROR|") || message.startsWith("FAIL|")) {
                if (errorCallback != null) {
                    Platform.runLater(() -> errorCallback.accept(message));
                }
            } else if (message.startsWith("ADD_GROUP_MEMBER|")) {
                if (addGroupMemberCallback != null) {
                    Platform.runLater(() -> addGroupMemberCallback.accept(message));
                }
            } else if (message.startsWith("MEMBER_ROLE|")) {
                if (memberRoleCallback != null) {
                    Platform.runLater(() -> memberRoleCallback.accept(message));
                }
            } else if (message.startsWith("MESSAGE_SENT|") || message.startsWith("FILE_SENT|") ||
                    message.startsWith("GROUP_MESSAGE_SENT|") || message.startsWith("GROUP_FILE_SENT|")) {
                if (messageSentCallback != null) {
                    String[] parts = message.split("\\|");
                    Platform.runLater(() -> messageSentCallback.accept(parts));
                }
                if (errorCallback != null) {
                    Platform.runLater(() -> errorCallback.accept(message));
                }
            } else if (message.startsWith("FRIEND_REQUEST_SENT|")) {
                // Format: FRIEND_REQUEST_SENT|OK hoặc FRIEND_REQUEST_SENT|FAIL
                // Gửi qua broadcastCallback để AddFriendController có thể xử lý
                if (broadcastCallback != null) {
                    Platform.runLater(() -> broadcastCallback.accept(message));
                }
            } else if (message.startsWith("ACCEPT_FRIEND_OK") || message.startsWith("ACCEPT_FRIEND_FAIL")) {
                // Gửi qua broadcastCallback
                if (broadcastCallback != null) {
                    Platform.runLater(() -> broadcastCallback.accept(message));
                }
            } else if (message.startsWith("FILE_DATA|")) {
                // Format: FILE_DATA|messageId|fileName|fileSize hoặc FILE_DATA|FAIL|error
                String[] parts = message.split("\\|");
                if (parts.length >= 2 && !parts[1].equals("FAIL")) {
                    // Success: lưu metadata và chờ file data
                    try {
                        int messageId = Integer.parseInt(parts[1]);
                        String fileName = parts.length >= 3 ? parts[2] : "file";
                        long fileSize = parts.length >= 4 ? Long.parseLong(parts[3]) : 0;
                        pendingFileDownload = new FileDownloadInfo(messageId, fileName, fileSize);
                        logger.info("FILE_DATA metadata received: messageId={}, fileName={}, size={}", messageId,
                                fileName, fileSize);
                    } catch (NumberFormatException e) {
                        logger.error("Invalid FILE_DATA format: {}", message);
                        pendingFileDownload = null;
                    }
                } else {
                    // Error: FILE_DATA|FAIL|messageId|reason OR FILE_DATA|FAIL|reason
                    // (legacy/parsing error)
                    // Try to parse messageId if available
                    int failedMessageId = -1;
                    String error = "Unknown error";

                    if (parts.length >= 4) {
                        try {
                            failedMessageId = Integer.parseInt(parts[2]);
                            error = parts[3];
                        } catch (NumberFormatException e) {
                            error = parts[2]; // Fallback to old format logic
                        }
                    } else if (parts.length >= 3) {
                        error = parts[2];
                    }

                    logger.error("FILE_DATA error: {}", error);

                    if (failedMessageId != -1) {
                        Consumer<FileDownloadInfo> callback = fileDownloadCallbacks.remove(failedMessageId);
                        if (callback != null) {
                            Platform.runLater(() -> callback.accept(null));
                        }
                    }

                    pendingFileDownload = null;
                }
            } else if (message.startsWith("MESSAGES_READ|")) {
                // Format: MESSAGES_READ|receiverId
                // Người nhận (receiverId) đã đọc tin nhắn của người gửi (currentUserId)
                String[] parts = message.split("\\|");
                if (parts.length >= 2) {
                    int readerId = Integer.parseInt(parts[1]);
                    logger.debug("ChatEventManager: Tin nhắn đã được đọc bởi user {}", readerId);
                    // Gọi callback để cập nhật UI
                    if (broadcastCallback != null) {
                        Platform.runLater(() -> broadcastCallback.accept(message));
                    }
                }
                return;
            } else if (message.startsWith("BROADCAST|")) {
                if (broadcastCallback != null) {
                    Platform.runLater(() -> broadcastCallback.accept(message.substring(10)));
                }
            } else if (message.startsWith("USER_ONLINE|") || message.startsWith("USER_OFFLINE|") ||
                    message.startsWith("USER_PROFILE_UPDATED|") ||
                    message.startsWith("TYPING_INDICATOR|") || message.startsWith("TYPING_STOP|") ||
                    message.startsWith("MESSAGE_DELETED|") || message.startsWith("MESSAGE_RECALLED|") ||
                    message.startsWith("MESSAGE_EDITED|")) {
                if (broadcastCallback != null) {
                    Platform.runLater(() -> broadcastCallback.accept(message));
                }
            } else if (message.startsWith("KICKED|")) {
                // User bị kick bởi admin
                String reason = message.substring(7); // Lấy lý do sau "KICKED|"
                logger.warn("⚠️ Bị kick khỏi server: {}", reason);

                // Ngừng listening để không reconnect
                ChatClient.disconnect();

                // Hiển thị thông báo và đóng ứng dụng
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Bị đá khỏi server");
                    alert.setHeaderText("⚠️ Kết nối bị ngắt");
                    alert.setContentText(reason);
                    alert.showAndWait();

                    // Quay về màn hình login
                    try {
                        FXMLLoader loader = new FXMLLoader(
                                getClass().getResource("/org/example/zalu/views/auth/login-view.fxml"));
                        Parent root = loader.load();
                        Stage stage = LoginSession.getStage();
                        if (stage != null) {
                            Scene scene = new Scene(root, AppConstants.LOGIN_WIDTH, AppConstants.LOGIN_HEIGHT);
                            stage.setScene(scene);
                            stage.setTitle("Zalu - Đăng nhập");
                            stage.setResizable(false);
                            stage.centerOnScreen();
                        }
                    } catch (Exception e) {
                        logger.error("Error returning to login", e);
                    }
                });
                return;
            } else if (message.startsWith("SYSTEM_ANNOUNCEMENT|")) {
                String content = message.substring(20);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Thông báo từ hệ thống");
                    alert.setHeaderText("📢 THÔNG BÁO QUAN TRỌNG");
                    alert.setContentText(content);
                    alert.show();
                });
            } else if (message.startsWith("UPDATE_PROFILE|")) {
                if (updateProfileCallback != null) {
                    Platform.runLater(() -> updateProfileCallback.accept(message));
                }
            } else if (message.startsWith("VIDEO_CALL_INCOMING|") ||
                    message.startsWith("VIDEO_CALL_ACCEPTED|") ||
                    message.startsWith("VIDEO_CALL_REJECTED|") ||
                    message.startsWith("VIDEO_CALL_ENDED|") ||
                    message.startsWith("VIDEO_CALL_REQUEST|")) {
                // Video call events - forward to broadcast callback
                if (broadcastCallback != null) {
                    Platform.runLater(() -> broadcastCallback.accept(message));
                }
                logger.debug("Video call event: {}", message);
            } else if (message.startsWith("USER_AVATAR|")) {
                String[] parts = message.split("\\|");
                if (parts.length >= 3 && !parts[1].equals("FAIL")) {
                    try {
                        int targetUserId = Integer.parseInt(parts[1]);
                        int size = Integer.parseInt(parts[2]);
                        if (size > 0) {
                            pendingAvatarUserId = targetUserId;
                        } else {
                            ClientCache.getInstance().cacheAvatar(targetUserId, null);
                            if (userAvatarCallbacks.containsKey(targetUserId)) {
                                Platform.runLater(() -> {
                                    userAvatarCallbacks.get(targetUserId).accept(null);
                                    userAvatarCallbacks.remove(targetUserId);
                                });
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error parsing USER_AVATAR notification", e);
                    }
                }
                return;
            } else {
                // Các thông báo khác
                if (broadcastCallback != null) {
                    Platform.runLater(() -> broadcastCallback.accept(message));
                }
            }
        }

        // Handle byte[] for file data or avatar data
        if (obj instanceof byte[]) {
            byte[] data = (byte[]) obj;
            logger.info("Received byte data: {} bytes", data.length);

            // Kiểm tra xem có phải file download không
            if (pendingFileDownload != null) {
                FileDownloadInfo info = pendingFileDownload;
                pendingFileDownload = null;

                Consumer<FileDownloadInfo> callback = fileDownloadCallbacks.remove(info.getMessageId());
                if (callback != null) {
                    FileDownloadInfo downloadInfo = new FileDownloadInfo(info.getMessageId(), info.getFileName(),
                            data.length, data);
                    Platform.runLater(() -> callback.accept(downloadInfo));
                } else {
                    logger.warn("Received file data for message {} but no callback registered", info.getMessageId());
                }
                return;
            }

            // Avatar data
            if (pendingAvatarUserId > 0) {
                int targetUserId = pendingAvatarUserId;
                ClientCache.getInstance().cacheAvatar(targetUserId, data);
                if (userAvatarCallbacks.containsKey(targetUserId)) {
                    Platform.runLater(() -> {
                        userAvatarCallbacks.get(targetUserId).accept(data);
                        userAvatarCallbacks.remove(targetUserId);
                    });
                }
                pendingAvatarUserId = -1;
                return;
            }

            // Gắn file data vào message đã lưu tạm (cho NEW_FILE/NEW_GROUP_FILE)
            if (pendingFileMessage != null) {
                pendingFileMessage.setFileData(data);

                if (messageCallback != null) {
                    List<Message> singleMsg = new ArrayList<>();
                    singleMsg.add(pendingFileMessage);
                    Platform.runLater(() -> {
                        messageCallback.accept(singleMsg);
                        pendingFileMessage = null;
                    });
                } else {
                    pendingFileMessage = null;
                }
                return;
            }

            logger.warn("Nhận byte[] data nhưng không có metadata (pendingAvatarUserId={})", pendingAvatarUserId);
            return;
        }

        logger.warn("Unknown event type: {}", (obj != null ? obj.getClass().getName() : "null"));
    }

    // register/unregister...

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

    public void registerFileDownloadCallback(int messageId, Consumer<FileDownloadInfo> callback) {
        fileDownloadCallbacks.put(messageId, callback);
    }

    public void registerSearchUsersCallback(Consumer<List<org.example.zalu.model.User>> callback) {
        searchUsersCallback = callback;
    }

    public void registerGetUserByIdCallback(Consumer<List<org.example.zalu.model.User>> callback) {
        getUserByIdCallback = callback;
    }

    public void registerPendingRequestsMapCallback(
            Consumer<java.util.Map<String, List<org.example.zalu.model.User>>> callback) {
        pendingRequestsMapCallback = callback;
    }

    public void registerFriendsListFullCallback(Consumer<List<org.example.zalu.model.User>> callback) {
        friendsListFullCallback = callback;
    }

    public void registerGetMessagesCallback(BiConsumer<Integer, List<Message>> callback) {
        getMessagesCallback = callback;
    }

    public void registerGroupInfoCallback(Consumer<org.example.zalu.model.GroupInfo> callback) {
        groupInfoCallback = callback;
    }

    public void registerFriendsNotInGroupCallback(Consumer<List<org.example.zalu.model.User>> callback) {
        friendsNotInGroupCallback = callback;
    }

    public void registerAddGroupMemberCallback(Consumer<String> callback) {
        addGroupMemberCallback = callback;
    }

    public void registerMemberRoleCallback(Consumer<String> callback) {
        memberRoleCallback = callback;
    }

    public void registerUpdateProfileCallback(Consumer<String> callback) {
        updateProfileCallback = callback;
    }

    public void registerUserAvatarCallback(int userId, Consumer<byte[]> callback) {
        userAvatarCallbacks.put(userId, callback);
    }

    public void registerSearchMessagesCallback(Consumer<List<Message>> callback) {
        searchMessagesCallback = callback;
    }

    public void registerPinnedMessagesCallback(Consumer<List<Message>> callback) {
        pinnedMessagesCallback = callback;
    }

    public void registerMessageSentCallback(Consumer<String[]> callback) {
        messageSentCallback = callback;
    }

    public void registerUnreadCountCallback(Consumer<java.util.Map<Integer, Integer>> callback) {
        unreadCountCallback = callback;
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
        fileDownloadCallbacks.clear();
        searchUsersCallback = null;
        getUserByIdCallback = null;
        pendingRequestsMapCallback = null;
        getMessagesCallback = null;
    }

    public BlockingDeque<Object> getEventQueue() {
        return eventQueue;
    }
}
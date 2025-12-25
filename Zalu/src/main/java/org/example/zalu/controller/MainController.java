package org.example.zalu.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

import org.example.zalu.client.ChatClient;
import org.example.zalu.client.ChatEventManager;
import org.example.zalu.controller.auth.LoginController;
import org.example.zalu.controller.chat.ChatController;
import org.example.zalu.controller.chat.MessageListController;
import org.example.zalu.controller.chat.WelcomeController;
import org.example.zalu.controller.friend.FriendRequestController;
import org.example.zalu.controller.group.CreateGroupController;
import org.example.zalu.model.ChatItem;
import org.example.zalu.model.GroupInfo;
import org.example.zalu.model.Message;
import org.example.zalu.model.User;
import org.example.zalu.service.AvatarService;
import org.example.zalu.service.MessageUpdateService;
import org.example.zalu.service.NavigationManager;
import org.example.zalu.util.AppConstants;
import org.example.zalu.util.LogoutHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    @FXML
    private ListView<ChatItem> chatList;

    @FXML
    private VBox chatContainer;
    @FXML
    private ImageView navAvatarImage;

    @FXML
    private Button settingsBtn;

    private boolean isDarkMode = false;
    private ChatController chatController;
    private MessageListController messageListController;
    private Parent chatInputRoot;

    private Stage stage;
    private int currentUserId = -1;
    private int currentFriendId = -1;
    private int currentGroupId = -1;
    private ChatItem currentChatItem = null; // Track current chat item for message loading
    private boolean listenerStarted = false;
    private String welcomeUsername;
    private boolean isWelcomeMode = true;
    private org.example.zalu.model.User currentUser;

    // Services & Managers
    private MessageUpdateService messageUpdateService;
    private NavigationManager navigationManager;
    private ChatListManager chatListManager;

    @FXML
    public void initialize() {
        initData();
        setupNavAvatarClip();
        setupNavAccountMenu();
        chatList.setCellFactory(createChatItemCellFactory());
        chatList.getSelectionModel().selectedItemProperty().addListener((obs, old, newSelected) -> {
            if (chatListManager != null && chatListManager.isRefreshing()) {
                logger.debug("Skipping selection change processing due to isRefreshing=true");
                return;
            }

            if (newSelected != null) {
                logger.info("Selection changed to: {} (isWelcomeMode: {})",
                        newSelected.getDisplayName(), isWelcomeMode);
                onChatItemSelected();
            }
        });

        if (currentUserId > 0) {
            refreshFriendList();
        }

        if (!listenerStarted) {
            if (chatListManager != null)
                chatListManager.registerCallbacks();

            ChatEventManager.getInstance().registerMessagesCallback(this::onMessagesReceived);
            // Error & Broadcast callbacks
            ChatEventManager.getInstance().registerErrorCallback(this::onMessageSentResponse);
            ChatEventManager.getInstance().registerBroadcastCallback(this::onBroadcastMessage);

            // Register GetMessages callback ONCE here instead of in reloadChatForItem
            ChatEventManager.getInstance().registerGetMessagesCallback(this::handleGetMessagesResponse);

            listenerStarted = true;
        }

        URL addUrl = getClass().getResource("/org/example/zalu/views/friend/add-friend-tab.fxml");
        if (addUrl == null)
            logger.error("Add-friend-tab URL is null!");

        loadSubControllers();
        showWelcomeInMessageArea();
        fetchCurrentUserProfile();
    }

    private void initData() {
        messageUpdateService = new MessageUpdateService();
        navigationManager = new NavigationManager(navAvatarImage);
        chatListManager = new ChatListManager(this, chatList, messageUpdateService);
        logger.info("DAOs and Managers initialized");
    }

    private Callback<ListView<ChatItem>, ListCell<ChatItem>> createChatItemCellFactory() {
        return new ChatListCellFactory(this, messageUpdateService, chatListManager.getOnlineFriends(),
                chatListManager.getUnreadCounts());
    }

    public boolean isRefreshing() {
        return chatListManager != null && chatListManager.isRefreshing();
    }

    public void refreshFriendList() {
        if (chatListManager != null) {
            chatListManager.refreshFriendList(currentUserId);
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        logger.info("MainController stage set");
        if (chatController != null)
            chatController.setStage(stage);
        // Cập nhật kích thước Stage nếu cần
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        logger.info("MainController currentUserId set to: {}", userId);
        if (messageListController != null)
            messageListController.setCurrentUserId(userId);
        if (chatController != null)
            chatController.setCurrentUserId(userId);
        if (userId > 0) {
            refreshFriendList();
            fetchCurrentUserProfile();
        }
        if (navigationManager != null) {
            navigationManager.setCurrentUserId(userId);
        }
    }

    @FXML
    public void logout() {
        logger.info("Logout triggered for userId: {}", currentUserId);
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Xác nhận đăng xuất");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Bạn có chắc chắn muốn đăng xuất?");
        if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            ChatClient.disconnect();
            ChatEventManager.getInstance().unregisterAllCallbacks();
            resetDataFlags();
            switchToLogin();
            logger.info("Logout successful");
        }
    }

    private void switchToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/auth/login-view.fxml"));
            Parent root = loader.load();
            LoginController loginController = loader.getController();
            if (loginController != null) {
                loginController.setStage(stage);
            }
            Scene scene = new Scene(root, AppConstants.LOGIN_WIDTH, AppConstants.LOGIN_HEIGHT);
            stage.setScene(scene);
            stage.setTitle("Zalu - Đăng nhập");
            stage.setWidth(AppConstants.LOGIN_WIDTH);
            stage.setHeight(AppConstants.LOGIN_HEIGHT);
            stage.setResizable(false); // Login cố định
            stage.setMinWidth(AppConstants.LOGIN_WIDTH);
            stage.setMaxWidth(AppConstants.LOGIN_WIDTH);
            stage.setMinHeight(AppConstants.LOGIN_HEIGHT);
            stage.setMaxHeight(AppConstants.LOGIN_HEIGHT);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            logger.error("Error switching to login: ", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi Đăng Xuất");
            alert.setContentText("Không thể chuyển sang màn hình đăng nhập: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    public void openProfileView() {
        showProfileDialog();
    }

    @FXML
    private void openSettingsMenu() {
        ContextMenu contextMenu = new ContextMenu();

        // Profile Item
        MenuItem profileItem = new MenuItem("Thông tin tài khoản");
        profileItem.setOnAction(e -> openProfileView());

        // Theme Item
        CheckMenuItem themeItem = new CheckMenuItem("Giao diện tối (Dark Mode)");
        themeItem.setSelected(isDarkMode);
        themeItem.setOnAction(e -> toggleTheme(themeItem.isSelected()));

        // Logout Item
        MenuItem logoutItem = new MenuItem("Đăng xuất");
        logoutItem.setOnAction(e -> logout());

        contextMenu.getItems().addAll(profileItem, new SeparatorMenuItem(), themeItem, new SeparatorMenuItem(),
                logoutItem);

        if (settingsBtn != null) {
            contextMenu.show(settingsBtn, Side.TOP, 0, 0);
        }
    }

    private void toggleTheme(boolean enableDark) {
        if (stage == null || stage.getScene() == null)
            return;
        Scene scene = stage.getScene();
        String darkCss = getClass().getResource("/dark-theme.css").toExternalForm();

        if (enableDark) {
            if (!scene.getStylesheets().contains(darkCss)) {
                scene.getStylesheets().add(darkCss);
            }
            isDarkMode = true;
        } else {
            scene.getStylesheets().remove(darkCss);
            isDarkMode = false;
        }
    }

    public void resetDataFlags() {
        logger.info("Resetting data flags for logout...");
        currentUserId = -1;
        currentFriendId = -1;
        currentGroupId = -1;
        isWelcomeMode = true;
        listenerStarted = false;
        welcomeUsername = null;

        if (chatListManager != null) {
            chatListManager.clearData();
        }

        if (messageUpdateService != null) {
            messageUpdateService.clear();
        }

        if (chatList != null) {
            chatList.getItems().clear();
        }

        if (messageListController != null) {
            messageListController.clearChat();
            messageListController.showWelcomeScreen("Chào mừng trở lại!");
        }
        logger.info("Data flags reset completed");
    }

    @FXML
    public void createGroup() {
        if (currentUserId <= 0) {
            logger.warn("Cannot create group: User not logged in");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/zalu/views/group/create-group-view.fxml"));
            Parent content = loader.load();
            CreateGroupController controller = loader.getController();
            controller.setCurrentUserId(currentUserId);

            // Pass friend list directly instead of requesting from server
            if (chatListManager != null) {
                List<User> friends = chatListManager.getCachedFriendsList();
                if (friends != null && !friends.isEmpty()) {
                    controller.setFriendsList(friends);
                    logger.info("Passed {} friends to CreateGroupController", friends.size());
                }
            }

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Tạo nhóm mới");
            dialogStage.initOwner(stage);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.setResizable(false);
            Scene dialogScene = new Scene(content);
            dialogStage.setScene(dialogScene);

            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // Refresh groups list
            refreshFriendList();
        } catch (Exception e) {
            logger.error("Error opening create group dialog", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setContentText("Không thể mở dialog tạo nhóm: " + e.getMessage());
            alert.show();
        }
    }

    public void viewFriendRequests() {
        if (currentUserId <= 0) {
            logger.warn("Cannot view friend requests: User not logged in");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/zalu/views/friend/friend-request-view.fxml"));
            Parent root = loader.load();
            FriendRequestController controller = loader.getController();
            controller.setStage(stage);
            controller.setCurrentUserId(currentUserId);
            controller.setMainController(this);

            // Initialize data via server request instead of direct DAO
            controller.loadData();

            controller.initAddFriendTab();

            // Load vào center (chatContainer) thay vì tạo Scene mới - giữ sidebar bên trái
            chatContainer.getChildren().clear();
            chatContainer.getChildren().add(root);
            VBox.setVgrow(root, Priority.ALWAYS);

            // Ẩn input khi xem friend requests
            if (chatInputRoot != null) {
                chatInputRoot.setVisible(false);
                chatInputRoot.setManaged(false);
            }

            logger.info("Opened Friend Requests view");
        } catch (Exception e) {
            logger.error("Error opening friend requests view", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setContentText("Không thể mở màn hình bạn bè: " + e.getMessage());
            alert.show();
        }
    }

    private void onChatItemSelected() {
        if (chatListManager != null && chatListManager.isRefreshing()) {
            return;
        }
        ChatItem selected = chatList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        loadSubControllers();
        ensureMessageViewInContainer();

        if (messageListController == null) {
            System.err.println("Lỗi: messageListController chưa được khởi tạo.");
            return;
        }

        isWelcomeMode = false;
        if (chatInputRoot != null) {
            chatInputRoot.setVisible(true);
            chatInputRoot.setManaged(true);
        }

        logger.info("Loading chat for: {}", selected.getDisplayName());
        reloadChatForItem(selected);
    }

    public void reloadChatForItem(ChatItem item) {
        if (item == null)
            return;

        logger.info("Reloading chat for: {}", item.getDisplayName());
        loadSubControllers();
        ensureMessageViewInContainer();

        if (messageListController == null)
            return;

        isWelcomeMode = false;
        if (chatInputRoot != null) {
            chatInputRoot.setVisible(true);
            chatInputRoot.setManaged(true);
        }

        // Store current chat item for callback handler
        currentChatItem = item;

        if (item.isGroup()) {
            GroupInfo group = item.getGroup();
            currentGroupId = group.getId();
            currentFriendId = -1;
            if (chatController != null)
                chatController.setCurrentGroup(currentGroupId);

            // OPTIMIZATION: Show cached messages immediately
            int chatId = -currentGroupId;
            List<Message> cachedMessages = org.example.zalu.client.ClientCache.getInstance().getMessages(chatId);
            if (!cachedMessages.isEmpty()) {
                logger.info("Using cached messages for group {}: {} messages", currentGroupId, cachedMessages.size());
                messageListController.showChatWithGroup(group, cachedMessages, currentUserId);
            }

            // Request from server
            ChatClient.sendRequest("GET_GROUP_CONVERSATION|" + currentUserId + "|" + currentGroupId);
        } else {
            User friend = item.getUser();
            currentFriendId = friend.getId();
            currentGroupId = -1;
            if (chatController != null)
                chatController.setCurrentFriend(friend);
            messageListController.setCurrentFriend(friend);

            // OPTIMIZATION: Show cached messages immediately
            int chatId = currentFriendId;
            List<Message> cachedMessages = org.example.zalu.client.ClientCache.getInstance().getMessages(chatId);
            if (!cachedMessages.isEmpty()) {
                logger.info("Using cached messages for friend {}: {} messages", currentFriendId, cachedMessages.size());
                messageListController.showChatWithFriend(friend, cachedMessages);
            }

            // Request from server
            ChatClient.sendRequest("GET_CONVERSATION|" + currentUserId + "|" + currentFriendId);
        }
    }

    /**
     * Centralized handler for GET_MESSAGES callback
     * Called when server sends conversation history
     */
    private void handleGetMessagesResponse(Integer chatId, List<Message> messages) {
        Platform.runLater(() -> {
            if (messages == null || currentChatItem == null)
                return;

            // Sort messages by time
            messages.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));

            // Xác định chat hiện tại có khớp với messages nhận được không
            boolean isMatch = false;
            if (chatId != null && chatId != 0) {
                // Ưu tiên dùng chatId logic từ echo context
                if (currentGroupId > 0 && chatId == -currentGroupId)
                    isMatch = true;
                else if (currentFriendId > 0 && chatId == currentFriendId)
                    isMatch = true;
            } else {
                // Fallback logic cũ nếu chatId == 0 (batch login)
                if (currentGroupId > 0) {
                    if (messages.isEmpty() || messages.get(0).getGroupId() == currentGroupId) {
                        isMatch = true;
                    }
                } else if (currentFriendId > 0) {
                    if (!messages.isEmpty()) {
                        Message m = messages.get(0);
                        if ((m.getSenderId() == currentFriendId || m.getReceiverId() == currentFriendId)
                                && m.getGroupId() == 0) {
                            isMatch = true;
                        }
                    }
                }
            }

            if (isMatch) {
                if (currentGroupId > 0 && currentChatItem.isGroup()) {
                    messageListController.showChatWithGroup(currentChatItem.getGroup(), messages, currentUserId);
                } else if (currentFriendId > 0 && !currentChatItem.isGroup()) {
                    messageListController.showChatWithFriend(currentChatItem.getUser(), messages);
                }
            }

            updateLastMessages();
            if (chatListManager != null) {
                chatListManager.sortChatListByLastMessage(currentFriendId, currentGroupId);
            }
        });
    }

    private void onMessagesReceived(List<Message> messages) {
        if (messages == null || messages.isEmpty())
            return;
        logger.info("onMessagesReceived: {} messages", messages.size());

        for (Message msg : messages) {
            if (messageUpdateService != null) {
                int chatId = msg.getGroupId() > 0 ? msg.getGroupId()
                        : (msg.getSenderId() == currentUserId ? msg.getReceiverId() : msg.getSenderId());
                String preview = formatMessagePreview(msg);
                messageUpdateService.updateLastMessage(chatId, preview, msg.getCreatedAt());
            }
        }

        Platform.runLater(() -> {
            if (messageListController == null)
                loadSubControllers();

            ChatItem selected = chatList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                // ✨ THAY ĐỔI: Thêm TẤT CẢ tin nhắn, bao gồm cả tin nhắn của chính mình
                // MessageListController sẽ xử lý optimistic UI resolution
                messageListController.addNewMessages(messages);
            }

            updateLastMessages();

            if (chatListManager != null) {
                Map<Integer, Integer> counts = chatListManager.getUnreadCounts();
                for (Message msg : messages) {
                    if (msg.getSenderId() == currentUserId)
                        continue; // Tin nhắn mình gửi không tăng unread

                    int chatId = 0;
                    if (msg.getGroupId() > 0) {
                        chatId = -msg.getGroupId();
                        // Nếu đang chat với group này thì không tăng
                        if (currentGroupId == msg.getGroupId())
                            continue;
                    } else {
                        chatId = msg.getSenderId();
                        // Nếu đang chat với friend này thì không tăng
                        if (currentFriendId == chatId)
                            continue;
                    }

                    // Increment local count
                    counts.put(chatId, counts.getOrDefault(chatId, 0) + 1);
                }
                chatList.refresh();
                chatListManager.sortChatListByLastMessage(currentFriendId, currentGroupId);
            }
        });

    }

    public void updateUnreadCountForFriend(int friendId, int unreadCount) {
        if (chatListManager != null) {
            chatListManager.getUnreadCounts().put(friendId, unreadCount);
            chatList.refresh();
        }
    }

    public void updateUnreadCountForGroup(int groupId, int unreadCount) {
        if (chatListManager != null) {
            chatListManager.getUnreadCounts().put(-groupId, unreadCount);
            chatList.refresh();
        }
    }

    private String formatMessagePreview(Message msg) {
        if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
            return msg.getContent();
        } else if (msg.getFileName() != null) {
            return "[File: " + msg.getFileName() + "]";
        }
        return "Bắt đầu trò chuyện...";
    }

    public void onLoginSuccess(int userId) {
        currentUserId = userId;
        refreshFriendList();
        if (messageListController != null) {
            messageListController.setCurrentUserId(userId);
        }
    }

    // Callback khi nhận MESSAGE_SENT|OK hoặc GROUP_MESSAGE_SENT|OK từ server
    private void onMessageSentResponse(String message) {
        if (message != null && (message.startsWith("MESSAGE_SENT|OK") || message.startsWith("GROUP_MESSAGE_SENT|OK"))) {
            logger.info("Received {}, message confirmed by server",
                    (message.startsWith("MESSAGE_SENT|OK") ? "MESSAGE_SENT|OK" : "GROUP_MESSAGE_SENT|OK"));
            // Optimistic UI đã hiển thị tin nhắn, không cần reload
            // Tin nhắn sẽ được update qua handleMessageSent callback
        }
    }

    private void onBroadcastMessage(String message) {
        if (message == null)
            return;

        if (message.startsWith("MESSAGES_READ|")) {
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int readerId = Integer.parseInt(parts[1]);
                    logger.info("MainController: Messages read by user {}", readerId);
                    if (messageListController != null) {
                        messageListController.updateReadStatus(readerId);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing readerId from MESSAGES_READ: {}", message);
                }
            }
        } else if (message.startsWith("USER_ONLINE|")) {
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int onlineUserId = Integer.parseInt(parts[1]);
                    if (chatListManager != null) {
                        chatListManager.getOnlineFriends().put(onlineUserId, true);
                    }
                    Platform.runLater(() -> {
                        chatList.refresh();
                        updateChatHeaderStatus();
                    });
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing userId from USER_ONLINE: {}", message);
                }
            }
        } else if (message.startsWith("USER_OFFLINE|")) {
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int offlineUserId = Integer.parseInt(parts[1]);
                    if (chatListManager != null) {
                        chatListManager.getOnlineFriends().put(offlineUserId, false);
                    }
                    Platform.runLater(() -> {
                        chatList.refresh();
                        updateChatHeaderStatus();
                    });
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing userId from USER_OFFLINE: {}", message);
                }
            }
        } else if (message.startsWith("USER_PROFILE_UPDATED|")) {
            // User đã cập nhật profile (avatar, name, etc.)
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int updatedUserId = Integer.parseInt(parts[1]);
                    logger.info("User {} updated profile, refreshing avatar and info", updatedUserId);

                    // Clear avatar cache để reload avatar mới
                    org.example.zalu.client.ClientCache.getInstance().clearAvatarCache(updatedUserId);

                    // Refresh chat list để reload avatar
                    Platform.runLater(() -> {
                        chatList.refresh();
                        // Nếu đang chat với user này, cập nhật header
                        if (currentFriendId == updatedUserId) {
                            updateChatHeaderStatus();
                        }
                    });
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing userId from USER_PROFILE_UPDATED: {}", message);
                }
            }
        } else if (message.startsWith("TYPING_INDICATOR|")) {
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int typingUserId = Integer.parseInt(parts[1]);
                    if (messageListController != null) {
                        messageListController.showTypingIndicator(typingUserId);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing userId from TYPING_INDICATOR: {}", message);
                }
            }
        } else if (message.startsWith("TYPING_STOP|")) {
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int typingUserId = Integer.parseInt(parts[1]);
                    if (messageListController != null) {
                        messageListController.hideTypingIndicator(typingUserId);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing userId from TYPING_STOP: {}", message);
                }
            }
        } else if (message.startsWith("MESSAGE_DELETED|")) {
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int messageId = Integer.parseInt(parts[1]);
                    if (messageListController != null) {
                        messageListController.handleMessageDeleted(messageId);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing messageId from MESSAGE_DELETED: {}", message);
                }
            }
        } else if (message.startsWith("MESSAGE_RECALLED|")) {
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int messageId = Integer.parseInt(parts[1]);
                    if (messageListController != null) {
                        messageListController.handleMessageRecalled(messageId);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing messageId from MESSAGE_RECALLED: {}", message);
                }
            }
        } else if (message.startsWith("MESSAGE_EDITED|")) {
            String[] parts = message.split("\\|", 3);
            if (parts.length >= 3) {
                try {
                    int messageId = Integer.parseInt(parts[1]);
                    String newContent = parts[2];
                    if (messageListController != null) {
                        messageListController.handleMessageEdited(messageId, newContent);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing messageId from MESSAGE_EDITED: {}", message);
                }
            }
        } else if (message.startsWith("FRIEND_ACCEPTED|")) {
            // Khi có người chấp nhận kết bạn, refresh friend list
            String[] parts = message.split("\\|");
            if (parts.length >= 3) {
                try {
                    int accepterId = Integer.parseInt(parts[1]);
                    int requesterId = Integer.parseInt(parts[2]);

                    // Nếu là người gửi yêu cầu hoặc người chấp nhận, refresh friend list
                    if (currentUserId == accepterId || currentUserId == requesterId) {
                        logger.info("Friend request accepted: accepter={}, requester={}, refreshing friend list",
                                accepterId, requesterId);
                        Platform.runLater(() -> {
                            // Đợi một chút để server cập nhật xong
                            new Thread(() -> {
                                try {
                                    Thread.sleep(300); // Đợi 300ms
                                    Platform.runLater(() -> {
                                        refreshFriendList();
                                        logger.info("✓ Friend list refreshed after FRIEND_ACCEPTED broadcast");
                                    });
                                } catch (InterruptedException ex) {
                                    logger.warn("Interrupted while waiting to refresh friend list", ex);
                                }
                            }).start();
                        });
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing userIds from FRIEND_ACCEPTED: {}", message);
                }
            }
        }
    }

    public boolean isFriendOnline(int friendId) {
        return chatListManager != null && chatListManager.getOnlineFriends().getOrDefault(friendId, false);
    }

    public void loadSubControllers() {
        // Load controllers nếu chưa có
        if (messageListController == null || chatController == null) {
            try {
                // Load phần tin nhắn
                FXMLLoader messageLoader = new FXMLLoader(
                        getClass().getResource("/org/example/zalu/views/chat/message-list-view.fxml"));
                Parent messageRoot = messageLoader.load();
                messageListController = messageLoader.getController();
                messageListController.setCurrentUserId(currentUserId);
                messageListController.setMainController(this);

                // Load input
                FXMLLoader chatLoader = new FXMLLoader(
                        getClass().getResource("/org/example/zalu/views/chat/chat-input-view.fxml"));
                chatInputRoot = chatLoader.load();
                chatController = chatLoader.getController();
                chatController.setCurrentUserId(currentUserId);
                chatController.setMessageListController(messageListController);
                chatController.setStage(stage);

                logger.info("Sub-controllers loaded for the first time");
            } catch (IOException e) {
                logger.error("Error loading sub-controllers", e);
            }
        }

        // QUAN TRỌNG: Luôn đảm bảo message view được load vào chatContainer (kể cả khi
        // controller đã tồn tại)
        // Điều này đảm bảo khi click vào chat item, message view sẽ hiển thị thay vì
        // welcome view
        // Kiểm tra xem chatContainer có đang chứa message view không
        boolean hasMessageView = false;
        for (javafx.scene.Node node : chatContainer.getChildren()) {
            if (node.getId() != null && node.getId().equals("messageListView")) {
                hasMessageView = true;
                break;
            }
            // Hoặc kiểm tra bằng controller reference
            if (messageListController != null && node.getUserData() == messageListController) {
                hasMessageView = true;
                break;
            }
        }

        // Nếu không có message view trong container, load lại
        // DISABLED: This causes optimistic messages to be lost
        /*
         * if (!hasMessageView && messageListController != null) {
         * logger.warn("Message view not in container, reloading...");
         * // Tìm Parent của message view (có thể cần load lại)
         * FXMLLoader messageLoader = new FXMLLoader(
         * getClass().getResource("/org/example/zalu/views/chat/message-list-view.fxml")
         * );
         * Parent messageRoot = messageLoader.load();
         * MessageListController controller = messageLoader.getController();
         * controller.setCurrentUserId(currentUserId);
         * controller.setMainController(this);
         * 
         * // Cập nhật reference
         * messageListController = controller;
         * 
         * // Load input nếu chưa có
         * if (chatInputRoot == null) {
         * FXMLLoader chatLoader = new FXMLLoader(
         * getClass().getResource("/org/example/zalu/views/chat/chat-input-view.fxml"));
         * chatInputRoot = chatLoader.load();
         * chatController = chatLoader.getController();
         * chatController.setCurrentUserId(currentUserId);
         * chatController.setMessageListController(messageListController);
         * chatController.setStage(stage);
         * }
         * 
         * // Clear container và add message view
         * chatContainer.getChildren().clear();
         * chatContainer.getChildren().add(messageRoot);
         * VBox.setVgrow(messageRoot, Priority.ALWAYS);
         * 
         * // Add chat input
         * if (chatInputRoot != null) {
         * chatContainer.getChildren().add(chatInputRoot);
         * }
         * 
         * logger.info("Message view reloaded into chatContainer");
         * }
         */

        // Ẩn/hiện input dựa trên welcome mode
        if (chatInputRoot != null) {
            if (isWelcomeMode) {
                chatInputRoot.setVisible(false);
                chatInputRoot.setManaged(false);
            } else {
                chatInputRoot.setVisible(true);
                chatInputRoot.setManaged(true);
            }
        }
        // BỎ ĐIỆU KIỆN RETURN Ở ĐÂY → CHO PHÉP LOAD TIN NHẮN MỖI KHI CHỌN BẠN!!!
    }

    private void ensureMessageViewInContainer() {
        if (messageListController == null || chatContainer == null)
            return;
        try {
            boolean hasWelcomeView = chatContainer.getChildren().stream()
                    .anyMatch(node -> node.getStyleClass().contains("welcome-root"));
            if (hasWelcomeView || chatContainer.getChildren().isEmpty()) {
                FXMLLoader messageLoader = new FXMLLoader(
                        getClass().getResource("/org/example/zalu/views/chat/message-list-view.fxml"));
                Parent messageRoot = messageLoader.load();
                messageListController = messageLoader.getController();
                messageListController.setCurrentUserId(currentUserId);
                messageListController.setMainController(this);
                if (chatInputRoot == null) {
                    FXMLLoader chatLoader = new FXMLLoader(
                            getClass().getResource("/org/example/zalu/views/chat/chat-input-view.fxml"));
                    chatInputRoot = chatLoader.load();
                    chatController = chatLoader.getController();
                    chatController.setCurrentUserId(currentUserId);
                    chatController.setMessageListController(messageListController);
                    chatController.setStage(stage);
                }
                chatContainer.getChildren().clear();
                chatContainer.getChildren().add(messageRoot);
                VBox.setVgrow(messageRoot, Priority.ALWAYS);
                if (chatInputRoot != null)
                    chatContainer.getChildren().add(chatInputRoot);
            }
        } catch (IOException e) {
            logger.error("Error ensuring message view in container", e);
        }
    }

    public void setWelcomeUsername(String username) {
        this.welcomeUsername = username;
    }

    public void showWelcomeInMessageArea() {
        logger.debug("showWelcomeInMessageArea() called, loading welcome-view.fxml");

        // QUAN TRỌNG: Clear chatContainer trước để loại bỏ friend-request-view hoặc bất
        // kỳ view nào khác
        chatContainer.getChildren().clear();

        try {
            // Load welcome-view.fxml vào chatContainer
            FXMLLoader welcomeLoader = new FXMLLoader(
                    getClass().getResource("/org/example/zalu/views/chat/welcome-view.fxml"));
            Parent welcomeRoot = welcomeLoader.load();
            WelcomeController welcomeController = welcomeLoader.getController();

            // Set user info
            String nameToShow = (welcomeUsername != null && !welcomeUsername.isBlank()) ? welcomeUsername : "user";
            welcomeController.setUserInfo(nameToShow, currentUserId);
            welcomeController.setStage(stage);
            // Đặt flag embedded để ẩn button "Vào ứng dụng chính"
            welcomeController.setEmbedded(true);

            // Add welcome view vào container
            chatContainer.getChildren().add(welcomeRoot);
            VBox.setVgrow(welcomeRoot, Priority.ALWAYS);

            isWelcomeMode = true;

            // Ẩn chat input khi ở welcome mode
            if (chatInputRoot != null) {
                chatInputRoot.setVisible(false);
                chatInputRoot.setManaged(false);
            }

            logger.info("Welcome view (welcome-view.fxml) displayed successfully");
        } catch (Exception e) {
            logger.error("Error loading welcome view", e);

            // Fallback: hiển thị placeholder nếu không load được welcome view
            StackPane placeholder = new StackPane();
            placeholder.setStyle(
                    "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #f8f9fa 0%, #f5f6f6 50%, #f0f2f5 100%);");
            Label placeholderText = new Label("Chọn một cuộc trò chuyện để bắt đầu nhắn tin 💬");
            placeholderText.getStyleClass().add("placeholder-text");
            placeholder.getChildren().add(placeholderText);
            chatContainer.getChildren().add(placeholder);
            VBox.setVgrow(placeholder, Priority.ALWAYS);

            // Ẩn input
            if (chatInputRoot != null) {
                chatInputRoot.setVisible(false);
                chatInputRoot.setManaged(false);
            }
        }
    }

    private void setupNavAvatarClip() {
        if (navAvatarImage != null) {
            Circle clip = new Circle(28);
            clip.centerXProperty().bind(navAvatarImage.fitWidthProperty().divide(2));
            clip.centerYProperty().bind(navAvatarImage.fitHeightProperty().divide(2));
            clip.radiusProperty().bind(navAvatarImage.fitWidthProperty().divide(2));
            navAvatarImage.setClip(clip);
            navAvatarImage.setCursor(Cursor.HAND);
        }
    }

    private void setupNavAccountMenu() {
        if (navigationManager == null)
            return;
        navigationManager.setupMenu(
                this::openProfileView,
                this::logout,
                this::handleOpenSettings,
                this::handleUpgradeAccount);
    }

    private void fetchCurrentUserProfile() {
        if (currentUserId <= 0)
            return;
        ChatEventManager.getInstance().registerGetUserByIdCallback(users -> {
            if (users != null && !users.isEmpty()) {
                for (org.example.zalu.model.User u : users) {
                    if (u.getId() == currentUserId) {
                        this.currentUser = u;
                        if (navigationManager != null) {
                            navigationManager.updateUserInfo(u);
                        }
                        return;
                    }
                }
            }
        });
        ChatClient.sendRequest("GET_USER_BY_ID|" + currentUserId);
    }

    private void handleUpgradeAccount() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText("Nâng cấp tài khoản");
        alert.setContentText("Tính năng nâng cấp tài khoản sẽ được ra mắt sớm!");
        alert.showAndWait();
    }

    private void handleOpenSettings() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText("Cài đặt");
        alert.setContentText("Trang cài đặt đang được xây dựng.");
        alert.showAndWait();
    }

    public void onMessagesLoaded() {
        logger.info("Messages loading completed for friend {}", currentFriendId);
    }

    private void showProfileDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/zalu/views/profile/bio-view.fxml"));
            Parent root = loader.load();
            org.example.zalu.controller.profile.BioViewController bioCtrl = loader.getController();

            Stage profileStage = new Stage();
            bioCtrl.setStage(profileStage);
            bioCtrl.setCurrentUserId(currentUserId);

            if (currentUser != null) {
                bioCtrl.setUser(currentUser);
            } else {
                // Fallback if not cached yet
                fetchCurrentUserProfile(); // triggers update eventually
                // But for immediate show, we might want to wait?
                // For now, let's just set ID which might fail on client if userDAO used,
                // but at least we try. Or better, we register a one-time callback here?
                // To avoid complexity, let's just use what we have.
                // If currentUser is null, we can try to set ID but it will likely fail on
                // client.
                // Better to trigger fetch and wait?
                // Let's rely on fetchCurrentUserProfile being called earlier.
                // If not, we trigger it.
                // bioCtrl.setUserId(currentUserId); // Avoid this if possible on client
            }

            // Register a temporary callback to update the dialog if data arrives?
            // Actually, let's just send a fresh request for the dialog specifically if
            // needed.
            // Simplified:
            if (currentUser == null) {
                ChatEventManager.getInstance().registerGetUserByIdCallback(users -> {
                    if (users != null && !users.isEmpty()) {
                        for (org.example.zalu.model.User u : users) {
                            if (u.getId() == currentUserId) {
                                this.currentUser = u;
                                Platform.runLater(() -> bioCtrl.setUser(u));
                                if (navigationManager != null)
                                    navigationManager.updateUserInfo(u);
                            }
                        }
                    }
                });
                ChatClient.sendRequest("GET_USER_BY_ID|" + currentUserId);
            }

            // Register callback to refresh MainController when profile is updated in
            // BioViewController
            bioCtrl.setOnProfileUpdateCallback(this::fetchCurrentUserProfile);

            profileStage.setScene(new Scene(root, 650, 700));
            profileStage.setTitle("Hồ sơ của tôi");
            profileStage.show();
        } catch (IOException e) {
            logger.error("Error showing profile dialog", e);
        }
    }

    public int getCurrentUserId() {
        return currentUserId;
    }

    public MessageListController getMessageListController() {
        return messageListController;
    }

    public String getWelcomeUsername() {
        return welcomeUsername;
    }

    public boolean isWelcomeMode() {
        return isWelcomeMode;
    }

    public void updateChatHeaderStatus() {
        if (messageListController != null && currentFriendId > 0) {
            boolean isOnline = chatListManager != null
                    ? chatListManager.getOnlineFriends().getOrDefault(currentFriendId, false)
                    : false;
            messageListController.updateFriendStatus(isOnline);
        }
    }

    public void updateLastMessages() {
        if (chatList == null || chatList.getItems().isEmpty() || messageUpdateService == null) {
            return;
        }
        messageUpdateService.updateLastMessages(chatList.getItems(), currentUserId);
        chatList.refresh();
    }

}
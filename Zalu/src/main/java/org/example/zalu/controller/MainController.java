package org.example.zalu.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.scene.Cursor;
import org.example.zalu.client.ChatClient;
import org.example.zalu.client.ChatEventManager;
import org.example.zalu.controller.auth.LoginController;
import org.example.zalu.controller.chat.ChatController;
import org.example.zalu.controller.chat.MessageListController;
import org.example.zalu.controller.chat.WelcomeController;
import org.example.zalu.controller.friend.FriendRequestController;
import org.example.zalu.controller.group.CreateGroupController;
import org.example.zalu.controller.profile.ProfileController;
import org.example.zalu.dao.FriendDAO;
import org.example.zalu.dao.MessageDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.Message;
import org.example.zalu.model.User;
import org.example.zalu.model.GroupInfo;
import org.example.zalu.model.ChatItem;
import org.example.zalu.dao.GroupDAO;
import org.example.zalu.util.LogoutHandler;
import org.example.zalu.util.AppConstants;
import org.example.zalu.service.AvatarService;
import org.example.zalu.service.MessageUpdateService;
import org.example.zalu.service.NavigationManager;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainController {
    @FXML
    private ListView<ChatItem> chatList;

    @FXML
    private VBox chatContainer;
    @FXML
    private ImageView navAvatarImage;
    private ChatController chatController;
    private MessageListController messageListController;
    private Parent chatInputRoot;

    private Stage stage;
    private int currentUserId = -1;
    private int currentFriendId = -1;
    private int currentGroupId = -1;
    private final List<User> pendingUsers = new ArrayList<>();
    private boolean waitingForRequests = false;
    private boolean dataLoaded = false;
    private boolean listenerStarted = false;
    private Map<Integer, Boolean> onlineFriends = new HashMap<>();
    private LogoutHandler logoutHandler;
    private String welcomeUsername;
    private boolean isWelcomeMode = true;
    private boolean isLoadingMessages = false;
    private boolean isRefreshing = false; // Flag để tránh trigger selection khi refresh
    private boolean hasReceivedFriends = false; // Flag để phân biệt friends và online users
    private Map<Integer, Integer> unreadCounts = new HashMap<>(); // Lưu số tin nhắn chưa đọc cho mỗi conversation
    
    // Services
    private MessageUpdateService messageUpdateService;
    private NavigationManager navigationManager;
    
    // Lưu kích thước Stage trước khi chuyển view
    private double savedStageWidth = 1200;
    private double savedStageHeight = 750;
    

    private FriendDAO friendDAO;
    private MessageDAO messageDAO;
    private UserDAO userDAO;
    private GroupDAO groupDAO;

    @FXML
    public void initialize() {
        initData();
        setupNavAvatarClip();
        setupNavAccountMenu();
        chatList.setCellFactory(createChatItemCellFactory());
        chatList.getSelectionModel().selectedItemProperty().addListener((obs, old, newSelected) -> {
            // Chỉ bỏ qua nếu đang refresh và chưa có selection nào (initial load)
            // Nếu user click vào item thì luôn cho phép chọn
            if (isRefreshing && isWelcomeMode && old == null && newSelected == null) {
                System.out.println("Skipping selection during initial refresh");
                return;
            }
            
            if (newSelected != null) {
                System.out.println("Selection changed to: " + newSelected.getDisplayName() + " (isRefreshing: " + isRefreshing + ", isWelcomeMode: " + isWelcomeMode + ")");
                // Đảm bảo flag được reset trước khi xử lý selection
                isRefreshing = false;
                onChatItemSelected();
            }
        });

        if (currentUserId > 0) {
            refreshFriendList();
        }

        if (!listenerStarted) {
            ChatEventManager.getInstance().registerFriendsCallback(this::onFriendsUpdated);
            ChatEventManager.getInstance().registerMessagesCallback(this::onMessagesReceived);
            ChatEventManager.getInstance().registerGroupsCallback(this::onGroupsUpdated);
            ChatEventManager.getInstance().registerOnlineUsersCallback(this::onOnlineUsersReceived);
            // Đăng ký callback để reload tin nhắn sau khi gửi thành công
            ChatEventManager.getInstance().registerErrorCallback(this::onMessageSentResponse);
            // Đăng ký callback để xử lý read receipts và status updates
            ChatEventManager.getInstance().registerBroadcastCallback(this::onBroadcastMessage);
            listenerStarted = true;
        }

        URL addUrl = getClass().getResource("/org/example/zalu/views/friend/add-friend-tab.fxml");
        System.out.println("Add-friend-tab URL: " + addUrl);  // Nếu null → file missing

        loadSubControllers();
        showWelcomeInMessageArea();
        updateNavAvatar();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        System.out.println("MainController stage set");
        if (chatController != null) chatController.setStage(stage);
        // Lưu kích thước hiện tại
        if (stage != null) {
            savedStageWidth = stage.getWidth() > 0 ? stage.getWidth() : AppConstants.MAIN_WIDTH;
            savedStageHeight = stage.getHeight() > 0 ? stage.getHeight() : AppConstants.MAIN_HEIGHT;
        }
    }
    
    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        System.out.println("MainController currentUserId set to: " + userId);
        if (messageListController != null) messageListController.setCurrentUserId(userId);
        if (chatController != null) chatController.setCurrentUserId(userId);
        if (userId > 0) refreshFriendList();
        if (navigationManager != null) {
            navigationManager.setCurrentUserId(userId);
            navigationManager.updateAvatar(userId);
        }
    }

    @FXML
    public void logout() {
        System.out.println("Logout triggered for userId: " + currentUserId);
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Xác nhận đăng xuất");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Bạn có chắc chắn muốn đăng xuất?");
        if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            ChatClient.disconnect();
            ChatEventManager.getInstance().unregisterAllCallbacks();
            resetDataFlags();
            switchToLogin();
            System.out.println("Logout successful");
        }
    }

    private void switchToLogin() {
        try {
            // Lưu kích thước hiện tại trước khi chuyển
            if (stage != null) {
                savedStageWidth = stage.getWidth();
                savedStageHeight = stage.getHeight();
            }
            
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
            stage.setResizable(false);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error switching to login: " + e.getMessage());
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

    private void initData() {
        friendDAO = new FriendDAO();
        messageDAO = new MessageDAO();
        userDAO = new UserDAO();
        groupDAO = new GroupDAO();
        messageUpdateService = new MessageUpdateService(messageDAO);
        navigationManager = new NavigationManager(navAvatarImage, userDAO);
        System.out.println("DAOs initialized with HikariCP pool");
    }

    private Callback<ListView<ChatItem>, ListCell<ChatItem>> createChatItemCellFactory() {
        MainController mainControllerRef = this; // Reference để access từ ListCell
        return param -> new ListCell<ChatItem>() {
            private HBox itemBox;
            private Circle avatar;
            private Circle statusDot;
            private Label nameLabel;
            private Label previewLabel;
            private StackPane badgeContainer;
            private Label badgeLabel;

            {
                // Tạo container cho list item với styling đẹp hơn
                itemBox = new HBox(14);
                itemBox.setPadding(new Insets(14, 16, 14, 16));
                itemBox.setAlignment(Pos.CENTER_LEFT);
                itemBox.getStyleClass().add("chat-list-item");

                // Avatar với shadow
                StackPane avatarContainer = new StackPane();
                avatar = new Circle(26, Color.web("#0088ff"));
                avatar.setStroke(Color.WHITE);
                avatar.setStrokeWidth(2);
                
                statusDot = new Circle(8, Color.web("#31d559"));
                statusDot.setStroke(Color.WHITE);
                statusDot.setStrokeWidth(2.5);
                statusDot.setTranslateX(18);
                statusDot.setTranslateY(18);
                
                avatarContainer.getChildren().addAll(avatar, statusDot);

                // Info box với spacing tốt hơn
                VBox infoBox = new VBox(5);
                infoBox.setAlignment(Pos.CENTER_LEFT);
                
                nameLabel = new Label();
                nameLabel.getStyleClass().add("chat-list-name");
                nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #1c1e21;");
                
                previewLabel = new Label();
                previewLabel.getStyleClass().add("chat-list-preview");
                previewLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #8e8e93; -fx-font-weight: 400;");
                previewLabel.setMaxWidth(220);
                previewLabel.setWrapText(false);
                previewLabel.setEllipsisString("...");
                
                infoBox.getChildren().addAll(nameLabel, previewLabel);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                // Badge container với styling đẹp hơn
                badgeContainer = new StackPane();
                badgeContainer.setVisible(false);
                badgeLabel = new Label();
                badgeLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: white;");
                badgeContainer.getChildren().add(badgeLabel);
                badgeContainer.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #ff4444 0%, #e63950 100%); -fx-background-radius: 12; -fx-padding: 3 8; -fx-min-width: 22; -fx-pref-height: 22; -fx-effect: dropshadow(gaussian, rgba(231,57,80,0.4), 4, 0, 0, 2);");
                
                VBox rightBox = new VBox();
                rightBox.setAlignment(Pos.CENTER);
                rightBox.getChildren().addAll(badgeContainer);

                itemBox.getChildren().addAll(avatarContainer, infoBox, spacer, rightBox);
            }

            @Override
            protected void updateItem(ChatItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);
                    applyAvatarToCircle(avatar, item);
                    if (item.isGroup()) {
                        // Hiển thị nhóm
                        GroupInfo group = item.getGroup();
                        nameLabel.setText("👥 " + group.getName());
                        previewLabel.setText(group.getMemberCount() + " thành viên");
                        statusDot.setVisible(false);  // Nhóm không có status dot
                        
                        // Hiển thị badge số tin nhắn chưa đọc cho nhóm
                        int unreadCount = unreadCounts.getOrDefault(-group.getId(), 0);
                        if (unreadCount > 0) {
                            badgeContainer.setVisible(true);
                            String badgeText = unreadCount > 5 ? "5++" : String.valueOf(unreadCount);
                            badgeLabel.setText(badgeText);
                        } else {
                            badgeContainer.setVisible(false);
                        }
                    } else {
                        // Hiển thị bạn bè
                        User user = item.getUser();
                        String displayName = (user.getFullName() != null && !user.getFullName().trim().isEmpty()) 
                                ? user.getFullName() 
                                : user.getUsername();
                        nameLabel.setText(displayName);
                        
                        String preview = messageUpdateService.getLastMessage(user.getId());
                        if (preview.length() > 35) {
                            preview = preview.substring(0, 32) + "...";
                        }
                        previewLabel.setText(preview);
                        
                        boolean isOnline = onlineFriends.getOrDefault(user.getId(), false);
                        statusDot.setFill(isOnline ? Color.web("#31d559") : Color.web("#8e8e93"));
                        statusDot.setVisible(true);
                        
                        // Hiển thị badge số tin nhắn chưa đọc
                        int unreadCount = unreadCounts.getOrDefault(user.getId(), 0);
                        if (unreadCount > 0) {
                            badgeContainer.setVisible(true);
                            String badgeText = unreadCount > 5 ? "5++" : String.valueOf(unreadCount);
                            badgeLabel.setText(badgeText);
                        } else {
                            badgeContainer.setVisible(false);
                        }
                    }

                    setGraphic(itemBox);

                    // Apply CSS classes thay vì inline style
                    itemBox.getStyleClass().clear();
                    itemBox.getStyleClass().add("chat-list-item");
                    
                    // Selected state
                    if (getListView() != null && getListView().getSelectionModel().getSelectedItem() == item) {
                        itemBox.getStyleClass().add("selected");
                    }
                    
                    // QUAN TRỌNG: Thêm mouse click handler trên cả itemBox và ListCell để force reload
                    javafx.event.EventHandler<javafx.scene.input.MouseEvent> mouseClickHandler = e -> {
                        System.out.println("Mouse clicked on: " + (item != null ? item.getDisplayName() : "null"));
                        if (e.getClickCount() == 1 && getListView() != null && item != null) {
                            ChatItem currentSelected = getListView().getSelectionModel().getSelectedItem();
                            
                            // Check bằng ID thay vì equals() để đảm bảo chính xác
                            boolean isSameItem = false;
                            if (currentSelected != null) {
                                if (item.isGroup() && currentSelected.isGroup()) {
                                    isSameItem = (item.getGroup().getId() == currentSelected.getGroup().getId());
                                } else if (!item.isGroup() && !currentSelected.isGroup()) {
                                    isSameItem = (item.getUser().getId() == currentSelected.getUser().getId());
                                }
                            }
                            
                            System.out.println("Current selected: " + (currentSelected != null ? currentSelected.getDisplayName() : "null") + 
                                             ", Clicked item: " + item.getDisplayName() + 
                                             ", isSameItem: " + isSameItem);
                            
                            // Nếu click vào item đã được chọn, force reload trực tiếp
                            if (isSameItem) {
                                System.out.println("✓ Force reload: Click vào item đã selected - " + item.getDisplayName());
                                e.consume(); // Ngăn selection change default behavior
                                // Gọi trực tiếp reloadChatForItem để reload dữ liệu
                                javafx.application.Platform.runLater(() -> {
                                    mainControllerRef.reloadChatForItem(item);
                                });
                            } else {
                                // Nếu chưa được chọn, select bình thường (listener sẽ tự động trigger)
                                getListView().getSelectionModel().select(item);
                            }
                        }
                    };
                    
                    // Đặt handler trên cả itemBox và ListCell để đảm bảo luôn bắt được click
                    itemBox.setOnMouseClicked(mouseClickHandler);
                    setOnMouseClicked(mouseClickHandler);
                }
            }
        };
    }
    
    private void applyAvatarToCircle(Circle circle, ChatItem item) {
        if (item.isGroup()) {
            circle.setFill(Color.web("#4b7be5"));
            return;
        }
        User user = item.getUser();
        javafx.scene.image.Image avatarImage = AvatarService.resolveAvatar(user);
        if (avatarImage != null) {
            circle.setFill(new ImagePattern(avatarImage));
        } else {
            circle.setFill(Color.web("#0088ff"));
        }
    }

    public void refreshFriendList() {
        System.out.println("Refreshing friend list for userId: " + currentUserId);
        if (currentUserId <= 0 || friendDAO == null) {
            System.out.println("Skipping refresh: Invalid userId or DAO null");
            return;
        }
        try {
            List<Integer> friendIds = friendDAO.getFriendsByUserId(currentUserId);
            List<ChatItem> chatItems = new ArrayList<>();
            
            // Thêm bạn bè
            for (int friendId : friendIds) {
                User friend = userDAO.getUserById(friendId);
                if (friend != null) {
                    chatItems.add(new ChatItem(friend));
                    // Status sẽ được cập nhật từ online users list hoặc broadcast messages
                    // Chỉ set default false nếu chưa có trong map
                    if (!onlineFriends.containsKey(friendId)) {
                        onlineFriends.put(friendId, false);
                    }
                    // Lấy số tin nhắn chưa đọc
                    try {
                        int unreadCount = messageDAO.getUnreadCountForConversation(currentUserId, friendId);
                        unreadCounts.put(friendId, unreadCount);
                    } catch (org.example.zalu.exception.database.DatabaseException | 
                             org.example.zalu.exception.database.DatabaseConnectionException e) {
                        e.printStackTrace();
                        unreadCounts.put(friendId, 0);
                    }
                }
            }
            
            // Thêm nhóm
            List<GroupInfo> groups = groupDAO.getUserGroups(currentUserId);
            for (GroupInfo group : groups) {
                chatItems.add(new ChatItem(group));
                // Lấy số tin nhắn chưa đọc cho nhóm (dùng negative ID để phân biệt)
                try {
                    int unreadCount = messageDAO.getUnreadCountForGroup(currentUserId, group.getId());
                    unreadCounts.put(-group.getId(), unreadCount);
                } catch (org.example.zalu.exception.database.DatabaseException | 
                         org.example.zalu.exception.database.DatabaseConnectionException e) {
                    e.printStackTrace();
                    unreadCounts.put(-group.getId(), 0);
                }
            }
            
            // Lưu trạng thái welcome mode trước khi set items
            boolean wasWelcomeMode = isWelcomeMode;
            
            // Set flag để tránh trigger selection listener
            if (wasWelcomeMode) {
                isRefreshing = true;
            }
            
            // Clear selection trước khi set items để tránh auto-selection
            chatList.getSelectionModel().clearSelection();
            
            // Cập nhật last messages trước
            messageUpdateService.updateLastMessages(chatItems, currentUserId);
            
            // Sắp xếp chat items theo thời gian tin nhắn cuối cùng (mới nhất lên đầu)
            chatItems.sort((a, b) -> {
                LocalDateTime timeA = messageUpdateService.getLastMessageTime(a.getId());
                LocalDateTime timeB = messageUpdateService.getLastMessageTime(b.getId());
                // So sánh ngược lại để mới nhất lên đầu
                return timeB.compareTo(timeA);
            });
            
            chatList.setItems(FXCollections.observableArrayList(chatItems));
            updateLastMessages();
            
            // Clear selection lại sau khi set items (đảm bảo không có selection)
            if (wasWelcomeMode) {
                chatList.getSelectionModel().clearSelection();
                // Đảm bảo welcome screen vẫn hiển thị
                if (messageListController != null) {
                    String nameToShow = (welcomeUsername != null && !welcomeUsername.isBlank()) ? welcomeUsername : "bạn";
                    messageListController.showWelcomeScreen("Chào mừng " + nameToShow + " đến với Zalu.\nChọn một người bạn để bắt đầu trò chuyện.");
                }
            }
            
            // Reset flag ngay lập tức sau khi set items xong - cho phép user click vào item
            isRefreshing = false;
            System.out.println("✓ Friend list refreshed, isRefreshing reset to false");
            
            System.out.println("Refreshed " + friendIds.size() + " friends and " + groups.size() + " groups");
        } catch (SQLException e) {
            System.err.println("SQLException in refreshFriendList: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Unexpected error in refreshFriendList: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void createGroup() {
        if (currentUserId <= 0) {
            System.out.println("Cannot create group: User not logged in");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/group/create-group-view.fxml"));
            Parent content = loader.load();
            CreateGroupController controller = loader.getController();
            controller.setCurrentUserId(currentUserId);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Tạo nhóm mới");
            dialogStage.initOwner(stage);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.setResizable(false);
            Scene dialogScene = new Scene(content);
            dialogStage.setScene(dialogScene);

            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setContentText("Không thể mở dialog tạo nhóm: " + e.getMessage());
            alert.show();
        }
    }

    public void viewFriendRequests() {
        if (currentUserId <= 0) {
            System.out.println("Cannot view friend requests: User not logged in");
            return;
        }
        try {
            List<User> incoming = friendDAO.getPendingRequestsWithUserInfo(currentUserId, userDAO);
            List<User> outgoing = friendDAO.getOutgoingRequestsWithUserInfo(currentUserId, userDAO);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/friend/friend-request-view.fxml"));
            Parent root = loader.load();
            FriendRequestController controller = loader.getController();
            controller.setStage(stage);
            controller.setCurrentUserId(currentUserId);
            controller.setMainController(this);
            controller.setIncomingRequests(incoming);
            controller.setOutgoingRequests(outgoing);
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
            
            System.out.println("Opened Friend Requests with " + incoming.size() + " incoming and " + outgoing.size() + " outgoing");
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setContentText("Không thể mở màn hình bạn bè: " + e.getMessage());
            alert.show();
        }
    }

    private void onFriendsUpdated(List<Integer> friendIds) {
        Platform.runLater(() -> {
            hasReceivedFriends = true; // Đánh dấu đã nhận friends
            if (friendIds != null && !friendIds.isEmpty()) {
                System.out.println("Received friend IDs update: " + friendIds.size() + " IDs");
                List<User> friends = new ArrayList<>();
                try {
                    for (int friendId : friendIds) {
                        try {
                            User friend = userDAO.getUserById(friendId);
                            if (friend != null) {
                                friends.add(friend);
                                // Status sẽ được cập nhật từ online users list hoặc broadcast messages
                                // Chỉ set default false nếu chưa có trong map
                                if (!onlineFriends.containsKey(friendId)) {
                                    onlineFriends.put(friendId, false);
                                }
                            }
                        } catch (org.example.zalu.exception.auth.UserNotFoundException e) {
                            System.out.println("Friend with ID " + friendId + " not found, skipping...");
                        } catch (org.example.zalu.exception.database.DatabaseException | org.example.zalu.exception.database.DatabaseConnectionException e) {
                            System.err.println("Error loading friend: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    refreshFriendList();
                    if (messageListController != null) {
                    }
                } catch (Exception e) {
                    System.err.println("Unexpected error in onFriendsUpdated: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            if (chatList.getItems().isEmpty() || chatList.getSelectionModel().getSelectedItem() == null) {
                chatList.getSelectionModel().clearSelection();
                showWelcomeInMessageArea();
            }
        });
    }

    private void onOnlineUsersReceived(List<Integer> onlineUserIds) {
        Platform.runLater(() -> {
            if (onlineUserIds != null) {
                System.out.println("Received online users list: " + onlineUserIds.size() + " users");
                // Cập nhật online status cho tất cả friends
                for (int friendId : onlineUserIds) {
                    onlineFriends.put(friendId, true);
                }
                // Refresh friend list và chat header để hiển thị status mới
                refreshFriendList();
                updateChatHeaderStatus();
            }
        });
    }

    private void updateLastMessages() {
        if (chatList == null || chatList.getItems().isEmpty() || messageUpdateService == null) {
            return;
        }
        messageUpdateService.updateLastMessages(chatList.getItems(), currentUserId);
        chatList.refresh();
    }

    private void onChatItemSelected() {
        ChatItem selected = chatList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        // QUAN TRỌNG: Đảm bảo message view được load vào chatContainer (thay thế welcome view nếu có)
        loadSubControllers();
        ensureMessageViewInContainer();

        if (messageListController == null) {
            System.err.println("Lỗi: messageListController chưa được khởi tạo.");
            return;
        }

        // Hiển thị chat input khi chọn chat
        isWelcomeMode = false;
        if (chatInputRoot != null) {
            chatInputRoot.setVisible(true);
            chatInputRoot.setManaged(true);
        }
        
        // Luôn reload dữ liệu ngay cả khi click vào item đã được chọn
        System.out.println("Loading chat for: " + selected.getDisplayName() + " (currentFriendId: " + currentFriendId + ", currentGroupId: " + currentGroupId + ")");

        if (selected.isGroup()) {
            // Xử lý chọn nhóm
            GroupInfo group = selected.getGroup();
            currentGroupId = group.getId();
            currentFriendId = -1;
            
            System.out.println("Đã chọn nhóm: " + group.getName() + " (ID: " + currentGroupId + ")");
            
            if (chatController != null) {
                chatController.setCurrentGroup(currentGroupId);
            }
            
            try {
                List<Message> messages = messageDAO.getMessagesForGroup(currentGroupId);
                messages.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
                
                System.out.println("Hiển thị " + messages.size() + " tin nhắn cho nhóm " + group.getName());
                messageListController.showChatWithGroup(group, messages, currentUserId, userDAO);
            } catch (org.example.zalu.exception.message.MessageException | 
                     org.example.zalu.exception.database.DatabaseException | 
                     org.example.zalu.exception.database.DatabaseConnectionException e) {
                e.printStackTrace();
                messageListController.showEmptyChatMessage("Lỗi khi tải lịch sử nhóm.");
            }
        } else {
            // Xử lý chọn bạn bè (giữ nguyên logic cũ)
            User friend = selected.getUser();
            currentFriendId = friend.getId();
            currentGroupId = -1;
            
            System.out.println("Đã chọn bạn: " + friend.getUsername() + " (ID: " + currentFriendId + ")");

            messageListController.setCurrentFriend(friend);
            
            if (chatController != null) {
                chatController.setCurrentFriend(friend);
                System.out.println("Đã cập nhật ChatController với bạn: " + friend.getUsername());
            } else {
                System.err.println("CẢNH BÁO: chatController == null, không thể gửi tin nhắn!");
            }

            try {
                List<Message> messages = messageDAO.getMessagesBetween(currentUserId, currentFriendId);
                messages.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));

                System.out.println("Hiển thị tổng cộng " + messages.size() + " tin nhắn cho cuộc trò chuyện với " + friend.getUsername());
                messageListController.showChatWithFriend(friend, messages);

            } catch (org.example.zalu.exception.message.MessageException | 
                     org.example.zalu.exception.database.DatabaseException | 
                     org.example.zalu.exception.database.DatabaseConnectionException e) {
                e.printStackTrace();
                messageListController.showEmptyChatMessage("Lỗi khi tải lịch sử trò chuyện.");
            }
        }

        // Cập nhật last messages trước khi sắp xếp
        updateLastMessages();
        // Sắp xếp lại danh sách sau khi cập nhật tin nhắn (không gọi updateLastMessages() trong sort)
        sortChatListByLastMessage();
    }
    
    /**
     * Force reload chat cho một item cụ thể (được gọi khi click vào item đã được chọn)
     */
    public void reloadChatForItem(ChatItem item) {
        if (item == null) {
            return;
        }
        
        System.out.println("Force reload chat for: " + item.getDisplayName());
        
        // Đảm bảo message view được load
        loadSubControllers();
        ensureMessageViewInContainer();
        
        if (messageListController == null) {
            System.err.println("Lỗi: messageListController chưa được khởi tạo.");
            return;
        }
        
        // Hiển thị chat input
        isWelcomeMode = false;
        if (chatInputRoot != null) {
            chatInputRoot.setVisible(true);
            chatInputRoot.setManaged(true);
        }
        
        // Reload messages và hiển thị lại
        if (item.isGroup()) {
            GroupInfo group = item.getGroup();
            currentGroupId = group.getId();
            currentFriendId = -1;
            
            if (chatController != null) {
                chatController.setCurrentGroup(currentGroupId);
            }
            
            try {
                List<Message> messages = messageDAO.getMessagesForGroup(currentGroupId);
                messages.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
                
                System.out.println("Force reload: Hiển thị " + messages.size() + " tin nhắn cho nhóm " + group.getName());
                messageListController.showChatWithGroup(group, messages, currentUserId, userDAO);
            } catch (Exception e) {
                e.printStackTrace();
                messageListController.showEmptyChatMessage("Lỗi khi tải lịch sử nhóm.");
            }
        } else {
            User friend = item.getUser();
            currentFriendId = friend.getId();
            currentGroupId = -1;
            
            messageListController.setCurrentFriend(friend);
            
            if (chatController != null) {
                chatController.setCurrentFriend(friend);
            }
            
            try {
                List<Message> messages = messageDAO.getMessagesBetween(currentUserId, currentFriendId);
                messages.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
                
                System.out.println("Force reload: Hiển thị " + messages.size() + " tin nhắn cho bạn " + friend.getUsername());
                messageListController.showChatWithFriend(friend, messages);
            } catch (Exception e) {
                e.printStackTrace();
                messageListController.showEmptyChatMessage("Lỗi khi tải lịch sử trò chuyện.");
            }
        }
        
        // Cập nhật selection trong ListView để đảm bảo item được highlight
        if (chatList != null) {
            for (int i = 0; i < chatList.getItems().size(); i++) {
                ChatItem listItem = chatList.getItems().get(i);
                // Check bằng ID thay vì equals()
                boolean isSameItem = false;
                if (item.isGroup() && listItem.isGroup()) {
                    isSameItem = (item.getGroup().getId() == listItem.getGroup().getId());
                } else if (!item.isGroup() && !listItem.isGroup()) {
                    isSameItem = (item.getUser().getId() == listItem.getUser().getId());
                }
                if (isSameItem) {
                    chatList.getSelectionModel().select(i);
                    break;
                }
            }
        }
    }
    
    private void onGroupsUpdated(List<GroupInfo> groups) {
        Platform.runLater(() -> {
            refreshFriendList();
        });
    }

    private void onMessagesReceived(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        System.out.println("onMessagesReceived: Nhận được " + messages.size() + " tin nhắn từ server!");
        
            // Debug: In ra chi tiết các tin nhắn nhận được
        for (Message msg : messages) {
            System.out.println("  Tin nhắn từ server - ID: " + msg.getId() + 
                ", sender: " + msg.getSenderId() + 
                ", receiver: " + msg.getReceiverId() + 
                ", content: " + (msg.getContent() != null ? msg.getContent().substring(0, Math.min(50, msg.getContent().length())) : "null") +
                ", fileName: " + (msg.getFileName() != null ? msg.getFileName() : "null") +
                ", groupId: " + msg.getGroupId());
            
            // Cập nhật last message time cho tin nhắn mới
            if (messageUpdateService != null) {
                int chatId = msg.getGroupId() > 0 ? msg.getGroupId() : 
                             (msg.getSenderId() == currentUserId ? msg.getReceiverId() : msg.getSenderId());
                String preview = formatMessagePreview(msg);
                messageUpdateService.updateLastMessage(chatId, preview, msg.getCreatedAt());
            }
        }

        Platform.runLater(() -> {
            if (messageListController == null) {
                System.out.println("messageListController == null → Đang tải...");
                loadSubControllers();
            }

            // Nếu đang mở cuộc trò chuyện, reload lại tin nhắn từ DB để đảm bảo đồng bộ
            ChatItem selected = chatList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                if (selected.isGroup() && currentGroupId > 0) {
                    System.out.println("Đang reload tin nhắn cho nhóm hiện tại (ID: " + currentGroupId + ") sau khi nhận tin nhắn từ server");
                    try {
                        List<Message> dbMessages = messageDAO.getMessagesForGroup(currentGroupId);
                        dbMessages.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
                        
                        if (messageListController != null) {
                            System.out.println("Reload: Hiển thị " + dbMessages.size() + " tin nhắn nhóm từ DB");
                            messageListController.showChatWithGroup(selected.getGroup(), dbMessages, currentUserId, userDAO);
                        }
                    } catch (org.example.zalu.exception.message.MessageException | 
                             org.example.zalu.exception.database.DatabaseException | 
                             org.example.zalu.exception.database.DatabaseConnectionException e) {
                        System.err.println("Lỗi reload tin nhắn nhóm: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else if (!selected.isGroup() && currentFriendId > 0) {
                    System.out.println("Đang reload tin nhắn cho bạn hiện tại (ID: " + currentFriendId + ") sau khi nhận tin nhắn từ server");
                    try {
                        List<Message> dbMessages = messageDAO.getMessagesBetween(currentUserId, currentFriendId);
                        dbMessages.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
                        
                        if (messageListController != null) {
                            System.out.println("Reload: Hiển thị " + dbMessages.size() + " tin nhắn từ DB");
                            messageListController.showChatWithFriend(selected.getUser(), dbMessages);
                        }
                    } catch (org.example.zalu.exception.message.MessageException | 
                             org.example.zalu.exception.database.DatabaseException | 
                             org.example.zalu.exception.database.DatabaseConnectionException e) {
                        System.err.println("Lỗi reload tin nhắn: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } else {
                System.out.println("Chưa chọn bạn/nhóm, tin nhắn sẽ hiển thị khi chọn");
            }
            
            // Cập nhật last messages trước
            updateLastMessages();
            
            // Cập nhật badge số tin nhắn chưa đọc cho các conversation có tin nhắn mới
            for (Message msg : messages) {
                if (msg.getGroupId() > 0) {
                    // Nhóm
                    try {
                        int unreadCount = messageDAO.getUnreadCountForGroup(currentUserId, msg.getGroupId());
                        unreadCounts.put(-msg.getGroupId(), unreadCount);
                    } catch (org.example.zalu.exception.database.DatabaseException | 
                             org.example.zalu.exception.database.DatabaseConnectionException e) {
                        e.printStackTrace();
                    }
                } else {
                    // 1-1 chat
                    int friendId = msg.getSenderId() == currentUserId ? msg.getReceiverId() : msg.getSenderId();
                    try {
                        int unreadCount = messageDAO.getUnreadCountForConversation(currentUserId, friendId);
                        unreadCounts.put(friendId, unreadCount);
                    } catch (org.example.zalu.exception.database.DatabaseException | 
                             org.example.zalu.exception.database.DatabaseConnectionException e) {
                        e.printStackTrace();
                    }
                }
            }
            chatList.refresh();
            
            // Sắp xếp lại danh sách chat khi có tin nhắn mới (không gọi updateLastMessages() trong sort)
            sortChatListByLastMessage();
        });
    }
    
    /**
     * Cập nhật số tin nhắn chưa đọc cho một friend cụ thể
     */
    public void updateUnreadCountForFriend(int friendId, int unreadCount) {
        unreadCounts.put(friendId, unreadCount);
        chatList.refresh();
    }
    
    /**
     * Cập nhật số tin nhắn chưa đọc cho một group cụ thể
     */
    public void updateUnreadCountForGroup(int groupId, int unreadCount) {
        unreadCounts.put(-groupId, unreadCount); // Group sử dụng key âm
        chatList.refresh();
    }
    
    /**
     * Format message preview text (helper method)
     */
    private String formatMessagePreview(Message msg) {
        if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
            return msg.getContent();
        } else if (msg.getFileName() != null) {
            return "[File: " + msg.getFileName() + "]";
        }
        return "Bắt đầu trò chuyện...";
    }
    
    /**
     * Sắp xếp lại danh sách chat theo thời gian tin nhắn cuối cùng
     */
    private void sortChatListByLastMessage() {
        if (chatList == null || messageUpdateService == null) {
            return;
        }
        
        Platform.runLater(() -> {
            var items = chatList.getItems();
            if (items == null || items.isEmpty()) {
                return;
            }
            
            // Tạo list mới từ items hiện tại
            List<ChatItem> sortedItems = new ArrayList<>(items);
            
            // Sắp xếp theo thời gian tin nhắn cuối cùng (mới nhất lên đầu)
            sortedItems.sort((a, b) -> {
                LocalDateTime timeA = messageUpdateService.getLastMessageTime(a.getId());
                LocalDateTime timeB = messageUpdateService.getLastMessageTime(b.getId());
                return timeB.compareTo(timeA);
            });
            
            // Kiểm tra xem có cần sắp xếp lại không (tránh sắp xếp không cần thiết)
            boolean needsSort = false;
            for (int i = 0; i < Math.min(items.size(), sortedItems.size()); i++) {
                if (items.get(i).getId() != sortedItems.get(i).getId()) {
                    needsSort = true;
                    break;
                }
            }
            
            if (!needsSort) {
                // Không cần sắp xếp lại, chỉ refresh
                chatList.refresh();
                return;
            }
            
            // Cập nhật lại danh sách (tạm thời vô hiệu hóa selection listener)
            isRefreshing = true;
            chatList.getSelectionModel().clearSelection();
            ChatItem selectedBefore = null;
            if (currentFriendId > 0 || currentGroupId > 0) {
                // Giữ lại selection hiện tại
                for (ChatItem item : items) {
                    if ((!item.isGroup() && item.getUser().getId() == currentFriendId) ||
                        (item.isGroup() && item.getGroup().getId() == currentGroupId)) {
                        selectedBefore = item;
                        break;
                    }
                }
            }
            
            chatList.setItems(FXCollections.observableArrayList(sortedItems));
            
            // Khôi phục selection
            if (selectedBefore != null) {
                for (int i = 0; i < sortedItems.size(); i++) {
                    ChatItem item = sortedItems.get(i);
                    if ((!item.isGroup() && item.getUser().getId() == currentFriendId) ||
                        (item.isGroup() && item.getGroup().getId() == currentGroupId)) {
                        chatList.getSelectionModel().select(i);
                        break;
                    }
                }
            }
            
            // Reset flag sau khi sắp xếp xong
            isRefreshing = false;
            
            // Chỉ refresh UI, không gọi updateLastMessages() để tránh vòng lặp
            chatList.refresh();
        });
    }

    public void onLoginSuccess(int userId) {
        currentUserId = userId;
        refreshFriendList();
        if (messageListController != null) {
            messageListController.setCurrentUserId(userId);
        }
    }
    
    // Callback khi nhận MESSAGE_SENT|OK hoặc GROUP_MESSAGE_SENT|OK từ server - reload tin nhắn từ DB để tránh duplicate
    private void onMessageSentResponse(String message) {
        if (message != null && (message.startsWith("MESSAGE_SENT|OK") || message.startsWith("GROUP_MESSAGE_SENT|OK"))) {
            System.out.println("Nhận " + (message.startsWith("MESSAGE_SENT|OK") ? "MESSAGE_SENT|OK" : "GROUP_MESSAGE_SENT|OK") + ", reload tin nhắn từ DB để đồng bộ...");
            // Server đã lưu vào DB trước khi gửi response, nên reload ngay
            Platform.runLater(() -> reloadCurrentChat());
        }
    }
    
    // Callback để xử lý broadcast messages (read receipts, status updates, etc.)
    private void onBroadcastMessage(String message) {
        if (message != null && message.startsWith("MESSAGES_READ|")) {
            // Format: MESSAGES_READ|receiverId
            // Người nhận (receiverId) đã đọc tin nhắn của người gửi (currentUserId)
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int readerId = Integer.parseInt(parts[1]);
                    System.out.println("MainController: Tin nhắn đã được đọc bởi user " + readerId);
                    // Cập nhật read status trong UI
                    if (messageListController != null) {
                        messageListController.updateReadStatus(readerId);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Lỗi parse readerId từ MESSAGES_READ: " + message);
                }
            }
        } else if (message != null && message.startsWith("USER_ONLINE|")) {
            // Format: USER_ONLINE|userId
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int onlineUserId = Integer.parseInt(parts[1]);
                    System.out.println("MainController: User " + onlineUserId + " đã online");
                    onlineFriends.put(onlineUserId, true);
                    refreshFriendList();
                    updateChatHeaderStatus();
                } catch (NumberFormatException e) {
                    System.err.println("Lỗi parse userId từ USER_ONLINE: " + message);
                }
            }
        } else if (message != null && message.startsWith("USER_OFFLINE|")) {
            // Format: USER_OFFLINE|userId
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int offlineUserId = Integer.parseInt(parts[1]);
                    System.out.println("MainController: User " + offlineUserId + " đã offline");
                    onlineFriends.put(offlineUserId, false);
                    refreshFriendList();
                    updateChatHeaderStatus();
                } catch (NumberFormatException e) {
                    System.err.println("Lỗi parse userId từ USER_OFFLINE: " + message);
                }
            }
        } else if (message != null && message.startsWith("TYPING_INDICATOR|")) {
            // Format: TYPING_INDICATOR|senderId
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int typingUserId = Integer.parseInt(parts[1]);
                    System.out.println("MainController: User " + typingUserId + " đang gõ...");
                    if (messageListController != null) {
                        messageListController.showTypingIndicator(typingUserId);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Lỗi parse userId từ TYPING_INDICATOR: " + message);
                }
            }
        } else if (message != null && message.startsWith("TYPING_STOP|")) {
            // Format: TYPING_STOP|senderId
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int typingUserId = Integer.parseInt(parts[1]);
                    System.out.println("MainController: User " + typingUserId + " đã dừng gõ");
                    if (messageListController != null) {
                        messageListController.hideTypingIndicator(typingUserId);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Lỗi parse userId từ TYPING_STOP: " + message);
                }
            }
        } else if (message != null && message.startsWith("MESSAGE_DELETED|")) {
            // Format: MESSAGE_DELETED|messageId
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int messageId = Integer.parseInt(parts[1]);
                    System.out.println("MainController: Message " + messageId + " đã được xóa");
                    if (messageListController != null) {
                        messageListController.handleMessageDeleted(messageId);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Lỗi parse messageId từ MESSAGE_DELETED: " + message);
                }
            }
        } else if (message != null && message.startsWith("MESSAGE_RECALLED|")) {
            // Format: MESSAGE_RECALLED|messageId
            String[] parts = message.split("\\|");
            if (parts.length >= 2) {
                try {
                    int messageId = Integer.parseInt(parts[1]);
                    System.out.println("MainController: Message " + messageId + " đã được thu hồi");
                    if (messageListController != null) {
                        messageListController.handleMessageRecalled(messageId);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Lỗi parse messageId từ MESSAGE_RECALLED: " + message);
                }
            }
        } else if (message != null && message.startsWith("MESSAGE_EDITED|")) {
            // Format: MESSAGE_EDITED|messageId|newContent
            String[] parts = message.split("\\|", 3);
            if (parts.length >= 3) {
                try {
                    int messageId = Integer.parseInt(parts[1]);
                    String newContent = parts[2];
                    System.out.println("MainController: Message " + messageId + " đã được chỉnh sửa");
                    if (messageListController != null) {
                        messageListController.handleMessageEdited(messageId, newContent);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Lỗi parse messageId từ MESSAGE_EDITED: " + message);
                }
            }
        }
    }

    private void updateChatHeaderStatus() {
        if (messageListController != null && currentFriendId > 0) {
            boolean isOnline = onlineFriends.getOrDefault(currentFriendId, false);
            messageListController.updateFriendStatus(isOnline);
        }
    }
    
    /**
     * Kiểm tra xem friend có online không
     */
    public boolean isFriendOnline(int friendId) {
        return onlineFriends.getOrDefault(friendId, false);
    }
    
    // Method để reload tin nhắn sau khi gửi thành công
    private void reloadCurrentChat() {
        ChatItem selected = chatList.getSelectionModel().getSelectedItem();
        if (selected != null && currentUserId > 0) {
            try {
                if (selected.isGroup() && currentGroupId > 0) {
                    List<Message> dbMessages = messageDAO.getMessagesForGroup(currentGroupId);
                    dbMessages.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
                    
                    if (messageListController != null) {
                        System.out.println("Reload sau khi gửi: Hiển thị " + dbMessages.size() + " tin nhắn nhóm từ DB");
                        messageListController.showChatWithGroup(selected.getGroup(), dbMessages, currentUserId, userDAO);
                    }
                } else if (!selected.isGroup() && currentFriendId > 0) {
                    List<Message> dbMessages = messageDAO.getMessagesBetween(currentUserId, currentFriendId);
                    dbMessages.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
                    
                    if (messageListController != null) {
                        System.out.println("Reload sau khi gửi: Hiển thị " + dbMessages.size() + " tin nhắn từ DB");
                        messageListController.showChatWithFriend(selected.getUser(), dbMessages);
                    }
                }
            } catch (org.example.zalu.exception.message.MessageException | 
                     org.example.zalu.exception.database.DatabaseException | 
                     org.example.zalu.exception.database.DatabaseConnectionException e) {
                System.err.println("Lỗi reload tin nhắn sau khi gửi: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void loadSubControllers() {
        // Load controllers nếu chưa có
        if (messageListController == null || chatController == null) {
            try {
                // Load phần tin nhắn
                FXMLLoader messageLoader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/chat/message-list-view.fxml"));
                Parent messageRoot = messageLoader.load();
                messageListController = messageLoader.getController();
                messageListController.setCurrentUserId(currentUserId);
                messageListController.setMainController(this);

                // Load input
                FXMLLoader chatLoader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/chat/chat-input-view.fxml"));
                chatInputRoot = chatLoader.load();
                chatController = chatLoader.getController();
                chatController.setCurrentUserId(currentUserId);
                chatController.setMessageListController(messageListController);
                chatController.setStage(stage);

                System.out.println("Sub-controllers loaded lần đầu");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // QUAN TRỌNG: Luôn đảm bảo message view được load vào chatContainer (kể cả khi controller đã tồn tại)
        // Điều này đảm bảo khi click vào chat item, message view sẽ hiển thị thay vì welcome view
        try {
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
            if (!hasMessageView && messageListController != null) {
                System.out.println("Message view not in container, reloading...");
                // Tìm Parent của message view (có thể cần load lại)
                FXMLLoader messageLoader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/chat/message-list-view.fxml"));
                Parent messageRoot = messageLoader.load();
                MessageListController controller = messageLoader.getController();
                controller.setCurrentUserId(currentUserId);
                controller.setMainController(this);
                
                // Cập nhật reference
                messageListController = controller;
                
                // Load input nếu chưa có
                if (chatInputRoot == null) {
                    FXMLLoader chatLoader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/chat/chat-input-view.fxml"));
                    chatInputRoot = chatLoader.load();
                    chatController = chatLoader.getController();
                    chatController.setCurrentUserId(currentUserId);
                    chatController.setMessageListController(messageListController);
                    chatController.setStage(stage);
                }
                
                // Clear container và add message view
                chatContainer.getChildren().clear();
                chatContainer.getChildren().add(messageRoot);
                VBox.setVgrow(messageRoot, Priority.ALWAYS);
                
                // Add chat input
                if (chatInputRoot != null) {
                    chatContainer.getChildren().add(chatInputRoot);
                }
                
                System.out.println("✓ Message view reloaded into chatContainer");
            }
            
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
        } catch (IOException e) {
            e.printStackTrace();
        }
        // BỎ ĐIỆU KIỆN RETURN Ở ĐÂY → CHO PHÉP LOAD TIN NHẮN MỖI KHI CHỌN BẠN!!!
    }
    
    private void ensureMessageViewInContainer() {
        if (messageListController == null || chatContainer == null) return;
        try {
            boolean hasWelcomeView = chatContainer.getChildren().stream()
                .anyMatch(node -> node.getStyleClass().contains("welcome-root"));
            if (hasWelcomeView || chatContainer.getChildren().isEmpty()) {
                FXMLLoader messageLoader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/chat/message-list-view.fxml"));
                Parent messageRoot = messageLoader.load();
                messageListController = messageLoader.getController();
                messageListController.setCurrentUserId(currentUserId);
                messageListController.setMainController(this);
                if (chatInputRoot == null) {
                    FXMLLoader chatLoader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/chat/chat-input-view.fxml"));
                    chatInputRoot = chatLoader.load();
                    chatController = chatLoader.getController();
                    chatController.setCurrentUserId(currentUserId);
                    chatController.setMessageListController(messageListController);
                    chatController.setStage(stage);
                }
                chatContainer.getChildren().clear();
                chatContainer.getChildren().add(messageRoot);
                VBox.setVgrow(messageRoot, Priority.ALWAYS);
                if (chatInputRoot != null) chatContainer.getChildren().add(chatInputRoot);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setWelcomeUsername(String username) {
        this.welcomeUsername = username;
    }

    public void showWelcomeInMessageArea() {
        System.out.println("showWelcomeInMessageArea() called, loading welcome-view.fxml");
        
        // QUAN TRỌNG: Clear chatContainer trước để loại bỏ friend-request-view hoặc bất kỳ view nào khác
        chatContainer.getChildren().clear();
        
        try {
            // Load welcome-view.fxml vào chatContainer
            FXMLLoader welcomeLoader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/chat/welcome-view.fxml"));
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
            
            System.out.println("✓ Welcome view (welcome-view.fxml) displayed successfully");
        } catch (Exception e) {
            System.err.println("✗ Error loading welcome view: " + e.getMessage());
            e.printStackTrace();
            
            // Fallback: hiển thị placeholder nếu không load được welcome view
            StackPane placeholder = new StackPane();
            placeholder.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #f8f9fa 0%, #f5f6f6 50%, #f0f2f5 100%);");
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
        if (navigationManager == null) return;
        navigationManager.setupMenu(
            this::openProfileView,
            this::logout,
            this::handleOpenSettings,
            this::handleUpgradeAccount
        );
    }

    private void refreshNavMenuHeader() {
        if (navigationManager != null) {
            navigationManager.refreshMenuHeader(currentUserId);
        }
    }

    private void updateNavAvatar() {
        if (navigationManager != null) {
            navigationManager.updateAvatar(currentUserId);
        }
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
        isLoadingMessages = false;
        System.out.println("Messages loading completed for friend " + currentFriendId);
    }

    private void showProfileDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/profile/profile-view.fxml"));
            Parent root = loader.load();
            ProfileController profileCtrl = loader.getController();
            profileCtrl.setCurrentUserId(currentUserId);
            Stage profileStage = new Stage();
            profileCtrl.setStage(profileStage);
            profileStage.setScene(new Scene(root, 800, 600));
            profileStage.setTitle("Edit Profile - Set Bio & Avatar");
            profileStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void resetDataFlags() {
        System.out.println("Resetting data flags for logout...");
        dataLoaded = false;
        listenerStarted = false;
        waitingForRequests = false;
        isWelcomeMode = true;
        isLoadingMessages = false;
        hasReceivedFriends = false;
        currentUserId = -1;
        currentFriendId = -1;
        pendingUsers.clear();
        onlineFriends.clear();
        unreadCounts.clear();
        if (messageUpdateService != null) {
            messageUpdateService.clear();
        }
        welcomeUsername = null;
        if (chatList != null) {
            chatList.getItems().clear();
        }
        if (messageListController != null) {
            messageListController.clearChat();
            messageListController.showWelcomeScreen("Chào mừng trở lại!");
        }
        System.out.println("Data flags reset completed");
    }
}
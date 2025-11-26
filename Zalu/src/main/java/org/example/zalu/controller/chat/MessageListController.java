package org.example.zalu.controller.chat;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.zalu.dao.GroupDAO;
import org.example.zalu.dao.MessageDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.GroupInfo;
import org.example.zalu.model.Message;
import org.example.zalu.model.User;
import org.example.zalu.util.ui.ChatRenderer;
import org.example.zalu.service.ChatHeaderService;
import org.example.zalu.util.IconUtil;
import org.example.zalu.service.InfoPanelService;
import org.example.zalu.controller.MainController;
import org.example.zalu.controller.group.ManageGroupController;
import org.example.zalu.controller.group.AddMemberController;
import org.example.zalu.controller.profile.BioViewController;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MessageListController {

    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatArea;
    @FXML private HBox chatHeader;
    @FXML private Label friendNameLabel;
    @FXML private Label friendStatusLabel;
    @FXML private ImageView friendAvatar;

    // Info panel
    @FXML private VBox infoPanel;
    @FXML private TabPane infoTabPane;
    @FXML private Tab directTab;
    @FXML private Tab groupTab;
    @FXML private ImageView infoAvatar;
    @FXML private Label infoNameLabel;
    @FXML private Label infoStatusLabel;
    @FXML private FlowPane mediaPreviewPane;
    @FXML private ListView<String> directFileListView;
    @FXML private ListView<String> directLinkListView;
    @FXML private ListView<String> groupFileListView;
    @FXML private ListView<String> groupLinkListView;
    @FXML private Label groupNameInfoLabel;
    @FXML private Label groupMemberCountLabel;
    @FXML private ListView<String> groupMembersList;
    @FXML private CheckBox hideConversationCheck;
    @FXML private CheckBox groupHideConversationCheck;
    
    // Search UI
    @FXML private HBox searchBar;
    @FXML private TextField searchField;
    @FXML private Button searchPrevButton;
    @FXML private Button searchNextButton;
    @FXML private Label searchResultLabel;
    @FXML private Button searchButton;
    
    // Pinned messages UI
    @FXML private VBox pinnedMessagesSection;
    @FXML private VBox pinnedMessagesContainer;

    private ChatRenderer chatRenderer;
    private ChatHeaderService chatHeaderService;
    private InfoPanelService infoPanelService;
    private int currentUserId = -1;
    private User currentFriend = null;
    private GroupInfo currentGroup = null;
    private UserDAO userDAO;
    private GroupDAO groupDAO;
    private MessageDAO messageDAO;
    private final List<Message> currentConversationMessages = new ArrayList<>();
    private MainController mainController;
    
    // Typing indicator auto-hide timer
    private Timeline typingIndicatorHideTimer = null;
    private static final int TYPING_INDICATOR_HIDE_DELAY = 3000; // Ẩn sau 3 giây
    
    // Search state
    private List<Message> searchResults = new ArrayList<>();
    private int currentSearchIndex = -1;
    private String currentSearchText = "";
    private final List<Node> highlightedNodes = new ArrayList<>();
    
    // Pinned messages
    private final List<Message> pinnedMessages = new ArrayList<>();

    @FXML
    public void initialize() {
        chatRenderer = new ChatRenderer(chatArea, chatScrollPane);
        userDAO = new UserDAO();
        groupDAO = new GroupDAO();
        messageDAO = new MessageDAO();
        
        // Initialize services
        chatHeaderService = new ChatHeaderService(chatHeader, friendNameLabel, friendStatusLabel, friendAvatar);
        infoPanelService = new InfoPanelService(infoPanel, infoTabPane, directTab, groupTab,
                infoAvatar, infoNameLabel, infoStatusLabel, mediaPreviewPane,
                directFileListView, directLinkListView, groupFileListView, groupLinkListView,
                groupNameInfoLabel, groupMemberCountLabel, groupMembersList,
                hideConversationCheck, groupHideConversationCheck, userDAO, groupDAO);
        
        infoPanelService.hideInfoPanel();
        chatHeaderService.hideHeader();
        clearChat();
        showWelcomeScreen("Chào mừng đến với Zalu!\nChọn một người bạn hoặc nhóm để bắt đầu trò chuyện 💬");
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
    }

    // ============================= KHI CHỌN BẠN =============================
    public void showChatWithFriend(User friend, List<Message> messages) {
        // Ẩn typing indicator khi chuyển chat
        if (chatRenderer != null) {
            chatRenderer.hideTypingIndicator();
        }
        if (typingIndicatorHideTimer != null) {
            typingIndicatorHideTimer.stop();
            typingIndicatorHideTimer = null;
        }
        
        this.currentFriend = friend;
        this.currentGroup = null;

        currentConversationMessages.clear();
        if (messages != null) currentConversationMessages.addAll(messages);

        // Hiển thị header với online status
        boolean isOnline = false;
        if (mainController != null) {
            isOnline = mainController.isFriendOnline(friend.getId());
        }
        chatHeaderService.showHeaderForFriend(friend, isOnline);

        chatRenderer.clearChat();
        loadPinnedMessages(); // Load pinned messages

        if (messages == null || messages.isEmpty()) {
            showEmptyChatMessage("Chưa có tin nhắn nào với " + friend.getUsername() + "!\nHãy gửi lời chào đầu tiên nào 😄");
        } else {
            System.out.println("Hiển thị " + messages.size() + " tin nhắn cho " + friend.getUsername());
            // Lấy thông tin friend một lần (chỉ dùng cho avatar, không hiển thị tên trong chat 1-1)
            byte[] friendAvatarData = friend.getAvatarData();
            String friendAvatarUrl = friend.getAvatarUrl();
            
            // Chỉ hiển thị read status ở tin nhắn cuối cùng (mới nhất)
            Message lastOwnMessage = null;
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message msg = messages.get(i);
                if (msg.getSenderId() == currentUserId) {
                    lastOwnMessage = msg;
                    break;
                }
            }
            
            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                boolean isOwn = msg.getSenderId() == currentUserId;
                // Chỉ hiển thị read status ở tin nhắn cuối cùng của mình
                boolean showReadStatus = isOwn && (lastOwnMessage != null && msg.getId() == lastOwnMessage.getId());
                boolean isRead = showReadStatus ? msg.getIsRead() : false;
                
                // Không hiển thị tên trong chat 1-1 vì đã biết đang nhắn với ai
                String senderName = null;
                byte[] senderAvatarData = isOwn ? null : friendAvatarData;
                String senderAvatarUrl = isOwn ? null : friendAvatarUrl;
                // Avatar của người đã đọc (chỉ khi tin nhắn đã đọc và là tin nhắn cuối cùng)
                byte[] readerAvatarData = (showReadStatus && isRead) ? friendAvatarData : null;
                String readerAvatarUrl = (showReadStatus && isRead) ? friendAvatarUrl : null;

                if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                    String displayContent = msg.isRecalled() ? "Tin nhắn đã được thu hồi" : 
                                           (msg.getEditedContent() != null && msg.isEdited() ? msg.getEditedContent() : msg.getContent());
                    chatRenderer.addTextMessage(displayContent, isOwn, msg.getCreatedAt(), 
                                               senderName, senderAvatarData, senderAvatarUrl, isRead, readerAvatarData, readerAvatarUrl,
                                               msg.isDeleted(), msg.isRecalled(), msg.isEdited(), 
                                               msg.getRepliedToContent(), msg.getRepliedToMessageId() > 0 ? msg.getRepliedToMessageId() : null);
                    // Store message reference for later updates
                    Node lastMessageNode = chatArea.getChildren().get(chatArea.getChildren().size() - 1);
                    if (lastMessageNode instanceof HBox) {
                        ((HBox) lastMessageNode).setUserData(msg.getId());
                    }
                } else if (msg.getFileName() != null && !msg.getFileName().trim().isEmpty()) {
                    // Kiểm tra nếu là ảnh
                    if (org.example.zalu.util.ui.ChatRenderer.isAudioFile(msg.getFileName()) && msg.getFileData() != null && msg.getFileData().length > 0) {
                        // Voice và image messages không hiển thị read status (chỉ text message hiển thị)
                        chatRenderer.addVoiceMessage(msg.getFileData(), msg.getFileName(), isOwn, msg.getCreatedAt(),
                                                     senderName, senderAvatarData, senderAvatarUrl, false);
                    } else if (org.example.zalu.util.ui.ChatRenderer.isImageFile(msg.getFileName()) && msg.getFileData() != null && msg.getFileData().length > 0) {
                        chatRenderer.addImageMessage(msg.getFileData(), msg.getFileName(), isOwn, msg.getCreatedAt(),
                                                    senderName, senderAvatarData, senderAvatarUrl, false);
                    } else {
                    int fileSize = msg.getFileData() != null ? msg.getFileData().length : 0;
                        // File messages không hiển thị read status (chỉ text message hiển thị)
                        chatRenderer.addFileMessage(msg.getFileName(), fileSize, isOwn, msg.getCreatedAt(),
                                                   senderName, senderAvatarData, senderAvatarUrl, false);
                    }
                }
            }
        }
        chatRenderer.scrollToBottom();
        infoPanelService.configureForFriend(friend);
        infoPanelService.updateSharedMediaAndFiles(currentConversationMessages, false);
        
        // Mark messages as read khi xem chat
        markMessagesAsReadForFriend(friend.getId());
        
        // Cập nhật badge sau khi đã đọc (reset về 0)
        if (mainController != null) {
            mainController.updateUnreadCountForFriend(friend.getId(), 0);
            Platform.runLater(() -> mainController.refreshFriendList());
        }
    }
    
    // Mark messages as read cho friend
    private void markMessagesAsReadForFriend(int friendId) {
        if (currentUserId <= 0) return;
        // Gửi request đến server để mark as read
        org.example.zalu.client.ChatClient.sendRequest("MARK_AS_READ|" + currentUserId + "|" + friendId);
    }

    public void showWelcomeScreen(String message) {
        chatRenderer.clearChat();
        chatHeaderService.hideHeader();

        // Clear chat area trước
        if (chatArea != null) {
            chatArea.getChildren().clear();
            chatArea.setAlignment(Pos.CENTER);

        Label welcomeLabel = new Label(message);
            welcomeLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: #8e8e93; -fx-font-weight: 500;");
        welcomeLabel.setWrapText(true);
        welcomeLabel.setAlignment(Pos.CENTER);
            welcomeLabel.setMaxWidth(500);
            welcomeLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        StackPane centerPane = new StackPane(welcomeLabel);
        centerPane.setAlignment(Pos.CENTER);
            centerPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
            centerPane.setMinHeight(400);
            centerPane.setStyle("-fx-background-color: transparent;");

            chatArea.getChildren().add(centerPane);
            VBox.setVgrow(centerPane, Priority.ALWAYS);
        }
    }

    public void addSystemMessage(String text) {
        Platform.runLater(() -> {
            Label label = new Label(text);
            label.setStyle("-fx-font-size: 13px; -fx-text-fill: #888888; -fx-font-style: italic; -fx-padding: 10;");
            label.setAlignment(Pos.CENTER);
            label.setMaxWidth(Double.MAX_VALUE);

            VBox box = new VBox(label);
            box.setAlignment(Pos.CENTER);
            box.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 8; -fx-background-radius: 12;");
            box.setMaxWidth(500);

            chatArea.getChildren().add(box);
            chatRenderer.scrollToBottom();
        });
    }

    public void showEmptyChatMessage(String text) {
        chatRenderer.clearChat();

        Label label = new Label(text);
        label.setStyle("-fx-font-size: 18px; -fx-text-fill: #888888; -fx-font-style: italic;");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);

        StackPane centerPane = new StackPane(label);
        centerPane.setAlignment(Pos.CENTER);
        centerPane.setPrefHeight(500);

        chatArea.getChildren().setAll(centerPane);
    }

    public void addNewMessage(Message msg) {
        if (currentGroup != null && msg.getGroupId() == currentGroup.getId()) {
            Platform.runLater(() -> {
                boolean isOwn = msg.getSenderId() == currentUserId;
                String senderName = null;
                byte[] senderAvatarData = null;
                String senderAvatarUrl = null;
                
                if (!isOwn && userDAO != null) {
                    try {
                        User sender = userDAO.getUserById(msg.getSenderId());
                        if (sender != null) {
                            senderName = (sender.getFullName() != null && !sender.getFullName().trim().isEmpty()) 
                                    ? sender.getFullName() 
                                    : sender.getUsername();
                            senderAvatarData = sender.getAvatarData();
                            senderAvatarUrl = sender.getAvatarUrl();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                    // Nhóm chat: không hiển thị read status (chỉ 1-1 chat mới có)
                    String displayContent = msg.isRecalled() ? "Tin nhắn đã được thu hồi" : 
                                           (msg.getEditedContent() != null && msg.isEdited() ? msg.getEditedContent() : msg.getContent());
                    chatRenderer.addTextMessage(displayContent, isOwn, msg.getCreatedAt(),
                                               senderName, senderAvatarData, senderAvatarUrl, false,
                                               null, null, msg.isDeleted(), msg.isRecalled(), msg.isEdited(),
                                               msg.getRepliedToContent(), msg.getRepliedToMessageId() > 0 ? msg.getRepliedToMessageId() : null);
                    // Store message reference for later updates
                    Node lastMessageNode = chatArea.getChildren().get(chatArea.getChildren().size() - 1);
                    if (lastMessageNode instanceof HBox) {
                        ((HBox) lastMessageNode).setUserData(msg.getId());
                    }
                } else if (msg.getFileName() != null && !msg.getFileName().trim().isEmpty()) {
                    // Kiểm tra nếu là ảnh
                    if (ChatRenderer.isAudioFile(msg.getFileName()) && msg.getFileData() != null && msg.getFileData().length > 0) {
                        chatRenderer.addVoiceMessage(msg.getFileData(), msg.getFileName(), isOwn, msg.getCreatedAt(),
                                                     senderName, senderAvatarData, senderAvatarUrl, msg.getIsRead());
                    } else if (ChatRenderer.isImageFile(msg.getFileName()) && msg.getFileData() != null && msg.getFileData().length > 0) {
                        chatRenderer.addImageMessage(msg.getFileData(), msg.getFileName(), isOwn, msg.getCreatedAt(),
                                                    senderName, senderAvatarData, senderAvatarUrl, msg.getIsRead());
                    } else {
                    int fileSize = msg.getFileData() != null ? msg.getFileData().length : 0;
                        chatRenderer.addFileMessage(msg.getFileName(), fileSize, isOwn, msg.getCreatedAt(),
                                                   senderName, senderAvatarData, senderAvatarUrl, msg.getIsRead());
                    }
                }
                chatRenderer.scrollToBottom();
                currentConversationMessages.add(msg);
                infoPanelService.updateSharedMediaAndFiles(currentConversationMessages, currentGroup != null);
            });
        } else if (currentFriend != null &&
                ((msg.getSenderId() == currentFriend.getId() && msg.getReceiverId() == currentUserId) ||
                        (msg.getSenderId() == currentUserId && msg.getReceiverId() == currentFriend.getId()))) {
            Platform.runLater(() -> {
                // Ẩn typing indicator khi có tin nhắn mới
                if (msg.getSenderId() == currentFriend.getId()) {
                    hideTypingIndicator(currentFriend.getId());
                }
                
                boolean isOwn = msg.getSenderId() == currentUserId;
                
                // Xóa read status khỏi tất cả tin nhắn cũ (vì có tin nhắn mới)
                // Chỉ hiển thị read status ở tin nhắn cuối cùng
                chatRenderer.hideReadStatusFromOldMessages();
                
                // Tin nhắn mới luôn là tin nhắn cuối cùng, chỉ hiển thị read status nếu là của mình
                boolean showReadStatus = isOwn;
                boolean isRead = showReadStatus ? msg.getIsRead() : false;
                
                // Không hiển thị tên trong chat 1-1 vì đã biết đang nhắn với ai
                String senderName = null;
                byte[] senderAvatarData = null;
                String senderAvatarUrl = null;
                
                if (!isOwn) {
                    senderAvatarData = currentFriend.getAvatarData();
                    senderAvatarUrl = currentFriend.getAvatarUrl();
                }
                
                // Avatar của người đã đọc (chỉ khi tin nhắn đã đọc và là tin nhắn cuối cùng)
                byte[] readerAvatarData = (showReadStatus && isRead) ? currentFriend.getAvatarData() : null;
                String readerAvatarUrl = (showReadStatus && isRead) ? currentFriend.getAvatarUrl() : null;

                if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                    String displayContent = msg.isRecalled() ? "Tin nhắn đã được thu hồi" : 
                                           (msg.getEditedContent() != null && msg.isEdited() ? msg.getEditedContent() : msg.getContent());
                    chatRenderer.addTextMessage(displayContent, isOwn, msg.getCreatedAt(),
                                               senderName, senderAvatarData, senderAvatarUrl, isRead, 
                                               readerAvatarData, readerAvatarUrl,
                                               msg.isDeleted(), msg.isRecalled(), msg.isEdited(),
                                               msg.getRepliedToContent(), msg.getRepliedToMessageId() > 0 ? msg.getRepliedToMessageId() : null);
                    // Store message reference for later updates
                    Node lastMessageNode = chatArea.getChildren().get(chatArea.getChildren().size() - 1);
                    if (lastMessageNode instanceof HBox) {
                        ((HBox) lastMessageNode).setUserData(msg.getId());
                    }
                } else if (msg.getFileName() != null && !msg.getFileName().trim().isEmpty()) {
                    // Kiểm tra nếu là ảnh
                    if (ChatRenderer.isAudioFile(msg.getFileName()) && msg.getFileData() != null && msg.getFileData().length > 0) {
                        chatRenderer.addVoiceMessage(msg.getFileData(), msg.getFileName(), isOwn, msg.getCreatedAt(),
                                                     senderName, senderAvatarData, senderAvatarUrl, msg.getIsRead());
                    } else if (ChatRenderer.isImageFile(msg.getFileName()) && msg.getFileData() != null && msg.getFileData().length > 0) {
                        chatRenderer.addImageMessage(msg.getFileData(), msg.getFileName(), isOwn, msg.getCreatedAt(),
                                                    senderName, senderAvatarData, senderAvatarUrl, msg.getIsRead());
                    } else {
                    int fileSize = msg.getFileData() != null ? msg.getFileData().length : 0;
                        chatRenderer.addFileMessage(msg.getFileName(), fileSize, isOwn, msg.getCreatedAt(),
                                                   senderName, senderAvatarData, senderAvatarUrl, msg.getIsRead());
                    }
                }
                chatRenderer.scrollToBottom();
                currentConversationMessages.add(msg);
                infoPanelService.updateSharedMediaAndFiles(currentConversationMessages, currentGroup != null);
            });
        } else {
            System.out.println("addNewMessage: Không khớp với bạn/nhóm hiện tại, bỏ qua");
        }
    }

    public void addLocalTextMessage(Message msg) {
        boolean isOwn = msg.getSenderId() == currentUserId;
        chatRenderer.addTextMessage(msg.getContent(), isOwn, msg.getCreatedAt());
        chatRenderer.scrollToBottom();
        currentConversationMessages.add(msg);
        infoPanelService.updateSharedMediaAndFiles(currentConversationMessages, false);
    }

    public void addFileMessage(String fileName, int size, boolean isSentByMe) {
        Platform.runLater(() -> {
            chatRenderer.addFileMessage(fileName, size, isSentByMe);
            chatRenderer.scrollToBottom();
        });
    }
    
    public void addImageMessage(byte[] imageData, String fileName, boolean isSentByMe) {
        Platform.runLater(() -> {
            chatRenderer.addImageMessage(imageData, fileName, isSentByMe, LocalDateTime.now());
            chatRenderer.scrollToBottom();
        });
    }
    
    public void addVoiceMessage(byte[] audioData, String fileName, boolean isSentByMe) {
        Platform.runLater(() -> {
            chatRenderer.addVoiceMessage(audioData, fileName, isSentByMe, LocalDateTime.now(), null, null, null, false);
            chatRenderer.scrollToBottom();
        });
    }

    public void clearChat() {
        chatRenderer.clearChat();
    }

    public User getCurrentFriend() {
        return currentFriend;
    }

    public void setCurrentFriend(User friend) {
        this.currentFriend = friend;
        this.currentGroup = null;
    }

    public void showChatWithGroup(GroupInfo group, List<Message> messages, int userId, UserDAO userDAO) {
        // Ẩn typing indicator khi chuyển chat
        if (chatRenderer != null) {
            chatRenderer.hideTypingIndicator();
        }
        if (typingIndicatorHideTimer != null) {
            typingIndicatorHideTimer.stop();
            typingIndicatorHideTimer = null;
        }
        
        this.currentGroup = group;
        this.currentFriend = null;
        this.currentUserId = userId;
        this.userDAO = userDAO;

        currentConversationMessages.clear();
        if (messages != null) currentConversationMessages.addAll(messages);

        // Hiển thị header khi chọn chat
        if (chatHeader != null) {
            chatHeader.setVisible(true);
            chatHeader.setManaged(true);
        }

        friendNameLabel.setText("👥 " + group.getName());
        friendStatusLabel.setText(group.getMemberCount() + " thành viên");

        chatRenderer.clearChat();
        loadPinnedMessages(); // Load pinned messages

        if (messages == null || messages.isEmpty()) {
            showEmptyChatMessage("Chưa có tin nhắn nào trong nhóm " + group.getName() + "!\nHãy gửi lời chào đầu tiên nào 😄");
        } else {
            for (Message msg : messages) {
                boolean isOwn = msg.getSenderId() == currentUserId;
                String senderName = null;
                byte[] senderAvatarData = null;
                String senderAvatarUrl = null;
                
                if (!isOwn) {
                    try {
                        User sender = userDAO.getUserById(msg.getSenderId());
                        if (sender != null) {
                            senderName = (sender.getFullName() != null && !sender.getFullName().trim().isEmpty()) 
                                    ? sender.getFullName() 
                                    : sender.getUsername();
                            senderAvatarData = sender.getAvatarData();
                            senderAvatarUrl = sender.getAvatarUrl();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                    chatRenderer.addTextMessage(msg.getContent(), isOwn, msg.getCreatedAt(),
                                               senderName, senderAvatarData, senderAvatarUrl, msg.getIsRead());
                } else if (msg.getFileName() != null && !msg.getFileName().trim().isEmpty()) {
                    // Kiểm tra nếu là ảnh
                    if (ChatRenderer.isAudioFile(msg.getFileName()) && msg.getFileData() != null && msg.getFileData().length > 0) {
                        chatRenderer.addVoiceMessage(msg.getFileData(), msg.getFileName(), isOwn, msg.getCreatedAt(),
                                                     senderName, senderAvatarData, senderAvatarUrl, msg.getIsRead());
                    } else if (ChatRenderer.isImageFile(msg.getFileName()) && msg.getFileData() != null && msg.getFileData().length > 0) {
                        chatRenderer.addImageMessage(msg.getFileData(), msg.getFileName(), isOwn, msg.getCreatedAt(),
                                                    senderName, senderAvatarData, senderAvatarUrl, msg.getIsRead());
                    } else {
                    int fileSize = msg.getFileData() != null ? msg.getFileData().length : 0;
                        chatRenderer.addFileMessage(msg.getFileName(), fileSize, isOwn, msg.getCreatedAt(),
                                                   senderName, senderAvatarData, senderAvatarUrl, msg.getIsRead());
                    }
                }
            }
        }
        chatRenderer.scrollToBottom();
        infoPanelService.configureForGroup(group);
        infoPanelService.updateSharedMediaAndFiles(currentConversationMessages, true);
        
        // Mark group messages as read khi xem chat
        markGroupMessagesAsRead(group.getId());
        
        // Cập nhật badge sau khi đã đọc (reset về 0)
        if (mainController != null) {
            mainController.updateUnreadCountForGroup(group.getId(), 0);
            Platform.runLater(() -> mainController.refreshFriendList());
        }
    }
    
    // Mark group messages as read
    private void markGroupMessagesAsRead(int groupId) {
        if (currentUserId <= 0) return;
        // Gửi request đến server để mark as read
        org.example.zalu.client.ChatClient.sendRequest("MARK_AS_READ|" + currentUserId + "|" + groupId + "|GROUP");
    }
    
    /**
     * Cập nhật read status của các tin nhắn đã gửi khi người nhận đọc
     * @param readerId ID của người đã đọc tin nhắn
     */
    public void updateReadStatus(int readerId) {
        // Chỉ cập nhật nếu đang chat với người đã đọc (readerId)
        if (currentFriend != null && currentFriend.getId() == readerId) {
            Platform.runLater(() -> {
                // Reload messages từ DB để lấy read status mới nhất
                try {
                    List<Message> updatedMessages = messageDAO.getMessagesBetween(currentUserId, readerId);
                    updatedMessages.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
                    
                    // Cập nhật read status trong currentConversationMessages
                    for (Message msg : currentConversationMessages) {
                        if (msg.getSenderId() == currentUserId) {
                            // Tìm message tương ứng trong updatedMessages
                            for (Message updatedMsg : updatedMessages) {
                                if (updatedMsg.getId() == msg.getId()) {
                                    msg.setIsRead(updatedMsg.getIsRead());
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Cập nhật UI: chỉ cập nhật read status ở tin nhắn cuối cùng
                    Message lastOwnMessage = null;
                    for (int i = updatedMessages.size() - 1; i >= 0; i--) {
                        Message msg = updatedMessages.get(i);
                        if (msg.getSenderId() == currentUserId) {
                            lastOwnMessage = msg;
                            break;
                        }
                    }
                    
                    if (lastOwnMessage != null) {
                        byte[] readerAvatarData = (lastOwnMessage.getIsRead()) ? currentFriend.getAvatarData() : null;
                        String readerAvatarUrl = (lastOwnMessage.getIsRead()) ? currentFriend.getAvatarUrl() : null;
                        chatRenderer.updateReadStatusForMessages(currentUserId, lastOwnMessage.getIsRead(), readerAvatarData, readerAvatarUrl);
                    }
                } catch (org.example.zalu.exception.message.MessageException | 
                         org.example.zalu.exception.database.DatabaseException | 
                         org.example.zalu.exception.database.DatabaseConnectionException e) {
                    System.err.println("Lỗi khi cập nhật read status: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    @FXML
    private void toggleInfoPanel() {
        infoPanelService.toggleInfoPanel();
        if (infoPanel != null && infoPanel.isVisible()) {
            if (currentFriend != null) {
                infoPanelService.configureForFriend(currentFriend);
                infoPanelService.updateSharedMediaAndFiles(currentConversationMessages, false);
            } else if (currentGroup != null) {
                infoPanelService.configureForGroup(currentGroup);
                infoPanelService.updateSharedMediaAndFiles(currentConversationMessages, true);
            }
        }
    }

    @FXML
    private void handleMuteConversation() {
        showAlert("Tính năng tắt thông báo sẽ được bổ sung sau.");
    }

    @FXML
    private void handlePinConversation() {
        showAlert("Tính năng ghim hội thoại sẽ được bổ sung sau.");
    }

    @FXML
    private void handleCreateGroupFromFriend() {
        showAlert("Sắp có: tạo nhóm trực tiếp từ cuộc trò chuyện này.");
    }

    @FXML
    private void handleViewBio() {
        if (currentFriend == null) {
            showAlert("Chưa chọn bạn bè để xem hồ sơ.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/profile/bio-view.fxml"));
            Parent root = loader.load();
            BioViewController bioController = loader.getController();
            bioController.setUser(currentFriend);
            
            Stage bioStage = new Stage();
            bioController.setStage(bioStage);
            bioStage.setScene(new Scene(root, 650, 700));
            bioStage.setTitle("Hồ sơ - " + (currentFriend.getFullName() != null && !currentFriend.getFullName().trim().isEmpty() 
                    ? currentFriend.getFullName() : currentFriend.getUsername()));
            bioStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Không thể mở hồ sơ: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewAllMedia() {
        openMediaDialog();
    }
    
    private void openMediaDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/media/media-view.fxml"));
            Parent root = loader.load();
            org.example.zalu.controller.media.MediaViewController controller = loader.getController();
            controller.setMessages(currentConversationMessages, currentGroup != null);
            
            Stage dialogStage = new Stage();
            controller.setDialogStage(dialogStage);
            dialogStage.setScene(new Scene(root));
            dialogStage.setTitle("Ảnh / Video");
            dialogStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Không thể mở dialog media: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewAllFiles() {
        openFilesDialog();
    }
    
    private void openFilesDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/media/files-view.fxml"));
            Parent root = loader.load();
            org.example.zalu.controller.media.FilesViewController controller = loader.getController();
            controller.setMessages(currentConversationMessages, currentGroup != null);
            
            Stage dialogStage = new Stage();
            controller.setDialogStage(dialogStage);
            dialogStage.setScene(new Scene(root));
            dialogStage.setTitle("File");
            dialogStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Không thể mở dialog files: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewAllLinks() {
        openLinksDialog();
    }
    
    private void openLinksDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/media/links-view.fxml"));
            Parent root = loader.load();
            org.example.zalu.controller.media.LinksViewController controller = loader.getController();
            controller.setMessages(currentConversationMessages, currentGroup != null);
            
            Stage dialogStage = new Stage();
            controller.setDialogStage(dialogStage);
            dialogStage.setScene(new Scene(root));
            dialogStage.setTitle("Link");
            dialogStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Không thể mở dialog links: " + e.getMessage());
        }
    }

    @FXML
    private void handleInviteMember() {
        // Redirect to handleAddMember
        handleAddMember();
    }

    @FXML
    private void handleLeaveGroup() {
        if (currentGroup == null) {
            showAlert("Chỉ áp dụng cho nhóm.");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn rời nhóm này?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    boolean success = groupDAO.leaveGroup(currentGroup.getId(), currentUserId);
                    if (success) {
                        showAlert("Bạn đã rời nhóm.");
                        org.example.zalu.client.ChatClient.sendRequest("LEAVE_GROUP|" + currentGroup.getId());
                        // Reload friend list để cập nhật danh sách nhóm
                        if (mainController != null) {
                            mainController.refreshFriendList();
                        }
                    } else {
                        showAlert("Không thể rời nhóm.");
                    }
                } catch (SQLException e) {
                    showAlert("Lỗi khi rời nhóm: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleManageGroup() {
        if (currentGroup == null) {
            showAlert("Chỉ áp dụng cho nhóm.");
            return;
        }
        openManageGroupDialog();
    }
    
    public void handleAddMember() {
        if (currentGroup == null) {
            showAlert("Chỉ áp dụng cho nhóm.");
            return;
        }
        openAddMemberDialog();
    }
    
    private void openAddMemberDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/group/add-member-view.fxml"));
            Parent root = loader.load();
            AddMemberController controller = loader.getController();
            controller.setGroupId(currentGroup.getId());
            controller.setCurrentUserId(currentUserId);
            controller.setOnMemberAdded(() -> {
                // Reload group info và members sau khi thêm thành viên
                if (currentGroup != null) {
                    try {
                        List<org.example.zalu.model.GroupInfo> groups = groupDAO.getUserGroups(currentUserId);
                        for (org.example.zalu.model.GroupInfo group : groups) {
                            if (group.getId() == currentGroup.getId()) {
                                currentGroup = group;
                                infoPanelService.configureForGroup(group);
                                break;
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            });
            
            Stage dialogStage = new Stage();
            controller.setDialogStage(dialogStage);
            dialogStage.setScene(new Scene(root));
            dialogStage.setTitle("Thêm thành viên - " + currentGroup.getName());
            dialogStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Không thể mở dialog thêm thành viên: " + e.getMessage());
        }
    }
    
    private void openManageGroupDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/group/manage-group-view.fxml"));
            Parent root = loader.load();
            ManageGroupController controller = loader.getController();
            controller.setGroupId(currentGroup.getId());
            controller.setCurrentUserId(currentUserId);
            controller.setOnGroupUpdated(() -> {
                // Reload group info và members
                if (currentGroup != null) {
                    try {
                        List<org.example.zalu.model.GroupInfo> groups = groupDAO.getUserGroups(currentUserId);
                        for (org.example.zalu.model.GroupInfo group : groups) {
                            if (group.getId() == currentGroup.getId()) {
                                currentGroup = group;
                                infoPanelService.configureForGroup(group);
                                break;
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            });
            
            Stage dialogStage = new Stage();
            controller.setDialogStage(dialogStage);
            dialogStage.setScene(new Scene(root));
            dialogStage.setTitle("Quản lý nhóm - " + currentGroup.getName());
            dialogStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Không thể mở dialog quản lý nhóm: " + e.getMessage());
        }
    }
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    /**
     * Cập nhật status của friend trong chat header
     */
    public void updateFriendStatus(boolean isOnline) {
        if (chatHeaderService != null && currentFriend != null) {
            chatHeaderService.updateFriendStatus(isOnline);
        }
    }

    @FXML
    private void handleViewAllGroupFiles() {
        openFilesDialog();
    }
    
    @FXML
    private void handleViewAllGroupLinks() {
        openLinksDialog();
    }

    @FXML
    private void handleReportConversation() {
        showAlert("Đã ghi nhận báo cáo của bạn.");
    }

    @FXML
    private void handleClearHistory() {
        showAlert("Xóa lịch sử sẽ được hỗ trợ trong bản sau.");
    }

    @FXML
    private void handleGroupReport() {
        showAlert("Đã ghi nhận báo cáo nhóm.");
    }

    @FXML
    private void handleGroupClearHistory() {
        showAlert("Xóa lịch sử nhóm sẽ được hỗ trợ trong bản sau.");
    }

    @FXML
    private void handleToggleHideConversation() {
        showAlert("Ẩn trò chuyện: " + (hideConversationCheck != null && hideConversationCheck.isSelected() ? "Bật" : "Tắt"));
    }

    @FXML
    private void handleToggleGroupHideConversation() {
        showAlert("Ẩn trò chuyện nhóm: " + (groupHideConversationCheck != null && groupHideConversationCheck.isSelected() ? "Bật" : "Tắt"));
    }
    
    /**
     * Hiển thị typing indicator cho một user
     * @param typingUserId ID của user đang gõ
     */
    public void showTypingIndicator(int typingUserId) {
        // Chỉ hiển thị nếu đang chat với user đó (chat 1-1)
        if (currentFriend == null || currentFriend.getId() != typingUserId) {
            return;
        }
        
        Platform.runLater(() -> {
            try {
                // Lấy thông tin user đang gõ
                User typingUser = userDAO.getUserById(typingUserId);
                if (typingUser == null) return;
                
                // Hiển thị typing indicator
                chatRenderer.showTypingIndicator(null, typingUser.getAvatarData(), typingUser.getAvatarUrl());
                
                // Hủy timer cũ nếu có
                if (typingIndicatorHideTimer != null) {
                    typingIndicatorHideTimer.stop();
                }
                
                // Tạo timer để tự động ẩn sau 3 giây
                typingIndicatorHideTimer = new Timeline(new KeyFrame(Duration.millis(TYPING_INDICATOR_HIDE_DELAY), e -> {
                    hideTypingIndicator(typingUserId);
                }));
                typingIndicatorHideTimer.setCycleCount(1);
                typingIndicatorHideTimer.play();
            } catch (Exception e) {
                System.err.println("Lỗi khi hiển thị typing indicator: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Ẩn typing indicator cho một user
     * @param typingUserId ID của user đã dừng gõ
     */
    public void hideTypingIndicator(int typingUserId) {
        // Chỉ ẩn nếu đang chat với user đó
        if (currentFriend == null || currentFriend.getId() != typingUserId) {
            return;
        }
        
        Platform.runLater(() -> {
            // Hủy timer nếu có
            if (typingIndicatorHideTimer != null) {
                typingIndicatorHideTimer.stop();
                typingIndicatorHideTimer = null;
            }
            
            // Ẩn typing indicator
            chatRenderer.hideTypingIndicator();
        });
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Xử lý message deleted event
     */
    public void handleMessageDeleted(int messageId) {
        updateMessageInUI(messageId, true, false, false, null);
    }
    
    /**
     * Xử lý message recalled event
     */
    public void handleMessageRecalled(int messageId) {
        updateMessageInUI(messageId, false, true, false, null);
    }
    
    /**
     * Xử lý message edited event
     */
    public void handleMessageEdited(int messageId, String newContent) {
        updateMessageInUI(messageId, false, false, true, newContent);
    }
    
    /**
     * Cập nhật message trong UI
     */
    private void updateMessageInUI(int messageId, boolean isDeleted, boolean isRecalled, boolean isEdited, String newContent) {
        Platform.runLater(() -> {
            // Find message in currentConversationMessages and update
            for (Message msg : currentConversationMessages) {
                if (msg.getId() == messageId) {
                    if (isDeleted) {
                        msg.setDeleted(true);
                    }
                    if (isRecalled) {
                        msg.setRecalled(true);
                        msg.setContent("Tin nhắn đã được thu hồi");
                    }
                    if (isEdited && newContent != null) {
                        msg.setEdited(true);
                        msg.setEditedContent(newContent);
                        msg.setContent(newContent);
                    }
                    break;
                }
            }
            
            // Find and update UI node
            for (Node node : chatArea.getChildren()) {
                if (node instanceof HBox) {
                    Object msgIdObj = ((HBox) node).getUserData();
                    if (msgIdObj != null && msgIdObj instanceof Integer && (Integer) msgIdObj == messageId) {
                        // Remove old node and re-render
                        chatArea.getChildren().remove(node);
                        // Reload message from DB and re-render
                        try {
                            Message updatedMsg = messageDAO.getMessageById(messageId);
                            if (updatedMsg != null) {
                                boolean isOwn = updatedMsg.getSenderId() == currentUserId;
                                String senderName = null;
                                byte[] senderAvatarData = null;
                                String senderAvatarUrl = null;
                                
                                if (!isOwn && currentFriend != null) {
                                    senderAvatarData = currentFriend.getAvatarData();
                                    senderAvatarUrl = currentFriend.getAvatarUrl();
                                } else if (!isOwn && currentGroup != null && userDAO != null) {
                                    try {
                                        User sender = userDAO.getUserById(updatedMsg.getSenderId());
                                        if (sender != null) {
                                            senderName = (sender.getFullName() != null && !sender.getFullName().trim().isEmpty()) 
                                                    ? sender.getFullName() : sender.getUsername();
                                            senderAvatarData = sender.getAvatarData();
                                            senderAvatarUrl = sender.getAvatarUrl();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                
                                String displayContent = updatedMsg.isRecalled() ? "Tin nhắn đã được thu hồi" : 
                                                       (updatedMsg.getEditedContent() != null && updatedMsg.isEdited() ? 
                                                        updatedMsg.getEditedContent() : updatedMsg.getContent());
                                
                                chatRenderer.addTextMessage(displayContent, isOwn, updatedMsg.getCreatedAt(),
                                                           senderName, senderAvatarData, senderAvatarUrl, updatedMsg.getIsRead(),
                                                           null, null, updatedMsg.isDeleted(), updatedMsg.isRecalled(), 
                                                           updatedMsg.isEdited(), updatedMsg.getRepliedToContent(), 
                                                           updatedMsg.getRepliedToMessageId() > 0 ? updatedMsg.getRepliedToMessageId() : null);
                            }
                        } catch (Exception e) {
                            System.err.println("Lỗi khi cập nhật message: " + e.getMessage());
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }
        });
    }
    
    // ============================= SEARCH FUNCTIONALITY =============================
    
    @FXML
    private void toggleSearchBar() {
        if (searchBar == null) return;
        boolean isVisible = searchBar.isVisible();
        searchBar.setVisible(!isVisible);
        searchBar.setManaged(!isVisible);
        if (!isVisible) {
            searchField.requestFocus();
        } else {
            handleCloseSearch();
        }
    }
    
    @FXML
    private void handleSearchKeyReleased() {
        // Search khi người dùng gõ
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            clearSearch();
        } else if (!text.equals(currentSearchText)) {
            performSearch(text);
        }
    }
    
    @FXML
    private void handleSearch() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            clearSearch();
        } else {
            performSearch(text);
        }
    }
    
    private void performSearch(String searchText) {
        if (currentFriend == null && currentGroup == null) {
            return;
        }
        
        try {
            List<Message> results;
            if (currentFriend != null) {
                results = messageDAO.searchMessages(currentUserId, currentFriend.getId(), searchText);
            } else {
                results = messageDAO.searchGroupMessages(currentGroup.getId(), searchText);
            }
            
            searchResults = results;
            currentSearchText = searchText;
            currentSearchIndex = -1;
            
            if (results.isEmpty()) {
                searchResultLabel.setText("Không tìm thấy");
                searchPrevButton.setDisable(true);
                searchNextButton.setDisable(true);
                clearHighlights();
            } else {
                searchResultLabel.setText("1/" + results.size());
                searchPrevButton.setDisable(false);
                searchNextButton.setDisable(false);
                highlightSearchResults(searchText);
                handleSearchNext(); // Tự động đi đến kết quả đầu tiên
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi tìm kiếm: " + e.getMessage());
            e.printStackTrace();
            showAlert("Lỗi khi tìm kiếm: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleSearchNext() {
        if (searchResults.isEmpty()) return;
        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size();
        navigateToSearchResult();
    }
    
    @FXML
    private void handleSearchPrevious() {
        if (searchResults.isEmpty()) return;
        currentSearchIndex = (currentSearchIndex - 1 + searchResults.size()) % searchResults.size();
        navigateToSearchResult();
    }
    
    private void navigateToSearchResult() {
        if (currentSearchIndex < 0 || currentSearchIndex >= searchResults.size()) return;
        
        Message msg = searchResults.get(currentSearchIndex);
        searchResultLabel.setText((currentSearchIndex + 1) + "/" + searchResults.size());
        
        // Tìm và scroll đến message node
        for (Node node : chatArea.getChildren()) {
            if (node instanceof HBox) {
                Object msgIdObj = ((HBox) node).getUserData();
                if (msgIdObj != null && msgIdObj instanceof Integer && (Integer) msgIdObj == msg.getId()) {
                    // Scroll đến node này
                    node.requestFocus();
                    chatScrollPane.setVvalue(chatArea.getChildren().indexOf(node) / (double) chatArea.getChildren().size());
                    
                    // Highlight node này
                    highlightNode(node);
                    break;
                }
            }
        }
    }
    
    private void highlightSearchResults(String searchText) {
        clearHighlights();
        
        for (Node node : chatArea.getChildren()) {
            if (node instanceof HBox) {
                Object msgIdObj = ((HBox) node).getUserData();
                if (msgIdObj != null && msgIdObj instanceof Integer) {
                    int msgId = (Integer) msgIdObj;
                    // Kiểm tra xem message này có trong kết quả tìm kiếm không
                    for (Message msg : searchResults) {
                        if (msg.getId() == msgId) {
                            // Highlight text trong message
                            highlightMessageText(node, searchText);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    private void highlightMessageText(Node messageNode, String searchText) {
        if (!(messageNode instanceof HBox)) return;
        
        HBox hbox = (HBox) messageNode;
        for (Node child : hbox.getChildren()) {
            if (child instanceof VBox) {
                VBox vbox = (VBox) child;
                for (Node vChild : vbox.getChildren()) {
                    if (vChild instanceof Label) {
                        Label label = (Label) vChild;
                        String text = label.getText();
                        if (text != null && text.toLowerCase().contains(searchText.toLowerCase())) {
                            // Apply highlight style
                            label.setStyle(label.getStyle() + "; -fx-background-color: #ffeb3b; -fx-background-radius: 3;");
                            highlightedNodes.add(label);
                        }
                    }
                }
            }
        }
    }
    
    private void highlightNode(Node node) {
        // Remove previous highlight
        for (Node n : chatArea.getChildren()) {
            if (n instanceof HBox) {
                ((HBox) n).setStyle(((HBox) n).getStyle().replaceAll("-fx-background-color:\\s*#[0-9a-fA-F]{6};?", ""));
            }
        }
        
        // Add highlight to current node
        if (node instanceof HBox) {
            String currentStyle = ((HBox) node).getStyle();
            if (currentStyle == null) currentStyle = "";
            ((HBox) node).setStyle(currentStyle + "-fx-background-color: #e3f2fd; -fx-background-radius: 8;");
        }
    }
    
    private void clearHighlights() {
        for (Node node : highlightedNodes) {
            if (node instanceof Label) {
                Label label = (Label) node;
                String style = label.getStyle();
                if (style != null) {
                    style = style.replaceAll("-fx-background-color:\\s*#[0-9a-fA-F]{6};?", "");
                    style = style.replaceAll("-fx-background-radius:\\s*\\d+;?", "");
                    label.setStyle(style);
                }
            }
        }
        highlightedNodes.clear();
        
        // Clear node highlights
        for (Node node : chatArea.getChildren()) {
            if (node instanceof HBox) {
                String style = ((HBox) node).getStyle();
                if (style != null) {
                    style = style.replaceAll("-fx-background-color:\\s*#[0-9a-fA-F]{6};?", "");
                    style = style.replaceAll("-fx-background-radius:\\s*\\d+;?", "");
                    ((HBox) node).setStyle(style);
                }
            }
        }
    }
    
    @FXML
    private void handleCloseSearch() {
        searchBar.setVisible(false);
        searchBar.setManaged(false);
        searchField.clear();
        clearSearch();
    }
    
    private void clearSearch() {
        searchResults.clear();
        currentSearchIndex = -1;
        currentSearchText = "";
        searchResultLabel.setText("0/0");
        searchPrevButton.setDisable(true);
        searchNextButton.setDisable(true);
        clearHighlights();
    }
    
    // ============================= PIN MESSAGES FUNCTIONALITY =============================
    
    private void loadPinnedMessages() {
        if (currentFriend == null && currentGroup == null) {
            if (pinnedMessagesSection != null) {
                pinnedMessagesSection.setVisible(false);
                pinnedMessagesSection.setManaged(false);
            }
            return;
        }
        
        if (pinnedMessagesSection == null || pinnedMessagesContainer == null) {
            return; // UI chưa được khởi tạo
        }
        
        try {
            List<Message> pinned;
            if (currentFriend != null) {
                pinned = messageDAO.getPinnedMessages(currentUserId, currentFriend.getId());
            } else {
                pinned = messageDAO.getPinnedGroupMessages(currentGroup.getId());
            }
            
            pinnedMessages.clear();
            if (pinned != null) {
                pinnedMessages.addAll(pinned);
            }
            
            if (pinnedMessages.isEmpty()) {
                pinnedMessagesSection.setVisible(false);
                pinnedMessagesSection.setManaged(false);
            } else {
                pinnedMessagesSection.setVisible(true);
                pinnedMessagesSection.setManaged(true);
                displayPinnedMessages();
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi tải tin nhắn đã ghim: " + e.getMessage());
            e.printStackTrace();
            // Ẩn section nếu có lỗi
            if (pinnedMessagesSection != null) {
                pinnedMessagesSection.setVisible(false);
                pinnedMessagesSection.setManaged(false);
            }
        }
    }
    
    private void displayPinnedMessages() {
        pinnedMessagesContainer.getChildren().clear();
        
        for (Message msg : pinnedMessages) {
            HBox pinnedItem = new HBox(8);
            pinnedItem.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 8; -fx-padding: 8;");
            pinnedItem.setAlignment(Pos.CENTER_LEFT);
            
            Label pinIcon = IconUtil.getPinIcon(18);
            
            String displayContent = msg.getContent();
            boolean hasAttachment = false;
            if (displayContent == null || displayContent.isEmpty()) {
                String fileName = msg.getFileName();
                if (fileName != null) {
                    displayContent = fileName;
                    hasAttachment = true;
                } else {
                    displayContent = "Tin nhắn";
                }
            }
            if (displayContent.length() > 50) {
                displayContent = displayContent.substring(0, 47) + "...";
            }
            
            HBox contentBox = new HBox(4);
            contentBox.setAlignment(Pos.CENTER_LEFT);
            if (hasAttachment) {
                Label attachIcon = IconUtil.getAttachmentIcon(14);
                contentBox.getChildren().add(attachIcon);
            }
            Label contentLabel = new Label(displayContent);
            contentLabel.setStyle("-fx-font-size: 12px;");
            contentBox.getChildren().add(contentLabel);
            
            Label unpinIcon = IconUtil.getCloseIcon(16);
            Button unpinButton = new Button();
            unpinButton.setGraphic(unpinIcon);
            unpinButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #666;");
            unpinButton.setOnAction(e -> handleUnpinMessage(msg.getId()));
            
            pinnedItem.getChildren().addAll(pinIcon, contentBox, new Region(), unpinButton);
            HBox.setHgrow(contentLabel, Priority.ALWAYS);
            
            pinnedItem.setOnMouseClicked(e -> {
                // Scroll đến message này trong chat
                scrollToMessage(msg.getId());
            });
            
            pinnedMessagesContainer.getChildren().add(pinnedItem);
        }
    }
    
    @FXML
    private void handleHidePinnedMessages() {
        pinnedMessagesSection.setVisible(false);
        pinnedMessagesSection.setManaged(false);
    }
    
    private void scrollToMessage(int messageId) {
        for (Node node : chatArea.getChildren()) {
            if (node instanceof HBox) {
                Object msgIdObj = ((HBox) node).getUserData();
                if (msgIdObj != null && msgIdObj instanceof Integer && (Integer) msgIdObj == messageId) {
                    node.requestFocus();
                    double index = chatArea.getChildren().indexOf(node);
                    double total = chatArea.getChildren().size();
                    if (total > 0) {
                        chatScrollPane.setVvalue(index / total);
                    }
                    highlightNode(node);
                    break;
                }
            }
        }
    }
    
    public void handlePinMessage(int messageId) {
        try {
            boolean success = messageDAO.pinMessage(messageId, true);
            if (success) {
                // Update message in list
                for (Message msg : currentConversationMessages) {
                    if (msg.getId() == messageId) {
                        msg.setPinned(true);
                        break;
                    }
                }
                loadPinnedMessages();
                showAlert("Đã ghim tin nhắn");
            } else {
                showAlert("Không thể ghim tin nhắn (có thể database chưa hỗ trợ)");
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi ghim tin nhắn: " + e.getMessage());
            e.printStackTrace();
            showAlert("Lỗi khi ghim tin nhắn: " + e.getMessage());
        }
    }
    
    private void handleUnpinMessage(int messageId) {
        try {
            boolean success = messageDAO.pinMessage(messageId, false);
            if (success) {
                // Update message in list
                for (Message msg : currentConversationMessages) {
                    if (msg.getId() == messageId) {
                        msg.setPinned(false);
                        break;
                    }
                }
                loadPinnedMessages();
            } else {
                showAlert("Không thể bỏ ghim tin nhắn");
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi bỏ ghim tin nhắn: " + e.getMessage());
            e.printStackTrace();
            showAlert("Lỗi khi bỏ ghim tin nhắn: " + e.getMessage());
        }
    }
}
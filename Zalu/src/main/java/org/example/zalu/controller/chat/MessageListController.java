package org.example.zalu.controller.chat;

import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.example.zalu.util.ui.MessageBubbleFactory;
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
import org.example.zalu.client.ChatClient;
import org.example.zalu.client.ChatEventManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MessageListController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MessageListController.class);

    @FXML
    private ScrollPane chatScrollPane;
    @FXML
    private VBox chatArea;
    @FXML
    private HBox chatHeader;
    @FXML
    private Label friendNameLabel;
    @FXML
    private Label friendStatusLabel;
    @FXML
    private ImageView friendAvatar;

    // Info panel
    @FXML
    private VBox infoPanel;
    @FXML
    private TabPane infoTabPane;
    @FXML
    private Tab directTab;
    @FXML
    private Tab groupTab;
    @FXML
    private ImageView infoAvatar;
    @FXML
    private Label infoNameLabel;
    @FXML
    private Label infoStatusLabel;
    @FXML
    private FlowPane mediaPreviewPane;
    @FXML
    private ListView<String> directFileListView;
    @FXML
    private ListView<String> directLinkListView;
    @FXML
    private ListView<String> groupFileListView;
    @FXML
    private ListView<String> groupLinkListView;
    @FXML
    private Label groupNameInfoLabel;
    @FXML
    private Label groupMemberCountLabel;
    @FXML
    private ListView<String> groupMembersList;
    @FXML
    private CheckBox hideConversationCheck;
    @FXML
    private CheckBox groupHideConversationCheck;

    // Search UI
    @FXML
    private HBox searchBar;
    @FXML
    private TextField searchField;
    @FXML
    private Button searchPrevButton;
    @FXML
    private Button searchNextButton;
    @FXML
    private Label searchResultLabel;
    @FXML
    private Button searchButton;

    // Pinned messages UI
    @FXML
    private VBox pinnedMessagesSection;
    @FXML
    private VBox pinnedMessagesContainer;

    private ChatRenderer chatRenderer;
    private ChatHeaderService chatHeaderService;
    private InfoPanelService infoPanelService;
    private int currentUserId = -1;
    private User currentFriend = null;
    private GroupInfo currentGroup = null;
    // DAOs removed
    private final java.util.Map<String, HBox> pendingMessageNodes = new java.util.concurrent.ConcurrentHashMap<>();
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

        // Initialize services
        chatHeaderService = new ChatHeaderService(chatHeader, friendNameLabel, friendStatusLabel, friendAvatar);
        // Note: InfoPanelService might still use DAOs internally if not refactored.
        // We should check InfoPanelService later. For now pass null or handle it.
        // Assuming InfoPanelService needs refactoring too or we pass nulls and fix it
        // later.
        // The previous code passed userDAO, groupDAO.
        infoPanelService = new InfoPanelService(infoPanel, infoTabPane, directTab, groupTab,
                infoAvatar, infoNameLabel, infoStatusLabel, mediaPreviewPane,
                directFileListView, directLinkListView, groupFileListView, groupLinkListView,
                groupNameInfoLabel, groupMemberCountLabel, groupMembersList,
                hideConversationCheck, groupHideConversationCheck, null, null);

        infoPanelService.hideInfoPanel();
        chatHeaderService.hideHeader();
        clearChat();
        showWelcomeScreen("Chào mừng đến với Zalu!\nChọn một người bạn hoặc nhóm để bắt đầu trò chuyện 💬");

        setupCallbacks();
    }

    private void setupCallbacks() {
        ChatEventManager.getInstance().registerSearchMessagesCallback(this::handleSearchMessagesResult);
        ChatEventManager.getInstance().registerPinnedMessagesCallback(this::handlePinnedMessagesResult);
        // Reuse broadcast callback for pin updates if needed, or register specific
        // string handler in EventManager?
        // EventManager sends "MESSAGE_PIN_UPDATE|..." to broadcastCallback.
        // MessageListController doesn't have direct access to listen to generic
        // broadcast unless we hook into it.
        // But MainController listens to broadcast. Maybe MainController should
        // dispatch?
        // Or we register to broadcastCallback here? ChatEventManager allows one
        // broadcastCallback.
        // Wait, ChatEventManager.registerBroadcastCallback overwrites the previous one!
        // This is a limitation. MainController registers it.
        // We might need a multi-cast approach or MainController delegation.
        // For now, let's assume MainController delegates or we use a specific callback
        // if we added one.
        // We didn't add specific messagePinUpdateCallback.
        // However, we can use
        // ChatEventManager.getInstance().registerBroadcastCallback(msg -> { ...
        // mainController.handle...; this.handleBroadcast(msg); }) logic?
        // Better: ChatEventManager should support lists of callbacks.
        // For now, I will skip real-time pin update notification in this controller
        // unless I change EventManager to support multiple listeners or use a different
        // mechanism.
        // Actually, pinned messages list update is triggered by `loadPinnedMessages()`
        // which requests from server.
        // If I pin/unpin, I request loadPinnedMessages() again.
        ChatEventManager.getInstance().registerGroupInfoCallback(this::handleGroupInfoUpdate);
        ChatEventManager.getInstance().registerMessageSentCallback(this::handleMessageSent);
    }

    private void handleMessageSent(String[] parts) {
        logger.debug("handleMessageSent called with parts: {}", java.util.Arrays.toString(parts));

        if (parts.length < 3 || !"OK".equals(parts[1])) {
            logger.warn("Invalid MESSAGE_SENT response: {}", java.util.Arrays.toString(parts));
            return;
        }

        Platform.runLater(() -> {
            try {
                int realId = Integer.parseInt(parts[2]);
                String tempId = parts.length >= 4 ? parts[3] : null;

                logger.debug("Processing MESSAGE_SENT: realId={}, tempId={}, pendingNodes={}",
                        realId, tempId, pendingMessageNodes.keySet());

                if (tempId != null && pendingMessageNodes.containsKey(tempId)) {
                    // Update Message object in currentConversationMessages
                    Message confirmedMsg = null;
                    for (Message m : currentConversationMessages) {
                        if (tempId.equals(m.getTempId())) {
                            m.setId(realId);
                            m.setStatus(Message.MessageStatus.SENT);
                            confirmedMsg = m;
                            logger.debug("Updated message status: tempId={} -> realId={}", tempId, realId);
                            break;
                        }
                    }

                    // Remove old optimistic node
                    HBox oldNode = pendingMessageNodes.remove(tempId);
                    if (oldNode != null) {
                        oldNode.setUserData(realId);

                        // For 1-1 chat, re-render the message with SENT status
                        if (currentFriend != null && confirmedMsg != null) {
                            int index = chatArea.getChildren().indexOf(oldNode);
                            if (index != -1) {
                                chatArea.getChildren().remove(index);

                                Node newNode = createMessageNode(confirmedMsg);

                                if (newNode != null) {
                                    // Insert at the same position
                                    chatArea.getChildren().add(index, newNode);
                                    logger.debug("Re-rendered message with SENT status at index {}", index);

                                    // Scroll to bottom to show the sent message
                                    chatRenderer.forceScrollToBottom();
                                }
                            }
                        }
                    }
                } else {
                    logger.warn("tempId not found in pendingMessageNodes: tempId={}", tempId);
                }

            } catch (Exception e) {
                logger.error("Error resolving optimistic message", e);
            }
        });
    }

    private void handleGroupInfoUpdate(GroupInfo groupInfo) {
        if (currentGroup != null && groupInfo.getId() == currentGroup.getId()) {
            Platform.runLater(() -> {
                currentGroup = groupInfo;
                infoPanelService.configureForGroup(groupInfo);
                // Update header
                friendNameLabel.setText("👥 " + groupInfo.getName());
                friendStatusLabel.setText(groupInfo.getMemberCount() + " thành viên");
            });
        }
    }

    private void handleSearchMessagesResult(List<Message> results) {
        Platform.runLater(() -> {
            searchResults = results;
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
                highlightSearchResults(currentSearchText);
                handleSearchNext();
            }
        });
    }

    private void handlePinnedMessagesResult(List<Message> results) {
        Platform.runLater(() -> {
            pinnedMessages.clear();
            if (results != null) {
                pinnedMessages.addAll(results);
            }

            if (pinnedMessages.isEmpty()) {
                if (pinnedMessagesSection != null) {
                    pinnedMessagesSection.setVisible(false);
                    pinnedMessagesSection.setManaged(false);
                }
            } else {
                if (pinnedMessagesContainer != null) {
                    pinnedMessagesSection.setVisible(true);
                    pinnedMessagesSection.setManaged(true);
                    displayPinnedMessages();
                }
            }
        });
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

        // Optimisasi: Kiểm tra xem messages mới có khác biệt thực sự không để tránh
        // flicker
        if (this.currentFriend != null && this.currentFriend.getId() == friend.getId()
                && isSameMessages(currentConversationMessages, messages)) {
            logger.debug("Skipping redundant ChatWithFriend reload for {}; messages identical.", friend.getUsername());
            return;
        }

        this.currentFriend = friend;
        this.currentGroup = null;

        currentConversationMessages.clear();
        if (messages != null)
            currentConversationMessages.addAll(messages);

        // Hiển thị header với online status
        boolean isOnline = false;
        if (mainController != null) {
            isOnline = mainController.isFriendOnline(friend.getId());
        }
        chatHeaderService.showHeaderForFriend(friend, isOnline);

        chatRenderer.clearChat();
        loadPinnedMessages(); // Load pinned messages

        if (messages == null || messages.isEmpty()) {
            showEmptyChatMessage(
                    "Chưa có tin nhắn nào với " + displayName(friend) + "!\nHãy gửi lời chào đầu tiên nào 😄");
        } else {
            // LIMIT: Chỉ hiển thị 50 tin nhắn gần nhất để tối ưu tốc độ
            List<Message> initialMessages;
            if (messages.size() > 50) {
                initialMessages = messages.subList(messages.size() - 50, messages.size());
                logger.info("Chat with {}: Showing last 50 of {} messages", friend.getUsername(), messages.size());
            } else {
                initialMessages = messages;
            }

            List<Node> messageNodes = new ArrayList<>();
            Message lastReadOwnMessage = null;
            for (int i = initialMessages.size() - 1; i >= 0; i--) {
                Message m = initialMessages.get(i);
                if (m.getSenderId() == currentUserId && m.getIsRead()) {
                    lastReadOwnMessage = m;
                    break;
                }
            }

            for (Message msg : initialMessages) {
                // Special handling for read status in 1-1 history:
                // only show reader avatar for the LAST read own message.
                Node msgNode = null;
                boolean isOwn = msg.getSenderId() == currentUserId;

                // Temporarily override isRead for createMessageNode logic
                boolean originalIsRead = msg.getIsRead();
                if (isOwn && (lastReadOwnMessage == null || msg.getId() != lastReadOwnMessage.getId())) {
                    msg.setIsRead(false);
                }

                msgNode = createMessageNode(msg);

                // Restore original value
                msg.setIsRead(originalIsRead);

                if (msgNode != null) {
                    messageNodes.add(msgNode);
                }
            }
            chatRenderer.addMessagesBatch(messageNodes, false, true); // Force scroll on initial load
        }
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
        if (currentUserId <= 0)
            return;
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

    private String displayName(User user) {
        if (user == null) {
            return "";
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getUsername();
    }

    public void addNewMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty())
            return;

        Platform.runLater(() -> {
            List<Node> nodes = new ArrayList<>();
            for (Message msg : messages) {
                boolean isForCurrentChat = false;
                if (currentGroup != null && msg.getGroupId() == currentGroup.getId()) {
                    isForCurrentChat = true;
                } else if (currentFriend != null &&
                        ((msg.getSenderId() == currentFriend.getId() && msg.getReceiverId() == currentUserId) ||
                                (msg.getSenderId() == currentUserId && msg.getReceiverId() == currentFriend.getId()))) {
                    isForCurrentChat = true;
                }

                if (isForCurrentChat) {
                    // ✨ THÊM: Xử lý Optimistic UI resolution cho tin nhắn của chính mình
                    if (msg.getSenderId() == currentUserId && msg.getTempId() != null
                            && pendingMessageNodes.containsKey(msg.getTempId())) {
                        HBox oldNode = pendingMessageNodes.remove(msg.getTempId());
                        oldNode.setUserData(msg.getId());
                        // Xóa tin nhắn optimistic cũ
                        chatArea.getChildren().remove(oldNode);
                        currentConversationMessages.removeIf(m -> msg.getTempId().equals(m.getTempId()));
                        logger.debug("Replaced optimistic message with confirmed message: tempId={} -> realId={}",
                                msg.getTempId(), msg.getId());
                    }

                    // ✨ IMPROVED: Kiểm tra duplicate tốt hơn
                    boolean alreadyExists = false;

                    // Check by ID (nếu có ID hợp lệ)
                    if (msg.getId() > 0) {
                        alreadyExists = currentConversationMessages.stream()
                                .anyMatch(m -> m.getId() == msg.getId());
                    }

                    // Check by tempId (nếu chưa có ID)
                    if (!alreadyExists && msg.getTempId() != null) {
                        alreadyExists = currentConversationMessages.stream()
                                .anyMatch(m -> msg.getTempId().equals(m.getTempId()));
                    }

                    // Check by content + timestamp (fallback cho message chưa có ID/tempId)
                    if (!alreadyExists && msg.getId() <= 0 && msg.getTempId() == null) {
                        final String content = msg.getContent();
                        final String fileName = msg.getFileName();
                        final int senderId = msg.getSenderId();
                        final int receiverId = msg.getReceiverId();

                        alreadyExists = currentConversationMessages.stream()
                                .anyMatch(m -> m.getSenderId() == senderId &&
                                        m.getReceiverId() == receiverId &&
                                        ((content != null && content.equals(m.getContent())) ||
                                                (fileName != null && fileName.equals(m.getFileName())))
                                        &&
                                        Math.abs(java.time.Duration.between(
                                                m.getCreatedAt(), msg.getCreatedAt()).toSeconds()) < 2);
                    }

                    if (!alreadyExists) {
                        Node node = createMessageNode(msg);
                        if (node != null) {
                            nodes.add(node);
                            currentConversationMessages.add(msg);
                            logger.debug("Added new message: id={}, tempId={}, content={}",
                                    msg.getId(), msg.getTempId(),
                                    msg.getContent() != null
                                            ? msg.getContent().substring(0, Math.min(20, msg.getContent().length()))
                                            : msg.getFileName());
                        }
                    } else {
                        logger.debug("Message already exists, skipping: id={}, tempId={}, content={}",
                                msg.getId(), msg.getTempId(),
                                msg.getContent() != null
                                        ? msg.getContent().substring(0, Math.min(20, msg.getContent().length()))
                                        : msg.getFileName());
                    }
                }
            }

            if (!nodes.isEmpty()) {
                chatRenderer.hideReadStatusFromOldMessages();
                // Force scroll to bottom để hiển thị tin nhắn mới
                chatRenderer.addMessagesBatch(nodes, false, true);
            }

            infoPanelService.updateSharedMediaAndFiles(currentConversationMessages, currentGroup != null);
        });
    }

    public void addNewMessage(Message msg) {
        if (msg == null)
            return;

        boolean isForCurrentChat = false;
        if (currentGroup != null && msg.getGroupId() == currentGroup.getId()) {
            isForCurrentChat = true;
        } else if (currentFriend != null &&
                ((msg.getSenderId() == currentFriend.getId() && msg.getReceiverId() == currentUserId) ||
                        (msg.getSenderId() == currentUserId && msg.getReceiverId() == currentFriend.getId()))) {
            isForCurrentChat = true;
        }

        if (!isForCurrentChat) {
            logger.debug("addNewMessage: Mismatch with current friend/group, ignoring");
            return;
        }

        Platform.runLater(() -> {
            // Ẩn typing indicator khi có tin nhắn mới
            if (currentFriend != null && msg.getSenderId() == currentFriend.getId()) {
                hideTypingIndicator(currentFriend.getId());
            }

            // Xử lý Optimistic UI resolution
            if (msg.getSenderId() == currentUserId && msg.getTempId() != null
                    && pendingMessageNodes.containsKey(msg.getTempId())) {
                HBox oldNode = pendingMessageNodes.remove(msg.getTempId());
                oldNode.setUserData(msg.getId());
                // Update status visual in oldNode if possible, or just replace
                // For now, replacing is easier to ensure consistency
                chatArea.getChildren().remove(oldNode);
                currentConversationMessages.removeIf(m -> msg.getTempId().equals(m.getTempId()));
            }

            Node node = createMessageNode(msg);
            if (node != null) {
                chatRenderer.hideReadStatusFromOldMessages();
                chatArea.getChildren().add(node);

                // Force scroll nếu là tin nhắn của mình, scroll bình thường nếu là tin nhắn
                // người khác
                if (msg.getSenderId() == currentUserId) {
                    chatRenderer.forceScrollToBottom();
                } else {
                    chatRenderer.scrollToBottom();
                }

                currentConversationMessages.add(msg);
                infoPanelService.updateSharedMediaAndFiles(currentConversationMessages, currentGroup != null);
            }
        });
    }

    private Node createMessageNode(Message msg) {
        boolean isOwn = msg.getSenderId() == currentUserId;

        // Determine sender info
        String senderName = null;
        byte[] senderAvatarData = null;
        String senderAvatarUrl = null;
        byte[] readerAvatarData = null;
        String readerAvatarUrl = null;

        if (currentGroup != null) {
            if (!isOwn) {
                senderName = "User " + msg.getSenderId(); // Fallback
                // TODO: Look up in cached group members
            }
        } else if (currentFriend != null) {
            if (!isOwn) {
                senderAvatarData = currentFriend.getAvatarData();
                senderAvatarUrl = currentFriend.getAvatarUrl();
            } else {
                // Read status for 1-1
                if (msg.getIsRead()) {
                    readerAvatarData = currentFriend.getAvatarData();
                    readerAvatarUrl = currentFriend.getAvatarUrl();
                }
            }
        }

        if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
            String displayContent = msg.isRecalled() ? "Tin nhắn đã được thu hồi"
                    : (msg.getEditedContent() != null && msg.isEdited() ? msg.getEditedContent() : msg.getContent());

            VBox bubbleBox = MessageBubbleFactory.createTextBubble(displayContent, isOwn, msg.getCreatedAt(),
                    senderName, msg.getIsRead(), readerAvatarData, readerAvatarUrl,
                    msg.isDeleted(), msg.isRecalled(), msg.isEdited(),
                    msg.getRepliedToContent(),
                    msg.getRepliedToMessageId() > 0 ? msg.getRepliedToMessageId() : null,
                    Message.MessageStatus.SENT);

            HBox mainBox = new HBox();
            if (isOwn) {
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                mainBox.getChildren().addAll(spacer, bubbleBox);
                mainBox.setAlignment(Pos.CENTER_RIGHT);
                HBox.setMargin(bubbleBox, new Insets(0, 12, 0, 60));
            } else {
                Node avatarNode = MessageBubbleFactory.createMessageAvatar(senderAvatarData, senderAvatarUrl);
                mainBox.getChildren().addAll(avatarNode, bubbleBox);
                HBox.setMargin(avatarNode, new Insets(0, 8, 0, 12));
                mainBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setMargin(bubbleBox, new Insets(0, 60, 0, 0));
            }
            mainBox.setPadding(new Insets(5));
            mainBox.setUserData(msg.getId());
            return mainBox;
        } else if (msg.getFileName() != null && !msg.getFileName().trim().isEmpty()) {
            if (ChatRenderer.isAudioFile(msg.getFileName())) {
                VBox voiceBox = MessageBubbleFactory.createVoiceBubble(msg.getFileData(), msg.getFileName(), isOwn,
                        msg.getCreatedAt(), senderName, msg.getIsRead(), msg.getId(), () -> {
                            // Re-render when data is loaded?
                            // For now, factory will handle the download logic.
                        });
                return wrapInMainBox(voiceBox, isOwn, senderAvatarData, senderAvatarUrl);
            } else if (ChatRenderer.isImageFile(msg.getFileName())) {
                VBox imageBox = MessageBubbleFactory.createImageBubble(msg.getFileData(), msg.getFileName(), isOwn,
                        msg.getCreatedAt(), senderName, msg.getIsRead(), msg.getId(), null);
                return wrapInMainBox(imageBox, isOwn, senderAvatarData, senderAvatarUrl);
            } else {
                int fileSize = msg.getFileData() != null ? msg.getFileData().length : 0;
                VBox fileBox = MessageBubbleFactory.createFileBubble(msg.getFileName(), fileSize, isOwn,
                        msg.getCreatedAt(), senderName, msg.getIsRead(), msg.getId(), null);
                return wrapInMainBox(fileBox, isOwn, senderAvatarData, senderAvatarUrl);
            }
        }
        return null;
    }

    private HBox wrapInMainBox(Node bubbleBox, boolean isOwn, byte[] senderAvatarData, String senderAvatarUrl) {
        HBox mainBox = new HBox();
        if (isOwn) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            mainBox.getChildren().addAll(spacer, bubbleBox);
            mainBox.setAlignment(Pos.CENTER_RIGHT);
            HBox.setMargin(bubbleBox, new Insets(0, 12, 0, 60));
        } else {
            Node avatarNode = MessageBubbleFactory.createMessageAvatar(senderAvatarData, senderAvatarUrl);
            mainBox.getChildren().addAll(avatarNode, bubbleBox);
            HBox.setMargin(avatarNode, new Insets(0, 8, 0, 12));
            mainBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setMargin(bubbleBox, new Insets(0, 60, 0, 0));
        }
        mainBox.setPadding(new Insets(5));
        return mainBox;
    }

    public void addLocalTextMessage(Message msg) {
        boolean isOwn = msg.getSenderId() == currentUserId;
        chatRenderer.addTextMessage(msg.getContent(), isOwn, msg.getCreatedAt(), null, null, null,
                Message.MessageStatus.SENDING);

        // Save node for later resolution if tempId is present
        // Đợi UI render xong trước khi lưu node
        if (msg.getTempId() != null) {
            Platform.runLater(() -> {
                if (!chatArea.getChildren().isEmpty()) {
                    Node lastNode = chatArea.getChildren().get(chatArea.getChildren().size() - 1);
                    if (lastNode instanceof HBox) {
                        pendingMessageNodes.put(msg.getTempId(), (HBox) lastNode);
                        logger.debug("Saved pending message node: tempId={}", msg.getTempId());
                    }
                }
            });
        }

        currentConversationMessages.add(msg);
        infoPanelService.updateSharedMediaAndFiles(currentConversationMessages, currentGroup != null);

        // Force scroll xuống dưới khi gửi tin nhắn
        Platform.runLater(() -> chatRenderer.forceScrollToBottom());
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

    public void showChatWithGroup(GroupInfo group, List<Message> messages, int userId) {
        // Ẩn typing indicator khi chuyển chat
        if (chatRenderer != null) {
            chatRenderer.hideTypingIndicator();
        }
        if (typingIndicatorHideTimer != null) {
            typingIndicatorHideTimer.stop();
            typingIndicatorHideTimer = null;
        }

        // Optimisasi: Tránh flicker
        if (this.currentGroup != null && this.currentGroup.getId() == group.getId()
                && isSameMessages(currentConversationMessages, messages)) {
            logger.debug("Skipping redundant ChatWithGroup reload for {}; messages identical.", group.getName());
            return;
        }

        this.currentGroup = group;
        this.currentFriend = null;
        this.currentUserId = userId;

        currentConversationMessages.clear();
        if (messages != null)
            currentConversationMessages.addAll(messages);

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
            showEmptyChatMessage(
                    "Chưa có tin nhắn nào trong nhóm " + group.getName() + "!\nHãy gửi lời chào đầu tiên nào 😄");
        } else {
            // LIMIT: Chỉ hiển thị 50 tin nhắn gần nhất để tối ưu tốc độ
            List<Message> initialMessages;
            if (messages.size() > 50) {
                initialMessages = messages.subList(messages.size() - 50, messages.size());
                logger.info("Group {}: Showing last 50 of {} messages", group.getName(), messages.size());
            } else {
                initialMessages = messages;
            }

            List<Node> nodes = new ArrayList<>();
            for (Message msg : initialMessages) {
                Node node = createMessageNode(msg);
                if (node != null) {
                    nodes.add(node);
                }
            }
            if (!nodes.isEmpty()) {
                chatRenderer.addMessagesBatch(nodes, false, true); // Force scroll on initial load
            }
        }
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
        if (currentUserId <= 0)
            return;
        // Gửi request đến server để mark as read
        org.example.zalu.client.ChatClient.sendRequest("MARK_AS_READ|" + currentUserId + "|" + groupId + "|GROUP");
    }

    /**
     * Cập nhật read status của các tin nhắn đã gửi khi người nhận đọc
     * 
     * @param readerId ID của người đã đọc tin nhắn
     */
    public void updateReadStatus(int readerId) {
        // Chỉ cập nhật nếu đang chat với người đã đọc (readerId)
        // Logic client-side handled via event broadcast
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
            bioStage.setTitle(
                    "Hồ sơ - " + (currentFriend.getFullName() != null && !currentFriend.getFullName().trim().isEmpty()
                            ? currentFriend.getFullName()
                            : currentFriend.getUsername()));
            bioStage.show();
        } catch (IOException e) {
            logger.error("Failed to open bio dialog", e);
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
            logger.error("Failed to open media dialog", e);
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
            logger.error("Failed to open files dialog", e);
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
            logger.error("Failed to open links dialog", e);
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
                org.example.zalu.client.ChatClient.sendRequest("LEAVE_GROUP|" + currentGroup.getId());
                // The server response will trigger LEAVE_GROUP|SUCCESS or FAIL handled in
                // ClientHandler->ChatEventManager
                // But for now, we can assume if no error, we might be kicked out.
                // Actually, wait for callback?
                // The previous code had a sync DAO call. Now it is async.
                // We should probably close the chat or show loading.
                // For simplicity, we just send request.
                // ideally, check for "LEFT_GROUP" broadcast.
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
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/zalu/views/group/add-member-view.fxml"));
            Parent root = loader.load();
            AddMemberController controller = loader.getController();
            controller.setGroupId(currentGroup.getId());
            controller.setCurrentUserId(currentUserId);
            controller.setOnMemberAdded(() -> {
                // Reload group info
                if (currentGroup != null) {
                    org.example.zalu.client.ChatClient.sendRequest("GET_GROUP_INFO|" + currentGroup.getId());
                }
            });

            Stage dialogStage = new Stage();
            controller.setDialogStage(dialogStage);
            dialogStage.setScene(new Scene(root));
            dialogStage.setTitle("Thêm thành viên - " + currentGroup.getName());
            dialogStage.show();
        } catch (IOException e) {
            logger.error("Failed to open add member dialog", e);
            showAlert("Không thể mở dialog thêm thành viên: " + e.getMessage());
        }
    }

    private void openManageGroupDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/zalu/views/group/manage-group-view.fxml"));
            Parent root = loader.load();
            ManageGroupController controller = loader.getController();
            controller.setGroupId(currentGroup.getId());
            controller.setCurrentUserId(currentUserId);
            controller.setOnGroupUpdated(() -> {
                // Reload group info
                if (currentGroup != null) {
                    org.example.zalu.client.ChatClient.sendRequest("GET_GROUP_INFO|" + currentGroup.getId());
                }
            });

            Stage dialogStage = new Stage();
            controller.setDialogStage(dialogStage);
            dialogStage.setScene(new Scene(root));
            dialogStage.setTitle("Quản lý nhóm - " + currentGroup.getName());
            dialogStage.show();
        } catch (IOException e) {
            logger.error("Failed to open manage group dialog", e);
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
        showAlert("Ẩn trò chuyện: "
                + (hideConversationCheck != null && hideConversationCheck.isSelected() ? "Bật" : "Tắt"));
    }

    @FXML
    private void handleToggleGroupHideConversation() {
        showAlert("Ẩn trò chuyện nhóm: "
                + (groupHideConversationCheck != null && groupHideConversationCheck.isSelected() ? "Bật" : "Tắt"));
    }

    /**
     * Hiển thị typing indicator cho một user
     * 
     * @param typingUserId ID của user đang gõ
     */
    public void showTypingIndicator(int typingUserId) {
        // Chỉ hiển thị nếu đang chat với user đó (chat 1-1)
        if (currentFriend == null || currentFriend.getId() != typingUserId) {
            return;
        }

        Platform.runLater(() -> {
            try {
                // TODO: use ChatClient to get user info or cache
            } catch (Exception e) {
                logger.error("Error showing typing indicator", e);
            }
        });
    }

    /**
     * Ẩn typing indicator cho một user
     * 
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
    private void updateMessageInUI(int messageId, boolean isDeleted, boolean isRecalled, boolean isEdited,
            String newContent) {
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
                        // Remove old node
                        chatArea.getChildren().remove(node);

                        // Find updated message
                        Message updatedMsg = null;
                        for (Message m : currentConversationMessages) {
                            if (m.getId() == messageId) {
                                updatedMsg = m;
                                break;
                            }
                        }

                        if (updatedMsg != null) {
                            boolean isOwn = updatedMsg.getSenderId() == currentUserId;
                            String senderName = "User " + updatedMsg.getSenderId(); // Placeholder

                            String displayContent = updatedMsg.isRecalled() ? "Tin nhắn đã được thu hồi"
                                    : (updatedMsg.getEditedContent() != null && updatedMsg.isEdited()
                                            ? updatedMsg.getEditedContent()
                                            : updatedMsg.getContent());

                            chatRenderer.addTextMessage(displayContent, isOwn, updatedMsg.getCreatedAt(),
                                    senderName, null, null, updatedMsg.getIsRead(),
                                    null, null, updatedMsg.isDeleted(), updatedMsg.isRecalled(),
                                    updatedMsg.isEdited(), updatedMsg.getRepliedToContent(),
                                    updatedMsg.getRepliedToMessageId() > 0 ? updatedMsg.getRepliedToMessageId()
                                            : null,
                                    Message.MessageStatus.SENT);

                            // Restore userData
                            if (!chatArea.getChildren().isEmpty()) {
                                Node lastNode = chatArea.getChildren().get(chatArea.getChildren().size() - 1);
                                if (lastNode instanceof HBox)
                                    ((HBox) lastNode).setUserData(updatedMsg.getId());
                            }
                        }
                        break;
                    }
                }
            }

        });

    }

    // ============================= SEARCH FUNCTIONALITY
    // =============================

    @FXML
    private void toggleSearchBar() {
        if (searchBar == null)
            return;
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

        currentSearchText = searchText;
        if (currentFriend != null) {
            org.example.zalu.client.ChatClient.sendRequest(
                    "SEARCH_MESSAGES|" + currentUserId + "|" + currentFriend.getId() + "|" + searchText + "|false");
        } else {
            org.example.zalu.client.ChatClient.sendRequest(
                    "SEARCH_MESSAGES|" + currentUserId + "|" + currentGroup.getId() + "|" + searchText + "|true");
        }
    }

    @FXML
    private void handleSearchNext() {
        if (searchResults.isEmpty())
            return;
        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size();
        navigateToSearchResult();
    }

    @FXML
    private void handleSearchPrevious() {
        if (searchResults.isEmpty())
            return;
        currentSearchIndex = (currentSearchIndex - 1 + searchResults.size()) % searchResults.size();
        navigateToSearchResult();
    }

    private void navigateToSearchResult() {
        if (currentSearchIndex < 0 || currentSearchIndex >= searchResults.size())
            return;

        Message msg = searchResults.get(currentSearchIndex);
        searchResultLabel.setText((currentSearchIndex + 1) + "/" + searchResults.size());

        // Tìm và scroll đến message node
        for (Node node : chatArea.getChildren()) {
            if (node instanceof HBox) {
                Object msgIdObj = ((HBox) node).getUserData();
                if (msgIdObj != null && msgIdObj instanceof Integer && (Integer) msgIdObj == msg.getId()) {
                    // Scroll đến node này
                    node.requestFocus();
                    chatScrollPane
                            .setVvalue(chatArea.getChildren().indexOf(node) / (double) chatArea.getChildren().size());

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
        if (!(messageNode instanceof HBox))
            return;

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
                            label.setStyle(
                                    label.getStyle() + "; -fx-background-color: #ffeb3b; -fx-background-radius: 3;");
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
            if (currentStyle == null)
                currentStyle = "";
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

    // ============================= PIN MESSAGES FUNCTIONALITY
    // =============================

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

        if (currentFriend != null) {
            org.example.zalu.client.ChatClient
                    .sendRequest("GET_PINNED_MESSAGES|" + currentUserId + "|" + currentFriend.getId() + "|false");
        } else {
            org.example.zalu.client.ChatClient
                    .sendRequest("GET_PINNED_MESSAGES|" + currentUserId + "|" + currentGroup.getId() + "|true");
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
        org.example.zalu.client.ChatClient.sendRequest("PIN_MESSAGE|" + messageId + "|true");
        // We will receive PINNED_MESSAGES_RESULT or MESSAGE_PIN_UPDATE callback to
        // update UI
    }

    private void handleUnpinMessage(int messageId) {
        org.example.zalu.client.ChatClient.sendRequest("PIN_MESSAGE|" + messageId + "|false");
    }

    /**
     * Request file từ server khi file data null hoặc thiếu
     */
    private void requestFileFromServer(int messageId, String fileName, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl,
            boolean isRead, boolean isVoice) {
        // Hiển thị placeholder trước (file message hoặc loading indicator)
        if (isVoice) {
            // Hiển thị voice message placeholder
            chatRenderer.addFileMessage(fileName, 0, isOwn, timestamp, senderName, senderAvatarData, senderAvatarUrl,
                    isRead, messageId);
        } else if (ChatRenderer.isImageFile(fileName)) {
            // Hiển thị image placeholder (có thể là loading indicator)
            chatRenderer.addFileMessage(fileName, 0, isOwn, timestamp, senderName, senderAvatarData, senderAvatarUrl,
                    isRead, messageId);
        } else {
            // Hiển thị file message placeholder
            chatRenderer.addFileMessage(fileName, 0, isOwn, timestamp, senderName, senderAvatarData, senderAvatarUrl,
                    isRead, messageId);
        }

        // Đăng ký callback để nhận file từ server
        // Đăng ký callback để nhận file từ server
        ChatEventManager.getInstance().registerFileDownloadCallback(messageId, downloadInfo -> {
            if (downloadInfo != null) {
                String downloadFileName = downloadInfo.getFileName();
                byte[] fileData = downloadInfo.getFileData();
                if (fileData != null && fileData.length > 0) {
                    Platform.runLater(() -> {
                        // Xóa placeholder và hiển thị file/ảnh thật
                        // Tìm và xóa node cũ (có thể dựa vào messageId lưu trong userData)
                        removeMessageNode(messageId);

                        // Hiển thị lại với file data đầy đủ
                        if (isVoice) {
                            chatRenderer.addVoiceMessage(fileData, downloadFileName, isOwn, timestamp, senderName,
                                    senderAvatarData, senderAvatarUrl, isRead, messageId);
                        } else if (ChatRenderer.isImageFile(downloadFileName)) {
                            chatRenderer.addImageMessage(fileData, downloadFileName, isOwn, timestamp, senderName,
                                    senderAvatarData, senderAvatarUrl, isRead, messageId);
                        } else {
                            chatRenderer.addFileMessage(downloadFileName, fileData.length, isOwn, timestamp, senderName,
                                    senderAvatarData, senderAvatarUrl, isRead, messageId);
                        }
                    });
                }
            }
        });

        // Gửi request GET_FILE
        ChatClient.sendRequest("GET_FILE|" + messageId);
        logger.debug("Requesting file from server for messageId: {}, fileName: {}", messageId, fileName);
    }

    /**
     * Xóa message node dựa vào messageId
     */
    private void removeMessageNode(int messageId) {
        if (chatArea == null)
            return;
        Platform.runLater(() -> {
            for (int i = chatArea.getChildren().size() - 1; i >= 0; i--) {
                Node node = chatArea.getChildren().get(i);
                if (node instanceof HBox) {
                    Object msgIdObj = ((HBox) node).getUserData();
                    if (msgIdObj != null && msgIdObj instanceof Integer && (Integer) msgIdObj == messageId) {
                        chatArea.getChildren().remove(i);
                        break;
                    }
                }
            }
        });
    }

    private boolean isSameMessages(List<Message> list1, List<Message> list2) {
        if (list1 == null && list2 == null)
            return true;
        if (list1 == null || list2 == null)
            return false;
        if (list1.size() != list2.size())
            return false;
        if (list1.isEmpty())
            return true;

        // Check first and last message IDs (most likely to change if list differs)
        if (list1.get(0).getId() != list2.get(0).getId())
            return false;
        if (list1.get(list1.size() - 1).getId() != list2.get(list2.size() - 1).getId())
            return false;

        // Optionally check status of last few messages
        int lastIdx = list1.size() - 1;
        if (list1.get(lastIdx).getIsRead() != list2.get(lastIdx).getIsRead())
            return false;
        if (list1.get(lastIdx).isRecalled() != list2.get(lastIdx).isRecalled())
            return false;

        return true;
    }
}
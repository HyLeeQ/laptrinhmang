package org.example.zalu.util.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.model.Message;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.zalu.client.ChatEventManager;

public class ChatRenderer {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ChatRenderer.class);
    private static Image DEFAULT_AVATAR = null; // Lazy init
    private final VBox chatArea;
    private final ScrollPane chatScrollPane;
    private HBox typingIndicatorNode = null; // Node hiển thị typing indicator
    private boolean userScrolledUp = false; // Track if user manually scrolled up
    private boolean programmaticScroll = false; // Flag to ignore listener during programmatic scroll
    private static final double SCROLL_THRESHOLD = 0.95; // Consider "at bottom" if > 95%

    public ChatRenderer(VBox chatArea, ScrollPane chatScrollPane) {
        this.chatArea = chatArea;
        this.chatScrollPane = chatScrollPane;
        setupScrollListener();
    }

    // Setup listener to detect manual scrolling
    private void setupScrollListener() {
        if (chatScrollPane != null) {
            chatScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                // Ignore if this is a programmatic scroll
                if (programmaticScroll) {
                    return;
                }

                double oldValue = oldVal != null ? oldVal.doubleValue() : 1.0;
                double newValue = newVal.doubleValue();

                // Detect if user manually scrolled UP (oldValue was higher, newValue is lower)
                // AND we're not at the bottom
                if (newValue < oldValue && newValue < SCROLL_THRESHOLD) {
                    userScrolledUp = true;
                    logger.debug("User scrolled up manually, disabling auto-scroll");
                }
                // If user scrolls back to bottom (or we auto-scroll), clear flag
                else if (newValue >= SCROLL_THRESHOLD) {
                    if (userScrolledUp) {
                        logger.debug("User scrolled back to bottom, enabling auto-scroll");
                    }
                    userScrolledUp = false;
                }
            });
        }
    }

    // Lazy load avatar (path đúng /images/, fallback nếu miss)
    private static synchronized Image getDefaultAvatar() {
        if (DEFAULT_AVATAR == null) {
            try {
                DEFAULT_AVATAR = new Image(ChatRenderer.class.getResourceAsStream("/images/default-avatar.jpg")); // Path
                                                                                                                  // chuẩn
                if (DEFAULT_AVATAR.isError()) {
                    throw new Exception("Invalid image");
                }
            } catch (Exception e) {
                DEFAULT_AVATAR = null;
                logger.warn("Avatar not found: {} - Using placeholder", e.getMessage());
            }
        }
        return DEFAULT_AVATAR;
    }

    // Batch add messages (cải thiện scroll toTop: dùng setVvalue chính xác hơn)
    public void addMessagesBatch(List<Node> messageNodes, boolean toTop) {
        addMessagesBatch(messageNodes, toTop, false);
    }

    // Overload với forceScroll parameter
    public void addMessagesBatch(List<Node> messageNodes, boolean toTop, boolean forceScroll) {
        if (messageNodes == null || messageNodes.isEmpty())
            return;
        Platform.runLater(() -> {
            if (chatArea == null)
                return;
            double oldVvalue = chatScrollPane.getVvalue(); // Save current scroll
            double oldHeight = chatArea.getHeight();

            if (toTop) {
                // Prepend: Reverse order (oldest first)
                List<Node> reversed = new ArrayList<>(messageNodes);
                Collections.reverse(reversed);
                for (Node node : reversed) {
                    chatArea.getChildren().add(0, node);
                }
                // Adjust scroll: Maintain position (push down by added height ratio)
                double newHeight = chatArea.getHeight();
                double addedRatio = (newHeight - oldHeight) / newHeight;
                chatScrollPane.setVvalue(oldVvalue + addedRatio);
            } else {
                // Append to bottom
                chatArea.getChildren().addAll(messageNodes);
                if (forceScroll) {
                    // Delay để đợi UI render xong
                    Platform.runLater(() -> {
                        Platform.runLater(() -> {
                            forceScrollToBottom();
                        });
                    });
                } else {
                    scrollToBottom();
                }
            }
        });
    }

    // Add text message (giữ 2 param cũ, thêm timestamp fallback now())
    public void addTextMessage(String content, boolean isOwn) {
        addTextMessage(content, isOwn, LocalDateTime.now()); // Fallback timestamp
    }

    // Overload với timestamp (giống Zalo: bubble bo tròn, time dưới, avatar left
    // nếu received)
    public void addTextMessage(String content, boolean isOwn, LocalDateTime timestamp) {
        addTextMessage(content, isOwn, timestamp, null, null, null);
    }

    // Overload với sender info (fullname, avatar)
    public void addTextMessage(String content, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl) {
        addTextMessage(content, isOwn, timestamp, senderName, senderAvatarData, senderAvatarUrl, false);
    }

    // Overload với status
    public void addTextMessage(String content, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl, Message.MessageStatus status) {
        addTextMessage(content, isOwn, timestamp, senderName, senderAvatarData, senderAvatarUrl, false, null, null,
                false, false, false, null, null, status);
    }

    // Overload với read status
    public void addTextMessage(String content, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl, boolean isRead) {
        addTextMessage(content, isOwn, timestamp, senderName, senderAvatarData, senderAvatarUrl, isRead, null, null);
    }

    // Overload với reader avatar (khi đã đọc)
    public void addTextMessage(String content, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl,
            boolean isRead, byte[] readerAvatarData, String readerAvatarUrl) {
        addTextMessage(content, isOwn, timestamp, senderName, senderAvatarData, senderAvatarUrl,
                isRead, readerAvatarData, readerAvatarUrl, false, false, false, null, null, Message.MessageStatus.SENT);
    }

    // Overload với hỗ trợ edit/delete/recall/reply
    public void addTextMessage(String content, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl,
            boolean isRead, byte[] readerAvatarData, String readerAvatarUrl,
            boolean isDeleted, boolean isRecalled, boolean isEdited,
            String repliedToContent, Integer repliedToMessageId, Message.MessageStatus status) {
        if (content == null || content.trim().isEmpty())
            return;
        Platform.runLater(() -> {
            if (chatArea == null)
                return;

            String trimmedContent = content.trim();
            logger.info("Rendering text msg: '{}' (length: {}, own: {})", trimmedContent, trimmedContent.length(),
                    isOwn);

            // Tạo bubble từ factory với đầy đủ thông tin
            VBox bubbleBox = MessageBubbleFactory.createTextBubble(trimmedContent, isOwn, timestamp, senderName, isRead,
                    readerAvatarData, readerAvatarUrl, isDeleted, isRecalled, isEdited, repliedToContent,
                    repliedToMessageId, status);

            // Lấy contentBox từ bubbleBox (có thể là VBox với senderLabel nếu có)
            VBox contentBox = bubbleBox;
            if (bubbleBox.getChildren().size() > 0 && bubbleBox.getChildren().get(0) instanceof VBox) {
                contentBox = (VBox) bubbleBox.getChildren().get(0);
            }

            // Tìm timeAndStatusBox (thường là child cuối cùng của contentBox)
            HBox timeAndStatusBox = null;
            for (Node child : contentBox.getChildren()) {
                if (child instanceof HBox) {
                    HBox hbox = (HBox) child;
                    if (hbox.getChildren().stream().anyMatch(n -> n instanceof Label &&
                            ((Label) n).getStyleClass().contains("message-time"))) {
                        timeAndStatusBox = hbox;
                        break;
                    }
                }
            }

            if (timeAndStatusBox == null) {
                // Fallback: tìm HBox cuối cùng
                for (int i = contentBox.getChildren().size() - 1; i >= 0; i--) {
                    if (contentBox.getChildren().get(i) instanceof HBox) {
                        timeAndStatusBox = (HBox) contentBox.getChildren().get(i);
                        break;
                    }
                }
            }

            // Main HBox với avatar nếu received
            HBox mainBox = new HBox();
            if (isOwn) {
                // Tin nhắn của mình: căn phải
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                mainBox.getChildren().addAll(spacer, bubbleBox);
                mainBox.setAlignment(Pos.CENTER_RIGHT);
                HBox.setMargin(bubbleBox, new Insets(0, 12, 0, 60)); // Margin right để cách lề phải
                if (timeAndStatusBox != null) {
                    timeAndStatusBox.setPadding(new Insets(0, 8, 0, 0));
                }
            } else {
                // Tin nhắn nhận: căn trái với avatar
                Node avatarNode = MessageBubbleFactory.createMessageAvatar(senderAvatarData, senderAvatarUrl);
                mainBox.getChildren().addAll(avatarNode, bubbleBox);
                HBox.setMargin(avatarNode, new Insets(0, 8, 0, 12));
                mainBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setMargin(bubbleBox, new Insets(0, 60, 0, 0));
                if (timeAndStatusBox != null) {
                    timeAndStatusBox.setPadding(new Insets(0, 0, 0, 8));
                }
            }

            mainBox.setPadding(new Insets(5));
            // Store message ID for later updates
            mainBox.setUserData(repliedToMessageId != null && repliedToMessageId > 0 ? -1 : null); // Will be set by
                                                                                                   // MessageListController
            chatArea.getChildren().add(mainBox);
            scrollToBottom();
        });
    }

    // Add file message (giữ 3 param cũ, thêm msgId fallback -1)

    // Overload với msgId (giống Zalo: preview với icon, name blue, size gray,
    // clickable)
    public void addFileMessage(String fileName, int fileSize, boolean isOwn) {
        addFileMessage(fileName, fileSize, isOwn, LocalDateTime.now()); // Fallback timestamp
    }

    // Overload với timestamp
    public void addFileMessage(String fileName, int fileSize, boolean isOwn, LocalDateTime timestamp) {
        addFileMessage(fileName, fileSize, isOwn, timestamp, null, null, null);
    }

    // Overload với sender info (fullname, avatar)
    public void addFileMessage(String fileName, int fileSize, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl) {
        addFileMessage(fileName, fileSize, isOwn, timestamp, senderName, senderAvatarData, senderAvatarUrl, false);
    }

    // Overload với read status
    public void addFileMessage(String fileName, int fileSize, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl, boolean isRead) {
        addFileMessage(fileName, fileSize, isOwn, timestamp, senderName, senderAvatarData, senderAvatarUrl, isRead, -1);
    }

    // Overload với messageId (để có thể download khi click)
    public void addFileMessage(String fileName, int fileSize, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl, boolean isRead, int messageId) {
        if (fileName == null || fileName.trim().isEmpty())
            return;
        Platform.runLater(() -> {
            if (chatArea == null)
                return;

            String formattedSize = formatFileSize(fileSize);
            logger.info("Rendering file msg: '{}' (size: {}, own: {})", fileName, formattedSize, isOwn);

            // Sử dụng MessageBubbleFactory để tạo file bubble với style đúng
            VBox contentBox = MessageBubbleFactory.createFileBubble(fileName, fileSize, isOwn, timestamp, senderName,
                    isRead, messageId, () -> downloadFile(messageId, fileName));

            if (isOwn) {
                contentBox.setPadding(new Insets(0, 0, 0, 60));
            } else {
                contentBox.setPadding(new Insets(0, 60, 0, 0));
            }

            // Main HBox
            HBox mainBox = new HBox();
            if (isOwn) {
                // Tin nhắn của mình: căn phải
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                mainBox.getChildren().addAll(spacer, contentBox);
                mainBox.setAlignment(Pos.CENTER_RIGHT);
                HBox.setMargin(contentBox, new Insets(0, 12, 0, 0));
            } else {
                Node avatarNode = MessageBubbleFactory.createMessageAvatar(senderAvatarData, senderAvatarUrl);
                mainBox.getChildren().addAll(avatarNode, contentBox);
                HBox.setMargin(avatarNode, new Insets(0, 8, 0, 12));
                mainBox.setAlignment(Pos.CENTER_LEFT);
            }

            mainBox.setPadding(new Insets(5));
            chatArea.getChildren().add(mainBox);
            scrollToBottom();
        });
    }

    // Helper createAvatar - sử dụng MessageBubbleFactory
    private Node createAvatar(byte[] avatarData, String avatarUrl) {
        return MessageBubbleFactory.createMessageAvatar(avatarData, avatarUrl);
    }

    // Add system message (giữ nguyên, center gray)
    public void addSystemMessage(String message) {
        if (message == null || message.trim().isEmpty())
            return;
        Platform.runLater(() -> {
            if (chatArea == null)
                return;

            String trimmedMessage = message.trim();
            Label systemLabel = new Label(trimmedMessage);
            systemLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d; -fx-padding: 10;");
            systemLabel.setAlignment(Pos.CENTER);

            HBox systemBox = new HBox(systemLabel);
            systemBox.setAlignment(Pos.CENTER);
            chatArea.getChildren().add(systemBox);
            scrollToBottom();
        });
    }

    // Add timestamp (center, cho date separator)
    public void addTimestamp(LocalDateTime time) {
        Platform.runLater(() -> {
            if (time == null || chatArea == null)
                return;

            String timeStr = time.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            Label timeLabel = new Label(timeStr);
            timeLabel
                    .setStyle("-fx-font-size: 12px; -fx-text-fill: #adb5bd; -fx-font-weight: bold; -fx-padding: 10 0;");
            timeLabel.setAlignment(Pos.CENTER);

            HBox timeBox = new HBox(timeLabel);
            timeBox.setAlignment(Pos.CENTER);
            chatArea.getChildren().add(timeBox);
        });
    }

    // Clear chat
    public void clearChat() {
        Platform.runLater(() -> {
            if (chatArea != null) {
                chatArea.getChildren().clear();
                typingIndicatorNode = null; // Reset typing indicator
                userScrolledUp = false; // Reset scroll flag for new chat
            }
        });
    }

    /**
     * Hiển thị typing indicator ("Đang gõ...")
     * 
     * @param senderName       Tên người đang gõ (null nếu không có)
     * @param senderAvatarData Avatar data của người đang gõ
     * @param senderAvatarUrl  Avatar URL của người đang gõ
     */
    public void showTypingIndicator(String senderName, byte[] senderAvatarData, String senderAvatarUrl) {
        Platform.runLater(() -> {
            if (chatArea == null)
                return;

            // Nếu đã có typing indicator, không tạo lại
            if (typingIndicatorNode != null && chatArea.getChildren().contains(typingIndicatorNode)) {
                return;
            }

            // Tạo typing indicator bubble
            Label typingLabel = new Label("Đang gõ...");
            typingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666; -fx-font-style: italic;");

            VBox typingBubble = new VBox(4);
            typingBubble.setStyle("-fx-background-color: #e9ecef; -fx-background-radius: 18; -fx-padding: 10 16;");
            typingBubble.getChildren().add(typingLabel);
            typingBubble.setMaxWidth(200);

            // Main HBox với avatar
            HBox mainBox = new HBox();
            Node avatarNode = MessageBubbleFactory.createMessageAvatar(senderAvatarData, senderAvatarUrl);
            mainBox.getChildren().addAll(avatarNode, typingBubble);
            HBox.setMargin(avatarNode, new Insets(0, 8, 0, 12));
            HBox.setMargin(typingBubble, new Insets(0, 60, 0, 0));
            mainBox.setAlignment(Pos.CENTER_LEFT);
            mainBox.setPadding(new Insets(5));

            typingIndicatorNode = mainBox;
            chatArea.getChildren().add(typingIndicatorNode);
            scrollToBottom();
        });
    }

    /**
     * Ẩn typing indicator
     */
    public void hideTypingIndicator() {
        Platform.runLater(() -> {
            if (chatArea != null && typingIndicatorNode != null) {
                chatArea.getChildren().remove(typingIndicatorNode);
                typingIndicatorNode = null;
            }
        });
    }

    /**
     * Cập nhật read status của các tin nhắn đã gửi trong chat area
     * 
     * @param senderId ID của người gửi (currentUserId)
     */
    /**
     * Xóa read status khỏi tất cả tin nhắn cũ
     * (Read status sẽ được thêm lại cho tin nhắn cuối cùng nếu cần)
     */
    public void hideReadStatusFromOldMessages() {
        Platform.runLater(() -> {
            if (chatArea == null)
                return;

            // Duyệt qua tất cả các message nodes và ẩn read status
            for (int i = 0; i < chatArea.getChildren().size(); i++) {

                Node node = chatArea.getChildren().get(i);
                if (node instanceof HBox) {
                    HBox mainBox = (HBox) node;
                    // Tìm VBox chứa message bubble
                    for (Node child : mainBox.getChildren()) {
                        if (child instanceof VBox) {
                            VBox bubbleBox = (VBox) child;
                            // Tìm timeAndStatusBox (thường là child cuối cùng)
                            if (bubbleBox.getChildren().size() > 0) {
                                Node lastChild = bubbleBox.getChildren().get(bubbleBox.getChildren().size() - 1);
                                if (lastChild instanceof HBox) {
                                    HBox timeAndStatusBox = (HBox) lastChild;
                                    // Xóa read status box
                                    for (int j = timeAndStatusBox.getChildren().size() - 1; j >= 0; j--) {
                                        Node statusNode = timeAndStatusBox.getChildren().get(j);
                                        if (statusNode instanceof HBox) {
                                            HBox readStatusBox = (HBox) statusNode;
                                            // Kiểm tra xem có phải là read status box không
                                            boolean isReadStatusBox = false;
                                            for (Node labelNode : readStatusBox.getChildren()) {
                                                if (labelNode instanceof Label) {
                                                    Label statusLabel = (Label) labelNode;
                                                    if (statusLabel.getStyleClass().contains("message-read-status")) {
                                                        isReadStatusBox = true;
                                                        break;
                                                    }
                                                } else if (labelNode instanceof ImageView) {
                                                    // Có thể là avatar reader
                                                    isReadStatusBox = true;
                                                    break;
                                                }
                                            }
                                            if (isReadStatusBox) {
                                                timeAndStatusBox.getChildren().remove(statusNode);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Cập nhật read status cho tin nhắn cuối cùng
     */
    public void updateReadStatusForMessages(int senderId, boolean isRead, byte[] readerAvatarData,
            String readerAvatarUrl) {
        Platform.runLater(() -> {
            if (chatArea == null)
                return;

            // Tìm tin nhắn cuối cùng (node cuối cùng trong chatArea)
            Node lastMessageNode = null;
            for (int i = chatArea.getChildren().size() - 1; i >= 0; i--) {
                Node node = chatArea.getChildren().get(i);
                if (node instanceof HBox) {
                    lastMessageNode = node;
                    break;
                }
            }

            if (lastMessageNode == null)
                return;

            // Cập nhật read status cho tin nhắn cuối cùng
            HBox mainBox = (HBox) lastMessageNode;
            for (Node child : mainBox.getChildren()) {
                if (child instanceof VBox) {
                    VBox bubbleBox = (VBox) child;
                    if (bubbleBox.getChildren().size() > 0) {
                        Node lastChild = bubbleBox.getChildren().get(bubbleBox.getChildren().size() - 1);
                        if (lastChild instanceof HBox) {
                            HBox timeAndStatusBox = (HBox) lastChild;
                            // Xóa read status cũ nếu có
                            for (int i = timeAndStatusBox.getChildren().size() - 1; i >= 0; i--) {
                                Node statusNode = timeAndStatusBox.getChildren().get(i);
                                if (statusNode instanceof HBox) {
                                    HBox readStatusBox = (HBox) statusNode;
                                    boolean isReadStatusBox = false;
                                    for (Node labelNode : readStatusBox.getChildren()) {
                                        if (labelNode instanceof Label) {
                                            Label statusLabel = (Label) labelNode;
                                            if (statusLabel.getStyleClass().contains("message-read-status")) {
                                                isReadStatusBox = true;
                                                break;
                                            }
                                        } else if (labelNode instanceof ImageView) {
                                            isReadStatusBox = true;
                                            break;
                                        }
                                    }
                                    if (isReadStatusBox) {
                                        timeAndStatusBox.getChildren().remove(statusNode);
                                        break;
                                    }
                                }
                            }

                            // Thêm read status mới
                            HBox newReadStatusBox = MessageBubbleFactory.createReadStatusBox(isRead, readerAvatarData,
                                    readerAvatarUrl);
                            timeAndStatusBox.getChildren().add(newReadStatusBox);
                        }
                    }
                }
            }
        });
    }

    /**
     * Deprecated: Giữ lại để tương thích
     */
    @Deprecated
    public void updateReadStatusForMessages(int senderId) {
        // Không làm gì - logic đã được thay thế
    }

    // Scroll to bottom (smooth) - only if user hasn't scrolled up
    public void scrollToBottom() {
        Platform.runLater(() -> {
            if (chatScrollPane != null && !userScrolledUp) {
                programmaticScroll = true;
                chatScrollPane.setVvalue(1.0);
                // Reset flag sau khi scroll xong
                Platform.runLater(() -> programmaticScroll = false);
            }
        });
    }

    // Force scroll to bottom (ignore user scroll state) - for initial load
    public void forceScrollToBottom() {
        Platform.runLater(() -> {
            if (chatScrollPane != null) {
                programmaticScroll = true;
                userScrolledUp = false; // Reset flag
                chatScrollPane.setVvalue(1.0);
                // Reset flag sau khi scroll xong
                Platform.runLater(() -> programmaticScroll = false);
            }
        });
    }

    // Helper formatFileSize (giữ nguyên)
    private String formatFileSize(int bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    // Helper: Kiểm tra file có phải là ảnh không
    public static boolean isImageFile(String fileName) {
        if (fileName == null || fileName.isEmpty())
            return false;
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                lowerName.endsWith(".bmp") || lowerName.endsWith(".webp");
    }

    // Helper: Kiểm tra file có phải là audio/voice không
    public static boolean isAudioFile(String fileName) {
        if (fileName == null || fileName.isEmpty())
            return false;
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".wav") || lowerName.endsWith(".mp3") ||
                lowerName.endsWith(".m4a") || lowerName.endsWith(".aac") ||
                lowerName.endsWith(".ogg") || lowerName.startsWith("voice_");
    }

    // Helper: Kiểm tra file có phải là video không
    public static boolean isVideoFile(String fileName) {
        if (fileName == null || fileName.isEmpty())
            return false;
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                lowerName.endsWith(".avi") || lowerName.endsWith(".mov") ||
                lowerName.endsWith(".wmv") || lowerName.endsWith(".flv") ||
                lowerName.endsWith(".webm");
    }

    // Add voice message với nút play (mở bằng ứng dụng mặc định)
    public void addVoiceMessage(byte[] audioData, String fileName, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl, boolean isRead) {
        addVoiceMessage(audioData, fileName, isOwn, timestamp, senderName, senderAvatarData, senderAvatarUrl, isRead,
                -1);
    }

    public void addVoiceMessage(byte[] audioData, String fileName, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl, boolean isRead, int messageId) {
        Platform.runLater(() -> {
            if (chatArea == null)
                return;

            try {
                // Sử dụng MessageBubbleFactory để tạo voice bubble với player tích hợp
                VBox voiceBubble = MessageBubbleFactory.createVoiceBubble(audioData, fileName, isOwn, timestamp,
                        senderName, isRead, messageId, null);

                HBox mainBox = new HBox();
                if (isOwn) {
                    // Tin nhắn của mình: căn phải
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    mainBox.getChildren().addAll(spacer, voiceBubble);
                    mainBox.setAlignment(Pos.CENTER_RIGHT);
                    HBox.setMargin(voiceBubble, new Insets(0, 12, 0, 60));
                } else {
                    Node avatarNode = MessageBubbleFactory.createMessageAvatar(senderAvatarData, senderAvatarUrl);
                    mainBox.getChildren().addAll(avatarNode, voiceBubble);
                    HBox.setMargin(avatarNode, new Insets(0, 8, 0, 12));
                    mainBox.setAlignment(Pos.CENTER_LEFT);
                    HBox.setMargin(voiceBubble, new Insets(0, 60, 0, 0));
                }

                mainBox.setPadding(new Insets(5));
                chatArea.getChildren().add(mainBox);
                scrollToBottom();

            } catch (Exception e) {
                logger.error("Lỗi khi tạo voice message", e);
            }
        });
    }

    // Download file (giữ nguyên, thêm msgId từ Message)
    private void downloadFile(int msgId, String fileName) {
        String getCmd = "GET_FILE|" + msgId;
        ChatClient.sendRequest(getCmd);
        logger.info("Requesting download for msgId: {}, file: {}", msgId, fileName);

        // Register callback để xử lý file download response
        // Register callback để xử lý file download response
        ChatEventManager.getInstance().registerFileDownloadCallback(msgId, downloadInfo -> {
            if (downloadInfo != null) {
                saveDownloadedFile(downloadInfo.getFileName(), downloadInfo.getFileData());
            }
        });
    }

    private void saveDownloadedFile(String fileName, byte[] fileData) {
        if (fileData == null || fileData.length == 0) {
            logger.warn("File data is null or empty");
            return;
        }

        Platform.runLater(() -> {
            try {
                // Mở file chooser để chọn nơi lưu
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Lưu file");
                fileChooser.setInitialFileName(fileName);
                fileChooser.setInitialDirectory(new File(System.getProperty("user.home") + "/Downloads"));

                // Lấy stage từ chatArea (nếu có)
                Stage stage = (Stage) chatArea.getScene().getWindow();
                File selectedFile = fileChooser.showSaveDialog(stage);

                if (selectedFile != null) {
                    // Lưu file
                    try (FileOutputStream fos = new FileOutputStream(selectedFile)) {
                        fos.write(fileData);
                        fos.flush();
                        logger.info("File saved: {}", selectedFile.getAbsolutePath());

                        // Mở file nếu có thể
                        try {
                            java.awt.Desktop.getDesktop().open(selectedFile);
                        } catch (Exception e) {
                            logger.warn("Could not open file: {}", e.getMessage());
                        }
                    } catch (IOException e) {
                        logger.error("Error saving file", e);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in saveDownloadedFile", e);
            }
        });
    }

    // Mới: Add message từ model (tích hợp isOwn từ senderId vs currentUserId)
    public void addMessage(Message msg, int currentUserId) {
        boolean isOwn = msg.getSenderId() == currentUserId;
        if (msg.getContent() != null && !msg.getContent().isEmpty()) {
            addTextMessage(msg.getContent(), isOwn, msg.getCreatedAt());
        } else if (msg.getFileName() != null) {
            // Kiểm tra loại file
            if (isImageFile(msg.getFileName())) {
                addImageMessage(msg.getFileData(), msg.getFileName(), isOwn, msg.getCreatedAt(),
                        null, null, null, msg.getIsRead(), msg.getId());
            } else if (isAudioFile(msg.getFileName())) {
                addVoiceMessage(msg.getFileData(), msg.getFileName(), isOwn, msg.getCreatedAt(),
                        null, null, null, msg.getIsRead());
            } else {
                int size = msg.getFileData() != null ? msg.getFileData().length : 0;
                addFileMessage(msg.getFileName(), size, isOwn, msg.getCreatedAt(),
                        null, null, null, msg.getIsRead(), msg.getId());
            }
        }
    }

    // Add image message (hiển thị thumbnail, click để xem full)
    public void addImageMessage(byte[] imageData, String fileName, boolean isOwn, LocalDateTime timestamp) {
        addImageMessage(imageData, fileName, isOwn, timestamp, null, null, null);
    }

    public void addImageMessage(byte[] imageData, String fileName, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl) {
        addImageMessage(imageData, fileName, isOwn, timestamp, senderName, senderAvatarData, senderAvatarUrl, false,
                -1);
    }

    public void addImageMessage(byte[] imageData, String fileName, boolean isOwn, LocalDateTime timestamp,
            String senderName, byte[] senderAvatarData, String senderAvatarUrl, boolean isRead, int messageId) {
        Platform.runLater(() -> {
            if (chatArea == null)
                return;

            try {
                // Sử dụng MessageBubbleFactory để tạo image bubble
                VBox imageBubble = MessageBubbleFactory.createImageBubble(imageData, fileName, isOwn, timestamp,
                        senderName, isRead, messageId, () -> showFullSizeImage(imageData, fileName));

                // Main HBox với avatar nếu received
                HBox mainBox = new HBox();
                if (isOwn) {
                    // Tin nhắn của mình: căn phải
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    mainBox.getChildren().addAll(spacer, imageBubble);
                    mainBox.setAlignment(Pos.CENTER_RIGHT);
                    HBox.setMargin(imageBubble, new Insets(0, 12, 0, 60));
                } else {
                    Node avatarNode = MessageBubbleFactory.createMessageAvatar(senderAvatarData, senderAvatarUrl);
                    mainBox.getChildren().addAll(avatarNode, imageBubble);
                    HBox.setMargin(avatarNode, new Insets(0, 8, 0, 12));
                    mainBox.setAlignment(Pos.CENTER_LEFT);
                    HBox.setMargin(imageBubble, new Insets(0, 60, 0, 0));
                }

                mainBox.setPadding(new Insets(5));
                chatArea.getChildren().add(mainBox);
                scrollToBottom();
            } catch (Exception e) {
                logger.error("Error creating image Bubble", e);
                // Fallback về file message nếu có lỗi
                addFileMessage(fileName, imageData.length, isOwn, timestamp, senderName, senderAvatarData,
                        senderAvatarUrl);
            }
        });
    }

    // Hiển thị ảnh full size trong dialog
    private void showFullSizeImage(byte[] imageData, String fileName) {
        Platform.runLater(() -> {
            try {
                Image fullImage = new Image(new ByteArrayInputStream(imageData));
                if (fullImage.isError()) {
                    logger.error("Error loading full size image");
                    return;
                }

                // Tạo Stage mới để hiển thị ảnh
                Stage imageStage = new Stage();
                imageStage.setTitle(fileName != null ? fileName : "Hình ảnh");
                imageStage.initModality(Modality.NONE);

                ImageView fullImageView = new ImageView(fullImage);
                fullImageView.setPreserveRatio(true);
                fullImageView.setSmooth(true);

                // Set size để fit screen nhưng không quá lớn
                double screenWidth = Screen.getPrimary().getVisualBounds().getWidth();
                double screenHeight = Screen.getPrimary().getVisualBounds().getHeight();
                double maxWidth = screenWidth * 0.9;
                double maxHeight = screenHeight * 0.9;

                double imageWidth = fullImage.getWidth();
                double imageHeight = fullImage.getHeight();
                double ratio = Math.min(maxWidth / imageWidth, maxHeight / imageHeight);
                ratio = Math.min(ratio, 1.0); // Không phóng to quá kích thước gốc

                fullImageView.setFitWidth(imageWidth * ratio);
                fullImageView.setFitHeight(imageHeight * ratio);

                // ScrollPane để scroll nếu ảnh lớn
                ScrollPane scrollPane = new ScrollPane(fullImageView);
                scrollPane.setFitToWidth(true);
                scrollPane.setFitToHeight(true);
                scrollPane.setPannable(true);
                scrollPane.setStyle("-fx-background-color: rgba(0,0,0,0.9);");

                // StackPane với background đen
                StackPane root = new StackPane(scrollPane);
                root.setStyle("-fx-background-color: rgba(0,0,0,0.9);");

                // Click để đóng
                root.setOnMouseClicked(e -> imageStage.close());

                Scene scene = new Scene(root, imageWidth * ratio, imageHeight * ratio);
                imageStage.setScene(scene);
                imageStage.show();

                // Center window
                imageStage.centerOnScreen();
            } catch (Exception e) {
                logger.error("Error showing full size image", e);
            }
        });
    }
}

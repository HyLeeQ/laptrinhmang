package org.example.zalu.service;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Circle;
import org.example.zalu.dao.GroupDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.GroupInfo;
import org.example.zalu.model.Message;
import org.example.zalu.model.User;
import org.example.zalu.service.AvatarService;
import org.example.zalu.util.ui.ChatRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service để quản lý info panel (thông tin user/group, media, files, links)
 */
public class InfoPanelService {
    private static final Logger logger = LoggerFactory.getLogger(InfoPanelService.class);
    private final VBox infoPanel;
    private final TabPane infoTabPane;
    private final Tab directTab;
    private final Tab groupTab;
    private final ImageView infoAvatar;
    private final Label infoNameLabel;
    private final Label infoStatusLabel;
    private final FlowPane mediaPreviewPane;
    private final ListView<String> directFileListView;
    private final ListView<String> directLinkListView;
    private final ListView<String> groupFileListView;
    private final ListView<String> groupLinkListView;
    private final Label groupNameInfoLabel;
    private final Label groupMemberCountLabel;
    private final ListView<String> groupMembersList;
    private final CheckBox hideConversationCheck;
    private final CheckBox groupHideConversationCheck;

    private final UserDAO userDAO;
    private final GroupDAO groupDAO;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|www\\.[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
            Pattern.CASE_INSENSITIVE);

    @SuppressWarnings("rawtypes")
    public InfoPanelService(VBox infoPanel, TabPane infoTabPane, Tab directTab, Tab groupTab,
            ImageView infoAvatar, Label infoNameLabel, Label infoStatusLabel,
            FlowPane mediaPreviewPane, ListView directFileListView,
            ListView<String> directLinkListView, ListView groupFileListView,
            ListView<String> groupLinkListView, Label groupNameInfoLabel,
            Label groupMemberCountLabel, ListView<String> groupMembersList,
            CheckBox hideConversationCheck, CheckBox groupHideConversationCheck,
            UserDAO userDAO, GroupDAO groupDAO) {
        this.infoPanel = infoPanel;
        this.infoTabPane = infoTabPane;
        this.directTab = directTab;
        this.groupTab = groupTab;
        this.infoAvatar = infoAvatar;
        this.infoNameLabel = infoNameLabel;
        this.infoStatusLabel = infoStatusLabel;
        this.mediaPreviewPane = mediaPreviewPane;
        @SuppressWarnings("unchecked")
        ListView<String> directFile = (ListView<String>) directFileListView;
        this.directFileListView = directFile;
        this.directLinkListView = directLinkListView;
        @SuppressWarnings("unchecked")
        ListView<String> groupFile = (ListView<String>) groupFileListView;
        this.groupFileListView = groupFile;
        this.groupLinkListView = groupLinkListView;
        this.groupNameInfoLabel = groupNameInfoLabel;
        this.groupMemberCountLabel = groupMemberCountLabel;
        this.groupMembersList = groupMembersList;
        this.hideConversationCheck = hideConversationCheck;
        this.groupHideConversationCheck = groupHideConversationCheck;
        this.userDAO = userDAO;
        this.groupDAO = groupDAO;

        setupInfoAvatarClip();
    }

    /**
     * Setup avatar clip thành hình tròn
     */
    private void setupInfoAvatarClip() {
        if (infoAvatar != null) {
            Circle clip = new Circle(30);
            clip.centerXProperty().bind(infoAvatar.fitWidthProperty().divide(2));
            clip.centerYProperty().bind(infoAvatar.fitHeightProperty().divide(2));
            clip.radiusProperty().bind(infoAvatar.fitWidthProperty().divide(2));
            infoAvatar.setClip(clip);
        }
    }

    /**
     * Cấu hình info panel cho friend
     */
    public void configureForFriend(User friend) {
        // Tự động chọn tab phù hợp (tab header đã được ẩn bằng CSS)
        if (infoTabPane != null) {
            infoTabPane.getSelectionModel().select(directTab);
        }

        String displayName = (friend.getFullName() != null && !friend.getFullName().trim().isEmpty())
                ? friend.getFullName()
                : friend.getUsername();
        if (infoNameLabel != null) {
            infoNameLabel.setText(displayName);
            infoNameLabel.setTooltip(new Tooltip(displayName)); // Tooltip để xem đầy đủ nếu bị cắt
        }
        if (infoStatusLabel != null) {
            infoStatusLabel.setText("Đang hoạt động");
        }

        loadAvatar(friend);
    }

    /**
     * Cấu hình info panel cho group
     */
    public void configureForGroup(GroupInfo group) {
        // Tự động chọn tab phù hợp (tab header đã được ẩn bằng CSS)
        if (infoTabPane != null) {
            infoTabPane.getSelectionModel().select(groupTab);
        }

        if (groupNameInfoLabel != null) {
            groupNameInfoLabel.setText(group.getName());
            groupNameInfoLabel.setTooltip(new Tooltip(group.getName())); // Tooltip để xem đầy đủ nếu bị cắt
        }
        if (groupMemberCountLabel != null) {
            groupMemberCountLabel.setText(group.getMemberCount() + " thành viên");
        }

        populateGroupMembers(group);
    }

    /**
     * Load avatar
     */
    private void loadAvatar(User user) {
        if (infoAvatar == null || user == null)
            return;

        try {
            javafx.scene.image.Image avatarImage = AvatarService.resolveAvatar(user);
            if (avatarImage != null && !avatarImage.isError()) {
                infoAvatar.setImage(avatarImage);
            } else {
                javafx.scene.image.Image defaultAvatar = AvatarService.getDefaultAvatar();
                if (defaultAvatar != null) {
                    infoAvatar.setImage(defaultAvatar);
                }
            }
        } catch (Exception e) {
            logger.error("Error loading avatar: {}", e.getMessage(), e);
        }
    }

    /**
     * Populate group members
     */
    private void populateGroupMembers(GroupInfo group) {
        if (groupMembersList == null || groupDAO == null)
            return;

        try {
            List<Integer> memberIds = groupDAO.getGroupMembers(group.getId());
            groupMembersList.getItems().clear();
            for (int memberId : memberIds) {
                try {
                    User member = userDAO.getUserById(memberId);
                    if (member != null) {
                        String displayName = (member.getFullName() != null && !member.getFullName().trim().isEmpty())
                                ? member.getFullName()
                                : member.getUsername();
                        groupMembersList.getItems().add(displayName);
                    }
                } catch (Exception e) {
                    logger.error("Error loading group member: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Error populating group members: {}", e.getMessage(), e);
        }
    }

    /**
     * Update shared media và files từ messages
     */
    public void updateSharedMediaAndFiles(List<Message> messages, boolean isGroup) {
        if (messages == null || messages.isEmpty())
            return;

        Platform.runLater(() -> {
            // Update media preview (6 ảnh/video gần nhất)
            updateMediaPreview(messages);

            // Update file list với icon, size, date
            @SuppressWarnings("unchecked")
            ListView<FileItem> fileListView = (ListView<FileItem>) (Object) (isGroup ? groupFileListView
                    : directFileListView);
            updateFileList(messages, fileListView);

            // Update link list
            @SuppressWarnings("unchecked")
            ListView<String> linkListView = isGroup ? groupLinkListView : directLinkListView;
            if (linkListView != null) {
                linkListView.getItems().clear();
                List<String> links = extractLinks(messages);
                linkListView.getItems().addAll(links);
            }
        });
    }

    /**
     * Update media preview pane với 6 ảnh/video gần nhất
     */
    private void updateMediaPreview(List<Message> messages) {
        if (mediaPreviewPane == null)
            return;

        mediaPreviewPane.getChildren().clear();

        // Lọc và sắp xếp media messages (ảnh và video) theo thời gian mới nhất trước
        List<Message> mediaMessages = new ArrayList<>();
        for (Message msg : messages) {
            if (msg.getFileName() != null && msg.getFileData() != null && msg.getFileData().length > 0) {
                if (ChatRenderer.isImageFile(msg.getFileName()) || isVideoFile(msg.getFileName())) {
                    mediaMessages.add(msg);
                }
            }
        }

        // Sắp xếp theo thời gian: mới nhất trước (DESC)
        mediaMessages.sort((m1, m2) -> {
            if (m1.getCreatedAt() == null && m2.getCreatedAt() == null)
                return 0;
            if (m1.getCreatedAt() == null)
                return 1;
            if (m2.getCreatedAt() == null)
                return -1;
            return m2.getCreatedAt().compareTo(m1.getCreatedAt());
        });

        // Hiển thị 6 ảnh/video gần nhất
        int displayCount = 0;
        int maxDisplay = 6;

        for (Message msg : mediaMessages) {
            if (displayCount >= maxDisplay)
                break;

            if (ChatRenderer.isImageFile(msg.getFileName())) {
                try {
                    Image image = new Image(new ByteArrayInputStream(msg.getFileData()), 80, 80, true, true);
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(80);
                    imageView.setFitHeight(80);
                    imageView.setPreserveRatio(true);
                    imageView.setSmooth(true);
                    imageView.setCache(true);

                    StackPane mediaContainer = new StackPane();
                    mediaContainer.setStyle(
                            "-fx-background-color: #f0f0f0; " +
                                    "-fx-background-radius: 8; " +
                                    "-fx-padding: 2; " +
                                    "-fx-cursor: hand;");
                    mediaContainer.getChildren().add(imageView);
                    mediaContainer.setPrefSize(80, 80);
                    mediaContainer.setMaxSize(80, 80);

                    mediaPreviewPane.getChildren().add(mediaContainer);
                    displayCount++;
                } catch (Exception e) {
                    logger.error("Error loading preview image: {}", e.getMessage(), e);
                }
            } else if (isVideoFile(msg.getFileName())) {
                StackPane videoContainer = new StackPane();
                videoContainer.setStyle(
                        "-fx-background-color: #1c1e21; " +
                                "-fx-background-radius: 8; " +
                                "-fx-padding: 4; " +
                                "-fx-cursor: hand;");
                videoContainer.setPrefSize(80, 80);
                videoContainer.setMaxSize(80, 80);

                Label playIcon = new Label("▶");
                playIcon.setStyle("-fx-font-size: 24px; -fx-text-fill: white; -fx-font-weight: bold;");
                videoContainer.getChildren().add(playIcon);

                mediaPreviewPane.getChildren().add(videoContainer);
                displayCount++;
            }
        }
    }

    /**
     * Update file list với custom cell factory (icon, size, date)
     */
    @SuppressWarnings("unchecked")
    private void updateFileList(List<Message> messages, ListView<FileItem> fileListView) {
        if (fileListView == null)
            return;

        // Tạo FileItem list
        List<FileItem> files = new ArrayList<>();
        for (Message msg : messages) {
            if (msg.getFileName() != null && !msg.getFileName().trim().isEmpty()) {
                // Skip images and videos (they're in media view)
                if (!ChatRenderer.isImageFile(msg.getFileName()) && !isVideoFile(msg.getFileName())) {
                    long fileSize = msg.getFileData() != null ? msg.getFileData().length : 0;
                    files.add(new FileItem(msg.getFileName(), fileSize, msg));
                }
            }
        }

        // Sắp xếp theo thời gian: mới nhất trước (DESC)
        files.sort((f1, f2) -> {
            Message m1 = f1.getMessage();
            Message m2 = f2.getMessage();
            if (m1 == null || m1.getCreatedAt() == null)
                return 1;
            if (m2 == null || m2.getCreatedAt() == null)
                return -1;
            return m2.getCreatedAt().compareTo(m1.getCreatedAt());
        });

        // Set cell factory
        fileListView.setCellFactory(param -> new ListCell<FileItem>() {
            private HBox itemBox;
            private StackPane iconContainer;
            private VBox infoBox;
            private Label nameLabel;
            private Label sizeLabel;
            private Label dateLabel;
            private Label checkIcon;

            {
                itemBox = new HBox(8);
                itemBox.setAlignment(Pos.CENTER_LEFT);
                itemBox.setPadding(new Insets(6, 8, 6, 8));

                iconContainer = new StackPane();
                iconContainer.setPrefSize(36, 36);

                infoBox = new VBox(2);
                infoBox.setAlignment(Pos.CENTER_LEFT);

                nameLabel = new Label();
                nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 500; -fx-text-fill: #1c1e21;");
                nameLabel.setMaxWidth(180);
                nameLabel.setWrapText(false);

                HBox metaBox = new HBox(6);
                sizeLabel = new Label();
                sizeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8e8e93;");

                dateLabel = new Label();
                dateLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8e8e93;");

                metaBox.getChildren().addAll(sizeLabel, new Label("•"), dateLabel);

                infoBox.getChildren().addAll(nameLabel, metaBox);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                checkIcon = new Label("✓");
                checkIcon.setStyle("-fx-font-size: 14px; -fx-text-fill: #31d559; -fx-font-weight: bold;");

                itemBox.getChildren().addAll(iconContainer, infoBox, spacer, checkIcon);
            }

            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);

                    nameLabel.setText(item.getFileName());
                    sizeLabel.setText(item.getFormattedSize());
                    dateLabel.setText(item.getFormattedDate());

                    // Create file icon với màu
                    iconContainer.getChildren().clear();
                    String ext = item.getFileExtension();
                    Color iconColor = getFileIconColor(ext);
                    String iconText = getFileIconText(ext);

                    Rectangle iconBg = new Rectangle(36, 36);
                    iconBg.setFill(iconColor);
                    iconBg.setArcWidth(8);
                    iconBg.setArcHeight(8);

                    Label iconLabel = new Label(iconText);
                    iconLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: white; -fx-font-weight: bold;");

                    iconContainer.getChildren().addAll(iconBg, iconLabel);

                    setGraphic(itemBox);
                    setStyle("-fx-cursor: hand; -fx-background-color: transparent;");
                }
            }
        });

        fileListView.getItems().setAll(files);
    }

    private boolean isVideoFile(String fileName) {
        if (fileName == null)
            return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov") ||
                lower.endsWith(".wmv") || lower.endsWith(".flv") || lower.endsWith(".mkv");
    }

    private Color getFileIconColor(String extension) {
        switch (extension) {
            case "doc":
            case "docx":
                return Color.web("#2b579a");
            case "xls":
            case "xlsx":
                return Color.web("#1d6f42");
            case "ppt":
            case "pptx":
                return Color.web("#d04423");
            case "pdf":
                return Color.web("#e53e3e");
            case "zip":
            case "rar":
            case "7z":
                return Color.web("#9c27b0");
            case "txt":
                return Color.web("#607d8b");
            case "wav":
            case "mp3":
            case "m4a":
                return Color.web("#ff9800");
            default:
                return Color.web("#757575");
        }
    }

    private String getFileIconText(String extension) {
        switch (extension) {
            case "doc":
            case "docx":
                return "W";
            case "xls":
            case "xlsx":
                return "X";
            case "ppt":
            case "pptx":
                return "P";
            case "pdf":
                return "PDF";
            case "zip":
            case "rar":
            case "7z":
                return "ZIP";
            case "txt":
                return "TXT";
            case "wav":
            case "mp3":
            case "m4a":
                return "♪";
            default:
                return "FILE";
        }
    }

    /**
     * FileItem class for file list
     */
    private static class FileItem {
        private String fileName;
        private long fileSize;
        private Message message;

        public FileItem(String fileName, long fileSize, Message message) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.message = message;
        }

        public String getFileName() {
            return fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public Message getMessage() {
            return message;
        }

        public String getFormattedSize() {
            if (fileSize < 1024)
                return fileSize + " B";
            if (fileSize < 1024 * 1024)
                return new DecimalFormat("#.##").format(fileSize / 1024.0) + " KB";
            return new DecimalFormat("#.##").format(fileSize / (1024.0 * 1024.0)) + " MB";
        }

        public String getFormattedDate() {
            if (message != null && message.getCreatedAt() != null) {
                return message.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
            return "";
        }

        public String getFileExtension() {
            if (fileName == null)
                return "";
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0 && lastDot < fileName.length() - 1) {
                return fileName.substring(lastDot + 1).toLowerCase();
            }
            return "";
        }
    }

    /**
     * Extract links từ messages
     */
    private List<String> extractLinks(List<Message> messages) {
        List<String> links = new java.util.ArrayList<>();
        for (Message msg : messages) {
            if (msg.getContent() != null) {
                Matcher matcher = URL_PATTERN.matcher(msg.getContent());
                while (matcher.find()) {
                    String url = matcher.group();
                    if (!links.contains(url)) {
                        links.add(url);
                    }
                }
            }
        }
        return links;
    }

    /**
     * Toggle info panel visibility
     */
    public void toggleInfoPanel() {
        if (infoPanel != null) {
            boolean isVisible = infoPanel.isVisible();
            infoPanel.setVisible(!isVisible);
            infoPanel.setManaged(!isVisible);
        }
    }

    /**
     * Hide info panel
     */
    public void hideInfoPanel() {
        if (infoPanel != null) {
            infoPanel.setVisible(false);
            infoPanel.setManaged(false);
        }
    }
}

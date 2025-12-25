package org.example.zalu.controller;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.util.Callback;
import org.example.zalu.model.ChatItem;
import org.example.zalu.model.GroupInfo;
import org.example.zalu.model.User;
import org.example.zalu.service.AvatarService;
import org.example.zalu.service.MessageUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ChatListCellFactory implements Callback<ListView<ChatItem>, ListCell<ChatItem>> {
    private static final Logger logger = LoggerFactory.getLogger(ChatListCellFactory.class);

    private final MainController mainController;
    private final MessageUpdateService messageUpdateService;
    private final Map<Integer, Boolean> onlineFriends;
    private final Map<Integer, Integer> unreadCounts;

    public ChatListCellFactory(MainController mainController,
            MessageUpdateService messageUpdateService,
            Map<Integer, Boolean> onlineFriends,
            Map<Integer, Integer> unreadCounts) {
        this.mainController = mainController;
        this.messageUpdateService = messageUpdateService;
        this.onlineFriends = onlineFriends;
        this.unreadCounts = unreadCounts;
    }

    @Override
    public ListCell<ChatItem> call(ListView<ChatItem> param) {
        return new ChatListCell();
    }

    private class ChatListCell extends ListCell<ChatItem> {
        private HBox itemBox;
        private Circle avatar;
        private Circle statusDot;
        private Label nameLabel;
        private Label previewLabel;
        private StackPane badgeContainer;
        private Label badgeLabel;

        public ChatListCell() {
            createLayout();
            setupEventHandlers();
        }

        private void createLayout() {
            // Container
            itemBox = new HBox(16);
            itemBox.setPadding(new Insets(16, 18, 16, 18));
            itemBox.setAlignment(Pos.CENTER_LEFT);
            itemBox.getStyleClass().add("chat-list-item");

            // Avatar setup - Tăng kích thước để dễ nhìn
            StackPane avatarContainer = new StackPane();
            avatar = new Circle(32, Color.web("#0088ff")); // Tăng từ 26 lên 32
            avatar.setStroke(Color.WHITE);
            avatar.setStrokeWidth(2.5);

            statusDot = new Circle(9, Color.web("#31d559")); // Tăng từ 8 lên 9
            statusDot.setStroke(Color.WHITE);
            statusDot.setStrokeWidth(2.5);
            statusDot.setTranslateX(22); // Điều chỉnh vị trí
            statusDot.setTranslateY(22);

            avatarContainer.getChildren().addAll(avatar, statusDot);

            // Info box (Name + Preview)
            VBox infoBox = new VBox(6); // Tăng spacing từ 5 lên 6
            infoBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(infoBox, Priority.ALWAYS); // Cho phép mở rộng

            nameLabel = new Label();
            nameLabel.getStyleClass().add("chat-list-name");
            nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 700; -fx-text-fill: #1c1e21;"); // Tăng từ 15px

            previewLabel = new Label();
            previewLabel.getStyleClass().add("chat-list-preview");
            previewLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #8e8e93; -fx-font-weight: 400;"); // Tăng từ 13px
            previewLabel.setMaxWidth(Double.MAX_VALUE); // Cho phép tự động điều chỉnh
            previewLabel.setWrapText(false);
            previewLabel.setEllipsisString("...");

            infoBox.getChildren().addAll(nameLabel, previewLabel);

            // Spacer
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.SOMETIMES);

            // Badge
            badgeContainer = new StackPane();
            badgeContainer.setVisible(false);
            badgeLabel = new Label();
            badgeLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: white;"); // Tăng từ 11px
            badgeContainer.getChildren().add(badgeLabel);
            badgeContainer.setStyle(
                    "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #ff4444 0%, #e63950 100%); " +
                            "-fx-background-radius: 13; -fx-padding: 4 9; -fx-min-width: 24; -fx-pref-height: 24; " +
                            "-fx-effect: dropshadow(gaussian, rgba(231,57,80,0.4), 4, 0, 0, 2);");

            VBox rightBox = new VBox();
            rightBox.setAlignment(Pos.CENTER);
            rightBox.getChildren().addAll(badgeContainer);

            itemBox.getChildren().addAll(avatarContainer, infoBox, spacer, rightBox);
        }

        private void setupEventHandlers() {
            // Click handler setup
            setOnMouseClicked(e -> {
                if (mainController.isRefreshing()) {
                    e.consume();
                    return;
                }

                ChatItem item = getItem();
                if (e.getClickCount() == 1 && getListView() != null && item != null) {
                    ChatItem currentSelected = getListView().getSelectionModel().getSelectedItem();
                    boolean isSameItem = false;

                    if (currentSelected != null) {
                        if (item.isGroup() && currentSelected.isGroup()) {
                            isSameItem = (item.getGroup().getId() == currentSelected.getGroup().getId());
                        } else if (!item.isGroup() && !currentSelected.isGroup()) {
                            isSameItem = (item.getUser().getId() == currentSelected.getUser().getId());
                        }
                    }

                    if (isSameItem) {
                        e.consume();
                        mainController.reloadChatForItem(item);
                    }
                }
            });
        }

        @Override
        protected void updateItem(ChatItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(null);
                applyAvatar(item);
                updateContent(item);
                updateBadge(item);
                setGraphic(itemBox);

                // Refresh style
                itemBox.getStyleClass().setAll("chat-list-item");
                if (isSelected()) {
                    itemBox.getStyleClass().add("selected");
                }
            }
        }

        private void applyAvatar(ChatItem item) {
            if (item.isGroup()) {
                avatar.setFill(Color.web("#4b7be5"));
            } else {
                User user = item.getUser();
                Image avatarImage = AvatarService.resolveAvatar(user);
                if (avatarImage != null) {
                    avatar.setFill(new ImagePattern(avatarImage));
                } else {
                    avatar.setFill(Color.web("#0088ff"));
                }
            }
        }

        private void updateContent(ChatItem item) {
            if (item.isGroup()) {
                GroupInfo group = item.getGroup();
                nameLabel.setText("👥 " + group.getName());
                previewLabel.setText(group.getMemberCount() + " thành viên");
                statusDot.setVisible(false);
            } else {
                User user = item.getUser();
                String displayName = (user.getFullName() != null && !user.getFullName().trim().isEmpty())
                        ? user.getFullName()
                        : user.getUsername();
                nameLabel.setText(displayName);

                String preview = messageUpdateService.getLastMessage(user.getId());
                if (preview != null && preview.length() > 35) {
                    preview = preview.substring(0, 32) + "...";
                }
                previewLabel.setText(preview != null ? preview : "");

                boolean isOnline = onlineFriends.getOrDefault(user.getId(), false);
                statusDot.setFill(isOnline ? Color.web("#31d559") : Color.web("#8e8e93"));
                statusDot.setVisible(true);
            }
        }

        private void updateBadge(ChatItem item) {
            int targetId = item.isGroup() ? -item.getGroup().getId() : item.getUser().getId();
            int count = unreadCounts.getOrDefault(targetId, 0);

            if (count > 0) {
                badgeContainer.setVisible(true);
                badgeLabel.setText(count > 5 ? "5++" : String.valueOf(count));
            } else {
                badgeContainer.setVisible(false);
            }
        }
    }
}

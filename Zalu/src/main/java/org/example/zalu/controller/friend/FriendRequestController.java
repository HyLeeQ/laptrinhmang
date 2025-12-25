package org.example.zalu.controller.friend;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.controller.MainController;
import org.example.zalu.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class FriendRequestController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(FriendRequestController.class);

    @FXML
    private TabPane tabPane;
    @FXML
    private ListView<User> incomingList;
    @FXML
    private ListView<User> outgoingList;
    // Buttons đã được di chuyển vào trong ListCell, không còn trong FXML

    // QUAN TRỌNG: Thêm dòng này để lấy controller của tab "Thêm Bạn"
    @FXML
    private AddFriendController addFriendTabController; // fx:id trong FXML là addFriendTab → JavaFX tự map thành
                                                        // addFriendTabController

    private Stage stage;
    private int currentUserId = -1;
    private List<User> incomingUsers = null;
    private List<User> outgoingUsers = null;
    private MainController mainController;

    // ==================== Setter ====================
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        System.out.println("FriendRequestController currentUserId set to: " + userId);
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        System.out.println("MainController set in FriendRequestController");
    }

    public void setIncomingRequests(List<User> incomingRequests) {
        this.incomingUsers = incomingRequests;
        loadIncomingList();
        System.out.println("Incoming requests set: " + incomingRequests.size());
    }

    public void setOutgoingRequests(List<User> outgoingRequests) {
        this.outgoingUsers = outgoingRequests;
        loadOutgoingList();
        System.out.println("Outgoing requests set: " + outgoingRequests.size());
    }

    // ==================== Initialize ====================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("FriendRequestController initialize for userId: " + currentUserId);

        // Cell factory - buttons giờ nằm trong mỗi card
        if (incomingList != null) {
            incomingList.setCellFactory(param -> createIncomingCell());
        }
        if (outgoingList != null) {
            outgoingList.setCellFactory(param -> createOutgoingCell());
        }

        // Mở tab đầu tiên
        if (tabPane != null) {
            tabPane.getSelectionModel().select(0);
        }
    }

    public void refreshRequests() {
        loadData();
    }

    public void loadData() {
        if (currentUserId > 0) {
            System.out.println("FriendRequestController: Loading data via Server for userId: " + currentUserId);

            // Register callback
            org.example.zalu.client.ChatEventManager.getInstance().registerPendingRequestsMapCallback(map -> {
                System.out.println("FriendRequestController: Received data map keys: " + map.keySet());
                List<User> incoming = map.get("incoming");
                List<User> outgoing = map.get("outgoing");

                javafx.application.Platform.runLater(() -> {
                    setIncomingRequests(incoming != null ? incoming : java.util.Collections.emptyList());
                    setOutgoingRequests(outgoing != null ? outgoing : java.util.Collections.emptyList());
                });
            });

            // Send request
            ChatClient.sendRequest("GET_FRIEND_REQUESTS_INFO|" + currentUserId);
        }
    }

    // DAOs no longer needed
    // private org.example.zalu.dao.FriendDAO friendDAO;
    // private org.example.zalu.dao.UserDAO userDAO;

    public void setDaos(Object unused1, Object unused2) {
        // Deprecated to keep compatibility if MainController calls it,
        // but basically do nothing or log warning
        System.out.println(
                "FriendRequestController: setDaos called but DAOs are DEPRECATED in partial Client-Server refactor.");
    }

    private ListCell<User> createIncomingCell() {
        return new ListCell<>() {
            private HBox card;
            private Circle avatar;
            private VBox infoBox;
            private Label nameLabel;
            private Label messageLabel;
            private HBox buttonsBox;
            private Button acceptBtn;
            private Button rejectBtn;

            {
                card = new HBox(16);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(16));
                card.getStyleClass().add("request-item-card");

                avatar = new Circle(32);
                avatar.setFill(Color.web("#0d8bff"));
                avatar.setStroke(Color.WHITE);
                avatar.setStrokeWidth(2);

                infoBox = new VBox(4);
                nameLabel = new Label();
                nameLabel.getStyleClass().add("request-item-name");
                messageLabel = new Label("muốn kết bạn với bạn");
                messageLabel.getStyleClass().add("request-item-text");
                infoBox.getChildren().addAll(nameLabel, messageLabel);
                infoBox.setAlignment(Pos.CENTER_LEFT);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                acceptBtn = new Button("✓ Chấp nhận");
                acceptBtn.getStyleClass().add("success-btn");
                acceptBtn.setPrefWidth(130);
                acceptBtn.setPrefHeight(38);

                rejectBtn = new Button("✗ Từ chối");
                rejectBtn.getStyleClass().add("danger-btn");
                rejectBtn.setPrefWidth(130);
                rejectBtn.setPrefHeight(38);

                buttonsBox = new HBox(10, acceptBtn, rejectBtn);
                buttonsBox.setAlignment(Pos.CENTER_RIGHT);

                card.getChildren().addAll(avatar, infoBox, spacer, buttonsBox);
            }

            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null || item.getId() == -1) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(resolveDisplayName(item));

                    // Set Avatar
                    javafx.scene.image.Image img = org.example.zalu.service.AvatarService.resolveAvatar(item);
                    if (img != null) {
                        avatar.setFill(new javafx.scene.paint.ImagePattern(img));
                    } else {
                        avatar.setFill(Color.web("#0d8bff"));
                    }

                    // Reset button actions
                    acceptBtn.setOnAction(e -> {
                        ChatClient.sendRequest("ACCEPT_FRIEND|" + currentUserId + "|" + item.getId());

                        // Show success notification
                        javafx.application.Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Thành công!");
                            alert.setHeaderText(null);
                            alert.setContentText("Đã chấp nhận lời mời kết bạn từ " + resolveDisplayName(item));
                            alert.showAndWait();

                            // Refresh UI - Load lại danh sách bạn bè mới nhất
                            refreshRequests();
                            backToMainAndRefresh();

                            // Đợi một chút để server xử lý xong, sau đó load lại friend list
                            new Thread(() -> {
                                try {
                                    Thread.sleep(500); // Đợi 500ms để server cập nhật
                                    javafx.application.Platform.runLater(() -> {
                                        if (mainController != null) {
                                            mainController.refreshFriendList();
                                            System.out.println(
                                                    "✓ Đã load lại danh sách bạn bè sau khi chấp nhận kết bạn");
                                        }
                                    });
                                } catch (InterruptedException ex) {
                                    logger.error("Thread interrupted: {}", ex.getMessage(), ex);
                                    Thread.currentThread().interrupt();
                                }
                            }).start();
                        });
                    });

                    rejectBtn.setOnAction(e -> {
                        ChatClient.sendRequest("REJECT_FRIEND|" + currentUserId + "|" + item.getId());

                        // Show success notification
                        javafx.application.Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Đã từ chối");
                            alert.setHeaderText(null);
                            alert.setContentText("Đã từ chối lời mời kết bạn từ " + resolveDisplayName(item));
                            alert.showAndWait();

                            // Refresh UI
                            refreshRequests();
                            backToMainAndRefresh();
                        });
                    });

                    setGraphic(card);
                }
            }
        };
    }

    public void initAddFriendTab() {
        if (addFriendTabController != null && currentUserId > 0) {
            addFriendTabController.setCurrentUserId(this.currentUserId);
            addFriendTabController.setMainController(this.mainController);
            addFriendTabController.setParentController(this);
            System.out.println("✓ Đã truyền currentUserId = " + currentUserId + " vào AddFriendController");
        } else {
            System.out.println("Lỗi: addFriendTabController null hoặc currentUserId <= 0");
        }
    }

    private ListCell<User> createOutgoingCell() {
        return new ListCell<>() {
            private HBox card;
            private Circle avatar;
            private VBox infoBox;
            private Label nameLabel;
            private Label messageLabel;
            private Button cancelBtn;

            {
                card = new HBox(16);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(16));
                card.getStyleClass().add("request-item-card");

                avatar = new Circle(32);
                avatar.setFill(Color.web("#0d8bff"));
                avatar.setStroke(Color.WHITE);
                avatar.setStrokeWidth(2);

                infoBox = new VBox(4);
                nameLabel = new Label();
                nameLabel.getStyleClass().add("request-item-name");
                messageLabel = new Label("Đã gửi lời mời kết bạn");
                messageLabel.getStyleClass().add("request-item-text");
                infoBox.getChildren().addAll(nameLabel, messageLabel);
                infoBox.setAlignment(Pos.CENTER_LEFT);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                cancelBtn = new Button("✕ Hủy yêu cầu");
                cancelBtn.getStyleClass().add("danger-btn");
                cancelBtn.setPrefWidth(150);
                cancelBtn.setPrefHeight(38);

                card.getChildren().addAll(avatar, infoBox, spacer, cancelBtn);
            }

            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null || item.getId() == -1) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(resolveDisplayName(item));

                    // Set Avatar
                    javafx.scene.image.Image img = org.example.zalu.service.AvatarService.resolveAvatar(item);
                    if (img != null) {
                        avatar.setFill(new javafx.scene.paint.ImagePattern(img));
                    } else {
                        avatar.setFill(Color.web("#0d8bff"));
                    }

                    // Reset button action
                    cancelBtn.setOnAction(e -> {
                        ChatClient.sendRequest("CANCEL_FRIEND|" + currentUserId + "|" + item.getId());
                        backToMainAndRefresh();
                    });

                    setGraphic(card);
                }
            }
        };
    }

    private void loadIncomingList() {
        incomingList.getItems().clear();
        if (incomingUsers == null || incomingUsers.isEmpty()) {
            Label emptyLabel = new Label("✨ Không có lời mời nào");
            emptyLabel.getStyleClass().add("empty-placeholder");
            incomingList.setPlaceholder(emptyLabel);
        } else {
            incomingList.getItems().addAll(incomingUsers);
        }
    }

    private void loadOutgoingList() {
        outgoingList.getItems().clear();
        if (outgoingUsers == null || outgoingUsers.isEmpty()) {
            Label emptyLabel = new Label("✨ Chưa gửi lời mời nào");
            emptyLabel.getStyleClass().add("empty-placeholder");
            outgoingList.setPlaceholder(emptyLabel);
        } else {
            outgoingList.getItems().addAll(outgoingUsers);
        }
    }

    private String resolveDisplayName(User user) {
        if (user == null)
            return "";
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getUsername();
    }

    // ==================== Action ====================
    // Các actions giờ được xử lý trực tiếp trong ListCell buttons
    // Không cần các methods này nữa vì buttons đã được di chuyển vào card

    private void backToMainAndRefresh() {
        // backToMain(); // Gây loop nếu gọi liên tục
        if (mainController != null) {
            mainController.refreshFriendList();
            // Ẩn item hiện tại khỏi list thay vì chuyển trang
            if (tabPane.getSelectionModel().getSelectedIndex() == 0) {
                // Đang ở tab Lời mời kết bạn
                // Xóa item đã xử lý (logic này nên được cải thiện bằng cách reload list từ
                // server)
                // Nhưng tạm thời refreshFriendList sẽ trigger update từ server
            }
        }
    }

    @FXML
    public void backToMain() {
        System.out.println("=== backToMain() called ===");
        System.out.println("mainController: " + (mainController != null));

        if (mainController != null) {
            try {
                // Quay lại main view bằng cách hiển thị welcome view
                mainController.showWelcomeInMessageArea();
                // Refresh danh sách bạn bè
                mainController.refreshFriendList();
                System.out.println("✓ Đã quay lại welcome view thành công");
            } catch (Exception e) {
                logger.error("Lỗi khi quay lại welcome view: {}", e.getMessage(), e);
            }
        } else {
            System.err.println("⚠ mainController is null! Không thể quay lại welcome view");
            // Fallback: Thử clear chatContainer trực tiếp nếu có thể
            javafx.application.Platform.runLater(() -> {
                javafx.scene.Node parent = null;
                if (tabPane != null) {
                    parent = tabPane.getParent();
                    while (parent != null && !(parent instanceof javafx.scene.layout.VBox)) {
                        parent = parent.getParent();
                    }
                    if (parent != null && parent.getParent() instanceof javafx.scene.layout.VBox) {
                        javafx.scene.layout.VBox container = (javafx.scene.layout.VBox) parent.getParent();
                        container.getChildren().clear();
                        System.out.println("✓ Đã clear container thủ công");
                    }
                }
            });
        }
    }
}
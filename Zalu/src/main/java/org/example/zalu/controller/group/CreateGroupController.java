package org.example.zalu.controller.group;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.example.zalu.client.ChatClient;
import org.example.zalu.dao.FriendDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.User;

import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CreateGroupController {
    @FXML
    private TextField groupNameField;
    @FXML
    private ListView<User> friendsListView;

    private int currentUserId;
    private FriendDAO friendDAO;
    private UserDAO userDAO;
    private ObservableList<User> friends;
    private final Set<Integer> selectedFriendIds = new HashSet<>();
    private Stage dialogStage;

    public void initialize() {
        // friendDAO = new FriendDAO(); // REMOVED
        // userDAO = new UserDAO(); // REMOVED

        // Tăng chiều cao để hiển thị ít nhất 3-4 user cùng lúc
        if (friendsListView != null) {
            friendsListView.setMinHeight(280); // Tăng từ 220 lên 280 (khoảng 4 user)
            friendsListView.setPrefHeight(350); // Tăng từ 220 lên 350
        }

        friendsListView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<User> call(ListView<User> param) {
                return new ListCell<>() {
                    private final CheckBox checkBox = new CheckBox();
                    private final javafx.scene.shape.Circle avatarCircle = new javafx.scene.shape.Circle(28); // Tăng từ
                                                                                                              // 16 lên
                                                                                                              // 28
                    private final Label nameLabel = new Label();
                    private final Label usernameLabel = new Label();
                    private final HBox container = new HBox(14); // Tăng spacing từ 10 lên 14

                    {
                        // Avatar setup - lớn hơn và đẹp hơn
                        avatarCircle.setFill(javafx.scene.paint.Color.web("#0088ff"));
                        avatarCircle.setStroke(javafx.scene.paint.Color.WHITE);
                        avatarCircle.setStrokeWidth(2.5);

                        // Text container - font lớn hơn
                        VBox textBox = new VBox(4); // Tăng spacing từ 2 lên 4
                        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #1c1e21;"); // Tăng
                                                                                                                  // từ
                                                                                                                  // 13px
                        usernameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #8e8e93;"); // Tăng từ 11px
                        textBox.getChildren().addAll(nameLabel, usernameLabel);

                        Region spacer = new Region();
                        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                        // Checkbox style
                        checkBox.setStyle("-fx-cursor: hand;");

                        container.setAlignment(Pos.CENTER_LEFT);
                        container.setPadding(new javafx.geometry.Insets(16, 18, 16, 18)); // Tăng padding
                        container.getChildren().addAll(avatarCircle, textBox, spacer, checkBox);
                        container.setStyle(
                                "-fx-background-radius: 12; " +
                                        "-fx-cursor: hand;");

                        // Click anywhere on container to toggle checkbox
                        container.setOnMouseClicked(e -> {
                            checkBox.setSelected(!checkBox.isSelected());
                            checkBox.fire();
                        });
                    }

                    @Override
                    protected void updateItem(User item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setGraphic(null);
                        } else {
                            String displayName = (item.getFullName() != null && !item.getFullName().isBlank())
                                    ? item.getFullName()
                                    : item.getUsername();
                            nameLabel.setText(displayName);
                            usernameLabel.setText("@" + item.getUsername());

                            // Load avatar
                            loadAvatar(item, avatarCircle);

                            checkBox.setSelected(selectedFriendIds.contains(item.getId()));
                            checkBox.setOnAction(e -> {
                                if (checkBox.isSelected()) {
                                    selectedFriendIds.add(item.getId());
                                    container.setStyle(
                                            "-fx-background-color: #e3f2fd; " +
                                                    "-fx-background-radius: 12; " +
                                                    "-fx-cursor: hand; " +
                                                    "-fx-border-color: #2196f3; " +
                                                    "-fx-border-width: 2; " +
                                                    "-fx-border-radius: 12;");
                                } else {
                                    selectedFriendIds.remove(item.getId());
                                    container.setStyle(
                                            "-fx-background-color: transparent; " +
                                                    "-fx-background-radius: 12; " +
                                                    "-fx-cursor: hand;");
                                }
                            });

                            // Set initial background
                            if (checkBox.isSelected()) {
                                container.setStyle(
                                        "-fx-background-color: #e3f2fd; " +
                                                "-fx-background-radius: 12; " +
                                                "-fx-cursor: hand; " +
                                                "-fx-border-color: #2196f3; " +
                                                "-fx-border-width: 2; " +
                                                "-fx-border-radius: 12;");
                            } else {
                                container.setStyle(
                                        "-fx-background-color: transparent; " +
                                                "-fx-background-radius: 12; " +
                                                "-fx-cursor: hand;");
                            }

                            // Hover effect
                            container.setOnMouseEntered(e -> {
                                if (!checkBox.isSelected()) {
                                    container.setStyle(
                                            "-fx-background-color: #f5f5f5; " +
                                                    "-fx-background-radius: 12; " +
                                                    "-fx-cursor: hand;");
                                }
                            });

                            container.setOnMouseExited(e -> {
                                if (!checkBox.isSelected()) {
                                    container.setStyle(
                                            "-fx-background-color: transparent; " +
                                                    "-fx-background-radius: 12; " +
                                                    "-fx-cursor: hand;");
                                }
                            });

                            setGraphic(container);
                        }
                    }
                };
            }
        });
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        loadFriends();
    }

    private void loadFriends() {
        if (currentUserId <= 0)
            return;

        System.out.println("CreateGroupController: Loading data via Server for userId: " + currentUserId);

        // Register one-time callback for this specific request
        org.example.zalu.client.ChatEventManager.getInstance().registerFriendsListFullCallback(fullList -> {
            javafx.application.Platform.runLater(() -> {
                if (fullList != null) {
                    friends = FXCollections.observableArrayList(fullList);
                    friendsListView.setItems(friends);
                    System.out.println("CreateGroupController: Loaded " + fullList.size() + " friends.");
                } else {
                    friendsListView.setItems(FXCollections.emptyObservableList());
                    System.out.println("CreateGroupController: Loaded 0 friends.");
                }
            });
        });

        // Send request
        ChatClient.sendRequest("GET_FRIENDS_LIST_FULL|" + currentUserId);
    }

    /**
     * Set friends list directly (used when passed from MainController)
     * This avoids callback conflicts
     */
    public void setFriendsList(List<User> friendsList) {
        javafx.application.Platform.runLater(() -> {
            if (friendsListView == null) {
                System.out.println("CreateGroupController: friendsListView is null!");
                return;
            }

            System.out.println("CreateGroupController: ListView visible=" + friendsListView.isVisible() +
                    ", managed=" + friendsListView.isManaged() +
                    ", height=" + friendsListView.getHeight());

            if (friendsList != null && !friendsList.isEmpty()) {
                friends = FXCollections.observableArrayList(friendsList);
                friendsListView.setItems(friends);
                friendsListView.refresh(); // Force refresh
                System.out.println("CreateGroupController: Set friends list with " + friendsList.size() + " friends.");
                System.out.println("CreateGroupController: ListView items count: " + friendsListView.getItems().size());
            } else {
                friendsListView.setItems(FXCollections.emptyObservableList());
                System.out.println("CreateGroupController: Set empty friends list.");
            }
        });
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    @FXML
    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    @FXML
    private void confirmCreateGroup() {
        String groupName = groupNameField.getText().trim();
        if (groupName.isEmpty()) {
            showAlert("Thiếu tên nhóm", "Vui lòng nhập tên nhóm trước khi tạo.");
            return;
        }
        if (selectedFriendIds.isEmpty()) {
            showAlert("Chưa chọn thành viên", "Hãy chọn ít nhất một người bạn để tạo nhóm.");
            return;
        }

        StringBuilder request = new StringBuilder("CREATE_GROUP|").append(groupName);
        for (Integer id : selectedFriendIds) {
            request.append("|").append(id);
        }
        ChatClient.sendRequest(request.toString());

        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void loadAvatar(User user, Circle circle) {
        try {
            javafx.scene.image.Image avatarImage = null;

            // Try to load from cache first
            byte[] cachedAvatar = org.example.zalu.client.ClientCache.getInstance().getAvatar(user.getId());
            if (cachedAvatar != null && cachedAvatar.length > 0) {
                avatarImage = new javafx.scene.image.Image(
                        new ByteArrayInputStream(cachedAvatar), 40, 40, true, true);
            } else if (user.getAvatarData() != null && user.getAvatarData().length > 0) {
                // Load from user data
                avatarImage = new javafx.scene.image.Image(
                        new ByteArrayInputStream(user.getAvatarData()), 40, 40, true, true);
            }

            if (avatarImage != null) {
                circle.setFill(new ImagePattern(avatarImage));
            } else {
                // Default avatar color
                circle.setFill(javafx.scene.paint.Color.web("#e0e7ff"));
            }
        } catch (Exception e) {
            System.err.println("Error loading avatar for user " + user.getId() + ": " + e.getMessage());
            circle.setFill(javafx.scene.paint.Color.web("#e0e7ff"));
        }
    }
}

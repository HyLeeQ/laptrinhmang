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
import javafx.stage.Stage;
import javafx.util.Callback;
import org.example.zalu.client.ChatClient;
import org.example.zalu.dao.FriendDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.User;

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
        friendDAO = new FriendDAO();
        userDAO = new UserDAO();

        friendsListView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<User> call(ListView<User> param) {
                return new ListCell<>() {
                    private final CheckBox checkBox = new CheckBox();
                    private final Label nameLabel = new Label();
                    private final Label subLabel = new Label();
                    private final HBox container = new HBox(12);

                    {
                        HBox textBox = new HBox();
                        textBox.setSpacing(4);
                        textBox.getChildren().addAll(nameLabel, subLabel);
                        subLabel.setStyle("-fx-text-fill: #8d96b2; -fx-font-size: 12px;");

                        Region spacer = new Region();
                        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                        container.setAlignment(Pos.CENTER_LEFT);
                        container.getChildren().addAll(checkBox, textBox, spacer);
                    }

                    @Override
                    protected void updateItem(User item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setGraphic(null);
                        } else {
                            nameLabel.setText(item.getUsername());
                            subLabel.setText(item.getFullName() != null && !item.getFullName().isBlank()
                                    ? "(" + item.getFullName() + ")"
                                    : "(ID: " + item.getId() + ")");
                            checkBox.setSelected(selectedFriendIds.contains(item.getId()));
                            checkBox.setOnAction(e -> {
                                if (checkBox.isSelected()) {
                                    selectedFriendIds.add(item.getId());
                                } else {
                                    selectedFriendIds.remove(item.getId());
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
        try {
            List<Integer> friendIds = friendDAO.getFriendsByUserId(currentUserId);
            friends = FXCollections.observableArrayList();
            for (int friendId : friendIds) {
                try {
                    User friend = userDAO.getUserById(friendId);
                    if (friend != null) {
                        friends.add(friend);
                    }
                } catch (org.example.zalu.exception.auth.UserNotFoundException e) {
                    // User might have been deleted, skip silently
                    System.out.println("Friend with ID " + friendId + " not found, skipping...");
                } catch (org.example.zalu.exception.database.DatabaseException | org.example.zalu.exception.database.DatabaseConnectionException e) {
                    System.err.println("Error loading friend: " + e.getMessage());
                }
            }
            friendsListView.setItems(friends);
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Lỗi", "Không thể tải danh sách bạn bè: " + e.getMessage());
        }
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
}

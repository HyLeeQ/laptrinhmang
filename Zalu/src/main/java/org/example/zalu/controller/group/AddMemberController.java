package org.example.zalu.controller.group;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.client.ChatEventManager;
import org.example.zalu.model.User;

import java.util.ArrayList;
import java.util.List;

public class AddMemberController {
    @FXML
    private Label groupNameLabel;
    @FXML
    private TextField searchFriendField;
    @FXML
    private ListView<User> friendsListView;
    @FXML
    private Button addMemberButton;

    private Stage dialogStage;
    private int groupId;
    private int currentUserId;
    private ObservableList<User> friends;
    private List<User> allCandidateFriends = new ArrayList<>();
    private Runnable onMemberAdded;
    private boolean isAdmin;

    public void initialize() {
        friends = FXCollections.observableArrayList();
        friendsListView.setItems(friends);

        friendsListView.setCellFactory(param -> new ListCell<User>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                } else {
                    String displayName = (user.getFullName() != null && !user.getFullName().trim().isEmpty())
                            ? user.getFullName()
                            : user.getUsername();
                    setText(displayName);
                }
            }
        });

        // Search listener
        searchFriendField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterFriends(newVal);
        });

        // Register callbacks
        ChatEventManager.getInstance().registerGroupInfoCallback(group -> {
            if (group != null && group.getId() == this.groupId) {
                groupNameLabel.setText(group.getName());
            }
        });

        ChatEventManager.getInstance().registerFriendsNotInGroupCallback(users -> {
            allCandidateFriends.clear();
            if (users != null) {
                allCandidateFriends.addAll(users);
            }
            filterFriends(searchFriendField.getText());
        });

        ChatEventManager.getInstance().registerAddGroupMemberCallback(msg -> {
            if (msg.startsWith("ADD_GROUP_MEMBER|SUCCESS")) {
                showAlert("Thành công", "Đã thêm thành viên vào nhóm.");
                if (onMemberAdded != null) {
                    onMemberAdded.run();
                }
                // Refresh list
                loadFriends();
            } else {
                String err = msg.replace("ADD_GROUP_MEMBER|FAIL|", "").replace("ADD_GROUP_MEMBER|FAIL", "");
                if (err.isEmpty())
                    err = "Lỗi không xác định";
                showAlert("Lỗi", "Không thể thêm thành viên: " + err);
            }
        });

        ChatEventManager.getInstance().registerMemberRoleCallback(msg -> {
            if (msg.startsWith("MEMBER_ROLE|")) {
                String role = msg.split("\\|")[1];
                isAdmin = "admin".equals(role);
                updateUIForAdminStatus();
            } else if (msg.equals("MEMBER_ROLE|ERROR")) {
                isAdmin = false;
                updateUIForAdminStatus();
            }
        });
    }

    private void filterFriends(String query) {
        if (query == null || query.trim().isEmpty()) {
            friends.setAll(allCandidateFriends);
        } else {
            String q = query.toLowerCase().trim();
            List<User> filtered = new ArrayList<>();
            for (User u : allCandidateFriends) {
                String name = (u.getFullName() != null ? u.getFullName() : u.getUsername()).toLowerCase();
                if (name.contains(q) || u.getUsername().toLowerCase().contains(q)) {
                    filtered.add(u);
                }
            }
            friends.setAll(filtered);
        }
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        // Check admin and load data only when both IDs are set
        if (groupId > 0 && currentUserId > 0) {
            checkAdminStatus();
            loadGroupInfo();
            loadFriends();
            // updateUIForAdminStatus called in callback
        }
    }

    private void checkAdminStatus() {
        if (groupId <= 0 || currentUserId <= 0)
            return;
        ChatClient.sendRequest("GET_MEMBER_ROLE|" + groupId + "|" + currentUserId);
    }

    private void updateUIForAdminStatus() {
        addMemberButton.setDisable(!isAdmin);
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public void setOnMemberAdded(Runnable callback) {
        this.onMemberAdded = callback;
    }

    private void loadGroupInfo() {
        ChatClient.sendRequest("GET_GROUP_INFO|" + groupId);
    }

    private void loadFriends() {
        ChatClient.sendRequest("GET_FRIENDS_NOT_IN_GROUP|" + groupId + "|" + currentUserId);
    }

    @FXML
    private void handleSearchFriend() {
        // Handled by listener
    }

    @FXML
    private void handleAddMember() {
        if (!isAdmin) {
            showAlert("Lỗi", "Chỉ admin mới có thể thêm thành viên.");
            return;
        }

        User selected = friendsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Chưa chọn", "Vui lòng chọn bạn bè để thêm vào nhóm.");
            return;
        }

        ChatClient.sendRequest("ADD_GROUP_MEMBER|" + groupId + "|" + selected.getId());
    }

    @FXML
    private void handleClose() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

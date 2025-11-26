package org.example.zalu.controller.group;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.dao.FriendDAO;
import org.example.zalu.dao.GroupDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.User;

import java.sql.SQLException;
import java.util.List;

public class AddMemberController {
    @FXML private Label groupNameLabel;
    @FXML private TextField searchFriendField;
    @FXML private ListView<User> friendsListView;
    @FXML private Button addMemberButton;
    
    private Stage dialogStage;
    private int groupId;
    private int currentUserId;
    private GroupDAO groupDAO;
    private UserDAO userDAO;
    private FriendDAO friendDAO;
    private ObservableList<User> friends;
    private Runnable onMemberAdded;
    private boolean isAdmin;
    
    public void initialize() {
        groupDAO = new GroupDAO();
        userDAO = new UserDAO();
        friendDAO = new FriendDAO();
        
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
                            ? user.getFullName() : user.getUsername();
                    setText(displayName);
                }
            }
        });
    }
    
    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }
    
    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        // Sau khi set cả groupId và currentUserId, mới check admin và load data
        if (groupId > 0 && currentUserId > 0) {
            checkAdminStatus();
            loadGroupInfo();
            loadFriends();
            updateUIForAdminStatus();
        }
    }
    
    private void checkAdminStatus() {
        if (groupId <= 0 || currentUserId <= 0) {
            System.err.println("Warning: Cannot check admin status - groupId or currentUserId not set");
            isAdmin = false;
            return;
        }
        try {
            String currentUserRole = groupDAO.getMemberRole(groupId, currentUserId);
            isAdmin = currentUserRole != null && currentUserRole.equals("admin");
            System.out.println("AddMemberController: Admin check - groupId=" + groupId + ", userId=" + currentUserId + ", role=" + currentUserRole + ", isAdmin=" + isAdmin);
        } catch (SQLException e) {
            System.err.println("Error checking admin status: " + e.getMessage());
            e.printStackTrace();
            isAdmin = false;
        }
    }
    
    private void updateUIForAdminStatus() {
        // Chỉ admin mới có thể thêm thành viên
        addMemberButton.setDisable(!isAdmin);
        if (!isAdmin) {
            showAlert("Lỗi", "Chỉ admin mới có thể thêm thành viên vào nhóm.");
        }
    }
    
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }
    
    public void setOnMemberAdded(Runnable callback) {
        this.onMemberAdded = callback;
    }
    
    private void loadGroupInfo() {
        try {
            org.example.zalu.model.GroupInfo group = groupDAO.getGroupById(groupId);
            if (group != null) {
                groupNameLabel.setText(group.getName());
            }
        } catch (SQLException e) {
            showAlert("Lỗi", "Không thể tải thông tin nhóm: " + e.getMessage());
        }
    }
    
    private void loadFriends() {
        try {
            List<Integer> friendIds = friendDAO.getFriendsByUserId(currentUserId);
            friends.clear();
            
            // Lấy danh sách thành viên hiện tại của nhóm
            List<Integer> memberIds = groupDAO.getGroupMembers(groupId);
            
            for (int friendId : friendIds) {
                try {
                    User friend = userDAO.getUserById(friendId);
                    if (friend != null) {
                        // Chỉ thêm bạn bè chưa là thành viên
                        if (!memberIds.contains(friendId)) {
                            friends.add(friend);
                        }
                    }
                } catch (org.example.zalu.exception.auth.UserNotFoundException e) {
                    System.out.println("Friend with ID " + friendId + " not found, skipping...");
                } catch (org.example.zalu.exception.database.DatabaseException | org.example.zalu.exception.database.DatabaseConnectionException e) {
                    System.err.println("Error loading friend: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            showAlert("Lỗi", "Không thể tải danh sách bạn bè: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleSearchFriend() {
        String searchText = searchFriendField.getText().trim().toLowerCase();
        if (searchText.isEmpty()) {
            loadFriends();
            return;
        }
        
        try {
            List<Integer> friendIds = friendDAO.getFriendsByUserId(currentUserId);
            List<Integer> memberIds = groupDAO.getGroupMembers(groupId);
            friends.clear();
            
            for (int friendId : friendIds) {
                try {
                    User friend = userDAO.getUserById(friendId);
                    if (friend != null && !memberIds.contains(friendId)) {
                        String displayName = (friend.getFullName() != null && !friend.getFullName().trim().isEmpty())
                                ? friend.getFullName() : friend.getUsername();
                        if (displayName.toLowerCase().contains(searchText) || 
                            friend.getUsername().toLowerCase().contains(searchText)) {
                            friends.add(friend);
                        }
                    }
                } catch (org.example.zalu.exception.auth.UserNotFoundException e) {
                    System.out.println("Friend with ID " + friendId + " not found, skipping...");
                } catch (org.example.zalu.exception.database.DatabaseException | org.example.zalu.exception.database.DatabaseConnectionException e) {
                    System.err.println("Error loading friend: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            showAlert("Lỗi", "Không thể tìm kiếm: " + e.getMessage());
        }
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
        
        try {
            boolean success = groupDAO.addMemberToGroup(groupId, selected.getId());
            if (success) {
                friends.remove(selected);
                showAlert("Thành công", "Đã thêm thành viên vào nhóm.");
                if (onMemberAdded != null) {
                    onMemberAdded.run();
                }
                // Gửi request đến server
                ChatClient.sendRequest("ADD_GROUP_MEMBER|" + groupId + "|" + selected.getId());
            } else {
                showAlert("Lỗi", "Không thể thêm thành viên (có thể đã là thành viên).");
            }
        } catch (SQLException e) {
            showAlert("Lỗi", "Lỗi khi thêm thành viên: " + e.getMessage());
        }
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


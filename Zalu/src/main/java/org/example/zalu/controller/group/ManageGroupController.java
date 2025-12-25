package org.example.zalu.controller.group;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.dao.GroupDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.User;
import org.example.zalu.service.AvatarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;

public class ManageGroupController {
    private static final Logger logger = LoggerFactory.getLogger(ManageGroupController.class);
    @FXML
    private Label groupNameLabel;
    @FXML
    private TextField groupNameField;
    @FXML
    private ListView<MemberWithRole> membersListView;
    @FXML
    private ImageView groupAvatarView;
    @FXML
    private Button changeAvatarButton;
    @FXML
    private Button updateNameButton;
    @FXML
    private Button removeMemberButton;
    @FXML
    private Button promoteAdminButton;
    @FXML
    private Button demoteAdminButton;
    @FXML
    private Button deleteGroupButton;

    private Stage dialogStage;
    private int groupId;
    private int currentUserId;
    private GroupDAO groupDAO;
    private UserDAO userDAO;
    private ObservableList<MemberWithRole> members;
    private Runnable onGroupUpdated;
    private String currentUserRole;
    private boolean isAdmin;
    private static final int MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2MB

    // Inner class để lưu member với role
    public static class MemberWithRole {
        private User user;
        private String role;

        public MemberWithRole(User user, String role) {
            this.user = user;
            this.role = role;
        }

        public User getUser() {
            return user;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        @Override
        public String toString() {
            String displayName = (user.getFullName() != null && !user.getFullName().trim().isEmpty())
                    ? user.getFullName()
                    : user.getUsername();
            return displayName + (role != null && role.equals("admin") ? " (Admin)" : "");
        }
    }

    public void initialize() {
        groupDAO = new GroupDAO();
        userDAO = new UserDAO();

        members = FXCollections.observableArrayList();
        membersListView.setItems(members);

        membersListView.setCellFactory(param -> new ListCell<MemberWithRole>() {
            private HBox itemBox;
            private javafx.scene.shape.Circle avatar;
            private Label nameLabel;
            private Label roleLabel;

            {
                itemBox = new HBox(12);
                itemBox.setAlignment(Pos.CENTER_LEFT);
                itemBox.setPadding(new Insets(8, 12, 8, 12));
                itemBox.setStyle("-fx-background-radius: 8;");

                // Avatar
                StackPane avatarContainer = new StackPane();
                avatar = new javafx.scene.shape.Circle(20, javafx.scene.paint.Color.web("#0088ff"));
                avatar.setStroke(javafx.scene.paint.Color.WHITE);
                avatar.setStrokeWidth(2);
                avatarContainer.getChildren().add(avatar);

                // Name and role
                VBox infoBox = new VBox(4);
                nameLabel = new Label();
                nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #1c1e21;");
                roleLabel = new Label();
                roleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #8e8e93;");
                infoBox.getChildren().addAll(nameLabel, roleLabel);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                itemBox.getChildren().addAll(avatarContainer, infoBox, spacer);

                // Listen for selection changes
                selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                    itemBox.getStyleClass().remove("selected-member-item");
                    if (isNowSelected) {
                        itemBox.getStyleClass().add("selected-member-item");
                        // Add checkmark icon if not exists
                        if (itemBox.getChildren().size() == 3) { // avatar, info, spacer
                            Label checkIcon = new Label("✓");
                            checkIcon.setStyle(
                                    "-fx-font-size: 18px; -fx-text-fill: #0088ff; -fx-font-weight: bold; -fx-padding: 0 8 0 0;");
                            itemBox.getChildren().add(checkIcon);
                        }
                    } else {
                        // Remove checkmark if exists
                        if (itemBox.getChildren().size() > 3) {
                            itemBox.getChildren().remove(itemBox.getChildren().size() - 1);
                        }
                    }
                });
            }

            @Override
            protected void updateItem(MemberWithRole memberWithRole, boolean empty) {
                super.updateItem(memberWithRole, empty);
                if (empty || memberWithRole == null) {
                    setText(null);
                    setGraphic(null);
                    itemBox.getStyleClass().remove("selected-member-item");
                } else {
                    setText(null);
                    User user = memberWithRole.getUser();
                    String displayName = (user.getFullName() != null && !user.getFullName().trim().isEmpty())
                            ? user.getFullName()
                            : user.getUsername();
                    nameLabel.setText(displayName);

                    String role = memberWithRole.getRole();
                    if (role != null && role.equals("admin")) {
                        roleLabel.setText("Quản trị viên");
                        roleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #0088ff; -fx-font-weight: 600;");
                    } else {
                        roleLabel.setText("Thành viên");
                        roleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #8e8e93;");
                    }

                    // Load avatar
                    try {
                        if (user.getAvatarData() != null && user.getAvatarData().length > 0) {
                            Image avatarImage = new Image(new ByteArrayInputStream(user.getAvatarData()), 40, 40, true,
                                    true);
                            javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(20, 20, 20);
                            ImageView avatarView = new ImageView(avatarImage);
                            avatarView.setClip(clip);
                            StackPane avatarContainer = (StackPane) itemBox.getChildren().get(0);
                            avatarContainer.getChildren().clear();
                            avatarContainer.getChildren().add(avatarView);
                        } else {
                            // Default avatar
                            Image defaultAvatar = AvatarService.getDefaultAvatar(40, 40);
                            if (defaultAvatar != null) {
                                javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(20, 20, 20);
                                ImageView avatarView = new ImageView(defaultAvatar);
                                avatarView.setClip(clip);
                                StackPane avatarContainer = (StackPane) itemBox.getChildren().get(0);
                                avatarContainer.getChildren().clear();
                                avatarContainer.getChildren().add(avatarView);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error loading avatar: {}", e.getMessage(), e);
                    }

                    setGraphic(itemBox);

                    // Set initial selected state
                    itemBox.getStyleClass().remove("selected-member-item");
                    if (isSelected()) {
                        itemBox.getStyleClass().add("selected-member-item");
                        if (itemBox.getChildren().size() == 3) {
                            Label checkIcon = new Label("✓");
                            checkIcon.setStyle(
                                    "-fx-font-size: 18px; -fx-text-fill: #0088ff; -fx-font-weight: bold; -fx-padding: 0 8 0 0;");
                            itemBox.getChildren().add(checkIcon);
                        }
                    } else {
                        // Remove checkmark if exists
                        if (itemBox.getChildren().size() > 3) {
                            itemBox.getChildren().remove(itemBox.getChildren().size() - 1);
                        }
                    }
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
            loadMembers();
            updateUIForAdminStatus();
        }
    }

    private void checkAdminStatus() {
        if (groupId <= 0 || currentUserId <= 0) {
            logger.warn("Cannot check admin status - groupId or currentUserId not set");
            isAdmin = false;
            return;
        }
        try {
            currentUserRole = groupDAO.getMemberRole(groupId, currentUserId);
            isAdmin = currentUserRole != null && currentUserRole.equals("admin");
            logger.debug("Admin check - groupId={}, userId={}, role={}, isAdmin={}", groupId, currentUserId,
                    currentUserRole, isAdmin);
        } catch (SQLException e) {
            logger.error("Error checking admin status: {}", e.getMessage(), e);
            isAdmin = false;
        }
    }

    private void updateUIForAdminStatus() {
        // Chỉ admin mới có thể thay đổi tên, avatar, xóa thành viên, xóa nhóm
        boolean canManage = isAdmin;
        updateNameButton.setDisable(!canManage);
        changeAvatarButton.setDisable(!canManage);
        removeMemberButton.setDisable(!canManage);
        promoteAdminButton.setDisable(!canManage);
        demoteAdminButton.setDisable(!canManage);
        deleteGroupButton.setDisable(!canManage);
        groupNameField.setEditable(canManage);
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public void setOnGroupUpdated(Runnable callback) {
        this.onGroupUpdated = callback;
    }

    private void loadGroupInfo() {
        try {
            org.example.zalu.model.GroupInfo group = groupDAO.getGroupById(groupId);
            if (group != null) {
                groupNameLabel.setText(group.getName());
                groupNameField.setText(group.getName());

                // Load avatar
                if (group.getAvatarData() != null && group.getAvatarData().length > 0) {
                    Image avatarImage = new Image(new ByteArrayInputStream(group.getAvatarData()), 100, 100, true,
                            true);
                    groupAvatarView.setImage(avatarImage);
                } else {
                    // Sử dụng default avatar
                    Image defaultAvatar = AvatarService.getDefaultAvatar(100, 100);
                    if (defaultAvatar != null) {
                        groupAvatarView.setImage(defaultAvatar);
                    }
                }
            }
        } catch (SQLException e) {
            showAlert("Lỗi", "Không thể tải thông tin nhóm: " + e.getMessage());
        }
    }

    private void loadMembers() {
        try {
            List<GroupDAO.GroupMemberInfo> memberInfos = groupDAO.getGroupMembersWithRole(groupId);
            members.clear();
            for (GroupDAO.GroupMemberInfo info : memberInfos) {
                try {
                    User member = userDAO.getUserById(info.getUserId());
                    if (member != null) {
                        members.add(new MemberWithRole(member, info.getRole()));
                    }
                } catch (org.example.zalu.exception.auth.UserNotFoundException e) {
                    logger.debug("Member with ID {} not found, skipping", info.getUserId());
                } catch (org.example.zalu.exception.database.DatabaseException
                        | org.example.zalu.exception.database.DatabaseConnectionException e) {
                    logger.error("Error loading member: {}", e.getMessage(), e);
                }
            }
        } catch (SQLException e) {
            showAlert("Lỗi", "Không thể tải danh sách thành viên: " + e.getMessage());
        }
    }

    @FXML
    private void handleUpdateGroupName() {
        if (!isAdmin) {
            showAlert("Lỗi", "Chỉ admin mới có thể đổi tên nhóm.");
            return;
        }

        String newName = groupNameField.getText().trim();
        if (newName.isEmpty()) {
            showAlert("Lỗi", "Tên nhóm không được để trống.");
            groupNameField.requestFocus();
            return;
        }

        if (newName.length() > 100) {
            showAlert("Lỗi", "Tên nhóm không được vượt quá 100 ký tự.");
            groupNameField.requestFocus();
            return;
        }

        // Kiểm tra xem tên có thay đổi không
        String currentName = groupNameLabel.getText();
        if (newName.equals(currentName)) {
            showAlert("Thông báo", "Tên nhóm không có thay đổi.");
            return;
        }

        try {
            boolean success = groupDAO.updateGroupName(groupId, newName);
            if (success) {
                groupNameLabel.setText(newName);
                showAlert("Thành công", "Đã cập nhật tên nhóm thành công.");
                if (onGroupUpdated != null) {
                    onGroupUpdated.run();
                }
                // Gửi request đến server để broadcast
                ChatClient.sendRequest("UPDATE_GROUP_NAME|" + groupId + "|" + newName);
            } else {
                showAlert("Lỗi", "Không thể cập nhật tên nhóm. Vui lòng thử lại.");
            }
        } catch (SQLException e) {
            logger.error("Lỗi khi cập nhật tên nhóm: {}", e.getMessage(), e);
            showAlert("Lỗi", "Lỗi khi cập nhật tên nhóm: " + e.getMessage());
        }
    }

    @FXML
    private void handleChangeAvatar() {
        if (!isAdmin) {
            showAlert("Lỗi", "Chỉ admin mới có thể đổi avatar nhóm.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh đại diện nhóm");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Hình ảnh", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("Tất cả", "*.*"));
        File selected = chooser.showOpenDialog(dialogStage);
        if (selected != null && selected.exists()) {
            try {
                // Kiểm tra kích thước file
                long fileSize = selected.length();
                if (fileSize > MAX_AVATAR_SIZE) {
                    showAlert("Lỗi",
                            String.format("File quá lớn (%.2f MB)! Kích thước tối đa: 2MB. Vui lòng chọn ảnh nhỏ hơn.",
                                    fileSize / (1024.0 * 1024.0)));
                    return;
                }

                // Đọc file
                byte[] bytes = Files.readAllBytes(selected.toPath());

                // Validate image format
                try {
                    Image testImage = new Image(new ByteArrayInputStream(bytes));
                    if (testImage.isError()) {
                        showAlert("Lỗi", "File không phải là hình ảnh hợp lệ. Vui lòng chọn file khác.");
                        return;
                    }
                } catch (Exception e) {
                    showAlert("Lỗi", "Không thể đọc file ảnh. Vui lòng chọn file khác.");
                    return;
                }

                // Cập nhật avatar
                boolean success = groupDAO.updateGroupAvatar(groupId, bytes);
                if (success) {
                    Image newImage = new Image(new ByteArrayInputStream(bytes), 100, 100, true, true);
                    groupAvatarView.setImage(newImage);
                    showAlert("Thành công", "Đã cập nhật avatar nhóm thành công.");
                    if (onGroupUpdated != null) {
                        onGroupUpdated.run();
                    }
                    // Gửi request đến server
                    ChatClient.sendRequest("UPDATE_GROUP_AVATAR|" + groupId);
                } else {
                    showAlert("Lỗi", "Không thể cập nhật avatar. Database có thể chưa hỗ trợ tính năng này.");
                }
            } catch (IOException e) {
                logger.error("Lỗi khi đọc file: {}", e.getMessage(), e);
                showAlert("Lỗi", "Lỗi khi đọc file: " + e.getMessage());
            } catch (SQLException e) {
                logger.error("Lỗi khi cập nhật avatar: {}", e.getMessage(), e);
                showAlert("Lỗi", "Lỗi khi cập nhật avatar: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleRemoveMember() {
        if (!isAdmin) {
            showAlert("Lỗi", "Chỉ admin mới có thể xóa thành viên.");
            return;
        }

        MemberWithRole selected = membersListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Chưa chọn", "Vui lòng chọn thành viên để xóa khỏi nhóm.");
            return;
        }

        User selectedUser = selected.getUser();
        String displayName = (selectedUser.getFullName() != null && !selectedUser.getFullName().trim().isEmpty())
                ? selectedUser.getFullName()
                : selectedUser.getUsername();

        if (selectedUser.getId() == currentUserId) {
            showAlert("Lỗi",
                    "Bạn không thể xóa chính mình khỏi nhóm.\n\nHãy sử dụng nút \"Rời nhóm\" nếu bạn muốn rời khỏi nhóm này.");
            return;
        }

        // Không cho phép xóa admin khác
        if (selected.getRole() != null && selected.getRole().equals("admin")) {
            showAlert("Lỗi", "Không thể xóa quản trị viên khỏi nhóm.\n\nHãy hạ quyền quản trị viên trước khi xóa.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xóa thành viên");
        confirm.setHeaderText("Xóa thành viên khỏi nhóm");
        confirm.setContentText("Bạn có chắc muốn xóa \"" + displayName + "\" khỏi nhóm?\n\n" +
                "Thành viên này sẽ không thể xem tin nhắn và thông tin nhóm sau khi bị xóa.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    boolean success = groupDAO.removeMemberFromGroup(groupId, selectedUser.getId());
                    if (success) {
                        members.remove(selected);
                        membersListView.getSelectionModel().clearSelection();
                        showAlert("Thành công", "Đã xóa \"" + displayName + "\" khỏi nhóm.");
                        if (onGroupUpdated != null) {
                            onGroupUpdated.run();
                        }
                        // Gửi request đến server
                        ChatClient.sendRequest("REMOVE_GROUP_MEMBER|" + groupId + "|" + selectedUser.getId());
                    } else {
                        showAlert("Lỗi", "Không thể xóa thành viên. Vui lòng thử lại.");
                    }
                } catch (SQLException e) {
                    logger.error("Lỗi khi xóa thành viên: {}", e.getMessage(), e);
                    showAlert("Lỗi", "Lỗi khi xóa thành viên: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handlePromoteAdmin() {
        if (!isAdmin) {
            showAlert("Lỗi", "Chỉ admin mới có thể thăng quyền admin.");
            return;
        }

        MemberWithRole selected = membersListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Chưa chọn", "Vui lòng chọn thành viên để thăng quyền admin.");
            return;
        }

        User selectedUser = selected.getUser();
        String displayName = (selectedUser.getFullName() != null && !selectedUser.getFullName().trim().isEmpty())
                ? selectedUser.getFullName()
                : selectedUser.getUsername();

        if (selected.getRole() != null && selected.getRole().equals("admin")) {
            showAlert("Thông báo", displayName + " đã là quản trị viên.");
            return;
        }

        if (selectedUser.getId() == currentUserId) {
            showAlert("Thông báo", "Bạn đã là quản trị viên.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận thăng quyền");
        confirm.setHeaderText("Thăng quyền quản trị viên");
        confirm.setContentText("Bạn có chắc muốn thăng quyền quản trị viên cho \"" + displayName + "\"?\n\n" +
                "Quản trị viên có thể:\n" +
                "• Đổi tên và avatar nhóm\n" +
                "• Thêm/xóa thành viên\n" +
                "• Thăng/hạ quyền admin\n" +
                "• Xóa nhóm");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    boolean success = groupDAO.updateMemberRole(groupId, selectedUser.getId(), "admin");
                    if (success) {
                        selected.setRole("admin");
                        membersListView.refresh();
                        showAlert("Thành công", "Đã thăng quyền quản trị viên cho \"" + displayName + "\".");
                        if (onGroupUpdated != null) {
                            onGroupUpdated.run();
                        }
                        ChatClient.sendRequest("PROMOTE_ADMIN|" + groupId + "|" + selectedUser.getId());
                    } else {
                        showAlert("Lỗi", "Không thể thăng quyền admin. Database có thể chưa hỗ trợ tính năng này.");
                    }
                } catch (SQLException e) {
                    logger.error("Lỗi khi thăng quyền admin: {}", e.getMessage(), e);
                    showAlert("Lỗi", "Lỗi khi thăng quyền admin: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleDemoteAdmin() {
        if (!isAdmin) {
            showAlert("Lỗi", "Chỉ admin mới có thể hạ quyền admin.");
            return;
        }

        MemberWithRole selected = membersListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Chưa chọn", "Vui lòng chọn quản trị viên để hạ quyền.");
            return;
        }

        User selectedUser = selected.getUser();
        String displayName = (selectedUser.getFullName() != null && !selectedUser.getFullName().trim().isEmpty())
                ? selectedUser.getFullName()
                : selectedUser.getUsername();

        if (selectedUser.getId() == currentUserId) {
            showAlert("Lỗi", "Bạn không thể hạ quyền chính mình.");
            return;
        }

        if (selected.getRole() == null || !selected.getRole().equals("admin")) {
            showAlert("Thông báo", "\"" + displayName + "\" không phải quản trị viên.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận hạ quyền");
        confirm.setHeaderText("Hạ quyền quản trị viên");
        confirm.setContentText("Bạn có chắc muốn hạ quyền quản trị viên của \"" + displayName + "\"?\n\n" +
                "Sau khi hạ quyền, thành viên này sẽ chỉ có quyền thành viên thông thường.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    boolean success = groupDAO.updateMemberRole(groupId, selectedUser.getId(), "member");
                    if (success) {
                        selected.setRole("member");
                        membersListView.refresh();
                        showAlert("Thành công", "Đã hạ quyền quản trị viên của \"" + displayName + "\".");
                        if (onGroupUpdated != null) {
                            onGroupUpdated.run();
                        }
                        ChatClient.sendRequest("DEMOTE_ADMIN|" + groupId + "|" + selectedUser.getId());
                    } else {
                        showAlert("Lỗi", "Không thể hạ quyền admin. Database có thể chưa hỗ trợ tính năng này.");
                    }
                } catch (SQLException e) {
                    logger.error("Lỗi khi hạ quyền admin: {}", e.getMessage(), e);
                    showAlert("Lỗi", "Lỗi khi hạ quyền admin: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleLeaveGroup() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn rời nhóm này?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    boolean success = groupDAO.leaveGroup(groupId, currentUserId);
                    if (success) {
                        showAlert("Thành công", "Bạn đã rời nhóm.");
                        // Gửi request đến server
                        ChatClient.sendRequest("LEAVE_GROUP|" + groupId);
                        if (dialogStage != null) {
                            dialogStage.close();
                        }
                    } else {
                        showAlert("Lỗi", "Không thể rời nhóm.");
                    }
                } catch (SQLException e) {
                    showAlert("Lỗi", "Lỗi khi rời nhóm: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleDeleteGroup() {
        if (!isAdmin) {
            showAlert("Lỗi", "Chỉ admin mới có thể xóa nhóm.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn xóa nhóm này? Hành động này không thể hoàn tác.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    boolean success = groupDAO.deleteGroup(groupId);
                    if (success) {
                        showAlert("Thành công", "Đã xóa nhóm.");
                        // Gửi request đến server
                        ChatClient.sendRequest("DELETE_GROUP|" + groupId);
                        if (dialogStage != null) {
                            dialogStage.close();
                        }
                    } else {
                        showAlert("Lỗi", "Không thể xóa nhóm.");
                    }
                } catch (SQLException e) {
                    showAlert("Lỗi", "Lỗi khi xóa nhóm: " + e.getMessage());
                }
            }
        });
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

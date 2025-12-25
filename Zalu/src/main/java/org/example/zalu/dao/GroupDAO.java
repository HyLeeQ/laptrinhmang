package org.example.zalu.dao;

import org.example.zalu.model.GroupInfo;
import org.example.zalu.util.database.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {

    // Không cần truyền Connection nữa – dùng chung HikariCP pool từ DBConnection
    public GroupDAO() {
        // Pool sẽ được lấy tự động khi cần
    }

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection(); // ← Xịn, an toàn, hiệu suất cao
    }

    public List<GroupInfo> getUserGroups(int userId) throws SQLException {
        List<GroupInfo> groups = new ArrayList<>();
        String sql = "SELECT g.id, g.name, COUNT(gm.user_id) as member_count " +
                "FROM groups g JOIN group_members gm ON g.id = gm.group_id " +
                "WHERE gm.user_id = ? GROUP BY g.id ORDER BY g.created_at DESC";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    GroupInfo group = new GroupInfo();
                    group.setId(rs.getInt("id"));
                    group.setName(rs.getString("name"));
                    group.setMemberCount(rs.getInt("member_count"));

                    // Load avatar nếu có
                    if (hasAvatarColumn(conn)) {
                        loadGroupAvatar(conn, group);
                    }

                    groups.add(group);
                }
            }
        }
        return groups;
    }

    public int createGroup(String name, int creatorId, List<Integer> memberIds) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                int groupId = insertGroupRecord(conn, name, creatorId);
                if (groupId <= 0) {
                    conn.rollback();
                    return -1;
                }

                // Thêm người tạo làm admin
                addMemberToGroup(conn, groupId, creatorId, "admin");

                // Thêm các thành viên khác
                for (int memberId : memberIds) {
                    if (memberId != creatorId) {
                        addMemberToGroup(conn, groupId, memberId, "member");
                    }
                }

                createInitialGroupMessage(conn, groupId, creatorId, name);

                conn.commit();
                return groupId;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // Sửa để nhận Connection từ pool (tránh tạo PreparedStatement mới mỗi lần)
    private void addMemberToGroup(Connection conn, int groupId, int userId, String role) throws SQLException {
        if (hasRoleColumn(conn)) {
            String sql = "INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, groupId);
                pstmt.setInt(2, userId);
                pstmt.setString(3, role != null ? role : "member");
                pstmt.executeUpdate();
            }
        } else {
            String sql = "INSERT INTO group_members (group_id, user_id) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, groupId);
                pstmt.setInt(2, userId);
                pstmt.executeUpdate();
            }
        }
    }

    public List<Integer> getGroupMembers(int groupId) throws SQLException {
        List<Integer> members = new ArrayList<>();
        String sql = "SELECT user_id FROM group_members WHERE group_id = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    members.add(rs.getInt("user_id"));
                }
            }
        }
        return members;
    }

    private int insertGroupRecord(Connection conn, String name, int creatorId) throws SQLException {
        String sqlWithCreator = "INSERT INTO groups (name, created_by) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlWithCreator, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, creatorId);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            if (isUnknownColumn(e, "created_by")) {
                String fallbackSql = "INSERT INTO groups (name) VALUES (?)";
                try (PreparedStatement pstmt = conn.prepareStatement(fallbackSql, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, name);
                    pstmt.executeUpdate();
                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }
            } else {
                throw e;
            }
        }
        return -1;
    }

    private void createInitialGroupMessage(Connection conn, int groupId, int creatorId, String groupName) {
        String sql = "INSERT INTO messages (sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String content = "Nhóm \"" + groupName + "\" vừa được tạo.";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, creatorId);
            pstmt.setNull(2, Types.INTEGER);
            pstmt.setString(3, content);
            pstmt.setNull(4, Types.BLOB);
            pstmt.setNull(5, Types.VARCHAR);
            pstmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setBoolean(7, true);
            pstmt.setInt(8, groupId);
            pstmt.executeUpdate();
        } catch (SQLException ex) {
            System.out.println("Warning: Không thể tạo tin nhắn khởi tạo cho nhóm: " + ex.getMessage());
        }
    }

    private static volatile Boolean groupMembersHasRoleColumn = null;

    private boolean hasRoleColumn(Connection conn) {
        if (groupMembersHasRoleColumn != null) {
            return groupMembersHasRoleColumn;
        }
        synchronized (GroupDAO.class) {
            if (groupMembersHasRoleColumn == null) {
                groupMembersHasRoleColumn = detectRoleColumn(conn);
                System.out.println("GroupDAO: group_members.role column available? " + groupMembersHasRoleColumn);
            }
        }
        return groupMembersHasRoleColumn;
    }

    private boolean detectRoleColumn(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            if (columnExists(meta, catalog, "group_members", "role")) {
                return true;
            }
            if (columnExists(meta, catalog, "GROUP_MEMBERS", "ROLE")) {
                return true;
            }
        } catch (SQLException e) {
            System.out.println("GroupDAO: không thể kiểm tra cột role, mặc định = false. Lỗi: " + e.getMessage());
        }
        return false;
    }

    private boolean columnExists(DatabaseMetaData meta, String catalog, String tableName, String columnName)
            throws SQLException {
        try (ResultSet rs = meta.getColumns(catalog, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private boolean isUnknownColumn(SQLException e, String column) {
        String message = e.getMessage();
        return message != null && message.toLowerCase().contains("unknown column '" + column.toLowerCase() + "'");
    }

    /**
     * Cập nhật tên nhóm
     */
    public boolean updateGroupName(int groupId, String newName) throws SQLException {
        String sql = "UPDATE groups SET name = ? WHERE id = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setInt(2, groupId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Thêm thành viên vào nhóm (public method)
     */
    public boolean addMemberToGroup(int groupId, int userId) throws SQLException {
        // Kiểm tra xem user đã là thành viên chưa
        if (isMemberOfGroup(groupId, userId)) {
            return false; // Đã là thành viên
        }

        try (Connection conn = getConnection()) {
            addMemberToGroup(conn, groupId, userId, "member");
            return true;
        }
    }

    /**
     * Xóa thành viên khỏi nhóm
     */
    public boolean removeMemberFromGroup(int groupId, int userId) throws SQLException {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Kiểm tra user có phải thành viên của nhóm không
     */
    public boolean isMemberOfGroup(int groupId, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Lấy role của user trong nhóm
     */
    public String getMemberRole(int groupId, int userId) throws SQLException {
        if (!hasRoleColumn(getConnection())) {
            return "member"; // Mặc định nếu không có cột role
        }

        String sql = "SELECT role FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String role = rs.getString("role");
                    return role != null ? role : "member";
                }
            }
        }
        return null; // Không phải thành viên
    }

    /**
     * Xóa nhóm (chỉ admin mới được)
     */
    public boolean deleteGroup(int groupId) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Xóa tất cả thành viên
                String deleteMembers = "DELETE FROM group_members WHERE group_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteMembers)) {
                    pstmt.setInt(1, groupId);
                    pstmt.executeUpdate();
                }

                // Xóa nhóm
                String deleteGroup = "DELETE FROM groups WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteGroup)) {
                    pstmt.setInt(1, groupId);
                    int result = pstmt.executeUpdate();
                    conn.commit();
                    return result > 0;
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Rời nhóm
     */
    public boolean leaveGroup(int groupId, int userId) throws SQLException {
        return removeMemberFromGroup(groupId, userId);
    }

    /**
     * Lấy thông tin nhóm theo ID (bao gồm avatar)
     */
    public GroupInfo getGroupById(int groupId) throws SQLException {
        String sql = "SELECT id, name FROM groups WHERE id = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    GroupInfo group = new GroupInfo();
                    group.setId(rs.getInt("id"));
                    group.setName(rs.getString("name"));

                    // Load avatar nếu có
                    if (hasAvatarColumn(conn)) {
                        loadGroupAvatar(conn, group);
                    }

                    // Đếm số thành viên
                    String countSql = "SELECT COUNT(*) FROM group_members WHERE group_id = ?";
                    try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                        countStmt.setInt(1, groupId);
                        try (ResultSet countRs = countStmt.executeQuery()) {
                            if (countRs.next()) {
                                group.setMemberCount(countRs.getInt(1));
                            }
                        }
                    }

                    return group;
                }
            }
        }
        return null;
    }

    /**
     * Cập nhật avatar nhóm
     */
    public boolean updateGroupAvatar(int groupId, byte[] avatarData) throws SQLException {
        try (Connection conn = getConnection()) {
            if (!hasAvatarColumn(conn)) {
                // Nếu không có cột avatar, không làm gì
                return false;
            }

            String sql = "UPDATE groups SET avatar_data = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                if (avatarData != null && avatarData.length > 0) {
                    pstmt.setBytes(1, avatarData);
                } else {
                    pstmt.setNull(1, Types.BLOB);
                }
                pstmt.setInt(2, groupId);
                return pstmt.executeUpdate() > 0;
            }
        }
    }

    /**
     * Kiểm tra xem bảng groups có cột avatar_data không
     */
    private static volatile Boolean groupsHasAvatarColumn = null;

    private boolean hasAvatarColumn(Connection conn) {
        if (groupsHasAvatarColumn != null) {
            return groupsHasAvatarColumn;
        }
        synchronized (GroupDAO.class) {
            if (groupsHasAvatarColumn == null) {
                groupsHasAvatarColumn = detectAvatarColumn(conn);
                System.out.println("GroupDAO: groups.avatar_data column available? " + groupsHasAvatarColumn);
            }
        }
        return groupsHasAvatarColumn;
    }

    private boolean detectAvatarColumn(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            if (columnExists(meta, catalog, "groups", "avatar_data")) {
                return true;
            }
            if (columnExists(meta, catalog, "GROUPS", "AVATAR_DATA")) {
                return true;
            }
        } catch (SQLException e) {
            System.out
                    .println("GroupDAO: không thể kiểm tra cột avatar_data, mặc định = false. Lỗi: " + e.getMessage());
        }
        return false;
    }

    /**
     * Load avatar data vào GroupInfo
     */
    private void loadGroupAvatar(Connection conn, GroupInfo group) throws SQLException {
        String sql = "SELECT avatar_data FROM groups WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, group.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    byte[] avatarData = rs.getBytes("avatar_data");
                    if (avatarData != null && avatarData.length > 0) {
                        group.setAvatarData(avatarData);
                    }
                }
            }
        }
    }

    /**
     * Lấy danh sách thành viên với role
     */
    public List<GroupMemberInfo> getGroupMembersWithRole(int groupId) throws SQLException {
        List<GroupMemberInfo> members = new ArrayList<>();
        String sql = hasRoleColumn(getConnection())
                ? "SELECT user_id, role FROM group_members WHERE group_id = ?"
                : "SELECT user_id FROM group_members WHERE group_id = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    GroupMemberInfo info = new GroupMemberInfo();
                    info.setUserId(rs.getInt("user_id"));
                    if (hasRoleColumn(conn)) {
                        String role = rs.getString("role");
                        info.setRole(role != null ? role : "member");
                    } else {
                        info.setRole("member");
                    }
                    members.add(info);
                }
            }
        }
        return members;
    }

    /**
     * Cập nhật role của thành viên (promote/demote admin)
     */
    public boolean updateMemberRole(int groupId, int userId, String role) throws SQLException {
        if (!hasRoleColumn(getConnection())) {
            return false; // Không hỗ trợ role
        }

        String sql = "UPDATE group_members SET role = ? WHERE group_id = ? AND user_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, role);
            pstmt.setInt(2, groupId);
            pstmt.setInt(3, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Inner class để lưu thông tin thành viên với role
     */
    public static class GroupMemberInfo {
        private int userId;
        private String role;

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    /**
     * Đếm tổng số group trong hệ thống
     */
    public int getTotalGroupCount() throws SQLException {
        String sql = "SELECT COUNT(*) as total FROM groups";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("total");
            }
            return 0;
        }
    }
}
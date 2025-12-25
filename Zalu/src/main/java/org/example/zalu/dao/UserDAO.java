package org.example.zalu.dao;

import org.example.zalu.model.User;
import org.example.zalu.util.database.DBConnection;
import org.example.zalu.exception.database.DatabaseConnectionException;
import org.example.zalu.exception.database.DatabaseException;
import org.example.zalu.exception.auth.UserNotFoundException;
import org.example.zalu.exception.auth.InvalidCredentialsException;
import org.example.zalu.exception.auth.RegistrationFailedException;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    // Không cần constructor nhận Connection nữa
    public UserDAO() {
        // Không làm gì cả – sẽ dùng DBConnection.getConnection()
    }

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    public boolean register(User user)
            throws RegistrationFailedException, DatabaseException, DatabaseConnectionException {
        String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        String sql = "INSERT INTO users (username, full_name, password, email, phone, avatar_url, avatar_data, bio, birthdate, gender, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getFullName());
            stmt.setString(3, hashedPassword);
            stmt.setString(4, user.getEmail());
            stmt.setString(5, user.getPhone());
            stmt.setString(6, user.getAvatarUrlRaw());
            stmt.setBytes(7, user.getAvatarData());
            stmt.setString(8, user.getBio());
            Date sqlDate = (user.getBirthdate() != null) ? Date.valueOf(user.getBirthdate()) : null;
            stmt.setDate(9, sqlDate);
            stmt.setString(10, user.getGender());
            stmt.setString(11, user.getStatus());
            boolean success = stmt.executeUpdate() > 0;
            if (!success) {
                throw new RegistrationFailedException("Không thể tạo tài khoản");
            }
            return success;
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            throw new RegistrationFailedException("Tên đăng nhập, email hoặc số điện thoại đã tồn tại", e);
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") ||
                    e.getMessage().toLowerCase().contains("timeout") ||
                    e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi đăng ký tài khoản", e);
        }
    }

    public User login(String username, String password)
            throws InvalidCredentialsException, UserNotFoundException, DatabaseException, DatabaseConnectionException {
        String sql = "SELECT id, username, full_name, password, email, phone, avatar_url, avatar_data, bio, birthdate, gender, status, created_at "
                +
                "FROM users WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hashedPass = rs.getString("password");
                    if (hashedPass == null || hashedPass.trim().isEmpty() || !BCrypt.checkpw(password, hashedPass)) {
                        throw new InvalidCredentialsException("Mật khẩu không chính xác");
                    }
                    User user = new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            rs.getString("avatar_url"),
                            rs.getString("bio"),
                            (rs.getDate("birthdate") != null) ? rs.getDate("birthdate").toLocalDate() : null,
                            rs.getString("status"),
                            rs.getString("gender"));
                    user.setCreatedAt(
                            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime()
                                    : null);
                    user.setAvatarData(rs.getBytes("avatar_data"));
                    return user;
                } else {
                    throw new UserNotFoundException("Không tìm thấy người dùng với username: " + username);
                }
            }
        } catch (InvalidCredentialsException | UserNotFoundException e) {
            throw e;
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") ||
                    e.getMessage().toLowerCase().contains("timeout") ||
                    e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi đăng nhập", e);
        }
    }

    public User getUserById(int id) throws UserNotFoundException, DatabaseException, DatabaseConnectionException {
        String sql = "SELECT id, username, full_name, email, phone, avatar_url, avatar_data, bio, birthdate, gender, status, created_at FROM users WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            rs.getString("avatar_url"),
                            rs.getString("bio"),
                            (rs.getDate("birthdate") != null) ? rs.getDate("birthdate").toLocalDate() : null,
                            rs.getString("status"),
                            rs.getString("gender"));
                    user.setCreatedAt(
                            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime()
                                    : null);
                    user.setAvatarData(rs.getBytes("avatar_data"));
                    return user;
                } else {
                    throw new UserNotFoundException("Không tìm thấy người dùng với ID: " + id);
                }
            }
        } catch (UserNotFoundException e) {
            throw e;
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") ||
                    e.getMessage().toLowerCase().contains("timeout") ||
                    e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi lấy thông tin người dùng", e);
        }
    }

    public boolean updateUser(User user) throws DatabaseException, DatabaseConnectionException {
        boolean hasPassword = user.getPassword() != null && !user.getPassword().isBlank();
        String sql = "UPDATE users SET username = ?, full_name = ?, email = ?, phone = ?, avatar_url = ?, avatar_data = ?, bio = ?, birthdate = ?, gender = ?, status = ?";
        if (hasPassword) {
            sql += ", password = ?";
        }
        sql += " WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            stmt.setString(idx++, user.getUsername());
            stmt.setString(idx++, user.getFullName());
            stmt.setString(idx++, user.getEmail());
            stmt.setString(idx++, user.getPhone());
            stmt.setString(idx++, user.getAvatarUrlRaw());
            stmt.setBytes(idx++, user.getAvatarData());
            stmt.setString(idx++, user.getBio());
            Date sqlDate = (user.getBirthdate() != null) ? Date.valueOf(user.getBirthdate()) : null;
            stmt.setDate(idx++, sqlDate);
            stmt.setString(idx++, user.getGender());
            stmt.setString(idx++, user.getStatus());
            if (hasPassword) {
                stmt.setString(idx++, BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
            }
            stmt.setInt(idx, user.getId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") ||
                    e.getMessage().toLowerCase().contains("timeout") ||
                    e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi cập nhật thông tin người dùng", e);
        }
    }

    public boolean updateStatus(int userId, String status) throws DatabaseException, DatabaseConnectionException {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") ||
                    e.getMessage().toLowerCase().contains("timeout") ||
                    e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi cập nhật trạng thái người dùng", e);
        }
    }

    public List<User> searchUsers(String query) throws DatabaseException, DatabaseConnectionException {
        List<User> users = new ArrayList<>();
        // Removed avatar_data
        String sql = "SELECT id, username, full_name, email, phone, avatar_url, bio, birthdate, gender, status "
                +
                "FROM users WHERE (full_name LIKE ? OR phone LIKE ? OR email LIKE ?) AND id != ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            stmt.setInt(4, 0);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    User user = new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            rs.getString("avatar_url"),
                            rs.getString("bio"),
                            (rs.getDate("birthdate") != null) ? rs.getDate("birthdate").toLocalDate() : null,
                            rs.getString("status"),
                            rs.getString("gender"));
                    // No avatar_data set
                    users.add(user);
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") ||
                    e.getMessage().toLowerCase().contains("timeout") ||
                    e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi tìm kiếm người dùng", e);
        }
        return users;
    }

    public boolean isUserOnline(int userId) throws DatabaseException, DatabaseConnectionException {
        String sql = "SELECT status FROM users WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && "online".equals(rs.getString("status"));
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") ||
                    e.getMessage().toLowerCase().contains("timeout") ||
                    e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi kiểm tra trạng thái người dùng", e);
        }
    }

    public List<User> getUsersByIds(List<Integer> ids) throws DatabaseException, DatabaseConnectionException {
        List<User> users = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return users;
        }

        StringBuilder sql = new StringBuilder(
                "SELECT id, username, full_name, email, phone, avatar_url, bio, birthdate, gender, status FROM users WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(")");

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < ids.size(); i++) {
                stmt.setInt(i + 1, ids.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    User user = new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            rs.getString("avatar_url"),
                            rs.getString("bio"),
                            (rs.getDate("birthdate") != null) ? rs.getDate("birthdate").toLocalDate() : null,
                            rs.getString("status"),
                            rs.getString("gender"));
                    // No avatar_data set
                    users.add(user);
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") ||
                    e.getMessage().toLowerCase().contains("timeout") ||
                    e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi lấy thông tin danh sách người dùng", e);
        }
        return users;
    }

    public byte[] getAvatarData(int userId) throws DatabaseException, DatabaseConnectionException {
        String sql = "SELECT avatar_data FROM users WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("avatar_data");
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") ||
                    e.getMessage().toLowerCase().contains("timeout") ||
                    e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi tải avatar", e);
        }
        return null;
    }

    /**
     * Đếm tổng số user đã đăng ký trong hệ thống
     */
    public int getTotalUserCount() throws DatabaseException, DatabaseConnectionException {
        String sql = "SELECT COUNT(*) as total FROM users";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("total");
            }
            return 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") ||
                    e.getMessage().toLowerCase().contains("timeout") ||
                    e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi đếm số lượng user", e);
        }
    }
}
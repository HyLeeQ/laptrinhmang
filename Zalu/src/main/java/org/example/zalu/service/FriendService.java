package org.example.zalu.service;

import org.example.zalu.dao.FriendDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.exception.database.DatabaseConnectionException;
import org.example.zalu.exception.database.DatabaseException;
import org.example.zalu.exception.auth.UserNotFoundException;
import org.example.zalu.model.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class FriendService {

    // Không cần lấy Connection thủ công nữa
    private final FriendDAO friendDAO;
    private final UserDAO userDAO;

    public FriendService() {
        // Giờ DAO tự lấy HikariCP pool từ DBConnection → luôn luôn sẵn sàng
        this.friendDAO = new FriendDAO();
        this.userDAO = new UserDAO();
        System.out.println("FriendService khởi tạo thành công bằng HikariCP pool!");
    }

    public List<User> getFriendsWithDetails(int userId) throws SQLException, DatabaseException, DatabaseConnectionException {
        List<Integer> ids = friendDAO.getFriendsByUserId(userId);
        List<User> friends = new ArrayList<>();
        for (int id : ids) {
            try {
                User u = userDAO.getUserById(id);
                if (u != null) {
                    friends.add(u);
                }
            } catch (UserNotFoundException e) {
                // User might have been deleted, skip silently
                System.out.println("Friend with ID " + id + " not found, skipping...");
            }
        }
        return friends;
    }

    // Các method khác bạn có thể thêm sau (gửi lời mời, chấp nhận, từ chối, pending requests...)
    public List<User> getPendingRequestsWithDetails(int userId) throws SQLException, DatabaseException, DatabaseConnectionException {
        return friendDAO.getPendingRequestsWithUserInfo(userId, userDAO);
    }

    public List<User> getOutgoingRequestsWithDetails(int userId) throws SQLException, DatabaseException, DatabaseConnectionException {
        return friendDAO.getOutgoingRequestsWithUserInfo(userId, userDAO);
    }

    public boolean sendFriendRequest(int userId, int friendId) throws SQLException {
        return friendDAO.sendFriendRequest(userId, friendId);
    }

    public boolean acceptFriendRequest(int userId, int friendId) throws SQLException {
        return friendDAO.acceptFriendRequest(userId, friendId);
    }

    public boolean rejectFriendRequest(int userId, int friendId) throws SQLException {
        return friendDAO.rejectFriendRequest(userId, friendId);
    }

    // Luôn trả về true vì DAO giờ được tạo ngay lập tức và không bao giờ null
    public boolean isReady() {
        return true;
    }
}
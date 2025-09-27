package org.example.zalu.controller;

import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.User;

public class Main {
    public static void main(String[] args) {
        UserDAO userDAO = new UserDAO();

        // Test đăng ký
        User newUser = new User(0, "huy", "123456", "huy@example.com");
        if (userDAO.register(newUser)) {
            System.out.println("Đăng ký thành công!");
        } else {
            System.out.println("Đăng ký thất bại!");
        }

        // Test đăng nhập
        User loginUser = userDAO.login("huy", "123456");
        if (loginUser != null) {
            System.out.println("Đăng nhập thành công! Xin chào " + loginUser.getUsername());
        } else {
            System.out.println("Sai tài khoản hoặc mật khẩu!");
        }
    }
}

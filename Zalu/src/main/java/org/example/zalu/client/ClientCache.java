package org.example.zalu.client;

import org.example.zalu.model.Message;
import org.example.zalu.model.User;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lớp quản lý bộ nhớ đệm tại Client để tăng tốc độ phản hồi UI
 */
public class ClientCache {
    private static ClientCache instance;

    private static final Logger logger = LoggerFactory.getLogger(ClientCache.class);
    private static final String CACHE_FILE = "client_cache.dat";

    // Cache thông tin User: userId -> User object
    private Map<Integer, User> userCache = new ConcurrentHashMap<>();

    // Cache tin nhắn: chatId (Id bạn bè hoặc -Id nhóm) -> List tin nhắn
    private Map<Integer, List<Message>> messageCache = new ConcurrentHashMap<>();

    // Cache Avatar: userId -> byte[]
    private final Map<Integer, byte[]> avatarCache = new ConcurrentHashMap<>();

    private ClientCache() {
    }

    public static synchronized ClientCache getInstance() {
        if (instance == null) {
            instance = new ClientCache();
            instance.loadCache();
        }
        return instance;
    }

    public synchronized void saveCache() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CACHE_FILE))) {
            oos.writeObject(new ArrayList<>(userCache.values()));
            oos.writeObject(new HashMap<>(messageCache));
            logger.info("Client cache saved to disk.");
        } catch (IOException e) {
            logger.error("Failed to save client cache: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void loadCache() {
        File file = new File(CACHE_FILE);
        if (!file.exists())
            return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            List<User> users = (List<User>) ois.readObject();
            for (User u : users)
                userCache.put(u.getId(), u);

            Map<Integer, List<Message>> messages = (Map<Integer, List<Message>>) ois.readObject();
            messageCache.putAll(messages);

            logger.info("Client cache loaded from disk: {} users, {} conversations", userCache.size(),
                    messageCache.size());
        } catch (Exception e) {
            logger.error("Failed to load client cache: {}", e.getMessage());
            // If load fails, we can just start with empty cache
        }
    }

    public void cacheUser(User user) {
        if (user != null) {
            userCache.put(user.getId(), user);
        }
    }

    public User getUser(int userId) {
        return userCache.get(userId);
    }

    public void cacheMessages(int chatId, List<Message> messages) {
        messageCache.put(chatId, new ArrayList<>(messages));
        saveCache();
    }

    public void addMessage(int chatId, Message message) {
        messageCache.computeIfAbsent(chatId, k -> Collections.synchronizedList(new ArrayList<>())).add(message);
        saveCache();
    }

    public List<Message> getMessages(int chatId) {
        return messageCache.getOrDefault(chatId, new ArrayList<>());
    }

    public void cacheAvatar(int userId, byte[] data) {
        if (data != null) {
            avatarCache.put(userId, data);
        }
    }

    public byte[] getAvatar(int userId) {
        return avatarCache.get(userId);
    }

    public void clearAvatarCache(int userId) {
        avatarCache.remove(userId);
    }

    public void clear() {
        userCache.clear();
        messageCache.clear();
        avatarCache.clear();
    }
}

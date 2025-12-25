package org.example.zalu.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.ListView;
import org.example.zalu.client.ChatClient;
import org.example.zalu.client.ChatEventManager;
import org.example.zalu.controller.chat.MessageListController;
import org.example.zalu.model.ChatItem;
import org.example.zalu.model.GroupInfo;
import org.example.zalu.model.User;
import org.example.zalu.service.MessageUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatListManager {
    private static final Logger logger = LoggerFactory.getLogger(ChatListManager.class);

    private final MainController mainController;
    private final ListView<ChatItem> chatList;
    private final MessageUpdateService messageUpdateService;

    // Data State
    private List<Integer> pendingFriendIds = new ArrayList<>();
    private List<GroupInfo> pendingGroups = new ArrayList<>();
    private Map<Integer, User> loadedUsers = new HashMap<>();
    private Map<Integer, Boolean> onlineFriends = new HashMap<>();
    private Map<Integer, Integer> unreadCounts = new HashMap<>();

    private boolean hasReceivedFriends = false;
    private boolean isRefreshing = false;

    public ChatListManager(MainController mainController,
            ListView<ChatItem> chatList,
            MessageUpdateService messageUpdateService) {
        this.mainController = mainController;
        this.chatList = chatList;
        this.messageUpdateService = messageUpdateService;
    }

    public void registerCallbacks() {
        ChatEventManager.getInstance().registerGroupsCallback(this::onGroupsUpdated);
        ChatEventManager.getInstance().registerOnlineUsersCallback(this::onOnlineUsersReceived);
        ChatEventManager.getInstance().registerFriendsCallback(this::onFriendsUpdated);
        ChatEventManager.getInstance().registerFriendsListFullCallback(this::onFriendsListFullReceived);
    }

    public void refreshFriendList(int currentUserId) {
        logger.info("Refreshing friend list for userId: {}", currentUserId);
        if (currentUserId <= 0) {
            logger.warn("Skipping refresh: Invalid userId");
            return;
        }
        // Sử dụng phiên bản đầy đủ để lấy toàn bộ User object
        ChatClient.sendRequest("GET_FRIENDS_LIST_FULL|" + currentUserId);
        ChatClient.sendRequest("GET_GROUPS|" + currentUserId);
    }

    private void onFriendsUpdated(List<Integer> friendIds) {
        // Vẫn giữ lại để tương tác nhưng ưu tiên FULL list
        if (friendIds != null && !friendIds.isEmpty() && !hasReceivedFriends) {
            ChatClient.sendRequest("GET_FRIENDS_LIST_FULL|" + mainController.getCurrentUserId());
        }
    }

    private void onFriendsListFullReceived(List<User> friends) {
        Platform.runLater(() -> {
            hasReceivedFriends = true;
            pendingFriendIds.clear();
            loadedUsers.clear();

            if (friends != null) {
                logger.info("Received full friend list: {} friends", friends.size());
                for (User user : friends) {
                    pendingFriendIds.add(user.getId());
                    loadedUsers.put(user.getId(), user);

                    // Pre-fetch avatar if not in cache
                    if (org.example.zalu.client.ClientCache.getInstance().getAvatar(user.getId()) == null) {
                        ChatClient.sendRequest("GET_USER_AVATAR|" + user.getId());
                    }
                }
            }

            buildChatItemsFromLoadedData();

            if (chatList.getItems().isEmpty()) {
                chatList.getSelectionModel().clearSelection();
                mainController.showWelcomeInMessageArea();
            }
        });
    }

    private void onGroupsUpdated(List<GroupInfo> groups) {
        Platform.runLater(() -> {
            pendingGroups = groups != null ? new ArrayList<>(groups) : new ArrayList<>();
            logger.info("Received groups update: {} groups", pendingGroups.size());
            buildChatItemsFromLoadedData();
        });
    }

    private void onOnlineUsersReceived(List<Integer> onlineUserIds) {
        Platform.runLater(() -> {
            if (onlineUserIds != null) {
                logger.info("Received online users list: {} users", onlineUserIds.size());
                for (int friendId : onlineUserIds) {
                    onlineFriends.put(friendId, true);
                }
                chatList.refresh();
                mainController.updateChatHeaderStatus();
            }
        });
    }

    private void buildChatItemsFromLoadedData() {
        List<ChatItem> chatItems = new ArrayList<>();

        // Add friends
        for (int friendId : pendingFriendIds) {
            User friend = loadedUsers.get(friendId);
            if (friend != null) {
                chatItems.add(new ChatItem(friend));
                if (!onlineFriends.containsKey(friendId)) {
                    onlineFriends.put(friendId, false);
                }
                unreadCounts.putIfAbsent(friendId, 0);
            }
        }

        // Add groups
        if (pendingGroups != null) {
            for (GroupInfo group : pendingGroups) {
                chatItems.add(new ChatItem(group));
                unreadCounts.putIfAbsent(-group.getId(), 0);
            }
        }

        int currentUserId = mainController.getCurrentUserId();

        // Update messages and sort
        if (messageUpdateService != null && currentUserId > 0) {
            messageUpdateService.updateLastMessages(chatItems, currentUserId);
            sortItemsByTime(chatItems);
            // Pre-fetch will be done at the end of this method
        }

        // Handle selection state
        ChatItem currentSelection = chatList.getSelectionModel().getSelectedItem();
        boolean wasRefreshing = isRefreshing;
        isRefreshing = true;

        try {
            chatList.setItems(FXCollections.observableArrayList(chatItems));

            if (currentSelection != null) {
                for (ChatItem item : chatItems) {
                    if (item.matches(currentSelection)) {
                        chatList.getSelectionModel().select(item);
                        break;
                    }
                }
            } else if (mainController.isWelcomeMode()) {
                chatList.getSelectionModel().clearSelection();
                MessageListController msgController = mainController.getMessageListController();
                if (msgController != null) {
                    String name = mainController.getWelcomeUsername();
                    String nameToShow = (name != null && !name.isBlank()) ? name : "bạn";
                    msgController.showWelcomeScreen(
                            "Chào mừng " + nameToShow + " đến với Zalu.\nChọn một người bạn để bắt đầu trò chuyện.");
                }
            }
        } finally {
            isRefreshing = wasRefreshing;
        }

        mainController.updateLastMessages();

        // PRE-FETCH: Tải trước tin nhắn cho top 5 cuộc trò chuyện gần nhất
        preFetchConversations(chatItems, 5);

        logger.info("Friend list refreshed with {} friends and {} groups",
                pendingFriendIds.size(), pendingGroups.size());
    }

    private void preFetchConversations(List<ChatItem> items, int limit) {
        if (items == null || items.isEmpty())
            return;

        int count = 0;
        int currentUserId = mainController.getCurrentUserId();

        for (ChatItem item : items) {
            if (count >= limit)
                break;

            // Chỉ pre-fetch nếu chưa có trong cache hoặc cache còn ít (tùy chọn)
            // Để đơn giản, cứ gửi request để update cache mới nhất
            if (item.isGroup()) {
                ChatClient.sendRequest("GET_GROUP_CONVERSATION|" + currentUserId + "|" + item.getGroup().getId());
            } else {
                ChatClient.sendRequest("GET_CONVERSATION|" + currentUserId + "|" + item.getUser().getId());
            }
            count++;
        }
        logger.info("Pre-fetched top {} conversations in background", count);
    }

    private void sortItemsByTime(List<ChatItem> items) {
        items.sort((a, b) -> {
            LocalDateTime timeA = messageUpdateService.getLastMessageTime(a.getId());
            LocalDateTime timeB = messageUpdateService.getLastMessageTime(b.getId());
            if (timeA == null)
                return 1;
            if (timeB == null)
                return -1;
            return timeB.compareTo(timeA);
        });
    }

    public void sortChatListByLastMessage(int currentFriendId, int currentGroupId) {
        if (chatList == null || messageUpdateService == null)
            return;

        Platform.runLater(() -> {
            var items = chatList.getItems();
            if (items == null || items.isEmpty())
                return;

            List<ChatItem> sortedItems = new ArrayList<>(items);
            sortItemsByTime(sortedItems);

            // Check if sort needed
            boolean needsSort = false;
            for (int i = 0; i < Math.min(items.size(), sortedItems.size()); i++) {
                if (items.get(i).getId() != sortedItems.get(i).getId()) {
                    needsSort = true;
                    break;
                }
            }

            if (!needsSort) {
                chatList.refresh();
                return;
            }

            boolean wasRefreshing = isRefreshing;
            isRefreshing = true;
            try {
                chatList.getSelectionModel().clearSelection();
                ChatItem selectedBefore = null;

                // Find selection to restore
                if (currentFriendId > 0 || currentGroupId > 0) {
                    for (ChatItem item : items) {
                        if ((!item.isGroup() && item.getUser().getId() == currentFriendId) ||
                                (item.isGroup() && item.getGroup().getId() == currentGroupId)) {
                            selectedBefore = item;
                            break;
                        }
                    }
                }

                chatList.setItems(FXCollections.observableArrayList(sortedItems));

                if (selectedBefore != null) {
                    for (int i = 0; i < sortedItems.size(); i++) {
                        ChatItem item = sortedItems.get(i);
                        if (item.matches(selectedBefore)) {
                            chatList.getSelectionModel().select(i);
                            break;
                        }
                    }
                }
            } finally {
                isRefreshing = wasRefreshing;
            }
            chatList.refresh();
        });
    }

    // Getters and Setters for shared state
    public Map<Integer, Boolean> getOnlineFriends() {
        return onlineFriends;
    }

    public Map<Integer, Integer> getUnreadCounts() {
        return unreadCounts;
    }

    public boolean isRefreshing() {
        return isRefreshing;
    }

    public void setRefreshing(boolean refreshing) {
        isRefreshing = refreshing;
    }

    public List<User> getCachedFriendsList() {
        return new ArrayList<>(loadedUsers.values());
    }

    public void clearData() {
        pendingFriendIds.clear();
        pendingGroups.clear();
        loadedUsers.clear();
        onlineFriends.clear();
        unreadCounts.clear();
        hasReceivedFriends = false;
        if (chatList != null) {
            chatList.getItems().clear();
        }
    }
}

# Callback Conflict Issues - Zalu Application

## Vấn đề chính
`ChatEventManager` chỉ lưu **1 callback duy nhất** cho mỗi event type. Khi nhiều controller đăng ký cùng callback, callback sau sẽ **GHI ĐÈ** callback trước, gây ra lỗi UI không cập nhật.

## Danh sách Callback Conflicts

### 1. ✅ `registerFriendsListFullCallback` - ĐÃ SỬA
**Conflict giữa:**
- `ChatListManager` (line 51) - Callback chính để load friend list
- `CreateGroupController` (line 105) - Callback tạm để load friends khi tạo nhóm

**Giải pháp đã áp dụng:**
- Sau khi đóng `CreateGroupDialog`, gọi `chatListManager.registerCallbacks()` để restore callback
- File: `MainController.java` line 312

---

### 2. ⚠️ `registerGetUserByIdCallback` - CẦN SỬA
**Conflict giữa:**
- `MainController` (line 935, 1002) - Load user profile
- `ProfileController` (line 79) - Load user info trong profile dialog
- `BioViewController` (line 61) - Load user info trong bio view

**Vấn đề:**
- Khi mở ProfileDialog hoặc BioView, callback của MainController bị ghi đè
- Sau khi đóng dialog, MainController không thể load user profile nữa

**Giải pháp đề xuất:**
1. **Ngắn hạn**: Re-register callback của MainController sau khi đóng dialog
2. **Dài hạn**: Refactor ChatEventManager để hỗ trợ nhiều callbacks

---

### 3. ⚠️ `registerErrorCallback` - CẦN KIỂM TRA
**Conflict giữa:**
- `MainController` (line 109) - Handle message sent response
- `RegisterController` (line 112) - Handle register response

**Vấn đề:**
- Khi ở màn hình Register, callback của MainController bị ghi đè
- Nhưng vì Register và Main không cùng lúc active nên ít gây vấn đề

**Giải pháp:** Không cần sửa (khác màn hình)

---

### 4. ⚠️ `registerBroadcastCallback` - CẦN KIỂM TRA
**Conflict giữa:**
- `MainController` (line 110) - Handle typing, online/offline, message events
- `AddFriendController` (line 286) - Handle friend request response

**Vấn đề:**
- Khi mở AddFriend tab, callback của MainController bị ghi đè
- Typing indicator, online status sẽ không hoạt động

**Giải pháp đề xuất:**
- Re-register MainController callback sau khi đóng FriendRequestView

---

### 5. ⚠️ `registerGetMessagesCallback` - CẦN KIỂM TRA
**Conflict:**
- `MainController` (line 412) - Load conversation history

**Vấn đề:** Chỉ có 1 nơi đăng ký, nhưng callback được đăng ký **MỖI KHI** click vào chat item
- Mỗi lần click = 1 callback mới
- Callback cũ bị ghi đè

**Giải pháp:** Đăng ký callback **1 LẦN** trong `initialize()`, không đăng ký lại mỗi lần click

---

## Giải pháp tổng quát

### Option 1: Multiple Callbacks (Recommended)
Sửa `ChatEventManager` để lưu **List<Callback>** thay vì **single Callback**:

```java
// Thay vì:
private Consumer<List<User>> friendsListFullCallback;

// Sử dụng:
private List<Consumer<List<User>>> friendsListFullCallbacks = new ArrayList<>();

public void registerFriendsListFullCallback(Consumer<List<User>> callback) {
    friendsListFullCallbacks.add(callback);
}

// Khi nhận event, gọi TẤT CẢ callbacks:
for (Consumer<List<User>> callback : friendsListFullCallbacks) {
    callback.accept(users);
}
```

### Option 2: Request ID
Thêm request ID vào mỗi request và callback:

```java
String requestId = UUID.randomUUID().toString();
ChatClient.sendRequest("GET_USER_BY_ID|" + userId + "|" + requestId);

ChatEventManager.getInstance().registerGetUserByIdCallback(requestId, users -> {
    // Handle response
});
```

### Option 3: Temporary Callbacks với Auto-cleanup
Callback tự động xóa sau khi được gọi 1 lần:

```java
public void registerOneTimeCallback(String type, Consumer callback) {
    // Wrap callback to auto-remove after execution
}
```

---

## Ưu tiên sửa

1. **HIGH**: `registerGetUserByIdCallback` - Ảnh hưởng Profile/Bio view
2. **MEDIUM**: `registerBroadcastCallback` - Ảnh hưởng typing indicator, online status
3. **MEDIUM**: `registerGetMessagesCallback` - Callback bị duplicate mỗi lần click
4. **LOW**: `registerErrorCallback` - Ít ảnh hưởng (khác màn hình)

---

## Files cần sửa

### Immediate fixes (re-register callbacks):
1. `MainController.java` - Re-register sau khi đóng ProfileDialog
2. `MainController.java` - Re-register sau khi đóng FriendRequestView
3. `MainController.java` - Move `registerGetMessagesCallback` ra khỏi `reloadChatForItem()`

### Long-term fix:
1. `ChatEventManager.java` - Refactor để hỗ trợ multiple callbacks

---

## Testing checklist

- [ ] Tạo nhóm → Danh sách bạn bè hiển thị
- [ ] Mở Profile → Thông tin hiển thị
- [ ] Đóng Profile → MainController vẫn hoạt động
- [ ] Mở BioView → Thông tin hiển thị
- [ ] Đóng BioView → MainController vẫn hoạt động
- [ ] Mở AddFriend → Typing indicator vẫn hoạt động
- [ ] Click nhiều chat items → Không bị duplicate callbacks

# 🏗️ ZALU - Kiến trúc & Thiết kế hệ thống

> Tài liệu chi tiết về kiến trúc, design patterns và quyết định thiết kế trong dự án Zalu.

---

## 📋 Mục lục

- [Tổng quan kiến trúc](#-tổng-quan-kiến-trúc)
- [Design Patterns](#-design-patterns)
- [Communication Protocol](#-communication-protocol)
- [Data Flow](#-data-flow)
- [Security](#-security)
- [Scalability](#-scalability)

---

## 🎯 Tổng quan kiến trúc

### **1. Layered Architecture (Kiến trúc phân lớp)**

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                    │
│  (JavaFX Controllers, FXML Views, CSS Styling)          │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                    Service Layer                         │
│  (Business Logic, Validation, Event Handling)           │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                    Data Access Layer                     │
│  (DAO Pattern, Database Operations)                     │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                    Database Layer                        │
│  (MySQL, Connection Pooling)                            │
└─────────────────────────────────────────────────────────┘
```

### **2. Client-Server Model**

**Server Architecture:**
```
ChatServer (Main)
    │
    ├─► ServerSocket (Port 12345)
    │       │
    │       └─► Accept Loop (Main Thread)
    │               │
    │               ├─► ClientHandler Thread 1
    │               ├─► ClientHandler Thread 2
    │               ├─► ClientHandler Thread 3
    │               └─► ClientHandler Thread N
    │
    ├─► ConcurrentHashMap<userId, OutputStream>
    │       └─► Thread-safe client registry
    │
    └─► DatabaseConnection Pool
            └─► HikariCP (Connection pooling)
```

**Client Architecture:**
```
ZaluApplication (JavaFX)
    │
    ├─► ChatClient (Singleton)
    │       │
    │       ├─► Socket Connection
    │       ├─► ObjectOutputStream (Send)
    │       └─► ObjectInputStream (Receive)
    │               │
    │               └─► Listener Thread
    │                       │
    │                       └─► ChatEventManager
    │                               │
    │                               └─► Callbacks
    │
    ├─► Controllers (UI Logic)
    │       ├─► MainController
    │       ├─► ChatController
    │       └─► MessageListController
    │
    └─► Services (Business Logic)
            ├─► MessageService
            ├─► AuthService
            └─► AvatarService
```

---

## 🎨 Design Patterns

### **1. Singleton Pattern**

**Mục đích:** Đảm bảo chỉ có 1 instance của ChatClient và ChatEventManager.

**Implementation:**
```java
public class ChatClient {
    private static ChatClient instance;
    private Socket socket;
    
    private ChatClient() {
        // Private constructor
    }
    
    public static ChatClient getInstance() {
        if (instance == null) {
            synchronized (ChatClient.class) {
                if (instance == null) {
                    instance = new ChatClient();
                }
            }
        }
        return instance;
    }
}
```

**Lợi ích:**
- ✅ Tránh multiple connections
- ✅ Global access point
- ✅ Thread-safe (double-checked locking)

---

### **2. Observer Pattern (Event-Driven)**

**Mục đích:** Tách biệt network layer và UI layer.

**Implementation:**
```java
public class ChatEventManager {
    private Consumer<Message> newMessageCallback;
    private Consumer<String> typingCallback;
    
    public void registerNewMessageCallback(Consumer<Message> callback) {
        this.newMessageCallback = callback;
    }
    
    public void processEvent(Object data) {
        if (data instanceof String) {
            String message = (String) data;
            if (message.startsWith("NEW_MESSAGE|")) {
                // Parse message
                Message msg = parseMessage(message);
                // Notify observers
                if (newMessageCallback != null) {
                    newMessageCallback.accept(msg);
                }
            }
        }
    }
}
```

**Lợi ích:**
- ✅ Loose coupling
- ✅ Easy to add new events
- ✅ Testable

---

### **3. Factory Pattern**

**Mục đích:** Tạo các loại message bubbles khác nhau.

**Implementation:**
```java
public class MessageBubbleFactory {
    public static VBox createTextBubble(String content, boolean isOwn) {
        // Create text message bubble
    }
    
    public static VBox createImageBubble(byte[] imageData, boolean isOwn) {
        // Create image message bubble
    }
    
    public static VBox createVoiceBubble(byte[] audioData, boolean isOwn) {
        // Create voice message bubble
    }
    
    public static VBox createFileBubble(String fileName, int fileSize, boolean isOwn) {
        // Create file message bubble
    }
}
```

**Lợi ích:**
- ✅ Centralized creation logic
- ✅ Easy to add new message types
- ✅ Consistent styling

---

### **4. DAO Pattern (Data Access Object)**

**Mục đích:** Tách biệt business logic và database operations.

**Implementation:**
```java
public class MessageDAO {
    public static Message saveMessage(int senderId, int receiverId, String content) {
        String sql = "INSERT INTO messages (sender_id, receiver_id, content) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);
            stmt.setString(3, content);
            stmt.executeUpdate();
            // Return saved message
        }
    }
    
    public static List<Message> getConversation(int userId, int friendId) {
        // Get messages between two users
    }
}
```

**Lợi ích:**
- ✅ Separation of concerns
- ✅ Easy to switch database
- ✅ Testable (mock DAO)

---

### **5. Builder Pattern**

**Mục đích:** Tạo complex objects (Message, User) một cách dễ đọc.

**Implementation:**
```java
public class Message {
    private int id;
    private int senderId;
    private int receiverId;
    private String content;
    private LocalDateTime timestamp;
    
    public static class Builder {
        private Message message = new Message();
        
        public Builder id(int id) {
            message.id = id;
            return this;
        }
        
        public Builder senderId(int senderId) {
            message.senderId = senderId;
            return this;
        }
        
        public Builder content(String content) {
            message.content = content;
            return this;
        }
        
        public Message build() {
            return message;
        }
    }
}

// Usage
Message msg = new Message.Builder()
    .id(1)
    .senderId(10)
    .receiverId(20)
    .content("Hello")
    .build();
```

---

### **6. Strategy Pattern**

**Mục đích:** Xử lý các loại file khác nhau (image, video, audio).

**Implementation:**
```java
public interface FileHandler {
    void handle(byte[] fileData, String fileName);
}

public class ImageFileHandler implements FileHandler {
    @Override
    public void handle(byte[] fileData, String fileName) {
        // Display image in chat
        messageListController.addImageMessage(fileData, fileName);
    }
}

public class VideoFileHandler implements FileHandler {
    @Override
    public void handle(byte[] fileData, String fileName) {
        // Show video player
        openVideoPlayer(fileData, fileName);
    }
}

public class FileHandlerFactory {
    public static FileHandler getHandler(String fileName) {
        if (ChatRenderer.isImageFile(fileName)) {
            return new ImageFileHandler();
        } else if (ChatRenderer.isVideoFile(fileName)) {
            return new VideoFileHandler();
        } else {
            return new GenericFileHandler();
        }
    }
}
```

---

## 📡 Communication Protocol

### **1. Message Format**

**Text Protocol:**
```
ACTION|PARAM1|PARAM2|PARAM3|...
```

**Examples:**
```
LOGIN|username|password
SEND_MESSAGE|senderId|receiverId|content
NEW_MESSAGE|messageId|senderId|receiverId|content|timestamp
TYPING|userId|friendId
```

### **2. Binary Protocol (Files)**

**Sequence:**
```
1. Send metadata (String)
   "SEND_FILE|senderId|receiverId|fileName|fileSize"

2. Send binary data (byte[])
   objectOutputStream.writeObject(fileData);

3. Server acknowledges
   "FILE_RECEIVED|messageId"
```

### **3. Request-Response Flow**

```
Client                          Server
  │                               │
  ├─── LOGIN|user|pass ──────────►│
  │                               ├─ Validate credentials
  │                               ├─ Create session
  │◄──── LOGIN_SUCCESS|userId ───┤
  │                               │
  ├─── SEND_MESSAGE|1|2|Hi ──────►│
  │                               ├─ Save to DB
  │                               ├─ Broadcast to receiver
  │◄──── MESSAGE_SENT|msgId ─────┤
  │                               │
  │◄──── NEW_MESSAGE|... ─────────┤ (from another user)
  │                               │
```

---

## 🔄 Data Flow

### **1. Send Message Flow**

```
User Input (TextField)
    │
    ▼
ChatController.sendMessage()
    │
    ▼
ChatClient.sendRequest("SEND_MESSAGE|...")
    │
    ▼
ObjectOutputStream.writeObject(request)
    │
    ▼
[Network - Socket]
    │
    ▼
Server: ClientHandler.handleRequest()
    │
    ▼
Server: MessageDAO.saveMessage()
    │
    ▼
Server: ChatServer.broadcastToUser(receiverId, "NEW_MESSAGE|...")
    │
    ▼
[Network - Socket]
    │
    ▼
Client: ChatEventManager.processEvent()
    │
    ▼
Client: newMessageCallback.accept(message)
    │
    ▼
MessageListController.addMessage()
    │
    ▼
Platform.runLater(() -> {
    VBox bubble = MessageBubbleFactory.createTextBubble(...);
    messageContainer.getChildren().add(bubble);
})
```

### **2. File Transfer Flow**

```
User selects file
    │
    ▼
ChatController.handleSendFile()
    │
    ▼
Files.readAllBytes(file.toPath())
    │
    ▼
Check file size (< 25MB)
    │
    ▼
if (isImage) {
    Show preview dialog
} else {
    Show file preview panel
}
    │
    ▼
User confirms send
    │
    ▼
ChatClient.sendRequest("SEND_FILE|...")
ChatClient.sendObject(fileData)
    │
    ▼
[Network - Socket]
    │
    ▼
Server: ClientHandler receives metadata
Server: ClientHandler receives binary data
    │
    ▼
Server: MessageDAO.saveFileMessage(fileData)
    │
    ▼
Server: Broadcast to receiver
    │
    ▼
[Network - Socket]
    │
    ▼
Client: ChatEventManager processes NEW_FILE
    │
    ▼
Client: Receives binary data
    │
    ▼
if (isImage) {
    messageListController.addImageMessage(fileData)
} else if (isVideo) {
    messageListController.addVideoMessage(fileData)
} else {
    messageListController.addFileMessage(fileName, fileSize)
}
```

---

## 🔒 Security

### **1. Password Security**

**Hashing Algorithm: SHA-256**
```java
public static String hashPassword(String password) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("SHA-256 not available", e);
    }
}
```

**Limitations:**
- ❌ No salt (vulnerable to rainbow tables)
- ❌ No pepper
- ❌ No key stretching (bcrypt/scrypt recommended)

**Recommended improvement:**
```java
// Use BCrypt instead
String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
boolean matches = BCrypt.checkpw(password, hashedPassword);
```

---

### **2. SQL Injection Prevention**

**Always use PreparedStatement:**
```java
// ❌ BAD - SQL Injection vulnerable
String sql = "SELECT * FROM users WHERE username = '" + username + "'";

// ✅ GOOD - Safe from SQL Injection
String sql = "SELECT * FROM users WHERE username = ?";
PreparedStatement stmt = conn.prepareStatement(sql);
stmt.setString(1, username);
```

---

### **3. Session Management**

**Current implementation:**
```java
public class LoginSession {
    private static User currentUser;
    
    public static void setCurrentUser(User user) {
        currentUser = user;
    }
    
    public static User getCurrentUser() {
        return currentUser;
    }
}
```

**Limitations:**
- ❌ No session timeout
- ❌ No session token
- ❌ No multi-device support

---

### **4. Data Validation**

**Input validation:**
```java
// Username validation
if (username == null || username.trim().isEmpty()) {
    throw new InvalidInputException("Username cannot be empty");
}
if (username.length() < 3 || username.length() > 50) {
    throw new InvalidInputException("Username must be 3-50 characters");
}
if (!username.matches("^[a-zA-Z0-9_]+$")) {
    throw new InvalidInputException("Username can only contain letters, numbers, and underscore");
}

// Email validation
if (email != null && !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
    throw new InvalidInputException("Invalid email format");
}
```

---

## 📈 Scalability

### **1. Current Limitations**

| Aspect | Current | Bottleneck |
|--------|---------|------------|
| **Concurrent users** | ~1000 | Thread limit |
| **File storage** | Database (BLOB) | Database size |
| **Message throughput** | ~100 msg/s | Single server |
| **Database connections** | ~20 | Connection pool |

---

### **2. Scaling Strategies**

#### **A. Horizontal Scaling (Multiple Servers)**

```
                    Load Balancer
                         │
        ┌────────────────┼────────────────┐
        │                │                │
   Server 1          Server 2        Server 3
        │                │                │
        └────────────────┼────────────────┘
                         │
                  Shared Database
                         │
                  Redis (Session Store)
```

**Challenges:**
- Session synchronization
- Message routing between servers
- Database replication

---

#### **B. Vertical Scaling (Better Hardware)**

**Current:**
- CPU: 4 cores
- RAM: 8GB
- Storage: HDD

**Scaled:**
- CPU: 16 cores
- RAM: 32GB
- Storage: SSD/NVMe

**Expected improvement:**
- 4x concurrent users
- 10x database performance
- 5x file I/O speed

---

#### **C. Database Optimization**

**1. Indexing:**
```sql
-- Message queries
CREATE INDEX idx_conversation ON messages(sender_id, receiver_id, created_at);
CREATE INDEX idx_group_messages ON messages(group_id, created_at);

-- Friend queries
CREATE INDEX idx_friends ON friends(user_id, status);
```

**2. Partitioning:**
```sql
-- Partition messages by month
ALTER TABLE messages
PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202401 VALUES LESS THAN (202402),
    PARTITION p202402 VALUES LESS THAN (202403),
    ...
);
```

**3. Caching:**
```java
// Redis cache for frequently accessed data
public class MessageCache {
    private static RedisClient redis = new RedisClient();
    
    public static List<Message> getConversation(int userId, int friendId) {
        String cacheKey = "conv:" + userId + ":" + friendId;
        
        // Try cache first
        List<Message> cached = redis.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Cache miss - query database
        List<Message> messages = MessageDAO.getConversation(userId, friendId);
        
        // Store in cache (TTL: 5 minutes)
        redis.set(cacheKey, messages, 300);
        
        return messages;
    }
}
```

---

#### **D. File Storage Optimization**

**Current: Database BLOB**
```java
// ❌ Lưu file trong database
MessageDAO.saveFileMessage(senderId, receiverId, fileName, fileData);
```

**Recommended: Cloud Storage**
```java
// ✅ Upload lên S3/Firebase
String fileUrl = CloudStorage.upload(fileData, fileName);
MessageDAO.saveFileMessage(senderId, receiverId, fileName, fileUrl);
```

**Benefits:**
- ✅ Giảm 90% database size
- ✅ Faster file access (CDN)
- ✅ Unlimited storage
- ✅ Better scalability

---

### **3. Performance Optimization**

#### **A. Connection Pooling**

**HikariCP configuration:**
```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:mysql://localhost:3306/laptrinhmang_db");
config.setUsername("root");
config.setPassword("");
config.setMaximumPoolSize(20);        // Max connections
config.setMinimumIdle(5);             // Min idle connections
config.setConnectionTimeout(30000);   // 30 seconds
config.setIdleTimeout(600000);        // 10 minutes
config.setMaxLifetime(1800000);       // 30 minutes

HikariDataSource dataSource = new HikariDataSource(config);
```

---

#### **B. Lazy Loading**

```java
// ❌ Load all messages at once
List<Message> allMessages = MessageDAO.getAllMessages(userId, friendId);

// ✅ Load in batches (pagination)
List<Message> recentMessages = MessageDAO.getMessages(userId, friendId, limit=50, offset=0);
```

---

#### **C. Asynchronous Processing**

```java
// ❌ Synchronous file upload (blocks UI)
byte[] fileData = Files.readAllBytes(file.toPath());
ChatClient.sendFile(fileData);

// ✅ Asynchronous file upload
CompletableFuture.runAsync(() -> {
    byte[] fileData = Files.readAllBytes(file.toPath());
    ChatClient.sendFile(fileData);
}).thenRun(() -> {
    Platform.runLater(() -> {
        showAlert("File uploaded successfully!");
    });
});
```

---

## 🧪 Testing Strategy

### **1. Unit Tests**

```java
@Test
public void testHashPassword() {
    String password = "password123";
    String hashed = AuthService.hashPassword(password);
    
    assertNotNull(hashed);
    assertNotEquals(password, hashed);
    assertEquals(hashed, AuthService.hashPassword(password)); // Consistent
}

@Test
public void testSaveMessage() {
    Message msg = MessageDAO.saveMessage(1, 2, "Test message");
    
    assertNotNull(msg);
    assertNotNull(msg.getId());
    assertEquals("Test message", msg.getContent());
}
```

---

### **2. Integration Tests**

```java
@Test
public void testSendAndReceiveMessage() {
    // Setup
    ChatClient client1 = new ChatClient();
    ChatClient client2 = new ChatClient();
    
    client1.connect("localhost", 12345);
    client2.connect("localhost", 12345);
    
    // Send message
    client1.sendMessage(1, 2, "Hello");
    
    // Wait for message
    Thread.sleep(100);
    
    // Verify
    List<Message> messages = client2.getMessages();
    assertEquals(1, messages.size());
    assertEquals("Hello", messages.get(0).getContent());
}
```

---

### **3. Load Testing**

```java
@Test
public void testConcurrentUsers() {
    int numClients = 1000;
    CountDownLatch latch = new CountDownLatch(numClients);
    
    for (int i = 0; i < numClients; i++) {
        new Thread(() -> {
            ChatClient client = new ChatClient();
            client.connect("localhost", 12345);
            client.sendMessage(1, 2, "Load test");
            latch.countDown();
        }).start();
    }
    
    latch.await(30, TimeUnit.SECONDS);
    // Verify server handled all connections
}
```

---

## 📊 Monitoring & Logging

### **1. Logging Strategy**

**Logback configuration:**
```xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>logs/zalu.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

**Usage:**
```java
private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);

logger.info("Server started on port {}", PORT);
logger.warn("Client {} disconnected unexpectedly", userId);
logger.error("Failed to save message", exception);
```

---

### **2. Metrics Collection**

```java
public class ServerMetrics {
    private static AtomicInteger activeConnections = new AtomicInteger(0);
    private static AtomicLong totalMessages = new AtomicLong(0);
    
    public static void incrementConnections() {
        activeConnections.incrementAndGet();
    }
    
    public static void incrementMessages() {
        totalMessages.incrementAndGet();
    }
    
    public static Map<String, Object> getMetrics() {
        return Map.of(
            "activeConnections", activeConnections.get(),
            "totalMessages", totalMessages.get(),
            "uptime", getUptime()
        );
    }
}
```

---

## 🎓 Lessons Learned

### **1. What Went Well**
- ✅ Clean separation of concerns (MVC pattern)
- ✅ Reusable components (MessageBubbleFactory)
- ✅ Event-driven architecture (easy to extend)
- ✅ Good UI/UX (modern, responsive)

### **2. What Could Be Improved**
- ❌ No encryption (security risk)
- ❌ Files in database (scalability issue)
- ❌ Single server (no redundancy)
- ❌ Limited error handling

### **3. Key Takeaways**
- 💡 Socket programming requires careful thread management
- 💡 UI responsiveness is critical (use Platform.runLater)
- 💡 Database design impacts performance significantly
- 💡 Testing early saves debugging time later

---

**📚 Tài liệu này sẽ được cập nhật liên tục khi hệ thống phát triển.**

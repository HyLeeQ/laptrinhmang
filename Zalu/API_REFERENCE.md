# 📡 ZALU - API Reference & Protocol Documentation

> Chi tiết về các message protocols, API endpoints và data formats trong Zalu.

---

## 📋 Mục lục

- [Protocol Overview](#-protocol-overview)
- [Authentication APIs](#-authentication-apis)
- [Messaging APIs](#-messaging-apis)
- [Friend Management APIs](#-friend-management-apis)
- [Group Management APIs](#-group-management-apis)
- [File Transfer APIs](#-file-transfer-apis)
- [Real-time Events](#-real-time-events)
- [Error Codes](#-error-codes)

---

## 🌐 Protocol Overview

### **Transport Layer**
- **Protocol:** TCP/IP
- **Port:** 12345 (default)
- **Serialization:** Java ObjectOutputStream/ObjectInputStream
- **Encoding:** UTF-8

### **Message Format**
```
ACTION|PARAM1|PARAM2|PARAM3|...
```

### **Data Types**
- **String:** Text data (UTF-8)
- **Integer:** Numeric IDs
- **Byte[]:** Binary data (files, images, audio)
- **Timestamp:** ISO 8601 format

---

## 🔐 Authentication APIs

### **1. LOGIN**

**Request:**
```
LOGIN|username|password
```

**Parameters:**
- `username` (String): User's username
- `password` (String): SHA-256 hashed password

**Response (Success):**
```
LOGIN_RESPONSE|SUCCESS|userId|username|fullName|avatarUrl
```

**Response (Failure):**
```
LOGIN_RESPONSE|FAIL|errorMessage
```

**Example:**
```java
// Client
ChatClient.sendRequest("LOGIN|john_doe|5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8");

// Server response
"LOGIN_RESPONSE|SUCCESS|123|john_doe|John Doe|null"
```

---

### **2. REGISTER**

**Request:**
```
REGISTER|username|password|fullName|email
```

**Parameters:**
- `username` (String): Desired username (3-50 chars)
- `password` (String): SHA-256 hashed password
- `fullName` (String): User's full name
- `email` (String): Email address

**Response (Success):**
```
REGISTER_RESPONSE|SUCCESS|userId
```

**Response (Failure):**
```
REGISTER_RESPONSE|FAIL|errorMessage
```

**Error Messages:**
- `Username already exists`
- `Email already exists`
- `Invalid username format`
- `Password too weak`

---

### **3. LOGOUT**

**Request:**
```
LOGOUT|userId
```

**Response:**
```
LOGOUT_SUCCESS
```

---

## 💬 Messaging APIs

### **1. SEND_MESSAGE (1-1 Chat)**

**Request:**
```
SEND_MESSAGE|senderId|receiverId|content
```

**Parameters:**
- `senderId` (Integer): Sender's user ID
- `receiverId` (Integer): Receiver's user ID
- `content` (String): Message content (max 10,000 chars)

**Response:**
```
MESSAGE_SENT|messageId|timestamp
```

**Broadcast to Receiver:**
```
NEW_MESSAGE|messageId|senderId|receiverId|content|timestamp|senderName|isRead
```

**Example:**
```java
// Client sends
ChatClient.sendRequest("SEND_MESSAGE|123|456|Hello, how are you?");

// Server response to sender
"MESSAGE_SENT|7890|2025-12-25T09:30:00"

// Server broadcasts to receiver (userId=456)
"NEW_MESSAGE|7890|123|456|Hello, how are you?|2025-12-25T09:30:00|John Doe|0"
```

---

### **2. SEND_GROUP_MESSAGE**

**Request:**
```
SEND_GROUP_MESSAGE|groupId|senderId|content
```

**Parameters:**
- `groupId` (Integer): Group ID
- `senderId` (Integer): Sender's user ID
- `content` (String): Message content

**Response:**
```
GROUP_MESSAGE_SENT|messageId|timestamp
```

**Broadcast to All Members:**
```
NEW_GROUP_MESSAGE|messageId|groupId|senderId|content|timestamp|senderName
```

---

### **3. GET_CONVERSATION**

**Request:**
```
GET_CONVERSATION|userId|friendId|limit|offset
```

**Parameters:**
- `userId` (Integer): Current user ID
- `friendId` (Integer): Friend's user ID
- `limit` (Integer): Number of messages to fetch (default: 50)
- `offset` (Integer): Offset for pagination (default: 0)

**Response:**
```
CONVERSATION_DATA|count
```

**Followed by:**
```
MESSAGE|messageId|senderId|receiverId|content|timestamp|isRead|fileName|fileSize
```
(Repeated for each message)

**Example:**
```java
// Request last 50 messages
ChatClient.sendRequest("GET_CONVERSATION|123|456|50|0");

// Server response
"CONVERSATION_DATA|3"
"MESSAGE|1|123|456|Hi|2025-12-25T09:00:00|1|null|0"
"MESSAGE|2|456|123|Hello|2025-12-25T09:01:00|1|null|0"
"MESSAGE|3|123|456|How are you?|2025-12-25T09:02:00|0|null|0"
```

---

### **4. MARK_AS_READ**

**Request:**
```
MARK_AS_READ|userId|friendId
```

**Parameters:**
- `userId` (Integer): Current user ID
- `friendId` (Integer): Friend's user ID

**Response:**
```
MARKED_AS_READ|count
```

**Broadcast to Sender:**
```
MESSAGES_READ|readerId|friendId
```

---

### **5. DELETE_MESSAGE**

**Request:**
```
DELETE_MESSAGE|messageId|userId
```

**Response:**
```
MESSAGE_DELETED|messageId
```

**Broadcast:**
```
MESSAGE_DELETED|messageId
```

---

### **6. RECALL_MESSAGE**

**Request:**
```
RECALL_MESSAGE|messageId|userId
```

**Response:**
```
MESSAGE_RECALLED|messageId
```

**Broadcast:**
```
MESSAGE_RECALLED|messageId
```

---

### **7. EDIT_MESSAGE**

**Request:**
```
EDIT_MESSAGE|messageId|userId|newContent
```

**Response:**
```
MESSAGE_EDITED|messageId|newContent
```

**Broadcast:**
```
MESSAGE_EDITED|messageId|newContent
```

---

## 👥 Friend Management APIs

### **1. SEND_FRIEND_REQUEST**

**Request:**
```
SEND_FRIEND_REQUEST|senderId|receiverId
```

**Response (Success):**
```
FRIEND_REQUEST_SENT|OK
```

**Response (Failure):**
```
FRIEND_REQUEST_SENT|FAIL|errorMessage
```

**Broadcast to Receiver:**
```
NEW_FRIEND_REQUEST|senderId|senderName|senderAvatar
```

---

### **2. ACCEPT_FRIEND_REQUEST**

**Request:**
```
ACCEPT_FRIEND_REQUEST|userId|friendId
```

**Response:**
```
FRIEND_REQUEST_ACCEPTED|friendId
```

**Broadcast to Both Users:**
```
FRIEND_ADDED|userId|userName|userAvatar
```

---

### **3. REJECT_FRIEND_REQUEST**

**Request:**
```
REJECT_FRIEND_REQUEST|userId|friendId
```

**Response:**
```
FRIEND_REQUEST_REJECTED|friendId
```

---

### **4. REMOVE_FRIEND**

**Request:**
```
REMOVE_FRIEND|userId|friendId
```

**Response:**
```
FRIEND_REMOVED|friendId
```

**Broadcast:**
```
FRIEND_REMOVED|userId
```

---

### **5. GET_FRIENDS**

**Request:**
```
GET_FRIENDS|userId
```

**Response:**
```
FRIENDS_LIST|count
```

**Followed by:**
```
FRIEND|userId|username|fullName|status|avatarUrl
```
(Repeated for each friend)

---

### **6. GET_FRIEND_REQUESTS**

**Request:**
```
GET_FRIEND_REQUESTS|userId
```

**Response:**
```
FRIEND_REQUESTS|incomingCount|outgoingCount
```

**Followed by:**
```
INCOMING|userId|username|fullName|avatarUrl|timestamp
OUTGOING|userId|username|fullName|avatarUrl|timestamp
```

---

### **7. SEARCH_USERS**

**Request:**
```
SEARCH_USERS|query|currentUserId
```

**Parameters:**
- `query` (String): Search term (username, email, or phone)
- `currentUserId` (Integer): Current user ID (to exclude from results)

**Response:**
```
SEARCH_RESULTS|count
```

**Followed by:**
```
USER|userId|username|fullName|email|avatarUrl
```

---

## 👥 Group Management APIs

### **1. CREATE_GROUP**

**Request:**
```
CREATE_GROUP|creatorId|groupName|memberIds
```

**Parameters:**
- `creatorId` (Integer): Creator's user ID
- `groupName` (String): Group name
- `memberIds` (String): Comma-separated member IDs (e.g., "123,456,789")

**Response:**
```
GROUP_CREATED|groupId|groupName
```

**Broadcast to All Members:**
```
ADDED_TO_GROUP|groupId|groupName|creatorName
```

---

### **2. ADD_GROUP_MEMBER**

**Request:**
```
ADD_GROUP_MEMBER|groupId|userId|newMemberId
```

**Response:**
```
MEMBER_ADDED|groupId|newMemberId
```

**Broadcast:**
```
MEMBER_ADDED|groupId|newMemberId|newMemberName
```

---

### **3. REMOVE_GROUP_MEMBER**

**Request:**
```
REMOVE_GROUP_MEMBER|groupId|userId|memberId
```

**Response:**
```
MEMBER_REMOVED|groupId|memberId
```

**Broadcast:**
```
MEMBER_REMOVED|groupId|memberId
```

---

### **4. LEAVE_GROUP**

**Request:**
```
LEAVE_GROUP|groupId|userId
```

**Response:**
```
LEFT_GROUP|groupId
```

**Broadcast:**
```
MEMBER_LEFT|groupId|userId|userName
```

---

### **5. UPDATE_GROUP**

**Request:**
```
UPDATE_GROUP|groupId|userId|groupName|description
```

**Response:**
```
GROUP_UPDATED|groupId
```

**Broadcast:**
```
GROUP_UPDATED|groupId|groupName|description
```

---

### **6. GET_GROUP_MEMBERS**

**Request:**
```
GET_GROUP_MEMBERS|groupId
```

**Response:**
```
GROUP_MEMBERS|count
```

**Followed by:**
```
MEMBER|userId|username|fullName|role|joinedAt
```

---

### **7. GET_USER_GROUPS**

**Request:**
```
GET_USER_GROUPS|userId
```

**Response:**
```
USER_GROUPS|count
```

**Followed by:**
```
GROUP|groupId|groupName|memberCount|lastMessage|lastMessageTime
```

---

## 📎 File Transfer APIs

### **1. SEND_FILE (1-1 Chat)**

**Request (Metadata):**
```
SEND_FILE|senderId|receiverId|fileName|fileSize
```

**Request (Binary Data):**
```java
objectOutputStream.writeObject(fileData); // byte[]
```

**Response:**
```
FILE_SENT|messageId
```

**Broadcast to Receiver:**
```
NEW_FILE|messageId|senderId|receiverId|fileName|fileSize|timestamp
```

**Followed by:**
```java
objectOutputStream.writeObject(fileData); // byte[]
```

---

### **2. SEND_GROUP_FILE**

**Request (Metadata):**
```
SEND_GROUP_FILE|groupId|senderId|fileName|fileSize
```

**Request (Binary Data):**
```java
objectOutputStream.writeObject(fileData); // byte[]
```

**Response:**
```
GROUP_FILE_SENT|messageId
```

**Broadcast:**
```
NEW_GROUP_FILE|messageId|groupId|senderId|fileName|fileSize|timestamp
```

**Followed by binary data**

---

### **3. GET_FILE**

**Request:**
```
GET_FILE|messageId
```

**Response:**
```
FILE_DATA|messageId|fileName|fileSize
```

**Followed by:**
```java
objectOutputStream.writeObject(fileData); // byte[]
```

---

### **4. UPDATE_AVATAR**

**Request (Metadata):**
```
UPDATE_AVATAR|userId|fileName|fileSize
```

**Request (Binary Data):**
```java
objectOutputStream.writeObject(avatarData); // byte[]
```

**Response:**
```
AVATAR_UPDATED|userId
```

---

### **5. UPDATE_GROUP_AVATAR**

**Request (Metadata):**
```
UPDATE_GROUP_AVATAR|groupId|userId|fileName|fileSize
```

**Request (Binary Data):**
```java
objectOutputStream.writeObject(avatarData); // byte[]
```

**Response:**
```
GROUP_AVATAR_UPDATED|groupId
```

**Broadcast:**
```
GROUP_AVATAR_UPDATED|groupId
```

---

## ⚡ Real-time Events

### **1. TYPING**

**Sent by Client:**
```
TYPING|userId|friendId
```

**Broadcast to Friend:**
```
TYPING|userId|userName
```

**Auto-stop after 3 seconds if no new TYPING signal**

---

### **2. TYPING_STOP**

**Sent by Client:**
```
TYPING_STOP|userId|friendId
```

**Broadcast:**
```
TYPING_STOP|userId
```

---

### **3. USER_STATUS_CHANGE**

**Broadcast:**
```
USER_STATUS|userId|status
```

**Status values:**
- `online`
- `offline`
- `away`
- `busy`

---

### **4. KICKED**

**Sent by Server:**
```
KICKED|reason
```

**Client action:**
- Disconnect immediately
- Show alert with reason
- Return to login screen

---

### **5. SYSTEM_ANNOUNCEMENT**

**Broadcast:**
```
SYSTEM_ANNOUNCEMENT|message
```

**Example:**
```
SYSTEM_ANNOUNCEMENT|Server will restart in 5 minutes for maintenance
```

---

## ❌ Error Codes

### **Authentication Errors**

| Code | Message | Description |
|------|---------|-------------|
| `AUTH_001` | Invalid credentials | Wrong username or password |
| `AUTH_002` | User not found | Username doesn't exist |
| `AUTH_003` | User already exists | Username/email taken |
| `AUTH_004` | Session expired | Need to re-login |
| `AUTH_005` | Account locked | Too many failed attempts |

---

### **Messaging Errors**

| Code | Message | Description |
|------|---------|-------------|
| `MSG_001` | Message too long | Content > 10,000 chars |
| `MSG_002` | Receiver not found | Invalid receiver ID |
| `MSG_003` | Not friends | Can't message non-friends |
| `MSG_004` | Message not found | Invalid message ID |
| `MSG_005` | Cannot edit | Message too old (>5 min) |
| `MSG_006` | Cannot recall | Message too old (>5 min) |

---

### **Friend Errors**

| Code | Message | Description |
|------|---------|-------------|
| `FRD_001` | Already friends | Users are already friends |
| `FRD_002` | Request pending | Friend request already sent |
| `FRD_003` | Cannot add self | Can't friend yourself |
| `FRD_004` | User not found | Invalid user ID |
| `FRD_005` | Request not found | Invalid request ID |

---

### **Group Errors**

| Code | Message | Description |
|------|---------|-------------|
| `GRP_001` | Group not found | Invalid group ID |
| `GRP_002` | Not a member | User not in group |
| `GRP_003` | Not admin | Only admins can do this |
| `GRP_004` | Cannot leave | Creator can't leave |
| `GRP_005` | Group name taken | Name already exists |

---

### **File Errors**

| Code | Message | Description |
|------|---------|-------------|
| `FILE_001` | File too large | Size > 25MB |
| `FILE_002` | Invalid file type | Unsupported format |
| `FILE_003` | Upload failed | Network/server error |
| `FILE_004` | File not found | Invalid file ID |
| `FILE_005` | Corrupted file | Data integrity check failed |

---

### **Server Errors**

| Code | Message | Description |
|------|---------|-------------|
| `SRV_001` | Internal error | Unexpected server error |
| `SRV_002` | Database error | DB connection failed |
| `SRV_003` | Service unavailable | Server overloaded |
| `SRV_004` | Maintenance mode | Server under maintenance |

---

## 📊 Response Time SLA

| API | Expected Response Time | Max Response Time |
|-----|------------------------|-------------------|
| LOGIN | < 100ms | 500ms |
| SEND_MESSAGE | < 50ms | 200ms |
| GET_CONVERSATION | < 200ms | 1000ms |
| SEND_FILE (1MB) | < 500ms | 2000ms |
| SEARCH_USERS | < 300ms | 1000ms |
| CREATE_GROUP | < 200ms | 800ms |

---

## 🔄 Versioning

**Current Version:** v1.0

**Version Header:**
```
VERSION|1.0
```

**Future versions will include:**
- v1.1: End-to-end encryption
- v1.2: Voice/Video calls
- v2.0: Cloud storage integration

---

## 📝 Example Client Implementation

```java
public class ZaluClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
        
        // Start listener thread
        new Thread(this::listen).start();
    }
    
    public void login(String username, String password) throws IOException {
        String hashedPassword = hashPassword(password);
        String request = "LOGIN|" + username + "|" + hashedPassword;
        out.writeObject(request);
        out.flush();
    }
    
    public void sendMessage(int senderId, int receiverId, String content) throws IOException {
        String request = "SEND_MESSAGE|" + senderId + "|" + receiverId + "|" + content;
        out.writeObject(request);
        out.flush();
    }
    
    public void sendFile(int senderId, int receiverId, String fileName, byte[] fileData) throws IOException {
        // Send metadata
        String request = "SEND_FILE|" + senderId + "|" + receiverId + "|" + fileName + "|" + fileData.length;
        out.writeObject(request);
        out.flush();
        
        // Send binary data
        out.writeObject(fileData);
        out.flush();
    }
    
    private void listen() {
        try {
            while (true) {
                Object data = in.readObject();
                handleResponse(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleResponse(Object data) {
        if (data instanceof String) {
            String message = (String) data;
            
            if (message.startsWith("LOGIN_RESPONSE|SUCCESS|")) {
                // Handle login success
            } else if (message.startsWith("NEW_MESSAGE|")) {
                // Handle new message
            } else if (message.startsWith("NEW_FILE|")) {
                // Handle new file (read binary data next)
                try {
                    byte[] fileData = (byte[]) in.readObject();
                    // Process file
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
```

---

**📚 API này sẽ được cập nhật khi có thêm tính năng mới.**

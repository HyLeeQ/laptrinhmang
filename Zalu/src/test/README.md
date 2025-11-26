# Unit Tests - Zalu Application

## Cấu trúc Test

```
src/test/java/org/example/zalu/
├── dao/
│   ├── UserDAOTest.java
│   ├── MessageDAOTest.java
│   └── FriendDAOTest.java
├── service/
│   ├── FriendServiceTest.java
│   └── MessageUpdateServiceTest.java
└── AllTests.java (Test Suite)
```

## Chạy Tests

### Chạy tất cả tests
```bash
mvn test
```

### Chạy một test class cụ thể
```bash
mvn test -Dtest=UserDAOTest
```

### Chạy một test method cụ thể
```bash
mvn test -Dtest=UserDAOTest#testRegisterUser_Success
```

## Lưu ý

### Database Requirements
- Các DAO tests yêu cầu database thật hoặc test database
- Đảm bảo database `laptrinhmang_db` đã được tạo
- Các test có thể skip nếu không có data phù hợp

### Test Strategy
- **Unit Tests**: Test các method riêng lẻ với mock hoặc test database
- **Integration Tests**: Test với database thật (cần setup data)

### Mocking
- Sử dụng Mockito để mock database connections (nếu cần)
- Hoặc sử dụng H2 in-memory database cho tests

## Cải thiện trong tương lai

1. **Mock Database**: Sử dụng H2 hoặc Mockito để không cần database thật
2. **Test Data Setup**: Tạo test data tự động trước khi chạy tests
3. **Test Coverage**: Tăng coverage cho các method quan trọng
4. **Integration Tests**: Thêm integration tests cho các flow phức tạp


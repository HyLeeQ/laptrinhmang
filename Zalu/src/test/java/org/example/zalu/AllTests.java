package org.example.zalu;

import org.example.zalu.dao.FriendDAOTest;
import org.example.zalu.dao.MessageDAOTest;
import org.example.zalu.dao.UserDAOTest;
import org.example.zalu.service.FriendServiceTest;
import org.example.zalu.service.MessageUpdateServiceTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Test suite để chạy tất cả các tests
 * Chạy: mvn test
 */
@Suite
@SelectClasses({
    UserDAOTest.class,
    MessageDAOTest.class,
    FriendDAOTest.class,
    FriendServiceTest.class,
    MessageUpdateServiceTest.class
})
public class AllTests {
    // Test suite class
}


package org.example.zalu.util.license;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Hệ thống bảo vệ license - Code chỉ hoạt động khi kết nối được với server license
 */
public class LicenseValidator {
    private static final Logger logger = LoggerFactory.getLogger(LicenseValidator.class);
    
    private static final String LICENSE_SERVER_URL_PROPERTY = "license.server.url";
    private static final String LICENSE_SERVER_PORT_PROPERTY = "license.server.port";
    private static final String LICENSE_KEY_PROPERTY = "license.key";
    
    private static final String DEFAULT_LICENSE_SERVER = "localhost";
    private static final int DEFAULT_LICENSE_PORT = 8888;
    private static final String DEFAULT_LICENSE_KEY = "ZALU-2024-VALID";
    
    private static boolean isValidated = false;
    private static String licenseServerUrl;
    private static int licenseServerPort;
    private static String licenseKey;
    
    static {
        loadLicenseConfig();
    }
    
    /**
     * Tải cấu hình license từ file properties
     */
    private static void loadLicenseConfig() {
        try {
            Properties props = new Properties();
            var inputStream = LicenseValidator.class.getClassLoader()
                    .getResourceAsStream("license.properties");
            
            if (inputStream != null) {
                props.load(inputStream);
                licenseServerUrl = props.getProperty(LICENSE_SERVER_URL_PROPERTY, DEFAULT_LICENSE_SERVER);
                licenseServerPort = Integer.parseInt(
                    props.getProperty(LICENSE_SERVER_PORT_PROPERTY, String.valueOf(DEFAULT_LICENSE_PORT))
                );
                licenseKey = props.getProperty(LICENSE_KEY_PROPERTY, DEFAULT_LICENSE_KEY);
                logger.info("Đã tải cấu hình license từ license.properties");
            } else {
                // Sử dụng giá trị mặc định nếu không có file
                licenseServerUrl = DEFAULT_LICENSE_SERVER;
                licenseServerPort = DEFAULT_LICENSE_PORT;
                licenseKey = DEFAULT_LICENSE_KEY;
                logger.warn("Không tìm thấy license.properties, sử dụng cấu hình mặc định");
            }
        } catch (Exception e) {
            logger.error("Lỗi khi tải cấu hình license: {}", e.getMessage());
            licenseServerUrl = DEFAULT_LICENSE_SERVER;
            licenseServerPort = DEFAULT_LICENSE_PORT;
            licenseKey = DEFAULT_LICENSE_KEY;
        }
    }
    
    /**
     * Xác thực license với server
     * @return true nếu license hợp lệ, false nếu không
     */
    public static boolean validateLicense() {
        if (isValidated) {
            return true;
        }
        
        logger.info("=== KIỂM TRA LICENSE ===");
        logger.info("Đang kết nối tới License Server: {}:{}", licenseServerUrl, licenseServerPort);
        
        try {
            String url = String.format("http://%s:%d/validate", licenseServerUrl, licenseServerPort);
            URL licenseUrl = URI.create(url).toURL();
            HttpURLConnection conn = (HttpURLConnection) licenseUrl.openConnection();
            
            // Cấu hình request
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000); // 5 giây timeout
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            
            // Gửi license key
            String jsonInput = String.format("{\"licenseKey\":\"%s\"}", licenseKey);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Đọc response
            int responseCode = conn.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    String responseStr = response.toString();
                    logger.info("Response từ License Server: {}", responseStr);
                    
                    // Kiểm tra response có chứa "valid":true
                    if (responseStr.contains("\"valid\":true") || responseStr.contains("valid\":true")) {
                        isValidated = true;
                        logger.info("✓ LICENSE HỢP LỆ - Ứng dụng được phép chạy");
                        logger.info("==========================================\n");
                        return true;
                    } else {
                        logger.error("✗ LICENSE KHÔNG HỢP LỆ");
                        logger.error("Response: {}", responseStr);
                        return false;
                    }
                }
            } else {
                logger.error("✗ Không thể kết nối tới License Server");
                logger.error("Response Code: {}", responseCode);
                logger.error("Server license không khả dụng hoặc không đúng cấu hình");
                return false;
            }
            
        } catch (java.net.ConnectException e) {
            logger.error("✗ KHÔNG THỂ KẾT NỐI TỚI LICENSE SERVER!");
            logger.error("   Server license tại {}:{} không khả dụng", licenseServerUrl, licenseServerPort);
            logger.error("   Code này chỉ hoạt động khi có License Server của bạn chạy");
            logger.error("   Vui lòng đảm bảo License Server đang chạy trên máy của bạn");
            return false;
        } catch (Exception e) {
            logger.error("✗ LỖI KHI XÁC THỰC LICENSE: {}", e.getMessage());
            logger.error("   Chi tiết: ", e);
            return false;
        }
    }
    
    /**
     * Kiểm tra lại license (dùng cho periodic check)
     */
    public static boolean revalidateLicense() {
        isValidated = false;
        return validateLicense();
    }
    
    /**
     * Lấy trạng thái validation hiện tại
     */
    public static boolean isValidated() {
        return isValidated;
    }
    
    /**
     * Reset trạng thái validation (dùng cho testing)
     */
    public static void resetValidation() {
        isValidated = false;
    }
}


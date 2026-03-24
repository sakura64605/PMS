package com.hongjie.pms.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
public class EmailUtils {

    // 更严谨的邮箱正则
    private static final String EMAIL_REGEX =
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +
                    "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

    // 备用简单版（您的版本）
    private static final String SIMPLE_EMAIL_REGEX =
            "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";

    /**
     * 验证邮箱格式
     */
    public static boolean isEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        email = email.trim();

        // 基本长度检查
        if (email.length() < 5 || email.length() > 50) {
            log.debug("邮箱长度不符合要求: {}", email);
            return false;
        }

        // 必须包含@符号
        if (!email.contains("@")) {
            return false;
        }

        // 正则验证
        boolean isValid = email.matches(SIMPLE_EMAIL_REGEX);
        if (!isValid) {
            log.debug("邮箱格式不正确: {}", email);
        }
        return isValid;
    }

    /**
     * 获取邮箱的域名
     */
    public static String getDomain(String email) {
        if (!isEmail(email)) {
            return null;
        }
        return email.substring(email.indexOf("@") + 1);
    }

    /**
     * 获取邮箱用户名部分
     */
    public static String getUsername(String email) {
        if (!isEmail(email)) {
            return null;
        }
        return email.substring(0, email.indexOf("@"));
    }

    /**
     * 脱敏邮箱（用于显示）
     * a***n@email.com
     */
    public static String maskEmail(String email) {
        if (!isEmail(email)) {
            return email;
        }
        String[] parts = email.split("@");
        String name = parts[0];
        String domain = parts[1];

        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + domain;
        }

        String maskedName = name.charAt(0) +
                "***" +
                name.charAt(name.length() - 1);
        return maskedName + "@" + domain;
    }
}

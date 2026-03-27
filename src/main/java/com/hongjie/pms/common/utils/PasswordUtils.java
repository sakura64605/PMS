package com.hongjie.pms.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PasswordUtils {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 加密密码
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    public static String encrypt(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return ENCODER.encode(rawPassword);
    }

    /**
     * 验证密码
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * 验证密码（带日志）
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @param username 用户名（用于日志）
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword, String username) {
        boolean matches = matches(rawPassword, encodedPassword);
        if (!matches) {
            log.warn("用户{}密码验证失败", username);
        }
        return matches;
    }

    public static void main(String[] args) {
        String password = "123456";
        String encodedPassword = PasswordUtils.encrypt(password);
        System.out.println(encodedPassword);
        System.out.println(PasswordUtils.matches(password, encodedPassword));
    }

}

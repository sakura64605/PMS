package com.hongjie.pms.common.utils;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public class AccountUtils {

    // 中国手机号正则（最常用）
    // 1开头的11位数字，第二位通常是3-9
    private static final String CHINA_PHONE_REGEX = "^1[3-9]\\d{9}$";
    private static final Pattern CHINA_PHONE_PATTERN = Pattern.compile(CHINA_PHONE_REGEX);

    // 宽松的手机号正则（只要是11位数字，1开头）
    private static final String LOOSE_PHONE_REGEX = "^1\\d{10}$";
    private static final Pattern LOOSE_PHONE_PATTERN = Pattern.compile(LOOSE_PHONE_REGEX);

    // 带国际区号的中国手机号
    private static final String INTERNATIONAL_PHONE_REGEX = "^(\\+?86)?1[3-9]\\d{9}$";
    private static final Pattern INTERNATIONAL_PHONE_PATTERN = Pattern.compile(INTERNATIONAL_PHONE_REGEX);

    // 纯数字正则
    private static final Pattern DIGIT_PATTERN = Pattern.compile("^\\d+$");

    /**
     * 严格判断是否是手机号（中国手机号格式）
     */
    public static boolean isPhone(String account) {
        if (!StringUtils.hasText(account)) {
            return false;
        }
        // 去除空格
        account = account.trim();
        return CHINA_PHONE_PATTERN.matcher(account).matches();
    }

    /**
     * 宽松判断是否是手机号
     */
    public static boolean isPhoneLoose(String account) {
        if (!StringUtils.hasText(account)) {
            return false;
        }
        account = account.trim();
        return LOOSE_PHONE_PATTERN.matcher(account).matches();
    }

    /**
     * 带国际区号的手机号
     */
    public static boolean isInternationalPhone(String account) {
        if (!StringUtils.hasText(account)) {
            return false;
        }
        account = account.trim();
        return INTERNATIONAL_PHONE_PATTERN.matcher(account).matches();
    }

}

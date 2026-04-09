package com.hongjie.pms.common.utils;

import org.apache.commons.lang3.StringEscapeUtils;

/**
 * XSS 防护工具类
 */
@Deprecated
public class XssUtils {

    /**
     * 清理用户输入（移除危险标签 + 转义）
     */
    public static String clean(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        String cleaned = input;
        
        // 1. 移除 script 标签
        cleaned = cleaned.replaceAll("(?i)<script.*?>.*?</script>", "");
        cleaned = cleaned.replaceAll("(?i)<iframe.*?>.*?</iframe>", "");
        cleaned = cleaned.replaceAll("(?i)<object.*?>.*?</object>", "");
        cleaned = cleaned.replaceAll("(?i)<embed.*?>.*?</embed>", "");

        // 2. 移除事件属性
        cleaned = cleaned.replaceAll("(?i)onload=", "onload&#61;");
        cleaned = cleaned.replaceAll("(?i)onerror=", "onerror&#61;");
        cleaned = cleaned.replaceAll("(?i)onclick=", "onclick&#61;");
        cleaned = cleaned.replaceAll("(?i)onmouseover=", "onmouseover&#61;");

        // 3. 转义 HTML 特殊字符
        cleaned = StringEscapeUtils.escapeHtml4(cleaned);
        
        return cleaned;
    }
    
    /**
     * 转义 HTML（用于输出到前端）
     */
    public static String escapeHtml(String content) {
        if (content == null) {
            return null;
        }
        return StringEscapeUtils.escapeHtml4(content);
    }
    
    /**
     * 转义 JavaScript
     */
    public static String escapeJs(String content) {
        if (content == null) {
            return null;
        }
        return StringEscapeUtils.escapeEcmaScript(content);
    }
    
    /**
     * 判断是否包含危险字符
     */
    public static boolean containsDangerousChars(String input) {
        if (input == null) {
            return false;
        }
        String lowerInput = input.toLowerCase();
        return lowerInput.contains("<script") 
                || lowerInput.contains("</script")
                || lowerInput.contains("onload=")
                || lowerInput.contains("onerror=")
                || lowerInput.contains("onclick=");
    }
}
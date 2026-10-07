package com.hongjie.pms.AI.modules.service;

import com.alibaba.fastjson2.JSON;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 长期记忆抽取器：把一段用户对话里"用户陈述的持久事实/偏好"抽出来，
 * 由 LLM 以 JSON 字符串数组返回，再经 {@link UserMemoryService} 落库。
 * <p>仅抽取关于用户的稳定信息（宠物/偏好/住地/身份等），不抽取临时的单次问题。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiMemoryExtractor {

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*?]", Pattern.DOTALL);

    private final ChatModel chatLanguageModel;

    public List<String> extractFacts(String conversationText) {
        if (conversationText == null || conversationText.isBlank()) {
            return List.of();
        }
        String prompt = """
            你是用户长期记忆抽取器。阅读下面这段"宠友社"平台用户与AI助手的对话，
            只提取【用户本人陈述的、跨会话有效的持久事实或偏好】，例如：
            - 养了什么宠物（名字/品种/数量）
            - 所在城市/地区
            - 固定偏好（如"只考虑小型犬""不接受有偿领养"）
            - 身份/生活状态（如"是学生""家里有小孩"）

            忽略：单次问题、临时请求、AI 自己说的话、寒暄客套。
            每条用一句简洁的中文陈述表达（不要用第一人称，如"用户养了一只橘猫叫圆圆"）。
            最多 5 条；没有则返回空数组。
            只输出 JSON 字符串数组，不要任何解释或多余文字。

            对话内容：
            %s
            """.formatted(conversationText);

        try {
            String raw = chatLanguageModel.chat(prompt);
            String array = extractJsonArray(raw);
            if (array == null) {
                log.warn("记忆抽取未返回 JSON 数组，原文: {}", truncate(raw));
                return List.of();
            }
            List<String> facts = JSON.parseArray(array, String.class);
            return facts == null ? List.of() : facts.stream().filter(f -> f != null && !f.isBlank()).toList();
        } catch (Exception e) {
            log.warn("长期记忆抽取失败", e);
            return List.of();
        }
    }

    private String extractJsonArray(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = JSON_ARRAY.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    private String truncate(String s) {
        return s != null && s.length() > 200 ? s.substring(0, 200) : s;
    }
}

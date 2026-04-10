package com.hongjie.pms.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lua脚本加载器
 */
@Slf4j
@Component
public class LuaScriptLoader {
    
    private final ConcurrentHashMap<String, RedisScript<Long>> scriptCache = new ConcurrentHashMap<>();
    
    /**
     * 获取令牌桶脚本
     */
    public RedisScript<Long> getTokenBucketScript() {
        return getScript("lua/token_bucket_advanced.lua");
    }
    
    /**
     * 获取滑动窗口脚本
     */
    public RedisScript<Long> getSlidingWindowScript() {
        return getScript("lua/sliding_window.lua");
    }
    
    /**
     * 获取漏桶脚本
     */
    public RedisScript<Long> getLeakyBucketScript() {
        return getScript("lua/leaky_bucket.lua");
    }
    
    /**
     * 获取增强版令牌桶脚本
     */
    public RedisScript<Long> getAdvancedTokenBucketScript() {
        return getScript("lua/token_bucket_advanced.lua");
    }
    
    /**
     * 获取批量限流脚本
     */
    public RedisScript<Long> getBatchRateLimitScript() {
        return getScript("lua/batch_rate_limit.lua");
    }
    
    /**
     * 加载Lua脚本
     */
    private RedisScript<Long> getScript(String scriptPath) {
        return scriptCache.computeIfAbsent(scriptPath, path -> {
            try {
                // 从resources目录加载
                ClassPathResource resource = new ClassPathResource(path);
                
                // 读取脚本内容
                StringBuilder scriptContent = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        scriptContent.append(line).append("\n");
                    }
                }
                
                // 创建RedisScript
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                script.setScriptText(scriptContent.toString());
                script.setResultType(Long.class);
                
                log.info("加载Lua脚本成功: {}", path);
                return script;
                
            } catch (Exception e) {
                log.error("加载Lua脚本失败: {}", path, e);
                throw new RuntimeException("加载Lua脚本失败: " + path, e);
            }
        });
    }
    
    /**
     * 重新加载所有脚本（用于热更新）
     */
    public void reloadAll() {
        scriptCache.clear();
        log.info("重新加载所有Lua脚本");
    }
}
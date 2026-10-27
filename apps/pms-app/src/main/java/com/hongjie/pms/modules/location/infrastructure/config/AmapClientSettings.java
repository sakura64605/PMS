package com.hongjie.pms.modules.location.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Browser key is public; the JS security code belongs exclusively to the server proxy. */
@Component
public record AmapClientSettings(boolean enabled, String jsApiKey) {
    public AmapClientSettings(
            @Value("${pms.location.amap.enabled:${PMS_AMAP_ENABLED:false}}") boolean enabled,
            @Value("${pms.location.amap.js-api-key:${PMS_AMAP_JS_API_KEY:}}") String jsApiKey) {
        this.enabled = enabled;
        this.jsApiKey = jsApiKey == null ? "" : jsApiKey.trim();
        if (enabled && !this.jsApiKey.matches("[A-Za-z0-9_-]{16,128}")) {
            throw new IllegalArgumentException("启用高德地图时必须配置有效的 Web端 JS API Key");
        }
    }
}

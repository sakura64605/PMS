package com.hongjie.pms.modules.location.api.controller;

import com.hongjie.pms.modules.location.infrastructure.config.AmapClientSettings;
import com.hongjie.pms.platform.pojo.CommonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pet-system/location")
public class MapConfigurationController {
    private final AmapClientSettings settings;

    public MapConfigurationController(AmapClientSettings settings) {
        this.settings = settings;
    }

    @GetMapping("/config")
    public CommonResult<ClientMapConfiguration> config() {
        return CommonResult.success(
                new ClientMapConfiguration(
                        settings.enabled(),
                        "AMAP",
                        "GCJ02",
                        settings.enabled() ? settings.jsApiKey() : null,
                        "/_AMapService"));
    }

    public record ClientMapConfiguration(
            boolean enabled,
            String provider,
            String coordinateSystem,
            String jsApiKey,
            String serviceHost) {}
}

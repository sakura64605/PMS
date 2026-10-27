package com.hongjie.pms.modules.location;

import com.hongjie.pms.modules.location.api.controller.MapConfigurationController;
import com.hongjie.pms.modules.location.infrastructure.config.AmapClientSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MapConfigurationTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            AmapClientSettings.class, MapConfigurationController.class);

    @Test
    void defaultsOffAndReturnsNoKey() {
        context.run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var response = ctx.getBean(MapConfigurationController.class).config().getData();
                    assertThat(response.enabled()).isFalse();
                    assertThat(response.jsApiKey()).isNull();
                });
    }

    @Test
    void runtimeEnvironmentEnablesPublicConfigurationWithoutSecurityCode() {
        context.withPropertyValues(
                        "PMS_AMAP_ENABLED=true",
                        "PMS_AMAP_JS_API_KEY=public_js_key_0123456789",
                        "PMS_AMAP_JS_SECURITY_CODE=never_return_this_secret")
                .run(
                        ctx -> {
                            assertThat(ctx).hasNotFailed();
                            var mvc =
                                    MockMvcBuilders.standaloneSetup(
                                                    ctx.getBean(MapConfigurationController.class))
                                            .build();
                            var body =
                                    mvc.perform(get("/pet-system/location/config"))
                                            .andExpect(status().isOk())
                                            .andExpect(jsonPath("$.code").value(200))
                                            .andExpect(jsonPath("$.data.enabled").value(true))
                                            .andExpect(
                                                    jsonPath("$.data.coordinateSystem")
                                                            .value("GCJ02"))
                                            .andExpect(
                                                    jsonPath("$.data.serviceHost")
                                                            .value("/_AMapService"))
                                            .andReturn()
                                            .getResponse()
                                            .getContentAsString();
                            assertThat(body)
                                    .contains("public_js_key_0123456789")
                                    .doesNotContain("never_return_this_secret", "securityJsCode");
                        });
    }

    @Test
    void enabledWithoutValidKeyFailsFast() {
        context.withPropertyValues("PMS_AMAP_ENABLED=true").run(ctx -> assertThat(ctx).hasFailed());
        assertThatThrownBy(() -> new AmapClientSettings(true, "key\ninvalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

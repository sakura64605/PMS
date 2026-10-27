package com.hongjie.pms.aiservice;

import com.hongjie.pms.aiservice.core.AiCoreClient;
import com.hongjie.pms.modules.ai.api.service.ChatSessionService;
import com.hongjie.pms.modules.ai.application.tool.ToolRegistry;
import com.hongjie.pms.modules.ai.infrastructure.persistence.mapper.AiChatSessionMapper;
import com.hongjie.pms.platform.pojo.UserInfo;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.ApplicationContext;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = AiServiceApplication.class,
        properties = {
            "pms.ai.core.service-token=test-service-token-at-least-32-characters",
            "management.health.redis.enabled=false",
            "management.health.db.enabled=false"
        })
@AutoConfigureMockMvc
class AiServiceBootTest {
    @MockitoBean ChatModel chat;
    @MockitoBean EmbeddingModel embedding;
    @MockitoBean AiCoreClient core;
    @MockitoBean ChatSessionService sessions;
    @Autowired MockMvc http;
    @Autowired ApplicationContext context;

    @Test
    void standaloneRuntimeOwnsAiRoutesMappersAndAllEightTools() throws Exception {
        assertThat(context.getBean(AiChatSessionMapper.class)).isNotNull();
        assertThat(context.getBean(ToolRegistry.class).getToolSpecifications()).hasSize(8);
        when(core.authenticate("Bearer token")).thenReturn(new UserInfo(7L, "user", 0, null, null));
        when(sessions.getUserSessions(7L)).thenReturn(List.of());
        http.perform(get("/pet-system/ai/sessions").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
        http.perform(get("/pet-system/ai/sessions")).andExpect(status().isUnauthorized());
        http.perform(
                        get("/pet-system/admin/ai/knowledge/search")
                                .header("Authorization", "Bearer token"))
                .andExpect(jsonPath("$.code").value(403));
        assertThatThrownBy(
                        () ->
                                Class.forName(
                                        "com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}

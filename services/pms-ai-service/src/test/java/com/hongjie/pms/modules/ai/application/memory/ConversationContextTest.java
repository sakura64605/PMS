package com.hongjie.pms.modules.ai.application.memory;
import com.hongjie.pms.modules.ai.infrastructure.config.AIAgentConfig;
import com.hongjie.pms.modules.ai.infrastructure.persistence.mapper.AiChatMessageMapper;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.AiChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.data.redis.core.RedisTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ConversationContextTest {
    @org.junit.jupiter.api.BeforeAll static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), AiChatMessage.class);
    }
    @Test void preservesRecentTurnsAndCompressesOlderOnes() {
        var mapper = mock(AiChatMessageMapper.class);
        var model = mock(ChatModel.class);
        var config = new AIAgentConfig(); config.setMaxMemoryRounds(1);
        var redis = mock(RedisTemplate.class);
        var rows = new ArrayList<AiChatMessage>();
        for (String content : List.of("old question", "old answer", "recent question", "recent answer")) {
            var row = new AiChatMessage(); row.setRole(rows.size() % 2 == 0 ? "user" : "assistant");
            row.setContent(content); rows.add(row);
        }
        when(mapper.selectList(any())).thenReturn(rows);
        when(model.chat(anyString())).thenReturn("older facts");
        var memory = new ConversationContext(mapper, model, config, redis).prepare("s1");
        assertThat(memory.messages().toString()).contains("older facts", "recent question", "recent answer")
                .doesNotContain("old question", "old answer");
        verify(model).chat(contains("old question"));
    }
    @Test void compressionFailureKeepsRecentWindow() {
        var mapper = mock(AiChatMessageMapper.class);
        var model = mock(ChatModel.class);
        var config = new AIAgentConfig(); config.setMaxMemoryRounds(1);
        var rows = new ArrayList<AiChatMessage>();
        for (String content : List.of("old", "recent")) {
            var row = new AiChatMessage(); row.setRole("user"); row.setContent(content); rows.add(row);
        }
        when(mapper.selectList(any())).thenReturn(rows);
        when(model.chat(anyString())).thenThrow(new IllegalStateException());
        var memory = new ConversationContext(mapper, model, config, mock(RedisTemplate.class)).prepare("s1");
        assertThat(memory.messages().toString()).contains("recent").doesNotContain("old");
    }
    @Test void disabledMemoryDoesNotReadHistoryOrCallModel() {
        var mapper = mock(AiChatMessageMapper.class); var model = mock(ChatModel.class);
        var config = new AIAgentConfig(); config.setEnableMemory(false);
        assertThat(new ConversationContext(mapper, model, config, mock(RedisTemplate.class))
                .prepare("s1").messages()).isEmpty();
        verifyNoInteractions(mapper, model);
    }
}

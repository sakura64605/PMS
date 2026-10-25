package com.hongjie.pms.platform.cache.serialization;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.ai.api.dto.request.AIAgentRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class CompatibleRedisSerializerTest {
    @ParameterizedTest @EnumSource(value=JsonTypeInfo.As.class,names={"PROPERTY","WRAPPER_ARRAY"})
    void preservesHistoricalTypeIdsAndReadsBothPackageNames(JsonTypeInfo.As inclusion){
        var serializer=new CompatibleRedisSerializer(new ObjectMapper().registerModule(new JavaTimeModule()),inclusion);
        var user=new User();user.setId(7L);user.setNickName(User.class.getName());
        var bytes=serializer.serialize(user);var json=new String(bytes,StandardCharsets.UTF_8);
        assertThat(json).contains("com.hongjie.pms.modules.user.entity.User");
        assertThat(((User)serializer.deserialize(bytes)).getNickName()).isEqualTo(User.class.getName());
        var current=json.replace("com.hongjie.pms.modules.user.entity.User",User.class.getName()).getBytes(StandardCharsets.UTF_8);
        assertThat(((User)serializer.deserialize(current)).getId()).isEqualTo(7L);
        assertThat(serializer.deserialize(serializer.serialize(null))).isNull();
    }
    @ParameterizedTest @EnumSource(value=JsonTypeInfo.As.class,names={"PROPERTY","WRAPPER_ARRAY"})
    void collectionsAndNestedDtoTypeIdsRemainCompatible(JsonTypeInfo.As inclusion){
        var serializer=new CompatibleRedisSerializer(new ObjectMapper().registerModule(new JavaTimeModule()),inclusion);
        var row=new AIAgentRequest.ChatHistory();row.setContent("nested");
        var bytes=serializer.serialize(new ArrayList<>(List.of(row)));
        assertThat(new String(bytes,StandardCharsets.UTF_8)).contains("com.hongjie.pms.AI.modules.dto.request.AIAgentRequest$ChatHistory");
        var decoded=(List<?>)serializer.deserialize(bytes);
        assertThat(((AIAgentRequest.ChatHistory)decoded.get(0)).getContent()).isEqualTo("nested");
    }
}

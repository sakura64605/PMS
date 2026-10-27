package com.hongjie.pms.bootstrap.ai;

import com.hongjie.pms.ai.contract.CoreQueryTool;
import com.hongjie.pms.modules.ai.api.dto.ToolCall;
import com.hongjie.pms.modules.identity.user.application.UserSessions;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivitySignupMapper;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiCoreControllerTest {
    final String key = "test-service-token-at-least-32-characters";
    UserSessions sessions = mock(UserSessions.class);
    UserMapper users = mock(UserMapper.class);
    CoreQueryTool tool = mock(CoreQueryTool.class);

    AiCoreController controller() {
        when(tool.getName()).thenReturn("my_pets");
        return new AiCoreController(
                key,
                sessions,
                users,
                mock(PetPostMapper.class),
                mock(ActivitySignupMapper.class),
                List.of(tool));
    }

    @Test
    void invalidServiceIdentityCannotReadUserOrInvokeTools() {
        var controller = controller();
        assertThatThrownBy(
                        () ->
                                controller.authenticate(
                                        "", new AiCoreController.Authentication("token")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                controller.tool(
                                        "forged",
                                        "my_pets",
                                        new AiCoreController.ToolRequest(7L, Map.of())))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(sessions, users);
        verify(tool, never()).execute(any(), any());
    }

    @Test
    void authDelegatesRevocationAndCurrentRoleToIdentityOwner() {
        var controller = controller();
        when(sessions.authenticate("token")).thenReturn(new UserInfo(7L, "user", 0, null, null));
        assertThat(
                        controller
                                .authenticate(key, new AiCoreController.Authentication("token"))
                                .getData()
                                .getRole())
                .isZero();
        assertThatThrownBy(
                        () ->
                                controller.authenticate(
                                        key, new AiCoreController.Authentication("expired")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void toolActorCannotBeOverriddenThroughModelArguments() {
        var controller = controller();
        var user = new User();
        user.setId(7L);
        user.setStatus(1);
        when(users.selectById(7L)).thenReturn(user);
        var args = Map.<String, Object>of("userId", 99);
        when(tool.execute(args, 7L))
                .thenReturn(ToolCall.builder().name("my_pets").result("own").build());
        assertThat(
                        controller
                                .tool(key, "my_pets", new AiCoreController.ToolRequest(7L, args))
                                .getData()
                                .getResult())
                .isEqualTo("own");
        verify(tool).execute(args, 7L);
        user.setStatus(0);
        assertThatThrownBy(
                        () ->
                                controller.tool(
                                        key, "my_pets", new AiCoreController.ToolRequest(7L, args)))
                .isInstanceOf(BusinessException.class);
    }
}

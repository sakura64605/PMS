package com.hongjie.pms.aiservice;

import com.hongjie.pms.aiservice.core.AiCoreClient;
import com.hongjie.pms.aiservice.config.AiAuthenticationFilter;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAuthenticationFilterTest {
    @Test
    void forgedActorHeaderCannotCreateAnAuthenticatedUser() throws Exception {
        var core = mock(AiCoreClient.class);
        var filter = new AiAuthenticationFilter(core, new ObjectMapper());
        var request = new MockHttpServletRequest("GET", "/pet-system/ai/sessions");
        request.addHeader("X-PMS-Actor", "1");
        var response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
        assertThat(UserContext.getUser()).isNull();
    }

    @Test
    void currentCoreRoleIsUsedAndThreadContextIsAlwaysCleared() throws Exception {
        var core = mock(AiCoreClient.class);
        when(core.authenticate("Bearer token")).thenReturn(new UserInfo(7L, "user", 0, null, null));
        var request = new MockHttpServletRequest("GET", "/pet-system/admin/ai/knowledge");
        request.addHeader("Authorization", "Bearer token");
        new AiAuthenticationFilter(core, new ObjectMapper())
                .doFilter(
                        request,
                        new MockHttpServletResponse(),
                        (r, s) -> {
                            assertThat(UserContext.getUserId()).isEqualTo(7L);
                            assertThat(UserContext.isAdmin()).isFalse();
                        });
        assertThat(UserContext.getUser()).isNull();
    }

    @Test
    void coreFailureReturns503InsteadOfAllowingRequest() throws Exception {
        var core = mock(AiCoreClient.class);
        when(core.authenticate("token")).thenThrow(new BusinessException(503, "unavailable"));
        var request = new MockHttpServletRequest("GET", "/pet-system/ai/ws");
        request.setParameter("token", "token");
        var response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        new AiAuthenticationFilter(core, new ObjectMapper()).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        verifyNoInteractions(chain);
    }
}

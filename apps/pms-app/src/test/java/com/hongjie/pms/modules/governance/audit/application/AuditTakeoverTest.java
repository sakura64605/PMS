package com.hongjie.pms.modules.governance.audit.application;

import com.hongjie.pms.modules.governance.audit.api.service.AuditService;
import com.hongjie.pms.modules.governance.audit.application.handler.AuditTargetHandlerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditTakeoverTest {
    @Configuration(proxyBeanMethods = false)
    @Import(PmsAuditServiceImpl.class)
    static class Config {}

    @Test
    void primaryRoutesQueriesAndCommandsToNewServices() {
        var legacy = mock(AuditService.class);
        var queries = mock(AuditQueryService.class);
        var commands = mock(AuditCommandService.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withBean("auditServiceImpl", AuditService.class, () -> legacy)
                .withBean(AuditQueryService.class, () -> queries)
                .withBean(AuditCommandService.class, () -> commands)
                .withBean(
                        AuditTargetHandlerFactory.class,
                        () -> mock(AuditTargetHandlerFactory.class))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            var service = context.getBean(AuditService.class);
                            assertThat(service).isInstanceOf(PmsAuditServiceImpl.class);
                            service.getPendingList("adopt", null, null, 1, 10);
                            verify(queries).pending("adopt", null, null, 1, 10);
                            service.approve(1L, "adopt");
                            verify(commands).approve(1L, "adopt");
                            verifyNoInteractions(legacy);
                        });
    }
}

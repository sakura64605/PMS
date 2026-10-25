package com.hongjie.pms.modules.identity.admin.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.modules.identity.admin.api.controller.AdminController;
import com.hongjie.pms.modules.identity.admin.api.service.AdminService;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.governance.audit.application.AuditCommandService;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAuditBridgeTest {
    com.hongjie.pms.modules.identity.user.application.UserAdministration accounts;AdminService legacy;PetPostMapper posts;AuditCommandService audit;PmsAdminServiceImpl bridge;
    @BeforeEach void setup(){
        legacy=mock(AdminService.class);posts=mock(PetPostMapper.class);audit=mock(AuditCommandService.class);
        accounts=mock(com.hongjie.pms.modules.identity.user.application.UserAdministration.class);
        bridge=new PmsAdminServiceImpl(accounts,posts,audit);
        UserContext.setUser(new UserInfo(1L,"admin",1,null,null));
    }
    @AfterEach void clear(){UserContext.clear();}
    void post(int type){var row=new PetPost();row.setId(10L);row.setType(type);when(posts.selectById(10L)).thenReturn(row);}
    @Test void adoptApprovalNeverCallsLegacyWrite(){post(0);bridge.accept(10L);verify(audit).approve(10L,"adopt");verifyNoInteractions(legacy);}
    @Test void helpRejectionPreservesReason(){post(1);bridge.reject(10L,"reason");verify(audit).reject(10L,"help","reason");verifyNoInteractions(legacy);}
    @Test void ordinaryUserFailsBeforeLoadingTarget(){
        UserContext.setUser(new UserInfo(2L,"user",0,null,null));
        assertThatThrownBy(()->bridge.accept(10L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(posts,audit,legacy);
    }
    @Test void missingTargetDoesNotReachCommandService(){
        assertThatThrownBy(()->bridge.accept(10L)).isInstanceOf(BusinessException.class);verifyNoInteractions(audit,legacy);
    }
    @Test void unsupportedTypeDoesNotCreateAnAuditRecord(){
        post(9);assertThatThrownBy(()->bridge.accept(10L)).isInstanceOf(BusinessException.class);verifyNoInteractions(audit,legacy);
    }
    @Test void accountOperationsUseNewAdministration(){
        var page=new Page<com.hongjie.pms.modules.identity.admin.api.dto.response.AdminUserSimpleDto>();
        when(accounts.list(1,10,"name",1)).thenReturn(page);
        assertThat(bridge.userList(1,10,"name",1)).isSameAs(page);
        bridge.batchDisableUsers(List.of(2L));bridge.batchEnableUsers(List.of(2L));bridge.batchResetPassword(List.of(2L));
        verify(accounts).batch(List.of(2L),com.hongjie.pms.modules.identity.user.application.UserAdminItem.Operation.DISABLE);
        verify(accounts).batch(List.of(2L),com.hongjie.pms.modules.identity.user.application.UserAdminItem.Operation.ENABLE);
        verify(accounts).batch(List.of(2L),com.hongjie.pms.modules.identity.user.application.UserAdminItem.Operation.RESET_PASSWORD);
        verifyNoInteractions(legacy);
        verifyNoInteractions(audit,posts);
    }
    @Configuration(proxyBeanMethods=false)
    @Import({PmsAdminServiceImpl.class,AdminController.class}) static class Config {}
    @Test void existingControllerReceivesBridgeAndRollbackSelectsLegacy(){
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withBean("adminServiceImpl",AdminService.class,()->legacy)
                .withBean(com.hongjie.pms.modules.identity.user.application.UserAdministration.class,()->accounts)
                .withBean(PetPostMapper.class,()->posts).withBean(AuditCommandService.class,()->audit).run(context->{
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AdminService.class)).isInstanceOf(PmsAdminServiceImpl.class);
                    assertThat(ReflectionTestUtils.getField(context.getBean(AdminController.class),"adminService"))
                            .isInstanceOf(PmsAdminServiceImpl.class);
                });
        new ApplicationContextRunner().withBean("adminServiceImpl",AdminService.class,()->legacy)
                .withBean(AdminController.class).run(context->{
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AdminService.class)).isSameAs(legacy);
                });
    }
}

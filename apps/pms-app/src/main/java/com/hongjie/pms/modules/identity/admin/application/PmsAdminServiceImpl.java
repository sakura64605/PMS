package com.hongjie.pms.modules.identity.admin.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.identity.admin.api.dto.response.AdminUserSimpleDto;
import com.hongjie.pms.modules.identity.admin.api.dto.response.BatchOperationResponse;
import com.hongjie.pms.modules.identity.admin.api.service.AdminService;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.governance.audit.application.AuditCommandService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

/** Compatibility facade for legacy admin paths; all operations use new services. */
@Service
@Primary
public class PmsAdminServiceImpl implements AdminService {
    private final com.hongjie.pms.modules.identity.user.application.UserAdministration accounts;
    private final PetPostMapper posts;
    private final AuditCommandService audit;

    public PmsAdminServiceImpl(
            com.hongjie.pms.modules.identity.user.application.UserAdministration accounts,
            PetPostMapper posts,
            AuditCommandService audit) {
        this.accounts = accounts;
        this.posts = posts;
        this.audit = audit;
    }

    @Override
    public void accept(Long id) {
        audit.approve(id, auditType(id));
    }

    @Override
    public void reject(Long id, String reason) {
        audit.reject(id, auditType(id), reason);
    }

    private String auditType(Long id) {
        if (!Integer.valueOf(1).equals(UserContext.getRole()))
            throw new BusinessException(ErrorCode.FORBIDDEN);
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR);
        var post = posts.selectById(id);
        if (post == null) throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        // 此查询只判断路由；真正的状态检查与修改由命令服务在目标行锁内完成。
        if (Objects.equals(post.getType(), 0)) return "adopt";
        if (Objects.equals(post.getType(), 1)) return "help";
        throw new BusinessException(ErrorCode.PARAM_ERROR, "未知宠物帖子类型");
    }

    @Override
    public IPage<AdminUserSimpleDto> userList(int page, int size, String keyword, Integer status) {
        return accounts.list(page, size, keyword, status);
    }

    @Override
    public BatchOperationResponse batchDisableUsers(List<Long> ids) {
        return accounts.batch(
                ids,
                com.hongjie.pms.modules.identity.user.application.UserAdminItem.Operation.DISABLE);
    }

    @Override
    public BatchOperationResponse batchEnableUsers(List<Long> ids) {
        return accounts.batch(
                ids,
                com.hongjie.pms.modules.identity.user.application.UserAdminItem.Operation.ENABLE);
    }

    @Override
    public BatchOperationResponse batchResetPassword(List<Long> ids) {
        return accounts.batch(
                ids,
                com.hongjie.pms.modules.identity.user.application.UserAdminItem.Operation
                        .RESET_PASSWORD);
    }
}

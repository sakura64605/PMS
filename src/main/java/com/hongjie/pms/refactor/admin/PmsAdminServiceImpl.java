package com.hongjie.pms.refactor.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.admin.dto.response.AdminUserSimpleDto;
import com.hongjie.pms.modules.admin.dto.response.BatchOperationResponse;
import com.hongjie.pms.modules.admin.service.AdminService;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.refactor.audit.AuditCommandService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

/** 旧后台审核路径的适配层；账号管理仍明确委托旧服务，避免混入另一批行为变更。 */
@Service
@Primary
public class PmsAdminServiceImpl implements AdminService {
    private final AdminService legacyAccounts;
    private final PetPostMapper posts;
    private final AuditCommandService audit;
    public PmsAdminServiceImpl(@Qualifier("adminServiceImpl") AdminService legacyAccounts,
                              PetPostMapper posts, AuditCommandService audit) {
        this.legacyAccounts=legacyAccounts;this.posts=posts;this.audit=audit;
    }
    @Override public void accept(Long id) { audit.approve(id,auditType(id)); }
    @Override public void reject(Long id,String reason) { audit.reject(id,auditType(id),reason); }
    private String auditType(Long id) {
        if(!Integer.valueOf(1).equals(UserContext.getRole()))throw new BusinessException(ErrorCode.FORBIDDEN);
        if(id==null||id<=0)throw new BusinessException(ErrorCode.PARAM_ERROR);
        var post=posts.selectById(id);
        if(post==null)throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        // 此查询只判断路由；真正的状态检查与修改由命令服务在目标行锁内完成。
        if(Objects.equals(post.getType(),0))return "adopt";
        if(Objects.equals(post.getType(),1))return "help";
        throw new BusinessException(ErrorCode.PARAM_ERROR,"未知宠物帖子类型");
    }
    @Override public IPage<AdminUserSimpleDto> userList(int page,int size,String keyword,Integer status) {
        return legacyAccounts.userList(page,size,keyword,status);
    }
    @Override public BatchOperationResponse batchDisableUsers(List<Long> ids) { return legacyAccounts.batchDisableUsers(ids); }
    @Override public BatchOperationResponse batchEnableUsers(List<Long> ids) { return legacyAccounts.batchEnableUsers(ids); }
    @Override public BatchOperationResponse batchResetPassword(List<Long> ids) { return legacyAccounts.batchResetPassword(ids); }
}

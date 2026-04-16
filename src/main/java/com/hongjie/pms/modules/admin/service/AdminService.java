package com.hongjie.pms.modules.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.admin.dto.response.AdminUserSimpleDto;
import com.hongjie.pms.modules.admin.dto.response.BatchOperationResponse;

import java.util.List;

public interface AdminService {

    void accept(Long id);

    void reject(Long id, String reason);

    IPage<AdminUserSimpleDto> userList(int pageNum, int pageSize, String keyword, Integer status);

    /**
     * 批量禁用用户
     */
    BatchOperationResponse batchDisableUsers(List<Long> userIds);

    /**
     * 批量启用用户
     */
    BatchOperationResponse batchEnableUsers(List<Long> userIds);

    /**
     * 批量重置密码
     */
    BatchOperationResponse batchResetPassword(List<Long> userIds);
}
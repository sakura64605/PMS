package com.hongjie.pms.modules.identity.user.application;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.identity.admin.api.dto.response.*;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.shared.core.page.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.*;

@Service @RequiredArgsConstructor
public class UserAdministration {
    private final UserMapper users;
    private final UserAdminItem item;
    public IPage<AdminUserSimpleDto> list(int page, int size, String keyword, Integer status) {
        admin();
        var where = new LambdaQueryWrapper<User>();
        if (status != null) where.eq(User::getStatus, status);
        if (StringUtils.hasText(keyword)) where.and(part -> part.like(User::getUserName, keyword)
                .or().like(User::getNickName, keyword).or().like(User::getPhone, keyword));
        where.orderByDesc(User::getCreateTime).orderByDesc(User::getId);
        return users.selectPage(PageQuery.of((long)page, (long)size).<User>toPage(), where)
                .convert(user -> AdminUserSimpleDto.builder().userId(user.getId()).username(user.getUserName())
                        .nickname(user.getNickName()).avatar(user.getAvatar()).isDisable(Objects.equals(user.getStatus(), 0)).build());
    }
    public BatchOperationResponse batch(List<Long> ids, UserAdminItem.Operation operation) {
        admin();
        if (ids == null || ids.isEmpty() || ids.size() > 100) throw new BusinessException(ErrorCode.PARAM_ERROR);
        List<BatchOperationResponse.FailResult> failures = new ArrayList<>();
        Set<Long> seen = new HashSet<>(); int succeeded = 0;
        for (Long id : ids) {
            try {
                if (!seen.add(id)) throw new BusinessException(ErrorCode.PARAM_ERROR, "重复用户ID");
                item.execute(operation, id); succeeded++;
            } catch (BusinessException expected) {
                failures.add(BatchOperationResponse.FailResult.builder().id(id).reason(expected.getMessage()).build());
            } catch (RuntimeException failure) {
                failures.add(BatchOperationResponse.FailResult.builder().id(id).reason("操作失败，请重试").build());
            }
        }
        return BatchOperationResponse.builder().totalCount(ids.size()).successCount(succeeded).failCount(failures.size()).failList(failures).build();
    }
    private void admin() { if (!UserContext.isAdmin()) throw new BusinessException(ErrorCode.FORBIDDEN); }
}

package com.hongjie.pms.modules.audit.handler;

import java.util.List;

/**
 * 审核目标类型处理器接口
 * 每种目标类型（宠物帖子、活动、日记）实现此接口，消除 AuditServiceImpl 中的 if/else-if 链
 */
public interface AuditTargetHandler {

    /**
     * 支持的审核目标类型代码列表
     */
    List<String> getTargetTypes();

    /**
     * 获取审核详情实体
     */
    Object getDetail(Long id);

    /**
     * 更新审核状态
     */
    void updateAuditStatus(Long targetId, Integer auditStatus, String rejectReason);

    /**
     * 清除缓存
     */
    void clearCache(Long targetId);

    /**
     * 获取目标类型描述
     */
    String getTargetTypeDesc(String targetType);

    /**
     * 获取标题（用于审核历史）
     */
    String getTitle(Long targetId);

    /**
     * 获取用户ID（用于审核历史）
     */
    Long getUserId(Long targetId);
}
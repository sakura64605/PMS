package com.hongjie.pms.modules.community.comment.application.converter;

import com.hongjie.pms.modules.community.comment.infrastructure.persistence.entity.Comment;
import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 构建评论树所需的、已批量查好的上下文。
 *
 * <p>评论树节点不是「一条评论映射成一个 DTO」那么简单：一个节点需要知道
 * 自己的子评论、作者、以及当前用户是否点过赞。把这些一次性查好打包进来，
 * 转换器就能保持<b>纯函数</b>（同样的上下文 + 同样的评论 → 同样的结果），
 * 而不必在递归过程中反复查库。
 *
 * @param repliesByParent 子评论按 parentId 分组，避免递归时线性扫描
 * @param users           作者 / 被回复人的简档
 * @param likedCommentIds 当前用户点赞过的评论 ID；未登录时为空集
 */
public record CommentTreeContext(
        Map<Long, List<Comment>> repliesByParent,
        Map<Long, UserSimpleDto> users,
        Set<Long> likedCommentIds) {}

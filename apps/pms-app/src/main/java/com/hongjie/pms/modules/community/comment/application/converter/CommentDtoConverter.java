package com.hongjie.pms.modules.community.comment.application.converter;

import com.hongjie.pms.modules.community.comment.api.dto.response.CommentRespDto;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.entity.Comment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 评论 -> {@link CommentRespDto} 的转换（含子树递归）。
 *
 * <p>没有实现 {@code EntityConverter} 接口：那个接口的契约是「一个源对象映射成一个目标对象」，
 * 而评论节点的目标结构依赖上下文（子评论、作者表）。与其为了套接口把上下文塞成成员变量
 * （会让转换器变成有状态、不再可安全复用），不如明确声明它需要一个上下文入参。
 *
 * <p><b>相对老实现补齐了 {@code isLiked}</b>：老 {@code buildCommentTree} 建了
 * {@code isLiked} 字段却从未赋值，前端拿到的永远是 {@code null}。
 * 现在由批量查好的点赞集合填充。
 */
@Component
public class CommentDtoConverter {

    /**
     * 把一个评论节点转成 DTO，并递归挂上它的子评论。
     *
     * @param comment 当前节点
     * @param context 已批量查好的上下文
     */
    public CommentRespDto toView(Comment comment, CommentTreeContext context) {
        CommentRespDto dto =
                CommentRespDto.builder()
                        .id(comment.getId())
                        .user(context.users().get(comment.getUserId()))
                        .content(comment.getContent())
                        .likeCount(comment.getLikeCount())
                        .isLiked(context.likedCommentIds().contains(comment.getId()))
                        .createTime(comment.getCreateTime())
                        .replyTo(
                                comment.getReplyTo() == null
                                        ? null
                                        : context.users().get(comment.getReplyTo()))
                        .parentId(comment.getParentId())
                        .replies(new ArrayList<>())
                        .build();

        List<Comment> children = context.repliesByParent().get(comment.getId());
        if (children != null && !children.isEmpty()) {
            // 与老实现一致：子评论按时间正序，读起来才是对话顺序
            dto.setReplies(
                    children.stream()
                            .sorted(Comparator.comparing(Comment::getCreateTime))
                            .map(child -> toView(child, context))
                            .toList());
        }
        return dto;
    }
}

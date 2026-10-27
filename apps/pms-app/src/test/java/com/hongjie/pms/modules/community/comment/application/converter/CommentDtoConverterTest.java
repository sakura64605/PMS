package com.hongjie.pms.modules.community.comment.application.converter;

import com.hongjie.pms.modules.community.comment.api.dto.response.CommentRespDto;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.entity.Comment;
import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评论树装配是本次重构里最容易写错的一段：递归、排序、被回复人查找、
 * 点赞态填充，任何一处错了都只表现为「前端显示不对」而不会报错。
 */
@DisplayName("CommentDtoConverter 评论树装配")
class CommentDtoConverterTest {

    private final CommentDtoConverter converter = new CommentDtoConverter();

    private static UserSimpleDto user(Long id, String name) {
        return UserSimpleDto.builder().userId(id).username(name).nickname(name).build();
    }

    private static Comment comment(
            Long id, Long userId, Long parentId, String content, int minute) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(content);
        comment.setLikeCount(0);
        comment.setStatus(1);
        comment.setCreateTime(LocalDateTime.of(2026, 10, 1, 10, minute));
        return comment;
    }

    private static CommentTreeContext context(
            Map<Long, List<Comment>> repliesByParent,
            Map<Long, UserSimpleDto> users,
            Set<Long> likedIds) {
        return new CommentTreeContext(repliesByParent, users, likedIds);
    }

    @Nested
    @DisplayName("节点字段")
    class NodeFields {

        @Test
        @DisplayName("基本字段逐项映射")
        void mapsBasicFields() {
            Comment root = comment(1L, 7L, 0L, "好可爱", 0);

            CommentRespDto dto =
                    converter.toView(root, context(Map.of(), Map.of(7L, user(7L, "张三")), Set.of()));

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getContent()).isEqualTo("好可爱");
            assertThat(dto.getParentId()).isZero();
            assertThat(dto.getUser().getNickname()).isEqualTo("张三");
            assertThat(dto.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
            assertThat(dto.getReplies()).isEmpty();
        }

        @Test
        @DisplayName("isLiked 由上下文填充 —— 老实现建了这个字段却从未赋值")
        void fillsIsLiked() {
            Comment liked = comment(1L, 7L, 0L, "a", 0);
            Comment notLiked = comment(2L, 7L, 0L, "b", 1);

            CommentTreeContext ctx = context(Map.of(), Map.of(7L, user(7L, "张三")), Set.of(1L));

            assertThat(converter.toView(liked, ctx).getIsLiked()).isTrue();
            assertThat(converter.toView(notLiked, ctx).getIsLiked()).isFalse();
        }

        @Test
        @DisplayName("replyTo 解析为被回复人的简档")
        void resolvesReplyTo() {
            Comment reply = comment(2L, 8L, 1L, "同问", 5);
            reply.setReplyTo(7L);

            CommentRespDto dto =
                    converter.toView(
                            reply,
                            context(
                                    Map.of(),
                                    Map.of(7L, user(7L, "张三"), 8L, user(8L, "李四")),
                                    Set.of()));

            assertThat(dto.getUser().getNickname()).isEqualTo("李四");
            assertThat(dto.getReplyTo().getNickname()).isEqualTo("张三");
        }

        @Test
        @DisplayName("没有 replyTo 时为 null，不抛 NPE")
        void nullReplyToIsSafe() {
            Comment root = comment(1L, 7L, 0L, "a", 0);
            root.setReplyTo(null);

            CommentRespDto dto =
                    converter.toView(root, context(Map.of(), Map.of(7L, user(7L, "张三")), Set.of()));

            assertThat(dto.getReplyTo()).isNull();
        }

        @Test
        @DisplayName("作者已注销时 user 为 null 而非抛异常")
        void toleratesMissingUser() {
            Comment root = comment(1L, 99L, 0L, "a", 0);

            CommentRespDto dto = converter.toView(root, context(Map.of(), Map.of(), Set.of()));

            assertThat(dto.getUser()).isNull();
            assertThat(dto.getContent()).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("子树递归")
    class Nesting {

        /** 1 <- 2 <- 3 三层，另外 1 下还有一个时间更早的 4，用于验证排序 */
        private CommentTreeContext threeLevelContext() {
            Comment c1 = comment(1L, 1L, 0L, "顶级", 0);
            Comment c2 = comment(2L, 2L, 1L, "回复", 10);
            Comment c3 = comment(3L, 3L, 2L, "再回复", 20);
            Comment c4 = comment(4L, 4L, 1L, "先说", 5);

            return context(
                    Map.of(1L, List.of(c2, c4), 2L, List.of(c3)),
                    Map.of(
                            1L,
                            user(1L, "u1"),
                            2L,
                            user(2L, "u2"),
                            3L,
                            user(3L, "u3"),
                            4L,
                            user(4L, "u4")),
                    Set.of());
        }

        @Test
        @DisplayName("逐层挂载，深度不受限")
        void buildsDeepTree() {
            CommentRespDto tree =
                    converter.toView(comment(1L, 1L, 0L, "顶级", 0), threeLevelContext());

            assertThat(tree.getReplies()).hasSize(2);
            CommentRespDto firstReply =
                    tree.getReplies().stream()
                            .filter(r -> r.getId().equals(2L))
                            .findFirst()
                            .orElseThrow();
            assertThat(firstReply.getReplies()).hasSize(1);
            assertThat(firstReply.getReplies().get(0).getId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("同一层的子评论按时间正序 —— 读起来才是对话顺序")
        void sortsSiblingsByTime() {
            CommentRespDto tree =
                    converter.toView(comment(1L, 1L, 0L, "顶级", 0), threeLevelContext());

            assertThat(tree.getReplies())
                    .extracting(CommentRespDto::getId)
                    .as("入参顺序是 [2(第10分), 4(第5分)]，应按时间重排为 [4, 2]")
                    .containsExactly(4L, 2L);
        }

        @Test
        @DisplayName("没有子评论时 replies 是空列表而非 null")
        void emptyRepliesNotNull() {
            CommentRespDto leaf =
                    converter.toView(comment(3L, 3L, 2L, "叶子", 20), threeLevelContext());

            assertThat(leaf.getReplies()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("上下文里没有对应子评论时安全返回叶节点")
        void missingChildrenIsSafe() {
            Comment orphan = comment(999L, 1L, 0L, "游离", 0);

            CommentRespDto dto = converter.toView(orphan, context(Map.of(), Map.of(), Set.of()));

            assertThat(dto.getReplies()).isEmpty();
        }
    }
}

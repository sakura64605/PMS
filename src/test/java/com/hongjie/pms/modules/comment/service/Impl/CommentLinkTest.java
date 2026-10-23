package com.hongjie.pms.modules.comment.service.Impl;

import com.hongjie.pms.common.enums.CommentLikeTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归测试：锁定「评论回复通知链接」的缺陷修复。
 *
 * <p>被测方法声明为包级静态正是为了让本测试无需构造 {@code CommentServiceImpl}
 * （它依赖近十个 Mapper 与 Service）。
 *
 * <p>修复前的错误表达式为
 * {@code cond ? "/activity/" : "/pet/" + targetId + "?commentId=" + commentId}；
 * 由于 {@code +} 优先级高于三元运算符，活动分支被截断成 {@code "/activity/"}。
 * 下面同时断言了「修复后的正确值」与「不再等于修复前的错误值」，
 * 避免将来有人"化简"代码时又写回同样的表达式。
 */
@DisplayName("评论回复通知链接（缺陷回归）")
class CommentLinkTest {

    @Nested
    @DisplayName("按目标类型生成正确链接")
    class CorrectLinks {

        @Test
        @DisplayName("宠物帖子 -> /pet/{id}?commentId={cid}")
        void petPostLink() {
            assertThat(CommentServiceImpl.buildCommentAnchorLink(CommentLikeTypes.PET_POST, 100L, 7L))
                    .isEqualTo("/pet/100?commentId=7");
        }

        @Test
        @DisplayName("活动 -> /activity/{id}?commentId={cid}（修复前只剩 \"/activity/\"）")
        void activityLink() {
            String link = CommentServiceImpl.buildCommentAnchorLink(CommentLikeTypes.PET_ACTIVITY, 200L, 8L);

            assertThat(link).isEqualTo("/activity/200?commentId=8");
            assertThat(link)
                    .as("修复前活动分支会退化成这个值，丢失活动 ID 与评论锚点")
                    .isNotEqualTo("/activity/");
        }

        @Test
        @DisplayName("日常动态 -> /daily/{id}?commentId={cid}（修复前被错发成 /pet/...）")
        void dailyLink() {
            String link = CommentServiceImpl.buildCommentAnchorLink(CommentLikeTypes.DAILY, 300L, 9L);

            assertThat(link).isEqualTo("/daily/300?commentId=9");
            assertThat(link).as("修复前日常动态会落到 else 分支").doesNotStartWith("/pet/");
        }
    }

    @Nested
    @DisplayName("边界与容错")
    class EdgeCases {

        @Test
        @DisplayName("未知目标类型回落到 /pet/ 前缀，而不是抛异常中断评论创建")
        void unknownTargetFallsBack() {
            assertThat(CommentServiceImpl.buildCommentAnchorLink("something_new", 1L, 2L))
                    .isEqualTo("/pet/1?commentId=2");
        }

        @Test
        @DisplayName("null 目标类型同样回落，不抛 NPE")
        void nullTargetFallsBack() {
            assertThat(CommentServiceImpl.buildCommentAnchorLink(null, 1L, 2L))
                    .isEqualTo("/pet/1?commentId=2");
        }

        @Test
        @DisplayName("始终包含 ?commentId= 锚点 —— 前端靠它滚动定位到具体评论")
        void alwaysCarriesAnchor() {
            for (String targetType : new String[]{
                    CommentLikeTypes.PET_POST, CommentLikeTypes.PET_ACTIVITY, CommentLikeTypes.DAILY}) {
                assertThat(CommentServiceImpl.buildCommentAnchorLink(targetType, 5L, 6L))
                        .as("目标类型 %s", targetType)
                        .contains("?commentId=6");
            }
        }
    }
}

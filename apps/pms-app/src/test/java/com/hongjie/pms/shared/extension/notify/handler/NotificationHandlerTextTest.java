package com.hongjie.pms.shared.extension.notify.handler;

import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.platform.enums.MessageType;
import com.hongjie.pms.shared.extension.notify.CommentNotificationContext;
import com.hongjie.pms.shared.extension.notify.LikeNotificationContext;
import com.hongjie.pms.shared.extension.notify.NotificationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定通知文案 —— 与老 {@code MessageService} 的
 * {@code getLikeTitle/getLikeContent/getCommentTitle/getCommentContent} 逐字一致。
 *
 * <p>这类「文案」改动最容易在重构中悄悄丢失却没人发现：编译通过、接口正常、
 * 单测（如果只测结构）也通过，只有用户看到的消息变了。
 */
@DisplayName("通知策略的文案渲染")
class NotificationHandlerTextTest {

    private static final String POST_TITLE = "橘猫找家";
    private static final String ACTIVITY_TITLE = "周末流浪猫救助";

    private static NotificationMessage comment(
            BizTargetType targetType, String title, String content) {
        return new CommentNotificationHandler()
                .build(
                        new CommentNotificationContext(
                                1L, 2L, targetType, title, content, 100L, "/pet/100?commentId=7"));
    }

    private static NotificationMessage like(BizTargetType targetType, String title) {
        return new LikeNotificationHandler()
                .build(new LikeNotificationContext(1L, 2L, targetType, title, 100L, "/pet/100"));
    }

    @Nested
    @DisplayName("评论通知")
    class CommentTexts {

        @Test
        @DisplayName("评论帖子")
        void onPetPost() {
            NotificationMessage message = comment(BizTargetType.PET_POST, POST_TITLE, "好可爱");

            assertThat(message.type()).isEqualTo(MessageType.COMMENT);
            assertThat(message.title()).isEqualTo("有人评论了你的帖子");
            assertThat(message.content()).isEqualTo("评论了你的帖子《橘猫找家》：好可爱");
        }

        @Test
        @DisplayName("评论活动")
        void onActivity() {
            NotificationMessage message =
                    comment(BizTargetType.PET_ACTIVITY, ACTIVITY_TITLE, "报名了");

            assertThat(message.title()).isEqualTo("有人评论了你的活动");
            assertThat(message.content()).isEqualTo("评论了你的活动《周末流浪猫救助》：报名了");
        }

        @Test
        @DisplayName("回复某条评论落到通用分支 —— 与老实现的 default 行为一致")
        void onCommentReply() {
            NotificationMessage message = comment(BizTargetType.PET_COMMENT, null, "回复了你的评论：同问");

            assertThat(message.title()).isEqualTo("有人评论了你");
            assertThat(message.content())
                    .as("回复场景不套用「评论了你的帖子《…》」模板，直接用调用方拼好的原文")
                    .isEqualTo("回复了你的评论：同问");
        }

        @Test
        @DisplayName("日常动态同样落到通用分支")
        void onDaily() {
            NotificationMessage message = comment(BizTargetType.DAILY, "今天遛狗", "照片真好看");

            assertThat(message.title()).isEqualTo("有人评论了你");
            assertThat(message.content()).isEqualTo("照片真好看");
        }

        @Test
        @DisplayName("正文超过 100 字截断并加省略号 —— 与老实现阈值一致")
        void truncatesLongContent() {
            String longContent = "啊".repeat(150);

            NotificationMessage message = comment(BizTargetType.PET_POST, POST_TITLE, longContent);

            assertThat(message.content()).hasSize(103);
            assertThat(message.content()).endsWith("...");
        }

        @Test
        @DisplayName("拼接后恰好 100 字不截断（边界）—— 截断作用在完整文案上，不是原始输入上")
        void keepsExactlyHundredChars() {
            String prefix = "评论了你的帖子《" + POST_TITLE + "》：";
            String filler = "啊".repeat(100 - prefix.length());

            NotificationMessage message = comment(BizTargetType.PET_POST, POST_TITLE, filler);

            assertThat(message.content()).isEqualTo(prefix + filler).hasSize(100);
        }

        @Test
        @DisplayName("拼接后 101 字即触发截断 —— 阈值是完整文案而非正文")
        void truncatesAtAssembledLengthNotRawInput() {
            String prefix = "评论了你的帖子《" + POST_TITLE + "》：";
            String filler = "啊".repeat(101 - prefix.length());

            NotificationMessage message = comment(BizTargetType.PET_POST, POST_TITLE, filler);

            assertThat(message.content()).hasSize(103).endsWith("...");
        }

        @Test
        @DisplayName("保留业务 ID 与链接，避免通知点进去跳错页")
        void carriesBusinessIdAndLink() {
            NotificationMessage message = comment(BizTargetType.PET_POST, POST_TITLE, "hi");

            assertThat(message.businessId()).isEqualTo(100L);
            assertThat(message.link()).isEqualTo("/pet/100?commentId=7");
            assertThat(message.userId()).isEqualTo(1L);
            assertThat(message.senderId()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("点赞通知")
    class LikeTexts {

        @Test
        @DisplayName("点赞帖子")
        void onPetPost() {
            NotificationMessage message = like(BizTargetType.PET_POST, POST_TITLE);

            assertThat(message.type()).isEqualTo(MessageType.LIKE);
            assertThat(message.title()).isEqualTo("有人点赞了你的帖子");
            assertThat(message.content()).isEqualTo("点赞了你的帖子《橘猫找家》");
        }

        @Test
        @DisplayName("点赞评论不重复标题内容")
        void onPetComment() {
            NotificationMessage message = like(BizTargetType.PET_COMMENT, POST_TITLE);

            assertThat(message.title()).isEqualTo("有人点赞了你的评论");
            assertThat(message.content()).isEqualTo("点赞了你的评论");
        }

        @Test
        @DisplayName("点赞活动")
        void onActivity() {
            NotificationMessage message = like(BizTargetType.PET_ACTIVITY, ACTIVITY_TITLE);

            assertThat(message.title()).isEqualTo("有人点赞了你的活动");
            assertThat(message.content()).isEqualTo("点赞了你的活动《周末流浪猫救助》");
        }

        @Test
        @DisplayName("其它目标类型走兜底文案")
        void onOtherTarget() {
            NotificationMessage message = like(BizTargetType.DAILY, POST_TITLE);

            assertThat(message.title()).isEqualTo("有人点赞了");
            assertThat(message.content()).isEqualTo("点赞了《橘猫找家》");
        }
    }
}

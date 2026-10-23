package com.hongjie.pms.refactor.extension.notify;

import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.common.enums.MessageType;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.refactor.extension.notify.handler.CommentNotificationHandler;
import com.hongjie.pms.refactor.extension.notify.handler.LikeNotificationHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证「按消息类型分发策略 + 模板方法投递」这条链路。
 *
 * <p>用一个记录型 {@code NotificationSender} 替代真实投递，因此这里完全不依赖
 * MySQL / RocketMQ —— 这正是把投递做成端口的好处：通用层可以独立验证。
 */
@DisplayName("NotificationFacade 策略分发与投递")
class NotificationFacadeTest {

    private final List<NotificationMessage> sent = new ArrayList<>();

    private final NotificationFacade facade = new NotificationFacade(
            new NotificationHandlerRegistry(List.of(
                    new CommentNotificationHandler(),
                    new LikeNotificationHandler())),
            sent::add);

    @Test
    @DisplayName("按消息类型定位到对应策略并投递一条通知")
    void dispatchesByMessageType() {
        facade.notify(MessageType.COMMENT, new CommentNotificationContext(
                1L, 2L, BizTargetType.PET_POST, "橘猫找家", "好可爱", 100L, "/pet/100"));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).title()).isEqualTo("有人评论了你的帖子");
    }

    @Test
    @DisplayName("不同消息类型走各自策略，互不干扰")
    void routesToDistinctStrategies() {
        facade.notify(MessageType.COMMENT, new CommentNotificationContext(
                1L, 2L, BizTargetType.PET_POST, "橘猫找家", "好可爱", 100L, "/pet/100"));
        facade.notify(MessageType.LIKE, new LikeNotificationContext(
                1L, 2L, BizTargetType.PET_POST, "橘猫找家", 100L, "/pet/100"));

        assertThat(sent).hasSize(2);
        assertThat(sent).extracting(NotificationMessage::title)
                .containsExactly("有人评论了你的帖子", "有人点赞了你的帖子");
        assertThat(sent).extracting(NotificationMessage::type)
                .containsExactly(MessageType.COMMENT, MessageType.LIKE);
    }

    @Test
    @DisplayName("未注册的消息类型明确报错，而不是静默丢弃通知")
    void unknownTypeFailsLoudly() {
        assertThatThrownBy(() -> facade.notify(MessageType.FOLLOW, new CommentNotificationContext(
                1L, 2L, BizTargetType.PET_POST, "t", "c", 100L, "/pet/100")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未注册的 通知处理器 类型");

        assertThat(sent).as("失败时不应有任何投递").isEmpty();
    }

    @Test
    @DisplayName("同一消息类型注册两个策略时启动即失败")
    void duplicateHandlerFailsFast() {
        assertThatThrownBy(() -> new NotificationHandlerRegistry(List.of(
                new CommentNotificationHandler(),
                new CommentNotificationHandler())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("策略键重复");
    }

    @Test
    @DisplayName("策略数量与消息类型一一对应，注册表规模可断言")
    void registrySizeReflectsRegisteredHandlers() {
        NotificationHandlerRegistry registry = new NotificationHandlerRegistry(List.of(
                new CommentNotificationHandler(),
                new LikeNotificationHandler()));

        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.contains(MessageType.COMMENT)).isTrue();
        assertThat(registry.contains(MessageType.FOLLOW)).isFalse();
    }
}

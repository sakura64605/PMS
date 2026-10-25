package com.hongjie.pms.refactor.extension.notify;
import com.hongjie.pms.refactor.message.api.MessageService;

/**
 * 通知投递端口 —— 依赖倒置的那一半。
 *
 * <p>{@code refactor.extension} 是业务无关的通用扩展层，不应该认识
 * {@code UserMessage} 表、{@code MessageService} 或 RocketMQ。所以这里只声明
 * 「把一条渲染好的通知送出去」这个能力，由业务侧的适配器去实现
 * （见 {@code refactor.message.NotificationSenderAdapter}）。
 *
 * <p>这样做的好处是通用层可以独立测试：给它一个假的 {@code NotificationSender}，
 * 就能验证「策略分发 + 文案渲染」这条链路，完全不需要起数据库和消息队列。
 */
public interface NotificationSender {

    void send(NotificationMessage message);
}

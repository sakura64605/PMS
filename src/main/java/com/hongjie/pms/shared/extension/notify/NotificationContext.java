package com.hongjie.pms.shared.extension.notify;

/**
 * 通知上下文 —— 各业务场景构造通知文案所需的原始素材。
 *
 * <p>老的 {@code MessageService} 用了 20 个 {@code sendXxxNotification} 方法，
 * 每个方法都是「一长串位置参数 + 内联文案拼接」，调用点极易传错顺序
 * （例如把接收者和发送者写反，编译器不会报错）。
 * 改用具体的上下文 record 后，字段有名字、类型有校验，接错会立刻编译失败。
 *
 * <p>本接口只放<b>所有通知都有</b>的三个字段；各场景的特有素材由实现类补充。
 */
public interface NotificationContext {

    /** 接收者 */
    Long receiverId();

    /** 触发者；系统通知可为 null */
    Long senderId();

    /** 关联业务对象 ID，便于前端跳转与去重 */
    Long businessId();
}

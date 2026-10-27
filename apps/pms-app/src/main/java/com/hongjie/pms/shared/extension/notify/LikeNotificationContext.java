package com.hongjie.pms.shared.extension.notify;

import com.hongjie.pms.shared.core.enums.BizTargetType;

/**
 * 点赞通知的上下文。
 *
 * <p>目标类型用 {@link BizTargetType} 枚举而非字符串，带来一个实际收益：
 * 老实现 {@code MessageService.getLikeTitle} 里有个 {@code case "系统通知"} 分支，
 * 而 {@code targetType} 实际只会是那几种互动目标 —— 那是个永远走不到、也永远测不到的死分支。
 * 换成枚举后，这种「非法取值」在类型上就不可表达了，无需再写防御分支。
 */
public record LikeNotificationContext(
        Long receiverId,
        Long senderId,
        BizTargetType targetType,
        String targetTitle,
        Long businessId,
        String link)
        implements NotificationContext {}

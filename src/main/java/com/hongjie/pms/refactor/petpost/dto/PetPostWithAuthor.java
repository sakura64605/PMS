package com.hongjie.pms.refactor.petpost.dto;

import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.refactor.petpost.entity.PmsPetPost;

/**
 * 「帖子 + 已装配好的关联数据」的中间值对象。
 *
 * <p>存在的理由是让转换器保持<b>纯函数</b>（只依赖入参）：帖子的作者、当前用户是否
 * 收藏/点赞都来自别的表。与其把转换器做成有状态、或把装配逻辑拆到 Service 与
 * Converter 两处，不如先批量查好、组装成本对象，再交给转换器做纯粹的字段映射。
 *
 * <p>用 record 而非 Lombok 类：它是短生命周期的内部流转对象，不需要可变性。
 * 作者可能为 null（用户已注销、数据不一致），因此不做非空约束。
 *
 * <p><b>收藏/点赞态为什么用包装类型 {@code Boolean} 而非 {@code boolean}</b>：
 * 老实现只在<b>已登录</b>时才设置这两个字段，匿名访问时它们是 {@code null}。
 * 若这里用原始类型，匿名场景就会变成 {@code false} —— 虽然前端的
 * {@code if (isLiked)} 对两者结果相同，但那是"顺手改掉了一个对外可见的返回值"，
 * 属于重构不该做的事。用 {@code null} 表达「未计算」，与老实现逐字段一致。
 */
public record PetPostWithAuthor(
        PmsPetPost post,
        UserSimpleDto author,
        Boolean favoritedByViewer,
        Boolean likedByViewer) {
}

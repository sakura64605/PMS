package com.hongjie.pms.shared.extension.target;

import com.hongjie.pms.shared.core.enums.BizTargetType;

import java.util.List;
import java.util.Map;

/**
 * 各表历史上使用过的 {@code target_type} 字面量方言。
 *
 * <p>同一个「目标类型」概念，三张表存了三套不同的值（见 {@link BizTargetType} 的类注释）。
 * 老表禁改、老后台按老字面量过滤，所以新代码写入时必须<b>按表还原成该表的方言</b>，
 * 否则 v2 产生的数据在老列表里查不到。
 */
public enum LegacyDialect {

    /** {@code comment.target_type} / {@code like_record.target_type} / {@code favorite_record.target_type} */
    INTERACTION(Map.of(
            BizTargetType.PET_POST, List.of("pet_post"),
            BizTargetType.PET_ACTIVITY, List.of("pet_activity"),
            BizTargetType.PET_COMMENT, List.of("pet_comment"),
            BizTargetType.DAILY, List.of("daily"))),

    /** {@code report_record.target_type} */
    REPORT(Map.of(
            BizTargetType.PET_POST, List.of("pet"),
            BizTargetType.PET_ACTIVITY, List.of("activity"),
            BizTargetType.PET_COMMENT, List.of("comment"),
            BizTargetType.USER, List.of("user"))),

    /**
     * {@code audit_record.target_type} —— 这套方言<b>不可逆</b>。
     *
     * <p>它按帖子的业务类型细分：领养帖写 {@code adopt}、救助帖写 {@code help}，
     * 也就是说「宠物帖子」这一种目标类型在这里被拆成了两个字面量。
     * 从 {@code adopt} 能唯一还原出「这是一条宠物帖子」，但反过来
     * 「宠物帖子」无法唯一决定该写 {@code adopt} 还是 {@code help} ——
     * 必须结合 {@code pet_post.type}。这正是防腐层存在的意义：
     * 把这种历史造成的不可逆性<b>显式建模</b>，而不是让它变成运行期的脏数据。
     */
    AUDIT(Map.of(
            BizTargetType.PET_POST, List.of("adopt", "help"),
            BizTargetType.PET_ACTIVITY, List.of("activity")));

    private final Map<BizTargetType, List<String>> canonicalToDialect;

    LegacyDialect(Map<BizTargetType, List<String>> canonicalToDialect) {
        this.canonicalToDialect = canonicalToDialect;
    }

    List<String> literalsFor(BizTargetType type) {
        return canonicalToDialect.getOrDefault(type, List.of(type.getCode()));
    }

    boolean supports(BizTargetType type) {
        return canonicalToDialect.containsKey(type);
    }
}

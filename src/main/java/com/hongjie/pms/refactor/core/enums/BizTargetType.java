package com.hongjie.pms.refactor.core.enums;

import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 多态互动目标类型的唯一真源（防腐层的读入口）。
 *
 * <p><b>背景</b>：同一个「目标类型」概念在历史上出现了三套互相冲突的字面量，散落在不同表里：
 * <ul>
 *   <li>{@code comment.target_type} / {@code like_record.target_type} / {@code favorite_record.target_type}
 *       —— {@code pet_post}、{@code pet_activity}</li>
 *   <li>{@code report_record.target_type} —— {@code pet}、{@code activity}、{@code comment}、{@code user}</li>
 *   <li>{@code audit_record.target_type} —— {@code adopt}、{@code help}、{@code activity}
 *       （按宠物的领养/救助类型再细分）</li>
 * </ul>
 * 此外代码里还有常量类 {@code CommentLikeTypes} 的第四套取值。
 *
 * <p><b>兼容策略 —— 读宽松、写规范</b>：
 * <ul>
 *   <li>读：{@link #resolve(String)} 的大小写不敏感别名表收录上述全部历史取值，老接口传任何历史字面量都能解析。</li>
 *   <li>写：新数据统一写 {@link #getCode()}，由 {@code TargetTypeCodec} 负责按表还原成该表的历史方言。</li>
 * </ul>
 *
 * <p><b>注意</b>：{@code user_message.type}（LIKE/COMMENT/FOLLOW…）是<b>消息类型轴</b>，
 * 与目标类型轴正交，<b>不属于</b>本枚举，见 {@code common.enums.MessageType}。
 *
 * @see com.hongjie.pms.refactor.extension.target.TargetTypeCodec
 */
public enum BizTargetType implements CodableEnum<String> {

    PET_POST("pet_post", "宠物帖子", List.of("pet_post", "pet", "adopt", "help")),
    PET_ACTIVITY("pet_activity", "活动", List.of("pet_activity", "activity", "pet_event")),
    PET_COMMENT("pet_comment", "评论", List.of("pet_comment", "comment", "comment_reply")),
    DAILY("daily", "日常动态", List.of("daily", "daily_post")),
    USER("user", "用户", List.of("user"));

    private final String code;
    private final String desc;
    private final List<String> aliases;

    /** 别名（小写）-> 枚举。别名冲突在类加载期即抛异常，属于启动即失败的快速失败设计。 */
    private static final Map<String, BizTargetType> LOOKUP = buildLookup();

    BizTargetType(String code, String desc, List<String> aliases) {
        this.code = code;
        this.desc = desc;
        this.aliases = aliases;
    }

    private static Map<String, BizTargetType> buildLookup() {
        Map<String, BizTargetType> lookup = new HashMap<>();
        for (BizTargetType type : values()) {
            for (String alias : type.aliases) {
                String key = alias.toLowerCase(Locale.ROOT);
                BizTargetType previous = lookup.put(key, type);
                if (previous != null) {
                    throw new IllegalStateException(
                            "BizTargetType 别名冲突: \"" + key + "\" 同时属于 " + previous + " 与 " + type);
                }
            }
        }
        return Map.copyOf(lookup);
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    /**
     * 宽松解析：兼容全部历史方言与大小写。
     *
     * @throws BusinessException 无法识别的类型
     */
    public static BizTargetType resolve(String raw) {
        return tryResolve(raw).orElseThrow(() -> new BusinessException(
                ErrorCode.PARAM_ERROR, "无效的目标类型: " + raw));
    }

    /** 宽松解析，无法识别时返回空 */
    public static Optional<BizTargetType> tryResolve(String raw) {
        return raw == null ? Optional.empty() : Optional.ofNullable(LOOKUP.get(raw.trim().toLowerCase(Locale.ROOT)));
    }

    /** 该枚举认可的全部字面量（含规范码），按表还原方言时使用 */
    public List<String> getAliases() {
        return aliases;
    }
}

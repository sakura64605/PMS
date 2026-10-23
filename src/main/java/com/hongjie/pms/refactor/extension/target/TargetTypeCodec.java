package com.hongjie.pms.refactor.extension.target;

import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.refactor.core.enums.biz.PetPostType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 目标类型的双向编解码 —— 防腐层（Anti-Corruption Layer）。
 *
 * <p>隔离「历史脏取值」与「新代码的规范取值」，使新代码只面对 {@link BizTargetType}，
 * 而历史方言的混乱被限制在本类内部。
 *
 * <p><b>读宽松、写规范</b>：
 * <ul>
 *   <li>{@link #read} —— 三套历史方言、大小写混写全部接受；</li>
 *   <li>{@link #write} —— 按目标表还原成该表认得的历史字面量，保证 v2 写入的数据
 *       老后台仍能过滤到。</li>
 * </ul>
 *
 * <p>本类不重写任何历史数据 —— 老表禁改，一致性靠读写两侧的翻译来维持。
 */
@Component
public class TargetTypeCodec {

    /** 宽容读取：任意历史方言 -> 规范枚举 */
    public BizTargetType read(String raw) {
        return BizTargetType.resolve(raw);
    }

    public Optional<BizTargetType> tryRead(String raw) {
        return BizTargetType.tryResolve(raw);
    }

    /** 规范枚举 -> 规范码（写入「没有历史包袱」的新表时用） */
    public String writeCanonical(BizTargetType type) {
        return type.getCode();
    }

    /**
     * 规范枚举 -> 指定表的历史字面量。
     *
     * @throws IllegalStateException 该方言下此类型对应多个字面量（见 {@link LegacyDialect#AUDIT}），
     *                               必须改用带业务类型的三参重载
     */
    public String write(BizTargetType type, LegacyDialect dialect) {
        List<String> literals = literals(type, dialect);
        if (literals.size() > 1) {
            throw new IllegalStateException(String.format(
                    "%s 方言下 %s 对应多个字面量 %s，需结合业务类型判定，请使用三参 write 重载",
                    dialect, type, literals));
        }
        return literals.get(0);
    }

    /**
     * 规范枚举 -> 指定表的历史字面量，携带业务类型以消歧。
     *
     * <p>当前用于审计方言：宠物帖子在 {@code audit_record} 中按领养/救助被拆成
     * {@code adopt} / {@code help} 两个字面量。
     */
    public String write(BizTargetType type, LegacyDialect dialect, PetPostType petPostType) {
        List<String> literals = literals(type, dialect);
        if (literals.size() == 1) {
            return literals.get(0);
        }
        if (type == BizTargetType.PET_POST && petPostType != null) {
            String wanted = petPostType == PetPostType.ADOPT ? "adopt" : "help";
            if (literals.contains(wanted)) {
                return wanted;
            }
        }
        throw new IllegalStateException(String.format(
                "无法为 %s / %s / %s 确定 %s 方言字面量", type, petPostType, literals, dialect));
    }

    /** 该方言下此类型认可的全部字面量，用于拼查询条件（如 {@code target_type IN (...)}） */
    public List<String> acceptedValues(BizTargetType type, LegacyDialect dialect) {
        return literals(type, dialect);
    }

    private List<String> literals(BizTargetType type, LegacyDialect dialect) {
        if (!dialect.supports(type)) {
            throw new IllegalStateException(
                    String.format("%s 方言不支持目标类型 %s", dialect, type));
        }
        return dialect.literalsFor(type);
    }
}

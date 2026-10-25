package com.hongjie.pms.shared.core.enums;

import com.hongjie.pms.platform.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BizTargetType 多态目标类型 —— 历史方言兼容")
class BizTargetTypeTest {

    @ParameterizedTest(name = "历史方言 \"{0}\" 应解析为 {1}")
    @CsvSource({
            // 规范码
            "pet_post,      PET_POST",
            "pet_activity,  PET_ACTIVITY",
            "pet_comment,   PET_COMMENT",
            "daily,         DAILY",
            "user,          USER",
            // comment / like / favorite 表的历史方言（与规范码一致，但确认无遗漏）
            "daily_post,    DAILY",
            "comment_reply, PET_COMMENT",
            // report_record 表的方言：pet / activity / comment / user
            "pet,           PET_POST",
            "activity,      PET_ACTIVITY",
            "comment,       PET_COMMENT",
            // audit_record 表的方言：adopt / help（按帖子业务类型细分）
            "adopt,         PET_POST",
            "help,          PET_POST",
            // 代码常量类 CommentLikeTypes 的取值
            "pet_event,     PET_ACTIVITY",
    })
    @DisplayName("读宽松：三套历史方言全部可解析")
    void resolveAcceptsAllHistoricalDialects(String raw, BizTargetType expected) {
        assertThat(BizTargetType.resolve(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "大小写混写 \"{0}\" 也应可解析")
    @ValueSource(strings = {"PET_POST", "Pet_Post", "PET_ACTIVITY", "ADOPT", "  pet_post  "})
    @DisplayName("大小写与首尾空白不敏感")
    void resolveIsCaseAndWhitespaceInsensitive(String raw) {
        assertThat(BizTargetType.tryResolve(raw)).isPresent();
    }

    @Test
    @DisplayName("写规范：getCode 恒为规范码，而非历史方言")
    void canonicalCodeIsStable() {
        assertThat(BizTargetType.resolve("adopt").getCode()).isEqualTo("pet_post");
        assertThat(BizTargetType.resolve("pet").getCode()).isEqualTo("pet_post");
        assertThat(BizTargetType.resolve("comment").getCode()).isEqualTo("pet_comment");
    }

    @Test
    @DisplayName("无法识别的类型抛参数错误")
    void unknownTypeThrows() {
        assertThatThrownBy(() -> BizTargetType.resolve("not_a_type"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的目标类型");
    }

    @Test
    @DisplayName("null 与非空字符串分别返回空与有值")
    void tryResolveHandlesNull() {
        assertThat(BizTargetType.tryResolve(null)).isEmpty();
        assertThat(BizTargetType.tryResolve("pet_post")).isPresent();
    }

    @Test
    @DisplayName("别名表全局唯一 —— 否则类加载期就该失败")
    void aliasesAreGloballyUnique() {
        Set<String> seen = new HashSet<>();
        for (BizTargetType type : BizTargetType.values()) {
            for (String alias : type.getAliases()) {
                assertThat(seen.add(alias))
                        .as("别名 %s 在枚举内重复", alias)
                        .isTrue();
            }
        }
        assertThat(seen).hasSizeGreaterThanOrEqualTo(13);
    }

    @Test
    @DisplayName("每个枚举自身的规范码必须在其别名列表内")
    void canonicalCodeIsAlwaysAnAlias() {
        for (BizTargetType type : BizTargetType.values()) {
            assertThat(type.getAliases())
                    .as("%s 的别名列表应包含规范码 %s", type, type.getCode())
                    .contains(type.getCode());
        }
    }
}

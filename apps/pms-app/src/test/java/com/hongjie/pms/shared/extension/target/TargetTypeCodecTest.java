package com.hongjie.pms.shared.extension.target;

import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.shared.core.enums.biz.PetPostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TargetTypeCodec 防腐层 —— 读宽松、写规范")
class TargetTypeCodecTest {

    private final TargetTypeCodec codec = new TargetTypeCodec();

    @Nested
    @DisplayName("读：三套历史方言统一入口")
    class Reading {

        @Test
        @DisplayName("同一语义的三种历史写法解析到同一枚举")
        void allDialectsCollapseToOneEnum() {
            assertThat(codec.read("pet_post")).isEqualTo(BizTargetType.PET_POST);
            assertThat(codec.read("pet")).isEqualTo(BizTargetType.PET_POST);
            assertThat(codec.read("adopt")).isEqualTo(BizTargetType.PET_POST);
            assertThat(codec.read("help")).isEqualTo(BizTargetType.PET_POST);
        }

        @Test
        @DisplayName("交互表方言与规范码一致，确认未漏配")
        void interactionDialectMatchesCanonical() {
            assertThat(codec.read("pet_activity")).isEqualTo(BizTargetType.PET_ACTIVITY);
            assertThat(codec.read("pet_comment")).isEqualTo(BizTargetType.PET_COMMENT);
            assertThat(codec.read("daily")).isEqualTo(BizTargetType.DAILY);
        }
    }

    @Nested
    @DisplayName("写：按表还原历史字面量，保证 v2 数据老后台仍能过滤到")
    class Writing {

        @Test
        @DisplayName("report_record 方言写 pet / activity / comment")
        void reportDialect() {
            assertThat(codec.write(BizTargetType.PET_POST, LegacyDialect.REPORT)).isEqualTo("pet");
            assertThat(codec.write(BizTargetType.PET_ACTIVITY, LegacyDialect.REPORT))
                    .isEqualTo("activity");
            assertThat(codec.write(BizTargetType.PET_COMMENT, LegacyDialect.REPORT))
                    .isEqualTo("comment");
            assertThat(codec.write(BizTargetType.USER, LegacyDialect.REPORT)).isEqualTo("user");
        }

        @Test
        @DisplayName("交互表方言写 pet_post / pet_activity")
        void interactionDialect() {
            assertThat(codec.write(BizTargetType.PET_POST, LegacyDialect.INTERACTION))
                    .isEqualTo("pet_post");
            assertThat(codec.write(BizTargetType.PET_ACTIVITY, LegacyDialect.INTERACTION))
                    .isEqualTo("pet_activity");
            assertThat(codec.write(BizTargetType.DAILY, LegacyDialect.INTERACTION))
                    .isEqualTo("daily");
        }

        @Test
        @DisplayName("活动在审计方言里无歧义，可直接写")
        void auditDialectForActivityIsUnambiguous() {
            assertThat(codec.write(BizTargetType.PET_ACTIVITY, LegacyDialect.AUDIT))
                    .isEqualTo("activity");
        }

        @Test
        @DisplayName("writeCanonical 用于没有历史包袱的新表")
        void canonicalForNewTables() {
            assertThat(codec.writeCanonical(BizTargetType.resolve("pet"))).isEqualTo("pet_post");
            assertThat(codec.writeCanonical(BizTargetType.resolve("comment")))
                    .isEqualTo("pet_comment");
        }
    }

    @Nested
    @DisplayName("审计方言的不可逆性 —— 必须显式建模，而非变成运行期脏数据")
    class AuditDialectAmbiguity {

        @Test
        @DisplayName("宠物帖子在审计方言下有两个字面量，二参 write 拒绝猜测")
        void ambiguousWriteRefusesToGuess() {
            assertThatThrownBy(() -> codec.write(BizTargetType.PET_POST, LegacyDialect.AUDIT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("多个字面量");
        }

        @Test
        @DisplayName("结合 pet_post.type 后消歧：领养写 adopt、救助写 help")
        void resolvesWithPetPostType() {
            assertThat(codec.write(BizTargetType.PET_POST, LegacyDialect.AUDIT, PetPostType.ADOPT))
                    .isEqualTo("adopt");
            assertThat(codec.write(BizTargetType.PET_POST, LegacyDialect.AUDIT, PetPostType.HELP))
                    .isEqualTo("help");
        }

        @Test
        @DisplayName("消歧失败时抛异常而非静默写错值")
        void throwsWhenCannotDisambiguate() {
            assertThatThrownBy(() -> codec.write(BizTargetType.PET_POST, LegacyDialect.AUDIT, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("无法为");
        }

        @Test
        @DisplayName("acceptedValues 给出该方言下全部合法字面量，供 IN 查询使用")
        void acceptedValuesForInQuery() {
            assertThat(codec.acceptedValues(BizTargetType.PET_POST, LegacyDialect.AUDIT))
                    .isEqualTo(List.of("adopt", "help"));
            assertThat(codec.acceptedValues(BizTargetType.PET_POST, LegacyDialect.REPORT))
                    .containsExactly("pet");
        }
    }

    @Nested
    @DisplayName("方言覆盖校验")
    class DialectCoverage {

        @Test
        @DisplayName("方言不支持的类型应明确报错，而不是回落到规范码写出错误数据")
        void unsupportedCombinationThrows() {
            assertThatThrownBy(() -> codec.write(BizTargetType.USER, LegacyDialect.INTERACTION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("不支持目标类型");
        }
    }
}

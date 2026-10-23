package com.hongjie.pms.refactor.core.enums;

import com.hongjie.pms.refactor.core.enums.biz.ActivityStatus;
import com.hongjie.pms.refactor.core.enums.biz.CommentStatus;
import com.hongjie.pms.refactor.core.enums.biz.NoticeStatus;
import com.hongjie.pms.refactor.core.enums.biz.PetPostStatus;
import com.hongjie.pms.refactor.core.enums.biz.SignupStatus;
import com.hongjie.pms.refactor.core.enums.biz.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CodableEnum 统一枚举契约")
class CodableEnumTest {

    @Test
    @DisplayName("of 按业务码解析")
    void ofResolvesByCode() {
        assertThat(CodableEnum.of(PetPostStatus.class, 1)).isEqualTo(PetPostStatus.PUBLISHED);
        assertThat(CodableEnum.of(NoticeStatus.class, 0)).isEqualTo(NoticeStatus.DRAFT);
        assertThat(CodableEnum.of(SignupStatus.class, 4)).isEqualTo(SignupStatus.NO_SHOW);
    }

    @Test
    @DisplayName("of 未匹配返回 null，descOf 返回兜底文案")
    void unmatchedCodeFallsBack() {
        assertThat(CodableEnum.of(PetPostStatus.class, 999)).isNull();
        assertThat(CodableEnum.descOf(PetPostStatus.class, 999, "未知")).isEqualTo("未知");
        assertThat(CodableEnum.descOf(PetPostStatus.class, 1, "未知")).isEqualTo("已发布");
    }

    @Test
    @DisplayName("getValue 与 getCode 一致 —— MyBatis-Plus 依据前者读写数据库")
    void valueMirrorsCode() {
        for (PetPostStatus status : PetPostStatus.values()) {
            assertThat(status.getValue()).isEqualTo(status.getCode());
        }
        for (ActivityStatus status : ActivityStatus.values()) {
            assertThat(status.getValue()).isEqualTo(status.getCode());
        }
    }

    @Test
    @DisplayName("同一数字在不同限界上下文语义相反 —— 这正是不能统一 status 的原因")
    void sameCodeDifferentMeaningAcrossContexts() {
        assertThat(CodableEnum.of(UserStatus.class, 0)).isEqualTo(UserStatus.DISABLED);
        assertThat(CodableEnum.of(NoticeStatus.class, 0)).isEqualTo(NoticeStatus.DRAFT);
        assertThat(CodableEnum.of(CommentStatus.class, 0)).isEqualTo(CommentStatus.DELETED);
        assertThat(CodableEnum.of(PetPostStatus.class, 0)).isEqualTo(PetPostStatus.PENDING_AUDIT);
    }

    @Test
    @DisplayName("枚举内业务码不重复")
    void codesAreUniqueWithinEachEnum() {
        assertThat(PetPostStatus.values()).extracting(PetPostStatus::getCode).doesNotHaveDuplicates();
        assertThat(NoticeStatus.values()).extracting(NoticeStatus::getCode).doesNotHaveDuplicates();
        assertThat(ActivityStatus.values()).extracting(ActivityStatus::getCode).doesNotHaveDuplicates();
        assertThat(SignupStatus.values()).extracting(SignupStatus::getCode).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("领域语义方法可用 —— 替代散落的 status == n 判断")
    void domainPredicatesReplaceMagicNumbers() {
        assertThat(PetPostStatus.PUBLISHED.isVisibleToPublic()).isTrue();
        assertThat(PetPostStatus.PENDING_AUDIT.isVisibleToPublic()).isFalse();
        assertThat(ActivityStatus.SIGNUP_OPEN.acceptsSignup()).isTrue();
        assertThat(SignupStatus.NO_SHOW.isPunishable()).isTrue();
        assertThat(SignupStatus.SIGNED_UP.occupiesSeat()).isTrue();
        assertThat(NoticeStatus.DRAFT.isEditable()).isTrue();
        assertThat(UserStatus.NORMAL.canLogin()).isTrue();
        assertThat(CommentStatus.NORMAL.isVisible()).isTrue();
    }
}

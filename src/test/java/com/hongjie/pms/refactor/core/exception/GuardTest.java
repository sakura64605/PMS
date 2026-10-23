package com.hongjie.pms.refactor.core.exception;

import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Guard 前置条件守卫 —— 收敛重复的「查实体 + 判空 + 抛异常」")
class GuardTest {

    @Test
    @DisplayName("requireFound 返回原实体，便于链式书写")
    void requireFoundReturnsEntity() {
        String entity = Guard.requireFound("present", String.class, 1L);
        assertThat(entity).isEqualTo("present");
    }

    @Test
    @DisplayName("requireFound 为 null 时抛 NOT_FOUND，错误信息含类型与主键")
    void requireFoundThrowsWithContext() {
        assertThatThrownBy(() -> Guard.requireFound(null, String.class, 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("String")
                .hasMessageContaining("42");
    }

    @Test
    @DisplayName("requireFound 支持自定义错误码，如 PET_NOT_FOUND")
    void requireFoundAcceptsCustomErrorCode() {
        assertThatThrownBy(() -> Guard.requireFound(null, ErrorCode.PET_NOT_FOUND, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("宠物信息不存在");
    }

    @Test
    @DisplayName("requireTrue 仅在条件为假时抛")
    void requireTrueChecksCondition() {
        Guard.requireTrue(true, ErrorCode.PARAM_ERROR, "不该抛");

        assertThatThrownBy(() -> Guard.requireTrue(false, ErrorCode.PARAM_ERROR, "条件不成立"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("条件不成立");
    }

    @Test
    @DisplayName("requireNotNull 返回非空值")
    void requireNotNullReturnsValue() {
        assertThat(Guard.requireNotNull("v", ErrorCode.PARAM_ERROR, "不能为空")).isEqualTo("v");
        assertThatThrownBy(() -> Guard.requireNotNull(null, ErrorCode.PARAM_ERROR, "不能为空"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("requireText 把空白串也视为空 —— 老代码只判 null 与 isEmpty，漏了全空格")
    void requireTextRejectsBlank() {
        assertThat(Guard.requireText("ok", ErrorCode.PARAM_ERROR, "不能为空")).isEqualTo("ok");

        assertThatThrownBy(() -> Guard.requireText(null, ErrorCode.PARAM_ERROR, "不能为空"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Guard.requireText("", ErrorCode.PARAM_ERROR, "不能为空"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Guard.requireText("   ", ErrorCode.PARAM_ERROR, "不能为空"))
                .isInstanceOf(BusinessException.class);
    }
}

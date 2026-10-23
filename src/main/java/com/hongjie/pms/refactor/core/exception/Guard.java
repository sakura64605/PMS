package com.hongjie.pms.refactor.core.exception;

import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;

/**
 * 前置条件守卫。
 *
 * <p>老代码里「查实体 → 判空 → 抛 NOT_FOUND」这段三行样板在 {@code NoticeServiceImpl}
 * 一处就重复了 4 次，全项目 {@code throw new BusinessException} 有 151 处。
 * 本类把这类判断收敛成一行，同时统一错误码口径。
 *
 * <p>用静态方法而非注解，是为了不引人额外 AOP 开销 —— 这些判断在热路径上，
 * 一个方法调用的成本远低于一次切面拦截。
 */
public final class Guard {

    private Guard() {
    }

    /**
     * 断言实体存在。
     *
     * @param entity 查询结果
     * @param type   实体类型，仅用于拼装错误信息
     * @param id     查询主键
     * @return 原实体，便于链式书写 {@code Guard.requireFound(mapper.selectById(id), PetPost.class, id)}
     */
    public static <T> T requireFound(T entity, Class<T> type, Object id) {
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    type.getSimpleName() + " 不存在: " + id);
        }
        return entity;
    }

    /** 断言实体存在，自定义错误码（如 PET_NOT_FOUND / ACTIVITY_NOT_FOUND） */
    public static <T> T requireFound(T entity, ErrorCode errorCode, Object id) {
        if (entity == null) {
            throw new BusinessException(errorCode, errorCode.getMessage() + ": " + id);
        }
        return entity;
    }

    /** 断言条件成立 */
    public static void requireTrue(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new BusinessException(errorCode, message);
        }
    }

    /** 断言非空 */
    public static <T> T requireNotNull(T value, ErrorCode errorCode, String message) {
        if (value == null) {
            throw new BusinessException(errorCode, message);
        }
        return value;
    }

    /** 断言字符串非空白 */
    public static String requireText(String value, ErrorCode errorCode, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(errorCode, message);
        }
        return value;
    }
}

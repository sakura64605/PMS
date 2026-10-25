package com.hongjie.pms.shared.core.service;

import com.hongjie.pms.shared.core.page.PageQuery;
import com.hongjie.pms.shared.core.page.PageResult;

/**
 * 应用层服务契约。
 *
 * <p><b>与 MyBatis-Plus {@code IService} 的分工</b>：{@code IService} 是 ORM 层契约
 * （save / update / getById / page…），本接口是应用层契约，只声明「业务上真正反复出现」
 * 的两件事。不重复声明 CRUD，避免与 ORM 层职责重叠。
 *
 * <p><b>关于泛型上界</b>：这里刻意<b>不加</b> {@code E extends Identifiable} 这类上界。
 * Java 是名义类型系统，「实体有 getId()」不等于「实体 implements Identifiable」——
 * 老项目 31 个实体没有一个声明了该接口，加上界就等于强迫逐个改动老实体，
 * 而老实体一旦 import {@code refactor.core}，就破坏了「删除新代码后老代码仍能独立编译」
 * 这条回滚底线。既然当前没有任何泛型算法真的需要调用 {@code getId()}，
 * 就不该为想象中的需求付出这个代价。
 *
 * <p>{@code Identifiable} / {@code BaseEntity} 保留给<b>新增</b>实体使用，
 * 它们带来的价值是审计字段与逻辑删除的统一，而不是充当泛型上界。
 *
 * @param <E> 实体类型
 */
public interface BaseService<E> {

    /**
     * 按主键查询，不存在即抛 NOT_FOUND。
     *
     * <p>替代老代码里重复出现的「查实体 + 判空 + 抛异常」三行样板。
     */
    E requireById(Long id);

    /** 按统一分页入参分页查询 */
    PageResult<E> page(PageQuery query);
}

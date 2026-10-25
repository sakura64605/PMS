package com.hongjie.pms.shared.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hongjie.pms.shared.core.exception.Guard;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.shared.core.page.PageBridge;
import com.hongjie.pms.shared.core.page.PageQuery;
import com.hongjie.pms.shared.core.page.PageResult;

/**
 * 单聚合服务基类：复用 MyBatis-Plus 的成熟实现，只补齐项目专属语义。
 *
 * <p><b>为什么继承 {@code ServiceImpl} 而不是组合 {@code BaseMapper}</b>：
 * 项目 ORM 已全线绑定 MyBatis-Plus（35 个 Mapper 继承 {@code BaseMapper}），
 * 耦合代价本就已付出；若改为组合 {@code BaseMapper}，需要重写
 * save / update / remove / getById / page 约 200 行样板，收益为负。
 *
 * <p><b>不适用于跨聚合编排</b>：像评论创建要同时操作 Comment、PetPost、Activity、User 的服务，
 * <b>不应</b>继承本类（本类绑定单一实体），而应是普通 {@code @Service} 注入多个
 * Mapper / 策略处理器。为「统一」把跨聚合服务塞进单实体泛型只会制造扭曲。
 *
 * @param <M> Mapper 类型
 * @param <E> 实体类型；刻意不加 {@code Identifiable} 之类上界，理由见 {@link BaseService}
 */
public abstract class AbstractBaseService<M extends BaseMapper<E>, E>
        extends ServiceImpl<M, E> implements BaseService<E> {

    @Override
    public E requireById(Long id) {
        return Guard.requireFound(getById(id), notFoundErrorCode(), id);
    }

    /**
     * 「实体不存在」时使用的错误码，默认 {@code NOT_FOUND}。
     *
     * <p>子类可覆写为领域专属错误码（如 {@code PET_NOT_FOUND}），让前端能按错误码区分类别，
     * 而不是一律拿到 404 再去猜是哪种对象不存在。
     */
    protected ErrorCode notFoundErrorCode() {
        return ErrorCode.NOT_FOUND;
    }

    @Override
    public PageResult<E> page(PageQuery query) {
        return PageBridge.of(page(query.toPage(), baseQuery()));
    }

    /**
     * 实体类型，用于异常信息与 MyBatis-Plus 内部反射。
     *
     * <p><b>必须声明为 public</b>：{@code IService} 本就定义了同名的 public 方法
     * （{@code ServiceImpl} 通过反射解析泛型给出实现）。这里刻意重新声明为抽象，
     * 是为了让子类<b>显式</b>给出实体类型，不依赖框架对泛型的反射推断 ——
     * 在 {@code AbstractBaseService} 这一层，泛型参数 E 尚未绑定，
     * 框架的推断在此处会退化成解析不出具体类型。
     */
    @Override
    public abstract Class<E> getEntityClass();

    /**
     * 该聚合的基础查询条件（如逻辑删除过滤、可见性过滤）。
     *
     * <p>刻意命名为 {@code baseQuery} 而非 {@code lambdaQuery} ——
     * {@code ServiceImpl} 已有一个返回 {@code LambdaQueryChainWrapper} 的 {@code lambdaQuery()}，
     * 同名会因返回类型不兼容而编译失败。
     */
    protected abstract LambdaQueryWrapper<E> baseQuery();
}

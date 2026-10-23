package com.hongjie.pms.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.hongjie.pms.common.base.core.UpdateTimeContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 按字段名填充 createTime / updateTime（老实现，保持不变）。
 *
 * <p>只认字段名 "createTime" / "updateTime"，因此列名不同的实体（如 AI 模块的
 * created_at / updated_at）填充不生效。新的 {@code CoreMetaObjectHandler} 用注解驱动
 * 解决了这一点，且是它的行为超集。
 *
 * <p><b>本类未做任何行为改动</b>，只加了一行条件注解：默认（未配置
 * {@code pms.orm.meta-handler}）时本类生效，与改造前完全一致；
 * 配置为 {@code core} 时才切换到新实现。回滚只需删掉那行配置。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pms.orm.meta-handler", havingValue = "legacy", matchIfMissing = true)
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void updateFill(MetaObject metaObject) {
        // 判断是否需要跳过更新时间
        if (UpdateTimeContext.shouldSkip()) {
            log.debug("跳过 updateTime 自动填充");
            return;  // 直接返回，不填充
        }

        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        log.debug("更新填充: updateTime");
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        this.setFieldValByName("createTime", LocalDateTime.now(), metaObject);
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }

}

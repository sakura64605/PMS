package com.hongjie.pms.shared.core.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hongjie.pms.platform.base.core.UpdateTimeContext;
import com.hongjie.pms.platform.base.core.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 注解驱动的字段自动填充（新）。
 *
 * <p><b>要解决的问题</b>：老的 {@code MyMetaObjectHandler} 只按<b>字段名</b>
 * {@code "createTime"} / {@code "updateTime"} 填充，因此任何列名或属性名不同的实体都填充失效 ——
 * AI 模块五个实体用 {@code created_at / updated_at}，实际一直没被填充过。
 *
 * <p><b>与老实现的关系：超集，不是替换。</b> 本类在注解驱动之外保留了原有按名填充的行为，
 * 因此在 {@code pms.orm.meta-handler=core} 下切过来，老实体的表现与切换前一致，
 * 同时额外支持任意列名。
 *
 * <p><b>默认不启用</b>：需要显式配置 {@code pms.orm.meta-handler=core}。
 * 不配则 {@code MyMetaObjectHandler} 生效（其 {@code matchIfMissing = true}），行为与改造前完全相同。
 * 这样这个影响全部 31 张表插入的高风险改动，回滚成本只是删掉一行配置。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pms.orm.meta-handler", havingValue = "core")
public class CoreMetaObjectHandler implements MetaObjectHandler {

    /** 老实现按名字填充的两个属性，作为行为兼容的兜底 */
    private static final String LEGACY_CREATE_TIME = "createTime";

    private static final String LEGACY_UPDATE_TIME = "updateTime";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();

        fillAnnotatedFields(metaObject, now, true);

        // 兼容兜底：老实现用 setFieldValByName（非严格，不要求注解），此处保持一致。
        // 这样即使某个老实体的 createTime 没有 @TableField(fill=...)，行为也不变。
        setIfPresent(metaObject, LEGACY_CREATE_TIME, now);
        setIfPresent(metaObject, LEGACY_UPDATE_TIME, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (UpdateTimeContext.shouldSkip()) {
            log.debug("跳过 updateTime 自动填充");
            return;
        }
        // 不额外做按名兜底：老实现此处用的是 strictUpdateFill（要求字段带注解），
        // 注解驱动这一遍已经覆盖了同样的字段集合。
        fillAnnotatedFields(metaObject, LocalDateTime.now(), false);
    }

    /**
     * 遍历实体的 TableInfo，填充所有声明了对应填充时机的字段。
     *
     * <p>取值由 setter 的 Java 类型决定，而非字段名 —— 这是支持任意列名的关键：
     * {@code LocalDateTime} 填当前时间，{@code Long} 填当前登录用户（创建人/更新人）。
     * 取不到值（如 MQ 消费、定时任务场景没有登录上下文）时跳过，不写 null。
     */
    private void fillAnnotatedFields(
            MetaObject metaObject, LocalDateTime now, boolean insertPhase) {
        for (TableFieldInfo field : annotatedFields(metaObject, insertPhase)) {
            String property = field.getProperty();
            if (!metaObject.hasSetter(property)) {
                continue;
            }
            Object value = resolveValue(metaObject, property, now);
            if (value != null) {
                metaObject.setValue(property, value);
            }
        }
    }

    private List<TableFieldInfo> annotatedFields(MetaObject metaObject, boolean insertPhase) {
        Object original = metaObject.getOriginalObject();
        if (original == null) {
            return List.of();
        }
        TableInfo tableInfo = TableInfoHelper.getTableInfo(original.getClass());
        if (tableInfo == null) {
            return List.of();
        }
        List<TableFieldInfo> matched = new ArrayList<>();
        for (TableFieldInfo field : tableInfo.getFieldList()) {
            FieldFill fill = field.getFieldFill();
            if (fill == null || fill == FieldFill.DEFAULT) {
                continue;
            }
            boolean isInsert = fill == FieldFill.INSERT || fill == FieldFill.INSERT_UPDATE;
            boolean isUpdate = fill == FieldFill.UPDATE || fill == FieldFill.INSERT_UPDATE;
            if (insertPhase ? isInsert : isUpdate) {
                matched.add(field);
            }
        }
        return matched;
    }

    private Object resolveValue(MetaObject metaObject, String property, LocalDateTime now) {
        Class<?> type = metaObject.getSetterType(property);
        if (LocalDateTime.class.equals(type)) {
            return now;
        }
        if (Long.class.equals(type) || long.class.equals(type)) {
            return UserContext.getUserId();
        }
        log.debug("字段 {} 的类型 {} 无对应的填充策略，跳过", property, type);
        return null;
    }

    private void setIfPresent(MetaObject metaObject, String property, Object value) {
        if (metaObject.hasSetter(property)) {
            metaObject.setValue(property, value);
        }
    }
}

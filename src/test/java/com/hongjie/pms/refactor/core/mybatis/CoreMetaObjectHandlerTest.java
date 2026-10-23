package com.hongjie.pms.refactor.core.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hongjie.pms.common.base.core.UpdateTimeContext;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import lombok.Data;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖重构中风险最高的一处改动：字段自动填充影响全部 31 张表的插入。
 *
 * <p>本测试用 MP 的 {@code TableInfoHelper} 手工初始化表信息，再直接驱动
 * {@code MetaObjectHandler}，因此不需要 Spring 上下文，也不需要数据库。
 */
@DisplayName("CoreMetaObjectHandler 注解驱动填充")
class CoreMetaObjectHandlerTest {

    private final CoreMetaObjectHandler handler = new CoreMetaObjectHandler();

    /** 老风格：属性名与列名都是驼峰，老实现按名填充也能覆盖 */
    @Data
    @TableName("t_legacy_style")
    static class LegacyStyleEntity {
        @TableId(type = IdType.AUTO)
        private Long id;

        @TableField(value = "create_time", fill = FieldFill.INSERT)
        private LocalDateTime createTime;

        @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updateTime;

        /** 没有任何填充注解，不应被改动 */
        @TableField("title")
        private String title;
    }

    /** 新风格：下划线列名 + 任意属性名 —— 老实现完全覆盖不到 */
    @Data
    @TableName("t_snake_case")
    static class SnakeCaseEntity {
        @TableId(type = IdType.AUTO)
        private Long id;

        @TableField(value = "created_at", fill = FieldFill.INSERT)
        private LocalDateTime createdAt;

        @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updatedAt;

        @TableField(value = "creator", fill = FieldFill.INSERT)
        private Long creator;

        @TableField(value = "updater", fill = FieldFill.INSERT_UPDATE)
        private Long updater;
    }

    @BeforeAll
    static void initTableInfo() {
        initTable(LegacyStyleEntity.class);
        initTable(SnakeCaseEntity.class);
    }

    private static void initTable(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
        UpdateTimeContext.clear();
    }

    private static void login(Long userId) {
        UserContext.setUser(new UserInfo(userId, "tester", 0, "测试用户", null));
    }

    @Test
    @DisplayName("插入时填充 createTime 与 updateTime —— 与老实现行为一致")
    void fillsLegacyAuditFieldsOnInsert() {
        LegacyStyleEntity entity = new LegacyStyleEntity();
        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("插入时填充 created_at / updated_at —— 老实现按字段名填充，做不到这一点")
    void fillsSnakeCaseAuditFieldsOnInsert() {
        SnakeCaseEntity entity = new SnakeCaseEntity();
        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreatedAt())
                .as("AI 模块五个实体用的就是 created_at / updated_at，此前一直未被填充")
                .isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("登录上下文存在时填充创建人 / 更新人")
    void fillsCreatorAndUpdaterWhenLoggedIn() {
        login(99L);
        SnakeCaseEntity entity = new SnakeCaseEntity();
        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreator()).isEqualTo(99L);
        assertThat(entity.getUpdater()).isEqualTo(99L);
    }

    @Test
    @DisplayName("无登录上下文（MQ 消费 / 定时任务）时不写入 null，保持字段原值")
    void leavesOperatorNullWithoutLoginContext() {
        SnakeCaseEntity entity = new SnakeCaseEntity();
        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreator()).isNull();
        assertThat(entity.getUpdater()).isNull();
        assertThat(entity.getCreatedAt()).as("时间戳仍应填充").isNotNull();
    }

    @Test
    @DisplayName("没有填充注解的字段不被触碰")
    void doesNotTouchUnannotatedFields() {
        LegacyStyleEntity entity = new LegacyStyleEntity();
        entity.setTitle("原标题");
        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getTitle()).isEqualTo("原标题");
        assertThat(entity.getId()).isNull();
    }

    @Test
    @DisplayName("更新时刷新 updateTime / updated_at / updater")
    void fillsOnUpdate() {
        login(7L);
        SnakeCaseEntity entity = new SnakeCaseEntity();
        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getUpdater()).isEqualTo(7L);
        assertThat(entity.getCreatedAt()).as("created_at 是 INSERT 时机，更新时不应被填").isNull();
    }

    @Test
    @DisplayName("UpdateTimeContext.skip() 应让更新填充整体跳过 —— 保留老实现的语义")
    void respectsUpdateTimeContextSkip() {
        SnakeCaseEntity entity = new SnakeCaseEntity();
        UpdateTimeContext.skip();

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdatedAt()).isNull();
        assertThat(entity.getUpdater()).isNull();
    }

    @Test
    @DisplayName("skip 被清除后恢复正常填充，避免 ThreadLocal 泄漏到下一个请求")
    void skipIsClearedBetweenRequests() {
        SnakeCaseEntity skipped = new SnakeCaseEntity();
        UpdateTimeContext.skip();
        handler.updateFill(SystemMetaObject.forObject(skipped));

        UpdateTimeContext.clear();
        SnakeCaseEntity normal = new SnakeCaseEntity();
        handler.updateFill(SystemMetaObject.forObject(normal));

        assertThat(skipped.getUpdatedAt()).isNull();
        assertThat(normal.getUpdatedAt()).isNotNull();
    }

    @ParameterizedTest(name = "实体 {0} 的插入填充都不抛异常")
    @ValueSource(classes = {LegacyStyleEntity.class, SnakeCaseEntity.class})
    @DisplayName("两类实体都能安全处理")
    void handlesBothEntityStyles(Class<?> ignored) {
        LegacyStyleEntity legacy = new LegacyStyleEntity();
        SnakeCaseEntity snake = new SnakeCaseEntity();

        handler.insertFill(SystemMetaObject.forObject(legacy));
        handler.insertFill(SystemMetaObject.forObject(snake));

        assertThat(legacy.getCreateTime()).isNotNull();
        assertThat(snake.getCreatedAt()).isNotNull();
    }
}

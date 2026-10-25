package com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hongjie.pms.shared.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/**
 * 宠物帖子（重构后的模型）。
 *
 * <p><b>当前映射的是老表 {@code pet_post}</b> —— 本轮的策略是先重写实现、
 * 不换物理表，因此这里用的是老表的列名（{@code pet_age}、{@code pet_type}）。
 * 之所以仍然建这个类而不是直接用老实体 {@code PetPost}，是因为：
 * <ul>
 *   <li>Java 侧字段名先改对（{@code petBreed} 才是品种、{@code petAgeText} 才是原文），
 *       老实体里叫 {@code petType}／{@code petAge} 的名字与含义不符；</li>
 *   <li>切表时只需改本类上的 {@code @TableName} 与几个 {@code @TableField}，
 *       以及 Mapper 里的表名，Service 逻辑一行不动。</li>
 * </ul>
 *
 * <p><b>切到新表 {@code pms_pet_post} 时要做的三件事</b>：
 * <ol>
 *   <li>本类改 {@code @TableName("pms_pet_post")}，去掉 {@code pet_age}/{@code pet_type}
 *       的列名映射（新表列名与属性名一致），并改为继承 {@code AuditEntity}
 *       以获得 creator/updater/deleted/version；</li>
 *   <li>{@code PmsPetPostMapper} 里的 SQL 表名换成 {@code pms_pet_post}，
 *       并补上 {@code deleted = 0} 条件；</li>
 *   <li>Service 中把「逻辑删除 = status 置 -1」改为操作 {@code deleted} 列
 *       （见 {@code PmsPetPostServiceImpl} 里标注「切表点」的注释）。</li>
 * </ol>
 *
 * <p>继承 {@link BaseEntity} 而非 {@code AuditEntity}：老表只有
 * {@code id / create_time / update_time}，没有 creator/updater/deleted/version。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName(value = "pet_post", autoResultMap = true)
public class PmsPetPost extends BaseEntity {

    private Long userId;

    /** 帖子类型：0-领养 1-救助 */
    private Integer type;

    private String title;

    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    private String petName;

    /** 性别：0-未知 1-公 2-母 */
    private Integer petGender;

    /** 年龄原文，如「3个月」（对应老表列 pet_age） */
    @TableField("pet_age")
    private String petAgeText;

    /** 品种自由文本，如「橘猫」（对应老表列 pet_type，名字与实际含义不符） */
    @TableField("pet_type")
    private String petBreed;

    /** 物种分类ID，见 pet_category 表 */
    private Integer petCategory;

    private String address;

    private String contactPhone;

    private String contactWechat;

    /**
     * 生命周期状态，取值见 {@code PetPostStatus}。
     *
     * <p><b>迁移期特例</b>：老表用 {@code status = -1} 表示「已删除」，把生命周期与
     * 删除态挤在同一列。切表后删除态由 {@code deleted} 承担，本字段只表示生命周期。
     */
    private Integer status;

    /** 审核结果，取值见 {@code AuditStatus} */
    private Integer auditStatus;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer shareCount;
}

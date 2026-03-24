package com.hongjie.pms.modules.petpost.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "pet_post", autoResultMap = true)
public class PetPost {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 发布者ID
     */
    private Long userId;

    /**
     * 类型：0-领养 1-救助
     */
    private Integer type;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 图片列表（JSON数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /**
     * 宠物性别：0-未知 1-公 2-母
     */
    private Integer petGender;

    /**
     * 宠物年龄，如：3个月
     */
    private String petAge;

    /**
     * 宠物品种，如：橘猫、金毛
     */
    private String petType;

    /**
     * 宠物名字
     */
    private String petName;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 微信号
     */
    private String contactWechat;

    /**
     * 地址
     */
    private String address;

    /**
     * 状态：0-待审核 1-已发布 2-已领养/已完成 3-已下架
     */
    @Builder.Default
    private Integer status = 0;

    /**
     * 浏览次数
     */
    @Builder.Default
    private Integer viewCount = 0;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer shareCount;
    private Integer commentCount;
    private Integer likeCount;
}

package com.hongjie.pms.modules.petpost.dto.response;

import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PetListResponseDto {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 宠物类型
     */
    private Integer type;

    /**
     * 标题
     */
    private String title;

    /**
     * 宠物名称
     */
    private String petName;

    /**
     * 宠物类型
     */
    private String petType;

    /**
     * 宠物年龄
     */
    private String petAge;

    /**
     * 宠物性别
     */
    private Integer petGender;

    /**
     * 宠物图片
     */
    private List<String> images;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 审核状态
     */
    private Integer auditStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    private UserSimpleDto user;

    private Integer shareCount;
    private Integer commentCount;
    private Integer likeCount;
//    private Boolean isCollected; // 当前用户是否收藏
//    private Boolean isLiked;     // 当前用户是否点赞
}

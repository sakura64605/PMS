package com.hongjie.pms.modules.petpost.dto;

import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PetDetailDto {

    private Long id;
    private Integer type;
    private String title;
    private String content;
    private List<String> images;
    private Integer petGender;
    private String petAge;
    private String petType;
    private String petName;
    private String contactPhone;
    private String contactWechat;
    private String address;
    private Integer viewCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 发布者信息
    private UserSimpleDto user;

    // 互动数据（可选）
    private Integer shareCount;
    private Integer commentCount;
    private Integer likeCount;
    private Boolean isLiked;    // 当前用户是否点赞
    private Boolean isFavorite;// 当前用户是否收藏

}

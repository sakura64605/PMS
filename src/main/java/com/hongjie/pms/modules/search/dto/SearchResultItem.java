package com.hongjie.pms.modules.search.dto;

import com.hongjie.pms.modules.user.dto.UserSimpleDto;
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
public class SearchResultItem {
    private String type;           // daily, activity, pet
    private Long id;               // 业务ID
    private String title;
    private String highlightTitle; // 高亮标题
    private String content;
    private String highlightContent; // 高亮内容
    private List<String> images;
    private String location;
    private UserSimpleDto user;
    private Integer likeCount;
    private Integer commentCount;
    private Integer viewCount;
    private LocalDateTime createTime;
    
    // 日记特有
    private List<String> topics;
    private Boolean isLiked;
    
    // 宠物特有
    private String petName;
    private String petType;
    private String petAge;
    private Integer petGender;
    private String address;
    private Boolean isFavorite;
    
    // 活动特有
    private Integer maxPeople;
    private Integer currentPeople;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer isSignedUp;
}
package com.hongjie.pms.modules.search.infrastructure.search.document;

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


public class UnifiedDoc {

    private String id;  // 格式: {type}_{businessId}

    private String type;  // daily, activity, pet

    private Long businessId;

    private String title;

    private String content;

    private List<String> topics;

    private String location;

    private List<String> images;

    private Long userId;

    private String userName;

    private String userAvatar;

    private Integer likeCount;

    private Integer commentCount;

    private Integer viewCount;

    private Integer status;  // 1-正常 0-删除

    private Integer auditStatus;  // 1-审核通过

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ========== 日记特有字段 ==========

    private String videoUrl;

    // ========== 宠物特有字段 ==========

    private String petName;

    private String petType;

    private String petAge;

    private Integer petGender;

    private String address;

    // ========== 活动特有字段 ==========

    private Integer maxPeople;

    private Integer currentPeople;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
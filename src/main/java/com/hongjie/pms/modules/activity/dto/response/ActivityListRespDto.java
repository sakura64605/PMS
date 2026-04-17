package com.hongjie.pms.modules.activity.dto.response;

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
public class ActivityListRespDto {

    private Long id;

    private UserSimpleDto user;

    private Integer isSignedUp;

    private String title;

    private String content;

    private String images;

    private String location;

    private Integer maxPeople;

    private Integer currentPeople;

    private Integer status;

    private Integer auditStatus;

    private LocalDateTime startTime;
    private LocalDateTime createTime;
    private LocalDateTime endTime;

    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;


}

package com.hongjie.pms.modules.daily.dto;

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
public class DailyPostDto {

    private Long id;
    private String content;
    private List<String> images;
    private String videoUrl;
    private String location;
    private UserSimpleDto user;

    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Boolean isLiked;
    private Boolean isFollowed;

    private List<TopicDto> topics;

    private LocalDateTime createTime;
}
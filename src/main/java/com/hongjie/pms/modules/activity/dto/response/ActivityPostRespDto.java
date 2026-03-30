package com.hongjie.pms.modules.activity.dto.response;

import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ActivityPostRespDto {

    private Long id;

    private Long userId;

    private String title;

    private String content;

    private List<String> images;

    private String location;

    private Integer maxPeople;

    private Integer status;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

}

package com.hongjie.pms.modules.activity.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityListRequestDto {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private Long userId;

    private String keyword;

    private Integer status;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private String orderBy;

    private String order;

    private String location;

}

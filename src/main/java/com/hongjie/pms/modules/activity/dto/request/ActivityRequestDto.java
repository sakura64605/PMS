package com.hongjie.pms.modules.activity.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ActivityRequestDto {
    private Long id;

    private Long userId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多100字")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    private List<String> images;

    @NotBlank(message = "地点不能为空")
    private String location;

    @NotNull(message = "人数限制不能为空")
    @Min(value = 1, message = "人数至少1人")
    private Integer maxPeople;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
}

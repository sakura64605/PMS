package com.hongjie.pms.modules.notice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NoticeRequestDto {
    
    private Long id;
    
    @NotBlank(message = "标题不能为空")
    private String title;
    
    @NotBlank(message = "内容不能为空")
    private String content;
    
    private Integer type = 1;      // 默认系统公告
    private Integer priority = 0;  // 默认普通
    private Integer isTop = 0;     // 默认不置顶

    //定时发布时间（可选，只有定时发布时才需要）
    private LocalDateTime schedulePublishTime;
}
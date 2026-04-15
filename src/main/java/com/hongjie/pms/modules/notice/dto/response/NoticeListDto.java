package com.hongjie.pms.modules.notice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor  // 添加这个
@AllArgsConstructor // 添加这个
public class NoticeListDto {
    private Long id;
    private String title;
    private Integer type;
    private Integer priority;
    private Integer status;
    private Integer isTop;
    private LocalDateTime publishTime;
    private LocalDateTime expireTime;
    private Boolean isRead;        // 当前用户是否已读
}
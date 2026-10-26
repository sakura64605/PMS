package com.hongjie.pms.modules.activity.api.dto.request;

import com.hongjie.pms.shared.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MySignupsRequest extends PageQuery {
    private String keyword;
    private Integer status;
    private Integer signupStatus;
    private String timeRange;
    private String sort = "startTime_asc";
}

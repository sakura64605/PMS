package com.hongjie.pms.modules.activity.dto.request;

import lombok.Data;

@Data
public class SignUpInfoRequest {

    private Long activityId;

    private String realName;

    private String phone;

    private String remark;

}

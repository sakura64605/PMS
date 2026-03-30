package com.hongjie.pms.modules.activity.dto.response;

import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SignUpResponse {

    private Long signupId;        // 报名记录ID
    private UserSimpleDto user;   // 报名人信息
    private String realName;      // 真实姓名
    private String phone;         // 联系电话
    private String remark;        // 备注
    private Integer status;       // 状态：1-已报名 2-已取消 3-已签到 4-爽约
    private LocalDateTime signupTime;  // 报名时间
    private Boolean isCheckedIn;       // 是否已签到

}

package com.hongjie.pms.modules.activity.api.dto.response;

import java.time.LocalDateTime;

public record MySignupResponse(ActivityListRespDto activity, SignUpResponse signup,
                               LocalDateTime checkInTime, boolean available, boolean canCancel) {}

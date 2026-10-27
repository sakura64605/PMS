package com.hongjie.pms.modules.activity.application;

import com.hongjie.pms.modules.activity.api.dto.response.ActivityDetailRespDto;

/** Keep the legacy integer flag and expose the boolean name used by the React client. */
public class ActivityDetailResponse extends ActivityDetailRespDto {
    public boolean getIsLiked() {
        return Integer.valueOf(1).equals(getIsLike());
    }
}

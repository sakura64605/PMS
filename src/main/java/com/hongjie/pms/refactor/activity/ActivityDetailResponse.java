package com.hongjie.pms.refactor.activity;

import com.hongjie.pms.modules.activity.dto.response.ActivityDetailRespDto;

/** Keep the legacy integer flag and expose the boolean name used by the React client. */
public class ActivityDetailResponse extends ActivityDetailRespDto {
    public boolean getIsLiked() { return Integer.valueOf(1).equals(getIsLike()); }
}

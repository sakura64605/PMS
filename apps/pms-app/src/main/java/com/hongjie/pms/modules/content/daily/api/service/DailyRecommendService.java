package com.hongjie.pms.modules.content.daily.api.service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.content.daily.api.dto.DailyPostDto;
import java.util.*;
/** Runtime contract implemented by the migrated service. */
public interface DailyRecommendService {
    IPage<DailyPostDto> recommend(Long userId, Integer pageNum, Integer pageSize);
    void recordAction(Long userId, Long targetId, String actionType);
}

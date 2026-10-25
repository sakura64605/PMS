package com.hongjie.pms.refactor.daily.api;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.daily.dto.DailyPostDto;
import com.hongjie.pms.modules.daily.dto.PublishDailyRequest;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import java.util.*;
/** Runtime contract implemented by the migrated service. */
public interface DailyPostService {
    DailyPostDto publish(PublishDailyRequest request);
    IPage<DailyPostDto> getRecommendFeed(Integer pageNum, Integer pageSize);
    DailyPostDto getDetail(Long id);
    boolean like(Long id);
    void delete(Long id);
    DailyPostDto convertToDto(DailyPost post, Long currentUserId);
    List<DailyPostDto> batchConvertToDto(List<DailyPost> posts, Long currentUserId);
}

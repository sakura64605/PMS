package com.hongjie.pms.modules.search.api.service;
import com.hongjie.pms.modules.search.api.dto.UnifiedSearchResponse;
import java.util.*;
/** Runtime contract implemented by the migrated service. */
public interface UnifiedSearchService {
    UnifiedSearchResponse search(String keyword, Integer pageNum, Integer pageSize,
                                         List<String> types, String sortBy, Long currentUserId);
}

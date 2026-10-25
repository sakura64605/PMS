package com.hongjie.pms.modules.search.application;
import com.hongjie.pms.modules.search.api.dto.SearchResultItem;
import java.util.*;
public interface SearchSource {
    String type();
    Map<Long,SearchResultItem> load(Set<Long> ids,Long actor);
}

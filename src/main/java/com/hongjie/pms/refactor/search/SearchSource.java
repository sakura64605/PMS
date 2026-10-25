package com.hongjie.pms.refactor.search;
import com.hongjie.pms.modules.search.dto.SearchResultItem;
import java.util.*;
public interface SearchSource {
    String type();
    Map<Long,SearchResultItem> load(Set<Long> ids,Long actor);
}

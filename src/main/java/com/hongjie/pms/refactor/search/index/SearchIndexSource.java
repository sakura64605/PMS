package com.hongjie.pms.refactor.search.index;
import com.hongjie.pms.modules.search.document.UnifiedDoc;
import java.util.*;
public interface SearchIndexSource {
    String type();
    String prefix();
    long maxId();
    List<Long> ids(long after,long through);
    Map<Long,UnifiedDoc> load(List<Long> ids);
}

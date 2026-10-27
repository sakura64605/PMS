package com.hongjie.pms.modules.search.application.index;

import com.hongjie.pms.modules.search.infrastructure.search.document.UnifiedDoc;
import java.util.*;

public interface SearchIndexSource {
    String type();

    String prefix();

    long maxId();

    List<Long> ids(long after, long through);

    Map<Long, UnifiedDoc> load(List<Long> ids);
}

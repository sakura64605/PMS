package com.hongjie.pms.modules.search.application.index;
import com.hongjie.pms.modules.search.infrastructure.search.document.UnifiedDoc;
import java.util.List;
public interface SearchIndexStore {
    String ALIAS="pms_unified_search";
    boolean exists(String index);
    List<String> targets();
    void create(String index);
    void switchTo(List<String> previous,String next);
    void write(String index,List<UnifiedDoc> documents);
    void delete(String id);
    void refresh(String index);
}

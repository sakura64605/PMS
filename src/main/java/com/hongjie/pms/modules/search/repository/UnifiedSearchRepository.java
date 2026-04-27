// modules/search/repository/UnifiedSearchRepository.java
package com.hongjie.pms.modules.search.repository;

import com.hongjie.pms.modules.search.document.UnifiedDoc;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnifiedSearchRepository extends ElasticsearchRepository<UnifiedDoc, String> {

    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"title^3\", \"content\", \"topics^2\", \"location\"]}}], \"filter\": [{\"term\": {\"auditStatus\": 1}}, {\"term\": {\"status\": 1}}]}}")
    List<UnifiedDoc> searchByKeyword(String keyword);
}
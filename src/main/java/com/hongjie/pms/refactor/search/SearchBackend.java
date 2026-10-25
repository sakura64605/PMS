package com.hongjie.pms.refactor.search;
import java.util.List;
public interface SearchBackend {
    Page search(SearchInput input);
    record Candidate(String type,Long id) {}
    record Page(long total,List<Candidate> candidates) {}
}

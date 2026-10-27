package com.hongjie.pms.modules.search.application;

import java.util.List;

public interface SearchBackend {
    Page search(SearchInput input);

    record Candidate(String type, Long id) {}

    record Page(long total, List<Candidate> candidates) {}
}

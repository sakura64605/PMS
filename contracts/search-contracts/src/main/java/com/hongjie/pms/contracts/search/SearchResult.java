package com.hongjie.pms.contracts.search;
import java.util.List;
import java.util.Set;
public record SearchResult(long total,List<Candidate> candidates) {
    public SearchResult {
        if(total<0||candidates==null||candidates.size()>100)throw new IllegalArgumentException("Invalid search result");
        candidates=List.copyOf(candidates);
    }
    public record Candidate(String type,Long id) {
        public Candidate {
            if(type==null||!Set.of("pet","activity","daily").contains(type)||id==null||id<=0)
                throw new IllegalArgumentException("Invalid search candidate");
        }
    }
}

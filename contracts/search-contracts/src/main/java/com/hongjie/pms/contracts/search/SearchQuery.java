package com.hongjie.pms.contracts.search;
import java.util.List;
import java.util.Set;
public record SearchQuery(String keyword,int pageNum,int pageSize,List<String> types,String sortBy,Long actor) {
    public SearchQuery {
        if(keyword==null||keyword.length()>200||pageNum<1||pageSize<1||pageSize>100||(long)pageNum*pageSize>10000
                ||sortBy==null||!Set.of("relevance","time","hot").contains(sortBy)||actor!=null&&actor<=0)
            throw new IllegalArgumentException("Invalid search query");
        types=types==null?List.of():List.copyOf(types);
        if(types.stream().anyMatch(t->!Set.of("pet","activity","daily").contains(t)))throw new IllegalArgumentException("Invalid search type");
    }
}

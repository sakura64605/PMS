package com.hongjie.pms.refactor.search.index;

import com.hongjie.pms.refactor.core.strategy.StrategyRegistry;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;

@Service @Slf4j
public class SearchIndexCoordinator {
    public static final String LOCK="pms:search:index";
    private final SearchIndexStore store;
    private final RedissonClient redisson;
    private final StrategyRegistry<String,SearchIndexSource> sources;
    private final AtomicBoolean ready=new AtomicBoolean();
    public SearchIndexCoordinator(SearchIndexStore store,RedissonClient redisson,List<SearchIndexSource> sources){
        this.store=store;this.redisson=redisson;
        this.sources=new StrategyRegistry<>("search index",sources,s->List.of(s.type()));
    }
    public void initialize(){
        if(ready.get())return;
        var lock=redisson.getReadWriteLock(LOCK).writeLock();lock.lock();
        try{
            if(!ready.get()){
                if(store.targets().isEmpty()){
                    String target="unified_search";
                    if(!store.exists(target)){target=nextName();store.create(target);}
                    store.switchTo(List.of(),target);
                }
                ready.set(true);
            }
        }finally{lock.unlock();}
    }
    public void sync(String type,Long id){
        if(id==null||id<=0)throw new IllegalArgumentException("Invalid search business id");
        var source=sources.get(type);initialize();
        var gate=redisson.getReadWriteLock(LOCK).readLock();gate.lock();
        var document=redisson.getLock(LOCK+":"+type+":"+id);
        try{
            document.lock();
            try{
                var row=source.load(List.of(id)).get(id);
                if(row==null)store.delete(source.prefix()+id);else store.write(SearchIndexStore.ALIAS,List.of(row));
            }finally{document.unlock();}
        }finally{gate.unlock();}
    }
    public void rebuild(LongConsumer progress){
        initialize();var lock=redisson.getReadWriteLock(LOCK).writeLock();lock.lock();
        try{
            String next=nextName();
            log.info("Building search index: {}",next);
            store.create(next);
            for(var source:sources.all())scan(source,next,progress);
            store.refresh(next);
            // Old indices remain intact; only the alias changes.
            store.switchTo(store.targets(),next);
            log.info("Search alias switched to {}",next);
        }finally{lock.unlock();}
    }
    public void resync(String type){
        var source=sources.get(type);long after=0,through=source.maxId();
        while(true){
            var ids=source.ids(after,through);if(ids.isEmpty())return;
            for(Long id:ids)sync(type,id);
            long next=ids.get(ids.size()-1);if(next<=after)throw new IllegalStateException("Search cursor did not advance");after=next;
        }
    }
    private void scan(SearchIndexSource source,String target,LongConsumer progress){
        long after=0,through=source.maxId();
        while(true){
            var ids=source.ids(after,through);if(ids.isEmpty())return;
            var docs=source.load(ids);store.write(target,List.copyOf(docs.values()));progress.accept(ids.size());
            long next=ids.get(ids.size()-1);if(next<=after)throw new IllegalStateException("Search cursor did not advance");after=next;
        }
    }
    private String nextName(){return "pms_search_"+UUID.randomUUID().toString().replace("-","");}
}

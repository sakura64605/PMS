package com.hongjie.pms.modules.search.application.index;

import org.redisson.api.*;
import com.hongjie.pms.modules.search.infrastructure.search.document.UnifiedDoc;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import static org.mockito.Mockito.*;

final class SearchIndexTestSupport {
    static RedissonClient locks(){
        var client=mock(RedissonClient.class);var gate=mock(RReadWriteLock.class);
        var real=new ReentrantReadWriteLock(true);
        RLock read=lock(real.readLock()),write=lock(real.writeLock());
        when(gate.readLock()).thenReturn(read);when(gate.writeLock()).thenReturn(write);
        when(client.getReadWriteLock(SearchIndexCoordinator.LOCK)).thenReturn(gate);
        Map<String,RLock> documents=new ConcurrentHashMap<>();
        when(client.getLock(anyString())).thenAnswer(inv->documents.computeIfAbsent(inv.getArgument(0),key->lock(new ReentrantLock())));
        return client;
    }
    private static RLock lock(Lock actual){
        var lock=mock(RLock.class);
        doAnswer(inv->{actual.lock();return null;}).when(lock).lock();
        doAnswer(inv->{actual.unlock();return null;}).when(lock).unlock();
        return lock;
    }
    static class Store implements SearchIndexStore {
        Map<String,Map<String,UnifiedDoc>> indices=new ConcurrentHashMap<>();
        volatile String target;
        volatile boolean failWrite,failRefresh;
        public boolean exists(String index){return indices.containsKey(index);}
        public List<String> targets(){return target==null?List.of():List.of(target);}
        public void create(String index){indices.put(index,new ConcurrentHashMap<>());}
        public void switchTo(List<String> previous,String next){target=next;}
        public void write(String index,List<UnifiedDoc> docs){
            if(failWrite)throw new IllegalStateException("injected bulk item failure");
            String destination=ALIAS.equals(index)?target:index;
            for(var doc:docs)indices.get(destination).put(doc.getId(),doc);
        }
        public void delete(String id){indices.get(target).remove(id);}
        public void refresh(String index){if(failRefresh)throw new IllegalStateException("injected refresh failure");}
        UnifiedDoc document(String id){return indices.get(target).get(id);}
    }
}

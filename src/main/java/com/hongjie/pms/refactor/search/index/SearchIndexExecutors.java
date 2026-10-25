package com.hongjie.pms.refactor.search.index;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
@Configuration(proxyBeanMethods=false)
public class SearchIndexExecutors {
    @Bean("searchIndexExecutor") ThreadPoolTaskExecutor incremental(){return pool("search-sync-",2,4,256);}
    @Bean("searchMaintenanceExecutor") ThreadPoolTaskExecutor maintenance(){return pool("search-rebuild-",1,1,1);}
    private ThreadPoolTaskExecutor pool(String prefix,int core,int max,int queue){
        var executor=new ThreadPoolTaskExecutor();executor.setThreadNamePrefix(prefix);executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);executor.setQueueCapacity(queue);executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);return executor;
    }
}

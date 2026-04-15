package com.hongjie.pms.common.delay;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DelayQueueService {
    
    private final RedissonClient redissonClient;
    private final DelayTaskHandler taskHandler;
    
    private static final String QUEUE_NAME = "delay:task:queue";
    private ExecutorService executorService;
    
    /**
     * 添加延时任务
     * 
     * @param type 任务类型
     * @param businessId 业务ID
     * @param delayMillis 延迟时间（毫秒）
     */
    public void addTask(String type, Long businessId, long delayMillis) {
        addTask(type, businessId, delayMillis, null);
    }
    
    /**
     * 添加延时任务（带参数）
     */
    public void addTask(String type, Long businessId, long delayMillis, String params) {
        RBlockingQueue<DelayTask> blockingQueue = redissonClient.getBlockingQueue(QUEUE_NAME);
        RDelayedQueue<DelayTask> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        
        DelayTask task = new DelayTask(type, businessId, System.currentTimeMillis() + delayMillis, params);
        delayedQueue.offer(task, delayMillis, TimeUnit.MILLISECONDS);
        
        log.info("延时任务已添加: type={}, businessId={}, delay={}ms, executeTime={}", 
            type, businessId, delayMillis, task.getExecuteTime());
    }
    
    /**
     * 启动消费者（项目启动时自动运行）
     */
    @PostConstruct
    public void startConsumer() {
        executorService = Executors.newSingleThreadExecutor();
        
        executorService.submit(() -> {
            RBlockingQueue<DelayTask> blockingQueue = redissonClient.getBlockingQueue(QUEUE_NAME);
            log.info("延时任务消费者已启动，等待任务...");
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DelayTask task = blockingQueue.take();
                    log.info("收到延时任务: type={}, businessId={}, executeTime={}", 
                        task.getType(), task.getBusinessId(), task.getExecuteTime());
                    
                    // 异步处理任务
                    taskHandler.handle(task);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("延时任务消费者被中断");
                    break;
                } catch (Exception e) {
                    log.error("处理延时任务失败", e);
                }
            }
        });
    }
    
    /**
     * 停止消费者（应用关闭时调用）
     */
    public void stopConsumer() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
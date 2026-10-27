package com.hongjie.pms.modules.communication.notice.application.listener;

import com.hongjie.pms.platform.mq.CacheUpdateProducer;
import com.hongjie.pms.platform.utils.RedisScanUtil;
import org.springframework.data.redis.core.RedisTemplate;
import com.hongjie.pms.modules.communication.notice.api.event.NoticeChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 公告变更后清除缓存 —— 在事务<b>提交之后</b>执行。
 *
 * <p><b>顺带修掉了一个缓存永不失效的缺陷。</b>
 * {@code CacheUpdateConsumer} 的语义是：
 * <ul>
 *   <li>{@code sendEvictAll(name)} 删除匹配 {@code name + ":*"} 的 key</li>
 *   <li>{@code sendEvict(name, key)} 删除 {@code name + ":" + key}</li>
 * </ul>
 * 而公告实际的缓存 key 是 {@code notice:list:{pageNum}_{pageSize}}（列表）
 * 与 {@code notice:{id}}（详情）。老代码清除列表时用的是
 * {@code sendEvictAll("noticeList")}（展开成 {@code noticeList:*}）与
 * {@code sendEvictAll("notice:List")}（展开成 {@code notice:List:*}）——
 * <b>两个都匹配不到任何 key</b>，于是公告列表缓存永远不会失效，
 * 增删改之后用户最长 10 分钟看到的是旧列表。
 *
 * <p>这里统一用常量收口，避免再出现同类拼写/大小写不一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeCacheEvictListener {

    /**
     * 列表缓存的区域名。
     *
     * <p>必须与 {@code listForUser} 里拼出的 key 前缀一致：
     * 该方法用的 key 是 {@code "notice:list:" + pageNum + "_" + pageSize}，
     * 因此区域名是 {@code notice:list}（展开后 {@code notice:list:*}）。
     */
    public static final String LIST_CACHE_REGION = "notice:list";

    /** 详情缓存的区域名，对应 key {@code "notice:" + id} */
    public static final String DETAIL_CACHE_REGION = "notice";

    private final CacheUpdateProducer cacheUpdateProducer;
    private final RedisTemplate<String, Object> redis;
    private final RedisScanUtil scan;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoticeChanged(NoticeChangedEvent event) {
        evict(event.getNoticeId());
    }

    /** 清除某条公告的详情缓存，以及全部列表分页缓存 */
    public void evict(Long noticeId) {
        if (noticeId != null) {
            run(() -> redis.delete(DETAIL_CACHE_REGION + ":" + noticeId));
        }
        run(() -> scan.scanAndDelete(LIST_CACHE_REGION + ":*"));
        if (noticeId != null) {
            run(() -> cacheUpdateProducer.sendEvict(DETAIL_CACHE_REGION, String.valueOf(noticeId)));
        }
        run(() -> cacheUpdateProducer.sendEvictAll(LIST_CACHE_REGION));
        log.debug("公告缓存清除已发送: noticeId={}, listRegion={}", noticeId, LIST_CACHE_REGION);
    }

    private void run(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("Notice committed but cache eviction failed", e);
        }
    }
}

package com.hongjie.pms.refactor.cache;

import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.cache.config.CacheAutoConfiguration;
import com.hongjie.pms.common.utils.RedisScanUtil;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import com.hongjie.pms.refactor.comment.listener.CommentCacheListener;
import com.hongjie.pms.refactor.following.*;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CacheWiringTest {
    @Test void productionProxyAndFacadeCanCoexistWithAllNewConsumers() {
        try (var context=new AnnotationConfigApplicationContext()) {
            var redis=mock(StringRedisTemplate.class);
            context.registerBean(StringRedisTemplate.class,()->redis);
            context.registerBean(RedissonClient.class,()->mock(RedissonClient.class));
            context.registerBean(RedisScanUtil.class,()->mock(RedisScanUtil.class));
            context.registerBean(FollowMapper.class,()->mock(FollowMapper.class));
            context.registerBean(UserMapper.class,()->mock(UserMapper.class));
            context.registerBean(FollowWriteMapper.class,()->mock(FollowWriteMapper.class));
            context.registerBean(FollowInboxWriter.class,()->mock(FollowInboxWriter.class));
            context.register(CacheAutoConfiguration.class,ActivityCache.class,CommentCacheListener.class,PmsFollowServiceImpl.class);
            context.refresh();
            assertThat(context.getBeansOfType(DistributedCache.class)).hasSize(2);
            assertThat(context.getBean(DistributedCache.class)).isSameAs(context.getBean("distributedCache"));
            assertThat(context.getBean(CommentCacheListener.class)).isNotNull();
            assertThat(context.getBean(PmsFollowServiceImpl.class)).isNotNull();
            context.getBean(ActivityCache.class).evict(42L);
            verify(redis).delete("activity_42");
        }
    }
}

package com.hongjie.pms.refactor.local;

import com.hongjie.pms.refactor.scheduling.*;
import com.hongjie.pms.refactor.search.index.*;
import com.hongjie.pms.AI.rag.*;
import com.hongjie.pms.AI.modules.mapper.AiKnowledgeBaseMapper;
import com.hongjie.pms.common.config.CacheWarmer;
import com.hongjie.pms.common.utils.RedisScanUtil;
import com.hongjie.pms.modules.notice.mapper.*;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalValidationConfigurationTest {
    @Configuration(proxyBeanMethods=false)
    @EnableConfigurationProperties({SchedulingProperties.class,RocketMQProperties.class})
    static class Binding {}
    ApplicationContextRunner runner(){
        return new ApplicationContextRunner().withUserConfiguration(Binding.class,LocalJobScheduler.class,XxlJobHandlers.class,
                SearchIndexBootstrap.class,CacheWarmer.class,KnowledgeBaseService.class)
                .withBean(PmsJobActions.class,()->mock(PmsJobActions.class))
                .withBean(SearchMaintenance.class,()->mock(SearchMaintenance.class))
                .withBean(AiKnowledgeBaseMapper.class,()->mock(AiKnowledgeBaseMapper.class))
                .withBean(QdrantVectorStore.class,()->mock(QdrantVectorStore.class))
                .withBean(RedisTemplate.class,()->mock(RedisTemplate.class))
                .withBean(RedisScanUtil.class,()->mock(RedisScanUtil.class))
                .withBean(NoticeMapper.class,()->mock(NoticeMapper.class))
                .withBean(NoticeReadRecordMapper.class,()->mock(NoticeReadRecordMapper.class))
                .withBean(PetPostMapper.class,()->mock(PetPostMapper.class))
                .withBean(ActivityMapper.class,()->mock(ActivityMapper.class))
                .withBean(UserMapper.class,()->mock(UserMapper.class));
    }
    @Test void localProfileDoesNotRunSharedBackgroundWork()throws Exception{
        var properties=new YamlPropertySourceLoader().load("local-validation",new ClassPathResource("application-local-validation.yaml"));
        runner().withInitializer(context->properties.forEach(p->context.getEnvironment().getPropertySources().addFirst(p))).run(context->{
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LocalJobScheduler.class).doesNotHaveBean(XxlJobHandlers.class)
                    .doesNotHaveBean(SearchIndexBootstrap.class).doesNotHaveBean(CacheWarmer.class);
            assertThat(context.getBean(SchedulingProperties.class).getMode()).isEqualTo(SchedulingProperties.Mode.DISABLED);
            verifyNoInteractions(context.getBean(AiKnowledgeBaseMapper.class),context.getBean(QdrantVectorStore.class));
            var listeners=context.getBean(RocketMQProperties.class).getConsumer().getListeners();
            assertThat(listeners.get("message-consumer-group").get("message-topic")).isFalse();
            assertThat(listeners.get("delay-task-consumer-group").get("delay-task-topic")).isFalse();
            assertThat(listeners.get("cache-update-consumer-group").get("cache-update-topic")).isFalse();
        });
    }
    @Test void existingDefaultsStillEnableStartupWork(){
        runner().run(context->{
            assertThat(context).hasNotFailed().hasSingleBean(LocalJobScheduler.class).hasSingleBean(SearchIndexBootstrap.class).hasSingleBean(CacheWarmer.class);
            verify(context.getBean(AiKnowledgeBaseMapper.class)).selectList(any());
        });
    }
}

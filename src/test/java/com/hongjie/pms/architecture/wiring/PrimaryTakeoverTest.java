package com.hongjie.pms.architecture.wiring;

import com.hongjie.pms.platform.cache.DistributedCache;
import com.hongjie.pms.platform.mq.CacheUpdateProducer;
import com.hongjie.pms.platform.utils.OssUtils;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.governance.audit.api.service.AuditService;
import com.hongjie.pms.modules.community.comment.api.controller.CommentController;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.mapper.CommentMapper;
import com.hongjie.pms.modules.community.comment.api.service.CommentService;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.DailyPostMapper;
import com.hongjie.pms.modules.community.feed.api.service.FeedService;
import com.hongjie.pms.modules.community.following.infrastructure.persistence.mapper.FollowMapper;
import com.hongjie.pms.modules.community.like.infrastructure.persistence.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.mapper.DelayTaskMapper;
import com.hongjie.pms.modules.scheduling.application.DelayTaskService;
import com.hongjie.pms.modules.communication.notification.api.service.MessageService;
import com.hongjie.pms.modules.communication.notice.api.controller.NoticeController;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.mapper.NoticeMapper;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.mapper.NoticeReadRecordMapper;
import com.hongjie.pms.modules.communication.notice.api.service.NoticeService;
import com.hongjie.pms.modules.content.petpost.api.controller.PetPostController;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.FavoriteRecordMapper;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.content.petpost.api.service.PetPostService;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.modules.activity.application.handler.ActivityTargetHandler;
import com.hongjie.pms.modules.community.comment.application.converter.CommentDtoConverter;
import com.hongjie.pms.modules.community.comment.application.service.PmsCommentServiceImpl;
import com.hongjie.pms.shared.core.event.DomainEventPublisher;
import com.hongjie.pms.modules.content.daily.application.handler.DailyPostTargetHandler;
import com.hongjie.pms.shared.extension.target.TargetHandlerRegistry;
import com.hongjie.pms.shared.extension.target.TargetTypeCodec;
import com.hongjie.pms.modules.communication.notice.application.converter.NoticeDtoConverter;
import com.hongjie.pms.modules.communication.notice.application.service.PmsNoticeServiceImpl;
import com.hongjie.pms.modules.content.petpost.application.converter.PetPostDtoConverter;
import com.hongjie.pms.modules.content.petpost.application.handler.PetPostTargetHandler;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PmsPetPostMapper;
import com.hongjie.pms.modules.content.petpost.application.service.PmsPetPostServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Verifies migrated implementations are wired and missing implementations cannot silently fall back. */
@DisplayName("Runtime wiring uses migrated implementations")
class PrimaryTakeoverTest {

    @Test
    @DisplayName("PetPostController 注入的是新实现（@Primary 生效）")
    void petPostControllerReceivesNewImplementation() {
        runWith(PetPostSlice.class, context -> {
            assertThat(context.getBean(PetPostService.class))
                    .as("按接口取 Bean 时应得到新实现")
                    .isInstanceOf(PmsPetPostServiceImpl.class);

            Object injected = ReflectionTestUtils.getField(
                    context.getBean(PetPostController.class), "petPostService");
            assertThat(injected)
                    .as("控制器字段里实际持有的实例")
                    .isInstanceOf(PmsPetPostServiceImpl.class);
        });
    }

    @Test
    @DisplayName("CommentController 注入的是新实现（@Primary 生效）")
    void commentControllerReceivesNewImplementation() {
        runWith(CommentSlice.class, context -> {
            assertThat(context.getBean(CommentService.class))
                    .isInstanceOf(PmsCommentServiceImpl.class);

            Object injected = ReflectionTestUtils.getField(
                    context.getBean(CommentController.class), "commentService");
            assertThat(injected).isInstanceOf(PmsCommentServiceImpl.class);
        });
    }

    @Test
    @DisplayName("NoticeController 注入的是新实现（@Primary 生效）")
    void noticeControllerReceivesNewImplementation() {
        runWith(NoticeSlice.class, context -> {
            assertThat(context.getBean(NoticeService.class))
                    .isInstanceOf(PmsNoticeServiceImpl.class);

            Object injected = ReflectionTestUtils.getField(
                    context.getBean(NoticeController.class), "noticeService");
            assertThat(injected).isInstanceOf(PmsNoticeServiceImpl.class);
            org.mockito.Mockito.verifyNoInteractions(context.getBean(RedissonClient.class));
        });
    }

    @Test
    @DisplayName("Missing migrated implementations must fail rather than silently enable reference code")
    void missingNewImplementationDoesNotFallBackToLegacy() {
        new ApplicationContextRunner().withUserConfiguration(MissingImplementationSlice.class)
                .run(context -> assertThat(context).hasFailed());
    }

    private void runWith(Class<?> userConfiguration, Consumer<ApplicationContext> assertions) {
        new ApplicationContextRunner()
                .withUserConfiguration(userConfiguration)
                .run(context -> {
                    assertThat(context)
                            .as("切片上下文应能正常启动（这里失败即说明真实启动也会因装配问题失败）")
                            .hasNotFailed();
                    assertions.accept(context);
                });
    }

    // ==================== 切片配置 ====================

    /** 与外部中间件打交道的协作者一律 Mock —— 本测试只关心装配关系 */
    @Configuration(proxyBeanMethods = false)
    static class Mocks {

        @Bean
        PetPostMapper petPostMapper() {
            return mock(PetPostMapper.class);
        }

        @Bean
        PmsPetPostMapper pmsPetPostMapper() {
            return mock(PmsPetPostMapper.class);
        }

        @Bean
        UserMapper userMapper() {
            return mock(UserMapper.class);
        }

        @Bean
        LikeRecordMapper likeRecordMapper() {
            return mock(LikeRecordMapper.class);
        }

        @Bean
        FavoriteRecordMapper favoriteRecordMapper() {
            return mock(FavoriteRecordMapper.class);
        }

        @Bean
        FollowMapper followMapper() {
            return mock(FollowMapper.class);
        }

        @Bean
        ActivityMapper activityMapper() {
            return mock(ActivityMapper.class);
        }

        @Bean
        DailyPostMapper dailyPostMapper() {
            return mock(DailyPostMapper.class);
        }

        @Bean
        CommentMapper commentMapper() {
            return mock(CommentMapper.class);
        }

        @Bean
        OssUtils ossUtils() {
            return mock(OssUtils.class);
        }

        @Bean
        DistributedCache distributedCache() {
            return mock(DistributedCache.class);
        }

        @Bean
        FeedService feedService() {
            return mock(FeedService.class);
        }

        @Bean
        com.hongjie.pms.modules.governance.audit.application.AuditCommandService auditCommands() {
            return mock(com.hongjie.pms.modules.governance.audit.application.AuditCommandService.class);
        }

        @Bean
        AuditService auditService() {
            return mock(AuditService.class);
        }

        @Bean
        MessageService messageService() {
            return mock(MessageService.class);
        }

        /** 老 NoticeServiceImpl 依赖：缓存更新消息生产者与 Redisson 客户端 */
        @Bean
        CacheUpdateProducer cacheUpdateProducer() {
            return mock(CacheUpdateProducer.class);
        }

        @Bean
        RedissonClient redissonClient() {
            return mock(RedissonClient.class);
        }

        @Bean
        ApplicationEventPublisher applicationEventPublisher() {
            return mock(ApplicationEventPublisher.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }

        @Bean
        TargetTypeCodec targetTypeCodec() {
            return new TargetTypeCodec();
        }

        @Bean
        PetPostDtoConverter petPostDtoConverter() {
            return new PetPostDtoConverter();
        }

        @Bean
        CommentDtoConverter commentDtoConverter() {
            return new CommentDtoConverter();
        }

        @Bean
        NoticeMapper noticeMapper() {
            return mock(NoticeMapper.class);
        }

        @Bean
        NoticeReadRecordMapper noticeReadRecordMapper() {
            return mock(NoticeReadRecordMapper.class);
        }

        @Bean
        DelayTaskService delayTaskService() {
            return mock(DelayTaskService.class);
        }

        /**
         * 必须提供：Spring 会对 {@code @Bean} 返回的实例做<b>字段注入</b>，
         * 而 {@code DelayTaskService} 类上有 {@code @Autowired DelayTaskMapper}。
         * 也就是说 mock 一个"字段注入式"的类并不能绕开它的依赖 —— 这里仍然要把
         * 那个依赖也 mock 出来，否则容器起不来。
         */
        @Bean
        DelayTaskMapper delayTaskMapper() {
            return mock(DelayTaskMapper.class);
        }

        @Bean
        NoticeDtoConverter noticeDtoConverter() {
            return new NoticeDtoConverter();
        }
    }

    /** petpost 模块的完整装配：老实现 + 新实现 + 控制器 */
    @Configuration(proxyBeanMethods = false)
    @Import({Mocks.class,PmsPetPostServiceImpl.class,PetPostController.class})
    static class PetPostSlice {
    }

    /** comment 模块的完整装配：老实现 + 新实现 + 控制器 + 策略族 */
    @Configuration(proxyBeanMethods = false)
    @Import({Mocks.class,PmsCommentServiceImpl.class,CommentController.class,DomainEventPublisher.class,PetPostTargetHandler.class,ActivityTargetHandler.class,DailyPostTargetHandler.class,TargetHandlerRegistry.class})
    static class CommentSlice {
    }

    /** notice 模块的完整装配：老实现 + 新实现 + 控制器 */
    @Configuration(proxyBeanMethods = false)
    @Import({Mocks.class,PmsNoticeServiceImpl.class,NoticeController.class,DomainEventPublisher.class})
    static class NoticeSlice {
    }

    /** 仅注册已停用的参考类时，运行接线必须失败。 */
    @Configuration(proxyBeanMethods = false)
    @Import({Mocks.class,PetPostController.class,CommentController.class})
    static class MissingImplementationSlice {
    }
}

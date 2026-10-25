package com.hongjie.pms.refactor.wiring;

import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.mq.CacheUpdateProducer;
import com.hongjie.pms.common.utils.OssUtils;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.modules.comment.controller.CommentController;
import com.hongjie.pms.modules.comment.mapper.CommentMapper;
import com.hongjie.pms.modules.comment.service.CommentService;
import com.hongjie.pms.modules.comment.service.Impl.CommentServiceImpl;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.feed.service.FeedService;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.common.punishment.scheduler.DelayTaskMapper;
import com.hongjie.pms.refactor.scheduling.DelayTaskService;
import com.hongjie.pms.refactor.message.api.MessageService;
import com.hongjie.pms.modules.notice.controller.NoticeController;
import com.hongjie.pms.modules.notice.mapper.NoticeMapper;
import com.hongjie.pms.modules.notice.mapper.NoticeReadRecordMapper;
import com.hongjie.pms.modules.notice.service.Impl.NoticeServiceImpl;
import com.hongjie.pms.modules.notice.service.NoticeService;
import com.hongjie.pms.modules.petpost.controller.PetPostController;
import com.hongjie.pms.modules.petpost.mapper.FavoriteRecordMapper;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.petpost.service.Impl.PetPostServiceImpl;
import com.hongjie.pms.modules.petpost.service.PetPostService;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.activity.handler.ActivityTargetHandler;
import com.hongjie.pms.refactor.comment.converter.CommentDtoConverter;
import com.hongjie.pms.refactor.comment.service.PmsCommentServiceImpl;
import com.hongjie.pms.refactor.core.event.DomainEventPublisher;
import com.hongjie.pms.refactor.daily.handler.DailyPostTargetHandler;
import com.hongjie.pms.refactor.extension.target.TargetHandlerRegistry;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import com.hongjie.pms.refactor.notice.converter.NoticeDtoConverter;
import com.hongjie.pms.refactor.notice.service.PmsNoticeServiceImpl;
import com.hongjie.pms.refactor.petpost.converter.PetPostDtoConverter;
import com.hongjie.pms.refactor.petpost.handler.PetPostTargetHandler;
import com.hongjie.pms.refactor.petpost.mapper.PmsPetPostMapper;
import com.hongjie.pms.refactor.petpost.service.PmsPetPostServiceImpl;
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
            assertThat(context.getBeansOfType(NoticeServiceImpl.class)).isEmpty();
            org.mockito.Mockito.verifyNoInteractions(context.getBean(RedissonClient.class));
        });
    }

    @Test
    @DisplayName("Missing migrated implementations must fail rather than silently enable reference code")
    void missingNewImplementationDoesNotFallBackToLegacy() {
        new ApplicationContextRunner().withUserConfiguration(LegacyOnlySlice.class)
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
        com.hongjie.pms.refactor.audit.AuditCommandService auditCommands() {
            return mock(com.hongjie.pms.refactor.audit.AuditCommandService.class);
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
    @Import({Mocks.class,PetPostServiceImpl.class,PmsPetPostServiceImpl.class,PetPostController.class})
    static class PetPostSlice {
    }

    /** comment 模块的完整装配：老实现 + 新实现 + 控制器 + 策略族 */
    @Configuration(proxyBeanMethods = false)
    @Import({Mocks.class,CommentServiceImpl.class,PmsCommentServiceImpl.class,CommentController.class,DomainEventPublisher.class,PetPostTargetHandler.class,ActivityTargetHandler.class,DailyPostTargetHandler.class,TargetHandlerRegistry.class})
    static class CommentSlice {
    }

    /** notice 模块的完整装配：老实现 + 新实现 + 控制器 */
    @Configuration(proxyBeanMethods = false)
    @Import({Mocks.class,NoticeServiceImpl.class,PmsNoticeServiceImpl.class,NoticeController.class,DomainEventPublisher.class})
    static class NoticeSlice {
    }

    /** 仅注册已停用的参考类时，运行接线必须失败。 */
    @Configuration(proxyBeanMethods = false)
    @Import({Mocks.class,PetPostServiceImpl.class,CommentServiceImpl.class,PetPostController.class,CommentController.class})
    static class LegacyOnlySlice {
    }
}

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
import com.hongjie.pms.common.punishment.scheduler.DelayTaskService;
import com.hongjie.pms.modules.message.service.MessageService;
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

/**
 * 验证「删除 refactor 目录即可回滚」这条承诺背后的容器行为。
 *
 * <p><b>为什么单开一个测试</b>：等价性测试是绕开 Spring、直接 {@code new} 出两个实现来跑的，
 * 它们能证明<b>实现逻辑等价</b>，却证明不了<b>容器真的选了新实现</b>。
 * 而整个收敛方案的两条承诺恰恰都建立在容器行为上：
 * <ul>
 *   <li>新实现标了 {@code @Primary}，所以 {@code PetPostController} / {@code CommentController}
 *       一行都不用改就能拿到新实现；</li>
 *   <li>反过来，一旦删掉 {@code refactor/} 目录，容器里只剩老实现，
 *       注入结果自动回到老实现 —— 这就是"删目录即回滚"。</li>
 * </ul>
 *
 * <p><b>为什么不用 {@code @SpringBootTest}</b>：完整启动需要 MySQL、Redis(Redisson)、
 * RocketMQ、Elasticsearch、Qdrant 五样东西，其中 Redisson 建 Bean 时就会主动连服务器，
 * 缺一样都起不来。这里用最小切片，只装配被测的两个 Service + 一个 Controller + Mock 的
 * Mapper，既不碰任何外部中间件，又能精确验证上面两条承诺。
 *
 * <p><b>服务实现必须用 {@code @Import} 而不是 {@code @Bean} 注册</b>：
 * 只有走注解处理这条路径，Spring 才会读取类上的 {@code @Primary}。
 * 若在测试里手工 {@code new} 一个 Bean，就等于把被测行为自己写死了，测试也就失去意义。
 */
@DisplayName("容器装配：@Primary 接管与删目录回滚")
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
            assertThat(context.getBean(NoticeServiceImpl.class).isRunning()).isFalse();
            org.mockito.Mockito.verifyNoInteractions(context.getBean(RedissonClient.class));
        });
    }

    @Test
    @DisplayName("移除新实现后容器自动回退到老实现 —— 这就是「删目录即回滚」")
    void contextFallsBackToLegacyWhenNewImplementationIsAbsent() {
        // 模拟「删掉 refactor/ 目录」后的状态：容器里只注册老实现
        runWith(LegacyOnlySlice.class, context -> {
            assertThat(context.getBean(PetPostService.class))
                    .as("新实现缺席时，接口的唯一候选就是老实现，不需要任何配置改动")
                    .isInstanceOf(PetPostServiceImpl.class);
            assertThat(context.getBean(CommentService.class))
                    .isInstanceOf(CommentServiceImpl.class);

            Object injected = ReflectionTestUtils.getField(
                    context.getBean(PetPostController.class), "petPostService");
            assertThat(injected).isInstanceOf(PetPostServiceImpl.class);
        });
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
    @Import({Mocks.class, PetPostServiceImpl.class, PmsPetPostServiceImpl.class,
            PetPostController.class})
    static class PetPostSlice {
    }

    /** comment 模块的完整装配：老实现 + 新实现 + 控制器 + 策略族 */
    @Configuration(proxyBeanMethods = false)
    @Import({Mocks.class, CommentServiceImpl.class, PmsCommentServiceImpl.class,
            CommentController.class, DomainEventPublisher.class,
            PetPostTargetHandler.class, ActivityTargetHandler.class, DailyPostTargetHandler.class,
            TargetHandlerRegistry.class})
    static class CommentSlice {
    }

    /** notice 模块的完整装配：老实现 + 新实现 + 控制器 */
    @Configuration(proxyBeanMethods = false)
    @Import({Mocks.class, NoticeServiceImpl.class, PmsNoticeServiceImpl.class,
            NoticeController.class, DomainEventPublisher.class})
    static class NoticeSlice {
    }

    /** 只有老实现的情形：模拟删掉 refactor/ 之后 */
    @Configuration(proxyBeanMethods = false)
    @Import({Mocks.class, PetPostServiceImpl.class, CommentServiceImpl.class,
            PetPostController.class, CommentController.class})
    static class LegacyOnlySlice {
    }
}

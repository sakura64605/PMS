package com.hongjie.pms.architecture.wiring;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class BusinessOwnershipTest {
    @Test void migratedFacadesDoNotInheritLegacyImplementations() throws Exception {
        for (String name : List.of("com.hongjie.pms.modules.content.daily.application.PmsDailyPostService",
"com.hongjie.pms.modules.content.daily.application.PmsDailyRecommendService",
"com.hongjie.pms.modules.content.daily.application.PmsDailyUserInterestService",
"com.hongjie.pms.modules.content.daily.application.PmsTopicService",
"com.hongjie.pms.modules.communication.notification.application.PmsMessageService",
"com.hongjie.pms.modules.search.application.PmsUnifiedSearchService",
"com.hongjie.pms.modules.search.application.index.PmsFullDataSyncService",
"com.hongjie.pms.modules.search.application.index.PmsSearchDataSyncService",
"com.hongjie.pms.modules.analytics.application.PmsStatisticsService")) {
            assertThat(Class.forName(name).getSuperclass()).as(name).isEqualTo(Object.class);
        }
    }
    @Test void retiredBusinessImplementationsAreAbsentFromRuntime() {
        for (String name : List.of("com.hongjie.pms.AI.agent.AIAgentEngine",
"com.hongjie.pms.AI.common.TokenUsageTracker",
"com.hongjie.pms.AI.common.config.AIAgentConfig",
"com.hongjie.pms.AI.common.config.AIAgentConfiguration",
"com.hongjie.pms.AI.modules.service.AiMemoryExtractor",
"com.hongjie.pms.AI.modules.service.ChatMemoryService",
"com.hongjie.pms.AI.modules.service.ChatSessionService",
"com.hongjie.pms.AI.modules.service.HumanTransferService",
"com.hongjie.pms.AI.modules.service.UserMemoryService",
"com.hongjie.pms.AI.rag.KnowledgeBaseService",
"com.hongjie.pms.AI.rag.QdrantVectorStore",
"com.hongjie.pms.AI.tool.DailyPostsTool",
"com.hongjie.pms.AI.tool.HotTopicsTool",
"com.hongjie.pms.AI.tool.KnowledgeSearchTool",
"com.hongjie.pms.AI.tool.MyActivitiesTool",
"com.hongjie.pms.AI.tool.MyNotificationsTool",
"com.hongjie.pms.AI.tool.MyPetsTool",
"com.hongjie.pms.AI.tool.SearchActivitiesTool",
"com.hongjie.pms.AI.tool.SearchPetsTool",
"com.hongjie.pms.AI.tool.ToolRegistry",
"com.hongjie.pms.common.punishment.scheduler.ActivityStatusScheduler",
"com.hongjie.pms.common.punishment.scheduler.DelayTaskHandler",
"com.hongjie.pms.common.punishment.scheduler.DelayTaskScheduler",
"com.hongjie.pms.common.punishment.scheduler.DelayTaskService",
"com.hongjie.pms.common.punishment.util.PunishmentUtil",
"com.hongjie.pms.modules.activity.service.Impl.ActivityServiceImpl",
"com.hongjie.pms.modules.admin.service.Impl.AdminServiceImpl",
"com.hongjie.pms.modules.audit.handler.ActivityAuditHandler",
"com.hongjie.pms.modules.audit.handler.AuditTargetHandlerFactory",
"com.hongjie.pms.modules.audit.handler.DailyPostAuditHandler",
"com.hongjie.pms.modules.audit.handler.PetPostAuditHandler",
"com.hongjie.pms.modules.audit.service.Impl.AuditServiceImpl",
"com.hongjie.pms.modules.comment.service.Impl.CommentServiceImpl",
"com.hongjie.pms.modules.daily.service.DailyPostService",
"com.hongjie.pms.modules.daily.service.DailyRankService",
"com.hongjie.pms.modules.daily.service.DailyRecallService",
"com.hongjie.pms.modules.daily.service.DailyRecommendService",
"com.hongjie.pms.modules.daily.service.DailyUserInterestService",
"com.hongjie.pms.modules.daily.service.TopicService",
"com.hongjie.pms.modules.feed.service.Impl.FeedServiceImpl",
"com.hongjie.pms.modules.feed.task.InboxCleanupTask",
"com.hongjie.pms.modules.following.service.Impl.FollowServiceImpl",
"com.hongjie.pms.modules.like.service.Impl.LikeServiceImpl",
"com.hongjie.pms.modules.message.mq.DelayTaskConsumer",
"com.hongjie.pms.modules.message.mq.MessageMqConsumer",
"com.hongjie.pms.modules.message.service.MessageService",
"com.hongjie.pms.modules.notice.service.Impl.NoticeServiceImpl",
"com.hongjie.pms.modules.petpost.service.Impl.PetPostServiceImpl",
"com.hongjie.pms.modules.privateMessage.service.Impl.PrivateMessageServiceImpl",
"com.hongjie.pms.modules.report.service.Impl.ReportServiceImpl",
"com.hongjie.pms.modules.search.config.ElasticsearchIndexInitializer",
"com.hongjie.pms.modules.search.service.FullDataSyncService",
"com.hongjie.pms.modules.search.service.SearchDataSyncService",
"com.hongjie.pms.modules.search.service.UnifiedSearchService",
"com.hongjie.pms.modules.statistics.service.StatisticsService",
"com.hongjie.pms.modules.user.service.Impl.AvatarServiceImpl",
"com.hongjie.pms.modules.user.service.Impl.UserServiceImpl",
"com.hongjie.pms.AI.tool.BaseTool",
"com.hongjie.pms.AI.tool.ToolPaging",
"com.hongjie.pms.AI.QuickTest",
"com.hongjie.pms.modules.audit.handler.AuditTargetHandler",
"com.hongjie.pms.modules.petpost.util.PetCategoryClassifier",
"com.hongjie.pms.common.annotation.RateLimit",
"com.hongjie.pms.common.aspect.RateLimitAspect",
"com.hongjie.pms.common.utils.RateLimiterManager",
"com.hongjie.pms.common.filter.XssFilter",
"com.hongjie.pms.common.filter.XssHttpServletRequestWrapper",
"com.hongjie.pms.common.utils.XssUtils",
"com.hongjie.pms.common.annotation.DistributedCacheable",
"com.hongjie.pms.common.aspect.DistributedCacheAspect",
"com.hongjie.pms.modules.activity.repository.ActivityRepository",
"com.hongjie.pms.modules.activity.repository.Impl.ActivityRepositoryImpl",
"com.hongjie.pms.modules.search.repository.UnifiedSearchRepository")) {
            assertThatThrownBy(()->Class.forName(name)).as(name).isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test void aiRuntimeHasLeftCoreClasspath(){
        for(String name:List.of("com.hongjie.pms.modules.ai.application.agent.AIAgentEngine","dev.langchain4j.model.chat.ChatModel"))
            assertThatThrownBy(()->Class.forName(name)).isInstanceOf(ClassNotFoundException.class);
    }
}

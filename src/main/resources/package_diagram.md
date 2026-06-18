@startuml
!theme plain

package "AI模块" {
  package "agent" {
    class AIAgentEngine
  }
  package "common/config" {
    class AIAgentConfig
    class AIAgentConfiguration
  }
  package "modules" {
    package "controller" {
      class AIAgentController
      class KnowledgeBaseAdminController
    }
    package "dto" {
      class AIAgentRequest
      class AIAgentResponse
      class ToolCall
    }
    package "entity" {
      class AiChatMessage
      class AiChatSession
      class AiHumanTransfer
      class AiKnowledgeBase
    }
    package "mapper" {
      class AiChatMessageMapper
      class AiChatSessionMapper
      class AiHumanTransferMapper
      class AiKnowledgeBaseMapper
    }
    package "service" {
      class ChatMemoryService
      class ChatSessionService
      class HumanTransferService
    }
  }
  package "rag" {
    class KnowledgeBaseService
  }
  package "tool" {
    class BaseTool
    class DailyPostsTool
    class HotTopicsTool
    class KnowledgeSearchTool
    class MyActivitiesTool
    class MyNotificationsTool
    class MyPetsTool
    class SearchActivitiesTool
    class SearchPetsTool
    class ToolRegistry
  }
  package "websocket" {
    class AIAgentWebSocketHandler
  }
}

package "通用模块" {
  package "annotation" {
    class DistributedCacheable
    class RateLimit
    class RedisRateLimit
  }
  package "aspect" {
    class DistributedCacheAspect
    class RateLimitAspect
    class RedisRateLimitAspect
  }
  package "base" {
    class BaseController
  }
  package "base/core" {
    class UserContext
    class UpdateTimeContext
  }
  package "cache" {
    class Cache
    class DistributedCache
    class CacheUtil
    class FastJson2Util
  }
  package "cache/config" {
    class BloomFilterPenetrateProperties
    class CacheAutoConfiguration
    class RedisDistributedProperties
  }
  package "circuitbreaker" {
    class CircuitBreaker
    class CircuitBreakerManager
    class SlidingWindow
  }
  package "circuitbreaker/annotation" {
    class CircuitBreakerAnnotation
  }
  package "circuitbreaker/aspect" {
    class CircuitBreakerAspect
  }
  package "config" {
    class MybatisPlusConfig
    class RedisConfig
    class RedissonConfig
    class WebMvcConfig
    class WebSocketConfig
    class OssConfig
  }
  package "enums" {
    class ErrorCode
    class MessageType
    class PostType
    class AuditStatus
    class TargetType
  }
  package "exception" {
    class GlobalExceptionHandler
    class BusinessException
  }
  package "filter" {
    class XssFilter
  }
  package "handler" {
    class MyMetaObjectHandler
  }
  package "idempotent" {
    class IdempotentAspect
    class IdempotentTokenService
    class IdempotentSpELService
    class IdempotentTokenController
  }
  package "interceptor" {
    class AuthInterceptor
  }
  package "mq" {
    class CacheUpdateProducer
    class CacheUpdateConsumer
  }
  package "pojo" {
    class CommonResult
    class UserInfo
  }
  package "punishment" {
    class DelayTaskService
    class ActivityStatusScheduler
    class PunishmentUtil
  }
  package "trace" {
    class TraceContext
    class TraceAspect
  }
  package "utils" {
    class JWTUtils
    class PasswordUtils
    class SecurityUtils
    class OssUtils
    class BloomFilterService
    class RedisRateLimiter
  }
}

package "业务模块" {
  package "user" {
    class UserAuthController
    class UserController
    class AvatarController
    class UserService
    class UserMapper
    class User
    class AvatarHistory
  }
  package "petpost" {
    class PetPostController
    class PetPostService
    class PetPostMapper
    class PetPost
    class FavoriteRecord
  }
  package "activity" {
    class ActivityController
    class ActivityService
    class ActivityMapper
    class Activity
    class ActivitySignup
  }
  package "daily" {
    class DailyPostController
    class DailyPostService
    class DailyPostMapper
    class DailyPost
    class Topic
    class DailyRecommendService
    class DailyRankService
  }
  package "comment" {
    class CommentController
    class CommentService
    class CommentMapper
    class Comment
  }
  package "like" {
    class LikeController
    class LikeService
    class LikeRecordMapper
    class LikeRecord
  }
  package "following" {
    class FollowingController
    class FollowService
    class FollowMapper
    class Follow
  }
  package "message" {
    class MessageController
    class MessageService
    class UserMessageMapper
    class UserMessage
    class MessageMqProducer
    class WebSocketHandler
  }
  package "privateMessage" {
    class PrivateMessageController
    class PrivateMessageService
    class PrivateMessageMapper
    class PrivateMessage
    class PrivateConversation
  }
  package "notice" {
    class NoticeController
    class NoticeService
    class NoticeMapper
    class Notice
    class NoticeReadRecord
  }
  package "admin" {
    class AdminController
    class AdminService
    class BatchOperationRequest
  }
  package "audit" {
    class AuditController
    class AuditService
    class AuditRecordMapper
    class AuditRecord
  }
  package "report" {
    class ReportController
    class ReportService
    class ReportRecordMapper
    class ReportRecord
  }
  package "search" {
    class SearchController
    class UnifiedSearchService
    class UnifiedSearchRepository
    class ElasticsearchIndexInitializer
    class SearchDataSyncListener
  }
  package "feed" {
    class FeedController
    class FeedService
    class UserInbox
    class BigVConfig
  }
  package "statistics" {
    class StatisticsController
    class StatisticsService
    class DailyStatisticsMapper
    class DailyStatistics
  }
}

' 依赖关系
AI模块 ..> 通用模块 : 使用
业务模块 ..> 通用模块 : 使用
AI模块 ..> 业务模块 : 调用服务

@enduml
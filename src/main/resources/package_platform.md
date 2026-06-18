@startuml
!theme plain

package "平台管理" {
  package "controller" {
    class AdminController
    class AuditController
    class ReportController
    class StatisticsController
    class AIAgentController
    class KnowledgeBaseAdminController
  }
  
  package "service" {
    class AdminService
    class AuditService
    class ReportService
    class StatisticsService
    class ChatSessionService
    class HumanTransferService
    class KnowledgeBaseService
    class AIAgentEngine
  }
  
  package "mapper" {
    class AuditRecordMapper
    class ReportRecordMapper
    class DailyStatisticsMapper
    class AiChatSessionMapper
    class AiChatMessageMapper
    class AiKnowledgeBaseMapper
    class AiHumanTransferMapper
  }
  
  package "entity" {
    class AuditRecord
    class ReportRecord
    class DailyStatistics
    class AiChatSession
    class AiChatMessage
    class AiKnowledgeBase
    class AiHumanTransfer
  }
  
  package "tool" {
    class KnowledgeSearchTool
    class MyPetsTool
    class MyActivitiesTool
    class MyNotificationsTool
    class SearchPetsTool
    class SearchActivitiesTool
    class HotTopicsTool
    class DailyPostsTool
    class ToolRegistry
  }
  
  package "circuitbreaker" {
    class CircuitBreaker
    class CircuitBreakerManager
  }
  
  package "cache" {
    class DistributedCache
    class CacheUtil
  }
  
  package "utils" {
    class JWTUtils
    class RedisRateLimiter
    class BloomFilterService
  }
}

controller --> service
service --> mapper
service --> tool
service --> circuitbreaker
service --> cache
service --> utils
mapper --> entity

@enduml

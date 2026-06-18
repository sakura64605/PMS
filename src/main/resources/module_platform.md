@startuml
!theme plain

package "平台管理" {
  class AuditRecord {
    +Long id
    +String targetType
    +Long targetId
    +Long auditorId
    +Integer result
    +String remark
    +LocalDateTime auditTime
  }
  
  class ReportRecord {
    +Long id
    +Long reporterId
    +String targetType
    +Long targetId
    +Integer reason
    +String description
    +Integer status
    +LocalDateTime createTime
  }
  
  class DailyStatistics {
    +Long id
    +LocalDate statisticsDate
    +Integer newUsers
    +Integer totalUsers
    +Integer newPetPosts
    +Integer newActivities
    +Integer newDailyPosts
    +Integer newComments
    +Integer dau
    +Integer wau
    +Integer mau
    +Integer activePetPosts
    +Integer pendingAuditCount
  }
  
  class AiChatSession {
    +Long id
    +String sessionId
    +Long userId
    +String title
    +Integer status
    +Integer messageCount
    +String source
    +LocalDateTime createdAt
    +LocalDateTime updatedAt
  }
  
  class AiChatMessage {
    +Long id
    +String sessionId
    +String role
    +String content
    +Integer inputTokens
    +Integer outputTokens
    +Integer latencyMs
    +Integer feedbackScore
    +LocalDateTime createTime
  }
  
  class AiKnowledgeBase {
    +Long id
    +String question
    +String answer
    +String category
    +Integer status
    +Integer sortOrder
    +LocalDateTime createTime
  }
  
  class AiHumanTransfer {
    +Long id
    +String sessionId
    +Long userId
    +String reason
    +Integer status
    +Long adminId
    +LocalDateTime createTime
    +LocalDateTime handledTime
  }
}

@enduml

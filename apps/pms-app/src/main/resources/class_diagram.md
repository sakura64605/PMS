@startuml
!theme plain

' ==================== 核心实体 ====================

class User {
  +Long id
  +String userName
  +String nickName
  +String password
  +String avatar
  +String phone
  +String email
  +Integer gender
  +Integer role
  +Integer status
  +String signature
  +List~String~ tags
  +Map~String,Boolean~ privacySettings
  +Integer searchable
  +Integer isMuted
  +LocalDateTime muteEndTime
  +Integer isBannedSignup
  +LocalDateTime banSignupEndTime
  +Integer followerCount
  +Integer followingCount
  +Integer likeCount
  +Integer signupMissCount
  +LocalDateTime lastActiveTime
  +LocalDateTime createTime
  +LocalDateTime updateTime
}

class AvatarHistory {
  +Long id
  +Long userId
  +String avatarUrl
  +LocalDateTime updateTime
}

class PetPost {
  +Long id
  +Long userId
  +Integer type
  +String title
  +String content
  +List~String~ images
  +Integer petGender
  +String petAge
  +String petType
  +String petName
  +String contactPhone
  +String contactWechat
  +String address
  +Integer status
  +Integer auditStatus
  +Integer viewCount
  +Integer shareCount
  +Integer commentCount
  +Integer likeCount
  +LocalDateTime createTime
  +LocalDateTime updateTime
}

class FavoriteRecord {
  +Long id
  +Long userId
  +Long postId
  +LocalDateTime createTime
}

class Activity {
  +Long id
  +Long userId
  +String title
  +String content
  +List~String~ images
  +String location
  +Integer maxPeople
  +Integer currentPeople
  +LocalDateTime startTime
  +LocalDateTime endTime
  +Integer status
  +Integer auditStatus
  +Integer viewCount
  +Integer likeCount
  +Integer commentCount
  +Integer shareCount
  +Integer deleted
  +LocalDateTime createTime
  +LocalDateTime updateTime
}

class ActivitySignup {
  +Long id
  +Long activityId
  +Long userId
  +Integer status
  +LocalDateTime signUpTime
  +LocalDateTime signInTime
}

class DailyPost {
  +Long id
  +Long userId
  +String content
  +List~String~ images
  +String videoUrl
  +Long topicId
  +String location
  +Integer viewCount
  +Integer likeCount
  +Integer commentCount
  +Integer shareCount
  +Integer status
  +Integer auditStatus
  +LocalDateTime createTime
}

class Topic {
  +Long id
  +String name
  +String description
  +Integer postCount
  +Integer viewCount
  +Double hotScore
  +Integer status
  +LocalDateTime createTime
}

class DailyTopicRel {
  +Long id
  +Long dailyId
  +Long topicId
}

class Comment {
  +Long id
  +Long userId
  +String targetType
  +Long targetId
  +String content
  +Long parentId
  +Long replyTo
  +Integer likeCount
  +Integer status
  +LocalDateTime createTime
}

class Follow {
  +Long id
  +Long followerId
  +Long followingId
  +LocalDateTime createTime
}

class LikeRecord {
  +Long id
  +Long userId
  +String targetType
  +Long targetId
  +LocalDateTime createTime
}

' ==================== 消息 ====================

class UserMessage {
  +Long id
  +Long fromUserId
  +Long toUserId
  +String content
  +Integer type
  +Integer status
  +Long relatedId
  +LocalDateTime createTime
}

class PrivateConversation {
  +Long id
  +Long userId
  +Long otherUserId
  +String lastMessage
  +LocalDateTime lastMessageTime
  +Integer userUnreadCount
  +Integer otherUnreadCount
  +LocalDateTime createTime
  +LocalDateTime updateTime
}

class PrivateMessage {
  +Long id
  +Long conversationId
  +Long senderId
  +Long receiverId
  +String content
  +Integer senderDelete
  +Integer receiverDelete
  +Integer status
  +LocalDateTime createTime
}

' ==================== 运营 ====================

class Notice {
  +Long id
  +String title
  +String content
  +Integer type
  +Integer priority
  +Integer status
  +Integer isTop
  +LocalDateTime scheduledPublishTime
  +LocalDateTime expireTime
  +LocalDateTime createTime
}

class NoticeReadRecord {
  +Long id
  +Long noticeId
  +Long userId
  +LocalDateTime readTime
}

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

' ==================== Feed ====================

class UserInbox {
  +Long id
  +Long userId
  +Long postId
  +String postType
  +Long posterId
  +String posterName
  +String posterAvatar
  +String title
  +String coverImage
  +Integer isRead
  +LocalDateTime readTime
  +LocalDateTime createTime
}

class BigVConfig {
  +Long id
  +Long userId
  +Integer pushThreshold
}

' ==================== AI ====================

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

' ==================== 统计 ====================

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

' ==================== 推荐 ====================

class DailyUserBehavior {
  +Long id
  +Long userId
  +String targetType
  +Long targetId
  +String actionType
  +Double weight
  +LocalDateTime createTime
}

class DailyUserInterest {
  +Long id
  +Long userId
  +String tag
  +Double score
  +LocalDateTime updateTime
}

class DailyItemFeature {
  +Long id
  +Long postId
  +String postType
  +Double hotScore
  +Double freshnessScore
  +Double qualityScore
  +Double overallScore
  +LocalDateTime updateTime
}

' ==================== 关系 ====================

User "1" --> "*" AvatarHistory : 拥有
User "1" --> "*" PetPost : 发布
User "1" --> "*" Activity : 创建
User "1" --> "*" DailyPost : 发布
User "1" --> "*" Comment : 评论
User "1" --> "*" Follow : 关注
User "1" --> "*" LikeRecord : 点赞
User "1" --> "*" FavoriteRecord : 收藏
User "1" --> "*" UserMessage : 接收消息
User "1" --> "*" ReportRecord : 举报
User "1" --> "*" PrivateConversation : 参与
User "1" --> "*" AiChatSession : AI对话

PetPost "1" --> "*" Comment : 评论
PetPost "1" --> "*" LikeRecord : 点赞
PetPost "1" --> "*" FavoriteRecord : 收藏

Activity "1" --> "*" ActivitySignup : 报名
Activity "1" --> "*" Comment : 评论
Activity "1" --> "*" LikeRecord : 点赞

DailyPost "1" --> "*" Comment : 评论
DailyPost "1" --> "*" LikeRecord : 点赞
DailyPost "*" --> "*" Topic : 关联
DailyPost "1" --> "*" DailyTopicRel : 分类

DailyPost "1" --> "*" DailyItemFeature : 特征

Comment "1" --> "*" Comment : 回复

PrivateConversation "1" --> "*" PrivateMessage : 包含

Notice "1" --> "*" NoticeReadRecord : 阅读记录

User "1" --> "*" UserInbox : Feed收件箱

AiChatSession "1" --> "*" AiChatMessage : 包含
AiChatSession "1" --> "*" AiHumanTransfer : 转接

@enduml
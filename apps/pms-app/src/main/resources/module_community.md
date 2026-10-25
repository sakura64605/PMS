@startuml
!theme plain

package "社区互动" {
  class DailyPost {
    +Long id
    +Long userId
    +String content
    +List<String> images
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
  
  class LikeRecord {
    +Long id
    +Long userId
    +String targetType
    +Long targetId
    +LocalDateTime createTime
  }
  
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
}

@enduml

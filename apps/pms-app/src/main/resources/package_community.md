@startuml
!theme plain

package "社区互动" {
  package "controller" {
    class DailyPostController
    class CommentController
    class LikeController
  }
  
  package "service" {
    class DailyPostService
    class DailyRecommendService
    class CommentService
    class LikeService
    class UnifiedSearchService
  }
  
  package "mapper" {
    class DailyPostMapper
    class TopicMapper
    class CommentMapper
    class LikeRecordMapper
  }
  
  package "entity" {
    class DailyPost
    class Topic
    class DailyTopicRel
    class Comment
    class LikeRecord
    class DailyUserBehavior
    class DailyUserInterest
    class UserInbox
    class BigVConfig
  }
}

controller --> service
service --> mapper
mapper --> entity

@enduml

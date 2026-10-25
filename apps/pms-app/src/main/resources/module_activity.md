@startuml
!theme plain

package "活动管理" {
  class Activity {
    +Long id
    +Long userId
    +String title
    +String content
    +List<String> images
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
  }
  
  class ActivitySignup {
    +Long id
    +Long activityId
    +Long userId
    +Integer status
    +LocalDateTime signUpTime
    +LocalDateTime signInTime
  }
}


@enduml

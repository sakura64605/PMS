@startuml
!theme plain

package "用户管理" {
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
    +List<String> tags
    +Map<String,Boolean> privacySettings
    +Integer searchable
    +Integer followerCount
    +Integer followingCount
    +LocalDateTime createTime
  }
  
  class AvatarHistory {
    +Long id
    +Long userId
    +String avatarUrl
    +LocalDateTime updateTime
  }
  
  class Follow {
    +Long id
    +Long followerId
    +Long followingId
    +LocalDateTime createTime
  }
}

@enduml

@startuml
!theme plain

package "宠物管理" {
  class PetPost {
    +Long id
    +Long userId
    +Integer type
    +String title
    +String content
    +List<String> images
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
  }
  
  class FavoriteRecord {
    +Long id
    +Long userId
    +Long postId
    +LocalDateTime createTime
  }
}

@enduml

@startuml
left to right direction
!theme plain

actor "用户" as User
actor "游客" as Guest

rectangle "宠物管理" {
  usecase "发布宠物帖子" as UC11
  usecase "查看宠物帖子" as UC12
  usecase "编辑宠物帖子" as UC13
  usecase "删除宠物帖子" as UC14
  usecase "彻底删除" as UC15
  usecase "恢复宠物帖子" as UC16
  usecase "下线宠物帖子" as UC17
  usecase "标记已完成" as UC18
  usecase "收藏/取消收藏" as UC19
  usecase "查看收藏列表" as UC20
}

User --> UC11
User --> UC13
User --> UC14
User --> UC15
User --> UC16
User --> UC17
User --> UC18
User --> UC19
User --> UC20
User --> UC12
Guest --> UC12

@enduml

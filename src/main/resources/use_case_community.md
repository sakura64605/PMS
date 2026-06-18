@startuml
left to right direction
!theme plain

actor "用户" as User
actor "游客" as Guest

rectangle "社区互动" {
  usecase "点赞/取消点赞" as UC21
  usecase "发表评论" as UC22
  usecase "删除评论" as UC24
  usecase "发布每日动态" as UC38
  usecase "浏览动态流" as UC39
  usecase "浏览首页Feed" as UC40
  usecase "话题互动" as UC41
}

User --> UC21
User --> UC22
User --> UC24
User --> UC38
User --> UC40
User --> UC41
User --> UC39
Guest --> UC39

@enduml

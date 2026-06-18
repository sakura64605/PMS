@startuml
left to right direction
!theme plain

actor "用户" as User
actor "游客" as Guest

rectangle "活动管理" {
  usecase "发布活动" as UC28
  usecase "查看活动列表" as UC29
  usecase "编辑活动" as UC30
  usecase "删除活动" as UC31
  usecase "恢复活动" as UC32
  usecase "报名活动" as UC33
  usecase "取消报名" as UC34
  usecase "活动签到" as UC35
  usecase "查看我的活动" as UC36
  usecase "查看报名列表" as UC37
}

User --> UC28
User --> UC30
User --> UC31
User --> UC32
User --> UC33
User --> UC34
User --> UC35
User --> UC36
User --> UC37
User --> UC29
Guest --> UC29

@enduml

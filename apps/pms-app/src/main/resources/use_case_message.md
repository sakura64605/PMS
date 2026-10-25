@startuml
left to right direction
!theme plain

actor "用户" as User

rectangle "消息与私信" {
  usecase "发送/接收私信" as UC42
  usecase "查看私信会话" as UC43
  usecase "查看系统公告" as UC44
  usecase "查看消息通知" as UC45
  usecase "标记消息已读" as UC46
}

User --> UC42
User --> UC43
User --> UC44
User --> UC45
User --> UC46

@enduml

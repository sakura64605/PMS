@startuml
left to right direction
!theme plain

actor "用户" as User

rectangle "用户管理" {
  usecase "用户注册" as UC1
  usecase "用户登录" as UC2
  usecase "退出登录" as UC3
  usecase "查看个人信息" as UC4
  usecase "编辑个人信息" as UC5
  usecase "查看他人主页" as UC6
  usecase "搜索用户" as UC7
  usecase "修改密码" as UC8
  usecase "上传头像" as UC9
  usecase "切换历史头像" as UC10
  usecase "关注/取消关注" as UC25
  usecase "查看关注列表" as UC26
  usecase "查看粉丝列表" as UC27
}

User --> UC1
User --> UC2
User --> UC3
User --> UC4
User --> UC5
User --> UC6
User --> UC7
User --> UC8
User --> UC9
User --> UC10
User --> UC25
User --> UC26
User --> UC27

@enduml

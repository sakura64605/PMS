@startuml
left to right direction
!theme plain

actor "游客" as Guest
actor "用户" as User
actor "管理员" as Admin
actor "AI助手" as AIAgent

rectangle "用户管理" {
  usecase "UC1:用户注册" as UC1
  usecase "UC2:用户登录" as UC2
  usecase "UC3:退出登录" as UC3
  usecase "UC4:查看个人信息" as UC4
  usecase "UC5:编辑个人信息" as UC5
  usecase "UC6:查看他人主页" as UC6
  usecase "UC7:搜索用户" as UC7
  usecase "UC8:修改密码" as UC8
  usecase "UC9:上传头像" as UC9
  usecase "UC10:切换历史头像" as UC10
}

rectangle "宠物帖子" {
  usecase "UC11:发布宠物帖子" as UC11
  usecase "UC12:查看宠物帖子" as UC12
  usecase "UC13:编辑宠物帖子" as UC13
  usecase "UC14:删除宠物帖子" as UC14
  usecase "UC15:彻底删除" as UC15
  usecase "UC16:恢复宠物帖子" as UC16
  usecase "UC17:下线宠物帖子" as UC17
  usecase "UC18:标记已完成" as UC18
  usecase "UC19:收藏/取消收藏" as UC19
  usecase "UC20:查看
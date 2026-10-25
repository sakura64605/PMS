@startuml
left to right direction
!theme plain

actor "用户" as User
actor "管理员" as Admin
actor "AI助手" as AIAgent

rectangle "平台管理" {
  usecase "提交举报" as UC57
  usecase "查看举报列表" as UC58
  usecase "处理举报" as UC59
  usecase "审核内容" as UC60
  usecase "批量审核" as UC61
  usecase "管理用户账号" as UC64
  usecase "批量重置密码" as UC65
  usecase "发布系统公告" as UC66
  usecase "查看数据统计" as UC67
  usecase "管理AI知识库" as UC69
  usecase "重建搜索索引" as UC70
  usecase "智能问答" as UC47
  usecase "知识库检索" as UC48
  usecase "查询我的内容" as UC49
  usecase "搜索平台内容" as UC50
  usecase "查看热门话题" as UC55
  usecase "人工转接" as UC56
}

User --> UC57
User --> UC47
User --> UC56
Admin --> UC58
Admin --> UC59
Admin --> UC60
Admin --> UC61
Admin --> UC64
Admin --> UC65
Admin --> UC66
Admin --> UC67
Admin --> UC69
Admin --> UC70
AIAgent --> UC47
AIAgent --> UC48
AIAgent --> UC49
AIAgent --> UC50
AIAgent --> UC55

@enduml

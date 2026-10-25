@startuml
!theme plain

package "消息与私信" {
  package "controller" {
    class MessageController
    class PrivateMessageController
    class NoticeController
  }
  
  package "service" {
    class MessageService
    class PrivateMessageService
    class NoticeService
  }
  
  package "mapper" {
    class UserMessageMapper
    class PrivateMessageMapper
    class PrivateConversationMapper
    class NoticeMapper
    class NoticeReadRecordMapper
  }
  
  package "entity" {
    class UserMessage
    class PrivateMessage
    class PrivateConversation
    class Notice
    class NoticeReadRecord
  }
  
  package "websocket" {
    class WebSocketHandler
  }
}

controller --> service
service --> mapper
mapper --> entity

@enduml

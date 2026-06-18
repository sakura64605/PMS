@startuml
!theme plain

package "消息与私信" {
  class PrivateConversation {
    +Long id
    +Long userId
    +Long otherUserId
    +String lastMessage
    +LocalDateTime lastMessageTime
    +Integer userUnreadCount
    +Integer otherUnreadCount
    +LocalDateTime createTime
  }
  
  class PrivateMessage {
    +Long id
    +Long conversationId
    +Long senderId
    +Long receiverId
    +String content
    +Integer senderDelete
    +Integer receiverDelete
    +Integer status
    +LocalDateTime createTime
  }
  
  class UserMessage {
    +Long id
    +Long fromUserId
    +Long toUserId
    +String content
    +Integer type
    +Integer status
    +Long relatedId
    +LocalDateTime createTime
  }
  
  class Notice {
    +Long id
    +String title
    +String content
    +Integer type
    +Integer priority
    +Integer status
    +Integer isTop
    +LocalDateTime scheduledPublishTime
    +LocalDateTime expireTime
    +LocalDateTime createTime
  }
  
  class NoticeReadRecord {
    +Long id
    +Long noticeId
    +Long userId
    +LocalDateTime readTime
  }
}

@enduml

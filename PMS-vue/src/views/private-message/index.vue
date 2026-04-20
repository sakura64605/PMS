<template>
  <div class="private-message-container">
    <!-- 左侧会话列表 -->
    <div class="conversation-list">
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索会话"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
      
      <!-- 新私信按钮 -->
      <div class="new-message-btn">
        <el-button type="primary" @click="handleNewMessageClick">
          <el-icon><Plus /></el-icon>
          新私信
        </el-button>
      </div>
      
      <!-- 会话列表 -->
      <div class="conversations">
        <div
          v-for="conversation in conversations"
          :key="conversation.conversationId"
          class="conversation-item"
          :class="{ active: activeConversationId === conversation.conversationId }"
          @click="selectConversation(conversation)"
        >
          <div class="conversation-avatar">
            <el-avatar :size="48" :src="conversation.otherUser.avatar || 'https://via.placeholder.com/48'">
              {{ conversation.otherUser.nickname?.charAt(0) || '?' }}
            </el-avatar>
            <el-badge v-if="conversation.unreadCount > 0" :value="conversation.unreadCount" type="danger" class="unread-badge"></el-badge>
          </div>
          <div class="conversation-info">
            <div class="conversation-header">
              <h3 class="conversation-name">{{ conversation.otherUser.nickname || conversation.otherUser.username }}</h3>
              <span class="conversation-time">{{ formatTime(conversation.lastMessageTime) }}</span>
            </div>
            <p class="conversation-last-message">{{ conversation.lastMessage || '暂无消息' }}</p>
          </div>
        </div>
        <div v-if="conversations.length === 0" class="empty-conversations">
          <el-empty description="暂无会话" />
        </div>
      </div>
    </div>
    
    <!-- 右侧聊天区域 -->
    <div class="chat-area" v-if="activeConversation">
      <!-- 聊天头部 -->
      <div class="chat-header">
        <div class="chat-header-info">
          <el-avatar :size="32" :src="activeConversation.otherUser.avatar || 'https://via.placeholder.com/32'">
            {{ activeConversation.otherUser.nickname?.charAt(0) || '?' }}
          </el-avatar>
          <div class="chat-header-details">
            <h3 class="chat-header-name">{{ activeConversation.otherUser.nickname || activeConversation.otherUser.username }}</h3>
            <span class="chat-header-status">在线</span>
          </div>
        </div>
        <div class="chat-header-actions">
          <el-dropdown>
            <el-button type="text">
              <el-icon><More /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleMarkRead">标记已读</el-dropdown-item>
                <el-dropdown-item @click="handleClearMessages">清空聊天记录</el-dropdown-item>
                <el-dropdown-item @click="handleDeleteConversation" type="danger">删除会话</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      
      <!-- 消息列表 -->
      <div class="message-list" ref="messageList">
        <div
          v-for="message in messages"
          :key="message.id"
          class="message-item"
          :class="{ 'my-message': message.fromUserId === currentUserId }"
        >
          <div class="message-avatar">
            <el-avatar :size="32" :src="message.fromUserAvatar || 'https://via.placeholder.com/32'">
              {{ message.fromUserName?.charAt(0) || '?' }}
            </el-avatar>
          </div>
          <div class="message-content">
            <div class="message-bubble" :class="{ 'my-message': message.fromUserId === currentUserId }">
              {{ message.content }}
            </div>
            <div class="message-time">{{ formatTime(message.createTime) }}</div>
          </div>
        </div>
        <div v-if="messages.length === 0" class="empty-messages">
          <el-empty description="暂无消息" />
        </div>
      </div>
      
      <!-- 输入区域 -->
      <div class="chat-input-area">
        <div class="input-tools">
          <el-button type="text" @click="toggleEmoji">
            <el-icon><ChatRound /></el-icon>
          </el-button>
          <el-button type="text">
            <el-icon><Paperclip /></el-icon>
          </el-button>
          <el-button type="text">
            <el-icon><Picture /></el-icon>
          </el-button>
        </div>
        <div class="input-container">
          <el-input
            v-model="messageContent"
            type="textarea"
            :rows="3"
            placeholder="输入消息..."
            @keyup.enter.exact="sendMessage"
          />
          <el-button type="primary" @click="sendMessage">发送</el-button>
        </div>
      </div>
    </div>
    
    <!-- 空状态 -->
    <div class="empty-state" v-else>
      <el-empty description="选择一个会话开始聊天" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Plus, More, ChatRound, Paperclip, Picture } from '@element-plus/icons-vue'
import request from '../../utils/request'
import emitter from '../../utils/eventBus'

// 路由
const router = useRouter()

// 状态
const conversations = ref<any[]>([])
const activeConversation = ref<any>(null)
const activeConversationId = ref<number | null>(null)
const messages = ref<any[]>([])
const messageContent = ref('')
const searchKeyword = ref('')
const currentUserId = ref<number>(0)
const messageList = ref<HTMLElement | null>(null)

// 方法
const formatTime = (timeStr: string | null) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const handleSearch = () => {
  // 搜索会话逻辑
  console.log('搜索会话:', searchKeyword.value)
}

const handleNewMessageClick = () => {
  // 新私信逻辑
  ElMessage.info('新私信功能开发中')
}

const selectConversation = (conversation: any) => {
  activeConversation.value = conversation
  activeConversationId.value = conversation.conversationId
  fetchMessages(conversation.conversationId)
  markAsRead(conversation.conversationId)
}

const fetchConversations = async () => {
  try {
    const response = await request({
      url: '/message/private/conversations',
      method: 'get',
      params: {
        pageNum: 1,
        pageSize: 20
      }
    })
    if (response.code === 200 && response.data) {
      conversations.value = response.data.records || []
    }
  } catch (error) {
    console.error('获取会话列表失败:', error)
    ElMessage.error('获取会话列表失败')
  }
}

const fetchMessages = async (conversationId: number) => {
  try {
    const response = await request({
      url: '/message/private/messages',
      method: 'get',
      params: {
        conversationId,
        pageNum: 1,
        pageSize: 100
      }
    })
    if (response.code === 200 && response.data) {
      messages.value = response.data.records || []
      console.log('Messages:', messages.value)
      console.log('Current user ID:', currentUserId.value)
      // 滚动到底部
      nextTick(() => {
        if (messageList.value) {
          messageList.value.scrollTop = messageList.value.scrollHeight
        }
      })
    }
  } catch (error) {
    console.error('获取聊天记录失败:', error)
    ElMessage.error('获取聊天记录失败')
  }
}

const sendMessage = async () => {
  if (!messageContent.value.trim() || !activeConversation.value) return
  
  try {
    const response = await request({
      url: '/message/private/send',
      method: 'post',
      data: {
        toUserId: activeConversation.value.otherUser.userId,
        content: messageContent.value,
        messageType: 1
      }
    })
    if (response.code === 200 && response.data) {
      // 添加消息到列表
      messages.value.push(response.data)
      messageContent.value = ''
      // 滚动到底部
      nextTick(() => {
        if (messageList.value) {
          messageList.value.scrollTop = messageList.value.scrollHeight
        }
      })
      // 刷新会话列表
      await fetchConversations()
      // 刷新聊天记录
      await fetchMessages(activeConversation.value.conversationId)
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    ElMessage.error('发送消息失败')
  }
}

const markAsRead = async (conversationId: number) => {
  try {
    await request({
      url: `/message/private/read/${conversationId}`,
      method: 'put'
    })
    // 更新未读计数
    const conversationIndex = conversations.value.findIndex(c => c.conversationId === conversationId)
    if (conversationIndex !== -1) {
      conversations.value[conversationIndex].unreadCount = 0
    }
  } catch (error) {
    console.error('标记已读失败:', error)
  }
}

const handleMarkRead = () => {
  if (activeConversationId.value) {
    markAsRead(activeConversationId.value)
  }
}

const handleClearMessages = async () => {
  if (!activeConversationId.value) return
  
  try {
    await request({
      url: `/message/private/messages/${activeConversationId.value}`,
      method: 'delete'
    })
    messages.value = []
    ElMessage.success('清空聊天记录成功')
  } catch (error) {
    console.error('清空聊天记录失败:', error)
    ElMessage.error('清空聊天记录失败')
  }
}

const handleDeleteConversation = async () => {
  if (!activeConversationId.value) return
  
  try {
    await request({
      url: `/message/private/conversation/${activeConversationId.value}`,
      method: 'delete'
    })
    // 从会话列表中移除
    conversations.value = conversations.value.filter(c => c.conversationId !== activeConversationId.value)
    activeConversation.value = null
    activeConversationId.value = null
    messages.value = []
    ElMessage.success('删除会话成功')
  } catch (error) {
    console.error('删除会话失败:', error)
    ElMessage.error('删除会话失败')
  }
}

const toggleEmoji = () => {
  // 表情选择器逻辑
  ElMessage.info('表情功能开发中')
}

// 监听新消息事件
const handleNewMessage = async (message: any) => {
  console.log('收到新消息:', message)
  console.log('消息类型:', message.type)
  console.log('消息数据:', message.data)
  console.log('消息所有属性:', Object.keys(message))
  
  // 尝试刷新会话列表，不管消息类型
  console.log('尝试刷新会话列表')
  await fetchConversations()
  
  // 如果当前正在查看的会话，尝试刷新聊天记录
  if (activeConversation.value) {
    console.log('当前活跃会话:', activeConversation.value)
    console.log('尝试刷新聊天记录')
    await fetchMessages(activeConversation.value.conversationId)
  }
}

// 生命周期
onMounted(async () => {
  // 注册事件监听器
  console.log('注册新消息事件监听器')
  emitter.on('new-message', handleNewMessage)
  
  // 清理监听器
  onUnmounted(() => {
    console.log('移除新消息事件监听器')
    emitter.off('new-message', handleNewMessage)
  })
  
  // 获取当前用户ID
  const userInfo = localStorage.getItem('userInfo')
  console.log('User info from localStorage:', userInfo)
  if (userInfo) {
    try {
      const parsedUserInfo = JSON.parse(userInfo)
      console.log('Parsed user info:', parsedUserInfo)
      // 尝试从不同字段获取用户ID
      currentUserId.value = parsedUserInfo.userId || 
                           parsedUserInfo.id || 
                           parsedUserInfo.user?.userId || 
                           parsedUserInfo.user?.id || 
                           parsedUserInfo.UserId || 
                           parsedUserInfo.ID || 
                           0
      console.log('Current user ID:', currentUserId.value)
      
      // 打印所有可能的用户ID字段
      console.log('All possible user ID fields:')
      console.log('parsedUserInfo.userId:', parsedUserInfo.userId)
      console.log('parsedUserInfo.id:', parsedUserInfo.id)
      console.log('parsedUserInfo.user?.userId:', parsedUserInfo.user?.userId)
      console.log('parsedUserInfo.user?.id:', parsedUserInfo.user?.id)
      console.log('parsedUserInfo.UserId:', parsedUserInfo.UserId)
      console.log('parsedUserInfo.ID:', parsedUserInfo.ID)
    } catch (error) {
      console.error('Failed to parse user info:', error)
    }
  } else {
    console.log('No user info in localStorage')
  }
  
  // 如果还是没有获取到用户ID，尝试从token中获取
  if (currentUserId.value === 0) {
    const token = localStorage.getItem('token')
    console.log('Token from localStorage:', token)
    if (token) {
      try {
        // 解析token获取用户ID
        const tokenParts = token.split('.')
        if (tokenParts.length === 3) {
          const payload = JSON.parse(atob(tokenParts[1]))
          console.log('Token payload:', payload)
          currentUserId.value = payload.userId || payload.user_id || payload.id || 0
          console.log('Current user ID from token:', currentUserId.value)
        }
      } catch (error) {
        console.error('Failed to parse token:', error)
      }
    }
  }
  
  // 如果还是没有获取到用户ID，尝试从sessionStorage获取
  if (currentUserId.value === 0) {
    const sessionUserInfo = sessionStorage.getItem('userInfo')
    console.log('User info from sessionStorage:', sessionUserInfo)
    if (sessionUserInfo) {
      try {
        const parsedSessionUserInfo = JSON.parse(sessionUserInfo)
        console.log('Parsed session user info:', parsedSessionUserInfo)
        // 尝试从不同字段获取用户ID
        currentUserId.value = parsedSessionUserInfo.userId || 
                             parsedSessionUserInfo.id || 
                             parsedSessionUserInfo.user?.userId || 
                             parsedSessionUserInfo.user?.id || 
                             parsedSessionUserInfo.UserId || 
                             parsedSessionUserInfo.ID || 
                             0
        console.log('Current user ID from sessionStorage:', currentUserId.value)
      } catch (error) {
        console.error('Failed to parse session user info:', error)
      }
    }
  }
  
  // 打印最终的用户ID
  console.log('Final current user ID:', currentUserId.value)
  
  // 获取会话列表
  await fetchConversations()
  
  // 检查是否有 conversationId 参数，如果有则直接激活会话
  const conversationId = route.query.conversationId
  if (conversationId) {
    const conversation = conversations.value.find(c => c.conversationId === Number(conversationId))
    if (conversation) {
      activeConversation.value = conversation
      activeConversationId.value = conversation.conversationId
      // 获取聊天记录
      fetchMessages(Number(conversationId))
    }
  }
  // 检查是否有 otherUserId 参数，如果有则自动创建或获取会话
  else {
    const otherUserId = route.query.otherUserId
    if (otherUserId) {
      try {
        const response = await request({
          url: '/message/private/conversation',
          method: 'get',
          params: {
            otherUserId: Number(otherUserId)
          }
        })
        if (response.code === 200 && response.data) {
          // 查找或添加会话到列表
          const existingConversation = conversations.value.find(c => c.conversationId === response.data.conversationId)
          if (existingConversation) {
            // 会话已存在，直接激活
            activeConversation.value = existingConversation
            activeConversationId.value = existingConversation.conversationId
          } else {
            // 会话不存在，添加到列表并激活
            conversations.value.unshift(response.data)
            activeConversation.value = response.data
            activeConversationId.value = response.data.conversationId
          }
          // 获取聊天记录
          fetchMessages(response.data.conversationId)
        }
      } catch (error) {
        console.error('创建会话失败:', error)
      }
    }
  }
})
</script>

<style scoped>
.private-message-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  background-color: #f5f7fa;
  overflow: hidden;
  margin: 0;
  padding: 0;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  box-sizing: border-box;
}

/* 左侧会话列表 */
.conversation-list {
  width: 350px;
  background-color: #ffffff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.search-bar {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.new-message-btn {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.conversations {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.3s;
  margin-bottom: 4px;
}

.conversation-item:hover {
  background-color: #f5f7fa;
}

.conversation-item.active {
  background-color: #ecf5ff;
}

.conversation-avatar {
  position: relative;
  margin-right: 12px;
}

.unread-badge {
  position: absolute;
  top: -4px;
  right: -4px;
}

.conversation-info {
  flex: 1;
  min-width: 0;
}

.conversation-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.conversation-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-time {
  font-size: 12px;
  color: #909399;
}

.conversation-last-message {
  font-size: 12px;
  color: #606266;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.empty-conversations {
  padding: 40px 20px;
  text-align: center;
}

/* 右侧聊天区域 */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: #ffffff;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #fafafa;
}

.chat-header-info {
  display: flex;
  align-items: center;
}

.chat-header-info .el-avatar {
  margin-right: 12px;
}

.chat-header-details {
  min-width: 0;
}

.chat-header-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 4px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-header-status {
  font-size: 12px;
  color: #67c23a;
}

.chat-header-actions {
  display: flex;
  align-items: center;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.message-item {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
  /* 默认左对齐 */
  justify-content: flex-start;
}

/* 自己的消息靠右 */
.message-item.my-message {
  justify-content: flex-end;
}

.message-avatar {
  margin: 0 12px;
  flex-shrink: 0;
}

.message-content {
  max-width: 60%;
  min-width: 120px;
}

/* 自己的消息头像和内容顺序调整 */
.message-item.my-message .message-avatar {
  order: 2;
  margin: 0 0 0 12px;
}

.message-item.my-message .message-content {
  order: 1;
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.4;
  word-wrap: break-word;
}

/* 对方消息气泡样式 */
.message-item:not(.my-message) .message-bubble {
  background-color: #ffffff;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

/* 自己的消息气泡样式 */
.message-item.my-message .message-bubble {
  background-color: #409eff;
  color: #ffffff;
  border-bottom-right-radius: 4px;
}

.message-time {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
  text-align: right;
}

/* 对方消息的时间左对齐 */
.message-item:not(.my-message) .message-time {
  text-align: left;
}

.empty-messages {
  padding: 40px 20px;
  text-align: center;
}

.chat-input-area {
  border-top: 1px solid #e4e7ed;
  padding: 16px 24px;
  background-color: #fafafa;
}

.input-tools {
  display: flex;
  margin-bottom: 12px;
  gap: 12px;
}

.input-container {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.input-container .el-input {
  flex: 1;
}

.input-container .el-button {
  margin-bottom: 4px;
}

/* 空状态 */
.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f7fa;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .private-message-container {
    flex-direction: column;
  }
  
  .conversation-list {
    width: 100%;
    height: 300px;
    border-right: none;
    border-bottom: 1px solid #e4e7ed;
  }
  
  .chat-area {
    flex: 1;
  }
  
  .message-content {
    max-width: 80%;
  }
}
</style>
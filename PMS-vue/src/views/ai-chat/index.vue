<template>
  <div class="ai-chat-container">
    <!-- 会话列表侧边栏 -->
    <div class="session-sidebar" :class="{ collapsed: !showSessionList }">
      <div class="sidebar-header">
        <h3>历史对话</h3>
      </div>
      <div class="session-list">
        <div
          v-for="session in sessionList"
          :key="session.sessionId"
          :class="['session-item', { active: session.sessionId === sessionId }]"
          @click="switchSession(session.sessionId)"
        >
          <div class="session-info">
            <el-icon><ChatLineRound /></el-icon>
            <div class="session-detail">
              <p class="session-title">对话 {{ formatSessionTime(session.updatedAt || session.createdAt) }}</p>
              <span class="session-time">{{ formatDateTime(session.updatedAt || session.createdAt) }}</span>
            </div>
          </div>
          <el-button
            @click.stop="handleDeleteSession(session.sessionId)"
            :icon="Delete"
            size="small"
            type="danger"
            text
          />
        </div>
        <el-empty v-if="sessionList.length === 0" description="暂无历史对话" :image-size="80" />
      </div>
    </div>

    <!-- 主聊天区域 -->
    <div class="chat-main">
    <!-- 聊天头部 -->
    <div class="chat-header">
      <div class="header-info">
        <el-icon :size="24" color="#409EFF"><ChatDotRound /></el-icon>
        <div class="header-text">
          <h3>AI助手 - 宠小伴</h3>
          <p class="status">{{ isConnected ? '在线' : '' }}</p>
        </div>
      </div>
      <div class="header-actions">
        <el-button @click="toggleSessionList" :icon="showSessionList ? Fold : Expand" size="small">
          {{ showSessionList ? '隐藏会话' : '显示会话' }}
        </el-button>
        <el-button @click="handleNewChat" :icon="Plus" size="small">
          新对话
        </el-button>
        <el-button @click="handleClearMemory" :icon="Delete" size="small">
          清除记忆
        </el-button>
        <el-button @click="handleTransferHuman" type="warning" size="small">
          转人工
        </el-button>
      </div>
    </div>

    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="welcome-message">
        <el-icon :size="48" color="#409EFF"><ChatDotRound /></el-icon>
        <h3>您好！我是 AI助手宠小伴 😊</h3>
        <p>有什么可以帮您的吗？</p>
        <div class="suggestions" v-if="suggestions.length > 0">
          <p>您可以尝试提问：</p>
          <el-tag
            v-for="(item, index) in suggestions"
            :key="index"
            class="suggestion-tag"
            @click="handleSuggestionClick(item)"
          >
            {{ item }}
          </el-tag>
        </div>
      </div>

      <div
        v-for="(msg, index) in messages"
        :key="index"
        :class="['message-item', msg.role === 'user' ? 'user-message' : 'ai-message']"
      >
        <div class="message-avatar">
          <el-icon v-if="msg.role === 'user'" :size="32" color="#409EFF"><User /></el-icon>
          <el-icon v-else :size="32" color="#67C23A"><ChatDotRound /></el-icon>
        </div>
        <div class="message-content">
          <div class="message-bubble">
            <div class="markdown-content" v-html="renderMarkdown(msg.content)"></div>
          </div>
          <div class="message-meta">
            <span class="message-time">{{ msg.time }}</span>
            <div v-if="msg.role === 'ai' && msg.messageId" class="message-actions">
              <el-button
                @click="handleFeedback(msg.messageId!, 1)"
                size="small"
                :icon="Check"
                circle
                title="有帮助"
              />
              <el-button
                @click="handleFeedback(msg.messageId!, 0)"
                size="small"
                :icon="Close"
                circle
                title="无帮助"
              />
              <el-button
                v-if="msg.needHuman"
                @click="handleTransferHuman"
                size="small"
                type="warning"
              >
                转人工客服
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="isLoading" class="message-item ai-message">
        <div class="message-avatar">
          <el-icon :size="32" color="#67C23A"><ChatDotRound /></el-icon>
        </div>
        <div class="message-content">
          <div class="message-bubble loading">
            <el-icon class="loading-icon"><Loading /></el-icon>
            <span>思考中...</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input">
      <el-input
        v-model="inputMessage"
        @keyup.enter="handleSendMessage"
        placeholder="请输入您的问题..."
        :disabled="isLoading"
        resize="none"
        :autosize="{ minRows: 1, maxRows: 4 }"
      />
      <el-button
        @click="handleSendMessage"
        type="primary"
        :icon="Promotion"
        :disabled="!inputMessage.trim() || isLoading"
        circle
      />
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, User, Promotion, Loading, Delete, Check, Close, Plus, Fold, Expand, ChatLineRound } from '@element-plus/icons-vue'
import { sendChatMessage, getChatHistory, clearMemory, transferToHuman, submitFeedback, getSuggestions, getUserSessions, deleteSession as deleteSessionApi } from '../../api/ai'
import { aiWebSocket } from '../../utils/aiWebSocket'
import { marked } from 'marked'

interface Message {
  role: 'user' | 'ai'
  content: string
  time: string
  messageId?: string
  needHuman?: boolean
}

const messages = ref<Message[]>([])
const inputMessage = ref('')
const isLoading = ref(false)
const isConnected = ref(false)
const sessionId = ref('')
const suggestions = ref<string[]>([])
const messagesRef = ref<HTMLElement>()
const sessionList = ref<any[]>([])
const showSessionList = ref(true)
const useWebSocket = ref(false)

// 初始化
onMounted(async () => {
  await loadSessionList()
  
  // 尝试从 localStorage 恢复 sessionId
  const savedSessionId = localStorage.getItem('ai_chat_session_id')
  if (savedSessionId) {
    sessionId.value = savedSessionId
    // 加载历史消息
    await loadHistory()
  }
  
  // 注意：不自动创建会话，首次发送消息时后端会自动创建
  await loadSuggestions()
  
  // 连接 WebSocket
  connectWebSocket()
})

onUnmounted(() => {
  aiWebSocket.disconnect()
})

// 加载历史消息
const loadHistory = async () => {
  if (!sessionId.value) return
  
  try {
    const res: any = await getChatHistory(sessionId.value)
    if (res.data && res.data.length > 0) {
      messages.value = res.data.map((msg: any) => ({
        role: msg.role === 'user' ? 'user' : 'ai',
        content: msg.content,
        time: formatTime(msg.createdAt),
        messageId: msg.messageId || msg.id
      }))
      scrollToBottom()
    }
  } catch (error) {
    console.error('加载历史消息失败:', error)
  }
}

// 加载建议问题
const loadSuggestions = async () => {
  try {
    const res: any = await getSuggestions()
    suggestions.value = res.data
  } catch (error) {
    console.error('加载建议失败:', error)
  }
}

// 连接 WebSocket
const connectWebSocket = () => {
  aiWebSocket.connect({
    onOpen: () => {
      isConnected.value = true
      useWebSocket.value = true
      console.log('WebSocket 连接成功')
    },
    onMessage: (data) => {
      isLoading.value = false
      messages.value.push({
        role: 'ai',
        content: data.content || data.answer,
        time: formatTime(new Date()),
        messageId: data.messageId,
        needHuman: data.needHuman
      })
      scrollToBottom()
    },
    onClose: () => {
      isConnected.value = false
      useWebSocket.value = false
      console.log('WebSocket 连接关闭')
    },
    onError: () => {
      isConnected.value = false
      useWebSocket.value = false
      console.log('WebSocket 连接失败，将使用 HTTP 方式')
    }
  })
}

// 发送消息
const handleSendMessage = async () => {
  const message = inputMessage.value.trim()
  if (!message || isLoading.value) return

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: message,
    time: formatTime(new Date())
  })
  inputMessage.value = ''
  isLoading.value = true
  scrollToBottom()

  console.log('=== 发送消息 ===')
  console.log('sessionId:', sessionId.value)
  console.log('message:', message)

  // 使用 HTTP 方式发送
  try {
    const res: any = await sendChatMessage({
      sessionId: sessionId.value || undefined,
      message: message
    })

    isLoading.value = false
    
    // 首次发送时，保存后端返回的 sessionId
    if (!sessionId.value && res.data.sessionId) {
      sessionId.value = res.data.sessionId
      localStorage.setItem('ai_chat_session_id', sessionId.value)
      // 刷新会话列表
      await loadSessionList()
    }

    messages.value.push({
      role: 'ai',
      content: res.data.content || res.data.answer,
      time: formatTime(new Date()),
      messageId: res.data.messageId,
      needHuman: res.data.needHuman,
      suggestions: res.data.suggestions
    })
    
    // 更新建议问题
    if (res.data.suggestions && res.data.suggestions.length > 0) {
      suggestions.value = res.data.suggestions
    }
    
    scrollToBottom()
  } catch (error) {
    isLoading.value = false
    ElMessage.error('发送消息失败')
  }
}

// 点击建议
const handleSuggestionClick = (item: string) => {
  inputMessage.value = item
  handleSendMessage()
}

// 加载会话列表
const loadSessionList = async () => {
  try {
    const res: any = await getUserSessions()
    sessionList.value = res.data || []
  } catch (error) {
    console.error('加载会话列表失败:', error)
  }
}

// 切换会话列表显示
const toggleSessionList = () => {
  showSessionList.value = !showSessionList.value
}

// 切换会话
const switchSession = async (newSessionId: string) => {
  if (newSessionId === sessionId.value) return
  
  sessionId.value = newSessionId
  localStorage.setItem('ai_chat_session_id', newSessionId)
  messages.value = []
  
  await loadHistory()
}

// 删除会话
const handleDeleteSession = async (sessionIdToDelete: string) => {
  try {
    await ElMessageBox.confirm('确定要删除这个对话吗？', '提示', {
      type: 'warning'
    })
    
    await deleteSessionApi(sessionIdToDelete)
    ElMessage.success('对话已删除')
    
    // 如果删除的是当前会话，清空聊天区域并重置sessionId
    if (sessionIdToDelete === sessionId.value) {
      sessionId.value = ''
      localStorage.removeItem('ai_chat_session_id')
      messages.value = []
    }
    
    // 刷新会话列表
    await loadSessionList()
  } catch (error) {
    // 用户取消
  }
}

// 新对话
const handleNewChat = async () => {
  try {
    await ElMessageBox.confirm('确定要开始新对话吗？当前对话记录将保存。', '提示', {
      type: 'info'
    })
    // 清空聊天区域，不调用后端接口，下次发送消息时自动创建会话
    sessionId.value = ''
    localStorage.removeItem('ai_chat_session_id')
    messages.value = []
    ElMessage.success('已开始新对话')
  } catch (error) {
    // 用户取消
  }
}

// 清除记忆
const handleClearMemory = async () => {
  try {
    await ElMessageBox.confirm('确定要清除当前会话的所有记忆吗？此操作不可恢复。', '提示', {
      type: 'warning'
    })
    await clearMemory(sessionId.value)
    messages.value = []
    ElMessage.success('会话记忆已清除')
  } catch (error) {
    // 用户取消
  }
}

// 转人工客服
const handleTransferHuman = async () => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入转接原因（可选）', '转接人工客服', {
      inputPlaceholder: '请输入原因...',
      type: 'info'
    })
    await transferToHuman(sessionId.value, reason)
    ElMessage.success('已为您转接人工客服，请稍候...')
  } catch (error) {
    // 用户取消
  }
}

// 提交反馈
const handleFeedback = async (messageId: string, score: number) => {
  try {
    await submitFeedback(messageId, score)
    ElMessage.success('感谢您的反馈！')
  } catch (error) {
    ElMessage.error('提交反馈失败')
  }
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

// Markdown 渲染
const renderMarkdown = (content: string) => {
  if (!content) return ''
  return marked(content, { breaks: true })
}

// 格式化时间（用于会话列表）
const formatSessionTime = (time: string) => {
  if (!time) return ''
  const date = new Date(time)
  if (isNaN(date.getTime())) return ''
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 86400000)
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate())

  if (target.getTime() === today.getTime()) {
    return `今天 ${formatTime(date)}`
  }
  if (target.getTime() === yesterday.getTime()) {
    return `昨天 ${formatTime(date)}`
  }
  return formatDateTime(time)
}

const formatDateTime = (time: string) => {
  if (!time) return ''
  const date = new Date(time)
  if (isNaN(date.getTime())) return ''
  const year = date.getFullYear()
  const month = (date.getMonth() + 1).toString().padStart(2, '0')
  const day = date.getDate().toString().padStart(2, '0')
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

// 格式化时间（用于消息）
const formatTime = (time: string | Date) => {
  if (!time) return ''
  const date = new Date(time)
  if (isNaN(date.getTime())) return ''
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${hours}:${minutes}`
}
</script>

<style scoped lang="scss">
.ai-chat-container {
  display: flex;
  height: 100vh;
  background: #f5f7fa;
  position: relative;

  .session-sidebar {
    width: 280px;
    background: #fff;
    border-right: 1px solid #e4e7ed;
    display: flex;
    flex-direction: column;
    transition: all 0.3s;

    &.collapsed {
      width: 0;
      overflow: hidden;
      border-right: none;
    }

    .sidebar-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px;
      border-bottom: 1px solid #e4e7ed;

      h3 {
        margin: 0;
        font-size: 16px;
        color: #303133;
      }
    }

    .session-list {
      flex: 1;
      overflow-y: auto;
      padding: 12px;

      .session-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 12px;
        margin-bottom: 8px;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.3s;
        background: #f8f9fa;

        &:hover {
          background: #ecf5ff;
        }

        &.active {
          background: #409EFF;
          color: #fff;

          .session-title,
          .session-time {
            color: #fff;
          }
        }

        .session-info {
          display: flex;
          align-items: center;
          gap: 12px;
          flex: 1;

          .session-detail {
            flex: 1;

            .session-title {
              margin: 0 0 4px;
              font-size: 14px;
              color: #303133;
              font-weight: 500;
            }

            .session-time {
              font-size: 12px;
              color: #909399;
            }
          }
        }
      }
    }
  }

  .chat-main {
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  .chat-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    background: #fff;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);

    .header-info {
      display: flex;
      align-items: center;
      gap: 12px;

      .header-text {
        h3 {
          margin: 0;
          font-size: 18px;
          color: #303133;
        }

        .status {
          margin: 4px 0 0;
          font-size: 12px;
          color: #67C23A;
        }
      }
    }

    .header-actions {
      display: flex;
      gap: 8px;
    }
  }

  .chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 20px;

    .welcome-message {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 60px 20px;
      text-align: center;

      h3 {
        margin: 16px 0 8px;
        font-size: 20px;
        color: #303133;
      }

      p {
        margin: 0 0 24px;
        font-size: 14px;
        color: #909399;
      }

      .suggestions {
        p {
          margin-bottom: 12px;
        }

        .suggestion-tag {
          margin: 4px;
          cursor: pointer;
          transition: all 0.3s;

          &:hover {
            transform: translateY(-2px);
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
          }
        }
      }
    }

    .message-item {
      display: flex;
      margin-bottom: 20px;
      gap: 12px;

      &.user-message {
        flex-direction: row-reverse;

        .message-content {
          align-items: flex-end;

          .message-bubble {
            background: #409EFF;
            color: #fff;
          }
        }
      }

      &.ai-message {
        .message-bubble {
          background: #fff;
          color: #303133;
        }
      }

      .message-avatar {
        flex-shrink: 0;
      }

      .message-content {
        display: flex;
        flex-direction: column;
        max-width: 70%;

        .message-bubble {
          padding: 12px 16px;
          border-radius: 12px;
          word-wrap: break-word;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);

          /* Markdown 渲染样式 */
          .markdown-content {
            line-height: 1.7;
            font-size: 14px;

            p { margin: 0 0 8px; &:last-child { margin-bottom: 0; } }
            h1, h2, h3, h4 { margin: 12px 0 6px; font-weight: 600; }
            h1 { font-size: 17px; }
            h2 { font-size: 16px; }
            h3 { font-size: 15px; }
            strong { font-weight: 600; }
            em { font-style: italic; }
            ul, ol { margin: 4px 0 8px; padding-left: 20px; }
            li { margin-bottom: 4px; line-height: 1.6; }
            code {
              background: rgba(0,0,0,0.06);
              padding: 2px 6px;
              border-radius: 4px;
              font-size: 13px;
              font-family: 'Consolas', monospace;
            }
            pre {
              background: rgba(0,0,0,0.06);
              padding: 12px;
              border-radius: 8px;
              overflow-x: auto;
              margin: 8px 0;
              code { background: none; padding: 0; }
            }
            blockquote {
              border-left: 3px solid #409EFF;
              padding-left: 12px;
              margin: 8px 0;
              color: #606266;
            }
            hr { border: none; border-top: 1px solid #e4e7ed; margin: 12px 0; }
          }

          &.loading {
            display: flex;
            align-items: center;
            gap: 8px;
            color: #909399;

            .loading-icon {
              animation: rotate 1s linear infinite;
            }
          }
        }

        .message-meta {
          display: flex;
          align-items: center;
          gap: 8px;
          margin-top: 6px;
          padding: 0 4px;

          .message-time {
            font-size: 12px;
            color: #909399;
          }

          .message-actions {
            display: flex;
            gap: 4px;
          }
        }
      }
    }
  }

  .chat-input {
    display: flex;
    gap: 12px;
    padding: 16px 20px;
    background: #fff;
    box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.1);

    .el-input {
      flex: 1;
    }
  }
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>

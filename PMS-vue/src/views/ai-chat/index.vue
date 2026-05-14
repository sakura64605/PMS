<template>
  <div class="ai-chat-container">
    <!-- 聊天头部 -->
    <div class="chat-header">
      <div class="header-info">
        <el-icon :size="24" color="#409EFF"><ChatDotRound /></el-icon>
        <div class="header-text">
          <h3>AI 客服助手 - 宠小伴</h3>
          <p class="status">{{ isConnected ? '在线' : '连接中...' }}</p>
        </div>
      </div>
      <div class="header-actions">
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
        <h3>您好！我是 AI 客服助手宠小伴 😊</h3>
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
            <p>{{ msg.content }}</p>
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
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, User, Promotion, Loading, Delete, Check, Close } from '@element-plus/icons-vue'
import { createSession, sendChatMessage, getChatHistory, clearMemory, transferToHuman, submitFeedback, getSuggestions } from '../../api/ai'
import { aiWebSocket } from '../../utils/aiWebSocket'

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

// 初始化
onMounted(async () => {
  await initSession()
  await loadSuggestions()
  connectWebSocket()
})

onUnmounted(() => {
  aiWebSocket.disconnect()
})

// 初始化会话
const initSession = async () => {
  try {
    const res: any = await createSession()
    sessionId.value = res.data
    // 加载历史消息
    await loadHistory()
  } catch (error) {
    ElMessage.error('创建会话失败')
  }
}

// 加载历史消息
const loadHistory = async () => {
  try {
    const res: any = await getChatHistory(sessionId.value)
    messages.value = res.data.map((msg: any) => ({
      role: msg.role === 'user' ? 'user' : 'ai',
      content: msg.content,
      time: formatTime(msg.createTime),
      messageId: msg.id
    }))
    scrollToBottom()
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
    },
    onError: () => {
      isConnected.value = false
      ElMessage.error('连接失败，请稍后重试')
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

  // 使用 HTTP 方式发送
  try {
    const res: any = await sendChatMessage({
      sessionId: sessionId.value,
      message: message
    })

    isLoading.value = false
    messages.value.push({
      role: 'ai',
      content: res.data.content || res.data.answer,
      time: formatTime(new Date()),
      messageId: res.data.messageId,
      needHuman: res.data.needHuman,
      suggestions: res.data.suggestions
    })
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

// 清除记忆
const handleClearMemory = async () => {
  try {
    await ElMessageBox.confirm('确定要清除会话记忆吗？', '提示', {
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

// 格式化时间
const formatTime = (time: string | Date) => {
  const date = new Date(time)
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${hours}:${minutes}`
}
</script>

<style scoped lang="scss">
.ai-chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;

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

          p {
            margin: 0;
            line-height: 1.6;
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

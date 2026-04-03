<template>
  <div class="message-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <h2>消息中心</h2>
          <el-button type="primary" @click="markAllRead" :loading="loading">
            全部标记已读
          </el-button>
        </div>
      </template>

      <div class="filter-bar">
        <el-select v-model="filterType" placeholder="消息类型" clearable>
          <el-option label="全部" value=""></el-option>
          <el-option label="点赞" value="LIKE"></el-option>
          <el-option label="评论" value="COMMENT"></el-option>
          <el-option label="关注" value="FOLLOW"></el-option>
          <el-option label="报名" value="SIGN_UP"></el-option>
          <el-option label="签到" value="SIGN_IN"></el-option>
          <el-option label="活动提醒" value="ACTIVITY_REMINDER"></el-option>
          <el-option label="满员提醒" value="ACTIVITY_FULL"></el-option>
          <el-option label="审核通过" value="AUDIT_PASS"></el-option>
          <el-option label="审核拒绝" value="AUDIT_REJECT"></el-option>
          <el-option label="惩罚通知" value="PUNISHMENT"></el-option>
          <el-option label="系统公告" value="NOTICE"></el-option>
          <el-option label="系统消息" value="SYSTEM"></el-option>
        </el-select>
      </div>

      <el-empty v-if="messageList.length === 0 && !loading" description="暂无消息"></el-empty>

      <div v-else class="message-list">
        <div
          v-for="message in messageList"
          :key="message.id"
          class="message-item"
          :class="{ 'unread': message.isRead === 0 }"
          @click="handleMessageClick(message)"
        >
          <div class="message-icon">
            <el-icon v-if="message.type === 'LIKE'">
              <StarFilled />
            </el-icon>
            <el-icon v-else-if="message.type === 'COMMENT'">
              <ChatLineRound />
            </el-icon>
            <el-icon v-else-if="message.type === 'FOLLOW'">
              <UserFilled />
            </el-icon>
            <el-icon v-else-if="message.type === 'SIGN_UP' || message.type === 'SIGN_IN'">
              <CircleCheckFilled />
            </el-icon>
            <el-icon v-else-if="message.type === 'ACTIVITY_REMINDER' || message.type === 'ACTIVITY_FULL'">
              <Timer />
            </el-icon>
            <el-icon v-else-if="message.type === 'AUDIT_PASS'">
              <SuccessFilled />
            </el-icon>
            <el-icon v-else-if="message.type === 'AUDIT_REJECT'">
              <WarningFilled />
            </el-icon>
            <el-icon v-else-if="message.type === 'PUNISHMENT'">
              <CircleCloseFilled />
            </el-icon>
            <el-icon v-else>
              <BellFilled />
            </el-icon>
          </div>
          <div class="message-content">
            <div class="message-title">{{ message.title }}</div>
            <div class="message-text">{{ message.content }}</div>
            <div class="message-time">{{ formatTime(message.createTime) }}</div>
          </div>
          <div class="message-badge">
            <el-badge v-if="message.isRead === 0" value="" type="danger" class="unread-badge"></el-badge>
          </div>
        </div>
      </div>

      <el-pagination
        v-if="total > 0"
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        layout="total, sizes, prev, pager, next, jumper"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { StarFilled, ChatLineRound, UserFilled, CircleCheckFilled, Timer, SuccessFilled, WarningFilled, CircleCloseFilled, BellFilled } from '@element-plus/icons-vue'
import { getMessageList, markMessageAsRead, markAllMessagesAsRead } from '../../api/message'
import { ElMessage } from 'element-plus'
import emitter from '../../utils/eventBus'

const router = useRouter()
const messageList = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterType = ref('')
const loading = ref(false)

// 加载消息列表
const loadMessages = async () => {
  loading.value = true
  try {
    const response = await getMessageList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      type: filterType.value || undefined
    })
    // 对返回的列表进行去重处理（按id去重）
    const records = response.data.records || []
    const uniqueMessages = []
    const idSet = new Set()
    for (const message of records) {
      if (!idSet.has(message.id)) {
        idSet.add(message.id)
        uniqueMessages.push(message)
      }
    }
    messageList.value = uniqueMessages
    total.value = response.data.total || 0
  } catch (error) {
    ElMessage.error('获取消息列表失败')
    console.error('获取消息列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 处理消息点击
const handleMessageClick = async (message: any) => {
  // 标记为已读
  if (message.isRead === 0) {
    try {
      await markMessageAsRead(message.id)
      message.isRead = 1
      // 通知MainLayout更新未读消息数量
      emitter.emit('refresh-unread-count')
    } catch (error) {
      console.error('标记已读失败:', error)
    }
  }

  // 跳转到对应页面
  try {
    let path = '/dashboard'
    let query = {}
    
    // 优先处理有link的消息
    if (message.link) {
      // 处理link格式，去除/pet-system前缀
      let link = message.link
      if (link.startsWith('/pet-system')) {
        link = link.replace('/pet-system', '')
      }
      
      // 确保路径以/开头
      if (!link.startsWith('/')) {
        link = '/' + link
      }
      
      // 解析link，支持 ?commentId=xxx
      const [linkPath, queryString] = link.split('?')
      if (queryString) {
        const urlParams = new URLSearchParams(queryString)
        urlParams.forEach((value, key) => {
          query[key] = value
        })
      }
      
      // 处理路径映射
      if (linkPath.startsWith('/pet/')) {
        // 宠物帖详情页
        const id = linkPath.split('/')[2]
        path = `/pets/${id}`
      } else if (linkPath.startsWith('/pet_post/')) {
        // 宠物帖相关路径（审核通知）
        const parts = linkPath.split('/')
        if (parts.length === 3) {
          // /pet_post/{id}
          const id = parts[2]
          path = `/pets/${id}`
        } else if (parts.length === 4 && parts[3] === 'edit') {
          // /pet_post/edit/{id}
          const id = parts[3]
          path = `/pets/${id}/edit`
        } else if (parts.length === 4 && parts[2] === 'edit') {
          // /pet_post/edit/{id}
          const id = parts[3]
          path = `/pets/${id}/edit`
        }
      } else if (linkPath.startsWith('/activity/')) {
        // 活动详情页
        const parts = linkPath.split('/')
        if (parts.length === 3) {
          // /activity/{id}
          const id = parts[2]
          path = `/activities/${id}`
        } else if (parts.length === 4 && parts[2] === 'signup-list') {
          // /activity/signup-list/{id}
          const id = parts[3]
          path = `/activities/${id}`
        }
      } else if (linkPath.startsWith('/pet_activity/')) {
        // 活动相关路径（审核通知）
        const parts = linkPath.split('/')
        if (parts.length === 3) {
          // /pet_activity/{id}
          const id = parts[2]
          path = `/activities/${id}`
        } else if (parts.length === 4 && parts[3] === 'edit') {
          // /pet_activity/edit/{id}
          const id = parts[3]
          path = `/activities/${id}/edit`
        } else if (parts.length === 4 && parts[2] === 'edit') {
          // /pet_activity/edit/{id}
          const id = parts[3]
          path = `/activities/${id}/edit`
        }
      } else if (linkPath.startsWith('/user/')) {
        // 用户主页
        const id = linkPath.split('/')[2]
        path = `/user/${id}`
      } else {
        // 其他路径
        path = linkPath
      }
    } else if (message.type === 'LIKE' || message.type === 'COMMENT') {
      // 点赞和评论的消息，尝试跳转到对应的宠物帖或活动详情页
      if (message.businessId && Number(message.businessId)) {
        path = `/pets/${message.businessId}`
      }
    } else if (message.type && message.businessId) {
      // 处理其他类型的消息
      if (message.type === 'SIGN_UP' || message.type === 'SIGN_IN' || message.type === 'ACTIVITY_REMINDER' || message.type === 'ACTIVITY_FULL') {
        // 活动相关消息，跳转到活动详情页
        path = `/activities/${message.businessId}`
      }
    }
    
    console.log('跳转到:', { path, query })
    if (Object.keys(query).length > 0) {
      router.push({ path, query })
    } else {
      router.push(path)
    }
  } catch (error) {
    console.error('跳转失败:', error)
    ElMessage.error('跳转失败，请稍后重试')
  }
}

// 全部标记已读
const markAllRead = async () => {
  try {
    await markAllMessagesAsRead(filterType.value || undefined)
    messageList.value.forEach(msg => {
      msg.isRead = 1
    })
    ElMessage.success('全部标记已读成功')
  } catch (error) {
    ElMessage.error('全部标记已读失败')
    console.error('全部标记已读失败:', error)
  }
}

// 分页处理
const handleSizeChange = (size: number) => {
  pageSize.value = size
  loadMessages()
}

const handleCurrentChange = (current: number) => {
  pageNum.value = current
  loadMessages()
}

// 格式化时间
const formatTime = (time: string) => {
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

// 监听筛选条件变化
watch(filterType, () => {
  pageNum.value = 1
  loadMessages()
})

// 处理新消息事件
const handleNewMessage = (message: any) => {
  console.log('收到新消息事件:', message)
  // 检查消息是否已存在于当前列表中
  const exists = messageList.value.some(item => item.id === message.id)
  if (!exists) {
    // 如果不存在，添加到列表顶部
    messageList.value.unshift(message)
    // 重新排序（按创建时间降序）
    messageList.value.sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime())
    // 更新总数
    total.value += 1
  }
  // 加载最新消息列表
  loadMessages()
}

// 处理消息刷新事件
const handleRefreshMessages = () => {
  // 重新加载消息列表
  loadMessages()
}

// 组件挂载时加载消息
onMounted(() => {
  loadMessages()
  // 监听新消息事件
  emitter.on('new-message', handleNewMessage)
  // 监听消息刷新事件
  emitter.on('refresh-messages', handleRefreshMessages)
})

// 组件卸载时移除事件监听
onUnmounted(() => {
  emitter.off('new-message', handleNewMessage)
  emitter.off('refresh-messages', handleRefreshMessages)
})
</script>

<style scoped>
.message-container {
  padding: 20px;
  min-height: 80vh;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  margin-bottom: 20px;
}

.message-list {
  margin-bottom: 20px;
}

.message-item {
  cursor: pointer;
  transition: all 0.3s;
  border-bottom: 1px solid #f0f0f0;
  padding: 15px 0;
  display: flex;
  align-items: flex-start;
}

.message-item:hover {
  background-color: #f5f7fa;
}

.message-item.unread {
  background-color: #f0f9ff;
  font-weight: 500;
}

.message-icon {
  font-size: 24px;
  margin-right: 15px;
  color: #409eff;
  flex-shrink: 0;
  margin-top: 2px;
}

.message-content {
  flex: 1;
}

.message-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 8px;
}

.message-text {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
  line-height: 1.5;
}

.message-time {
  font-size: 12px;
  color: #909399;
}

.message-badge {
  margin-left: 10px;
  flex-shrink: 0;
}

.unread-badge {
  margin-left: 0;
}
</style>

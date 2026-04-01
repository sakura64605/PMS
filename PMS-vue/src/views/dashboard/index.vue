<template>
  <div class="dashboard">
    <el-container>
      <el-header height="60px">
        <div class="header">
          <h1>宠物管理系统</h1>
          <el-button type="primary" @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main>
        <el-card>
          <template #header>
            <div class="card-header">
              <span>欢迎回来，{{ username }}</span>
            </div>
          </template>
          <div class="welcome">
            <p>这是宠物管理系统的首页</p>
            <p>您的角色：{{ role === 1 ? '系统管理员' : '普通用户' }}</p>
          </div>
        </el-card>
        
        <el-card class="notice-card">
          <template #header>
            <div class="card-header">
              <span>系统公告</span>
              <el-badge v-if="unreadCount > 0" :value="unreadCount" type="danger" />
            </div>
          </template>
          <div class="notice-list">
            <div v-for="notice in noticeList" :key="notice.id" class="notice-item" @click="handleNoticeClick(notice.id)">
              <div class="notice-header">
                <h3 :class="['notice-title', { 'unread': !notice.isRead, 'top': notice.isTop === 1 }]">
                  {{ notice.title }}
                  <span v-if="notice.isTop === 1" class="top-tag">置顶</span>
                </h3>
                <span :class="['notice-type', getNoticeTypeClass(notice.type)]">
                  {{ getNoticeTypeText(notice.type) }}
                </span>
              </div>
              <div class="notice-meta">
                <span class="notice-time">{{ notice.publishTime }}</span>
                <span v-if="notice.priority > 0" :class="['priority-tag', getPriorityClass(notice.priority)]">
                  {{ getPriorityText(notice.priority) }}
                </span>
              </div>
            </div>
            <div v-if="noticeList.length === 0" class="empty-section">
              <p>暂无公告</p>
            </div>
          </div>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getNoticeList, getUnreadCount } from '../../api/notice'

const router = useRouter()
const username = ref('')
const role = ref(0)
const noticeList = ref<any[]>([])
const unreadCount = ref(0)

onMounted(() => {
  // 从localStorage获取用户信息
  const userInfo = localStorage.getItem('userInfo')
  if (userInfo) {
    const info = JSON.parse(userInfo)
    username.value = info.username
    role.value = info.role
  }
  
  // 获取公告列表和未读数量
  fetchNoticeList()
  fetchUnreadCount()
})

const logout = () => {
  // 清除本地存储的token和用户信息
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  // 跳转到登录页面
  router.push('/login')
}

// 获取公告列表
const fetchNoticeList = async () => {
  try {
    const response = await getNoticeList({ pageNum: 1, pageSize: 5 })
    if (response.code === 200) {
      noticeList.value = response.data.records
    }
  } catch (error) {
    console.error('获取公告列表失败:', error)
  }
}

// 获取未读公告数量
const fetchUnreadCount = async () => {
  try {
    const response = await getUnreadCount()
    if (response.code === 200) {
      unreadCount.value = response.data
    }
  } catch (error) {
    console.error('获取未读公告数量失败:', error)
  }
}

// 处理公告点击
const handleNoticeClick = (id: number) => {
  // 跳转到公告详情页面
  router.push(`/notice/${id}`)
}

// 获取公告类型样式
const getNoticeTypeClass = (type: number) => {
  switch (type) {
    case 1: return 'system'
    case 2: return 'activity'
    case 3: return 'important'
    default: return ''
  }
}

// 获取公告类型文本
const getNoticeTypeText = (type: number) => {
  switch (type) {
    case 1: return '系统公告'
    case 2: return '活动通知'
    case 3: return '重要提醒'
    default: return ''
  }
}

// 获取优先级样式
const getPriorityClass = (priority: number) => {
  switch (priority) {
    case 1: return 'important'
    case 2: return 'urgent'
    default: return ''
  }
}

// 获取优先级文本
const getPriorityText = (priority: number) => {
  switch (priority) {
    case 1: return '重要'
    case 2: return '紧急'
    default: return ''
  }
}
</script>

<style scoped>
.dashboard {
  height: 100vh;
  background-color: #f5f7fa;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  padding: 0 20px;
  background-color: #409eff;
  color: white;
}

.header h1 {
  font-size: 20px;
  margin: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.welcome {
  padding: 20px 0;
}

.welcome p {
  margin: 10px 0;
  font-size: 16px;
}

.notice-card {
  margin-top: 20px;
}

.notice-list {
  padding: 10px 0;
}

.notice-item {
  padding: 15px 0;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  transition: all 0.3s;
}

.notice-item:hover {
  background-color: #f5f7fa;
  padding-left: 10px;
}

.notice-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}

.notice-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0;
  flex: 1;
}

.notice-title.unread {
  font-weight: bold;
}

.notice-title.top {
  color: #f56c6c;
}

.top-tag {
  display: inline-block;
  margin-left: 10px;
  padding: 2px 6px;
  font-size: 12px;
  background-color: #f56c6c;
  color: white;
  border-radius: 4px;
}

.notice-type {
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 12px;
  color: white;
}

.notice-type.system {
  background-color: #409eff;
}

.notice-type.activity {
  background-color: #67c23a;
}

.notice-type.important {
  background-color: #f56c6c;
}

.notice-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #909399;
}

.priority-tag {
  padding: 2px 8px;
  border-radius: 12px;
  color: white;
  font-size: 12px;
}

.priority-tag.important {
  background-color: #e6a23c;
}

.priority-tag.urgent {
  background-color: #f56c6c;
}

.empty-section {
  padding: 40px 0;
  text-align: center;
  color: #909399;
}
</style>
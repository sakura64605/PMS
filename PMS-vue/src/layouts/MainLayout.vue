<template>
  <div class="main-layout">
    <!-- 顶部导航栏 -->
    <el-header height="60px" class="header">
      <div class="header-left">
        <div class="logo">
          <el-avatar :size="40" src="https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cute%20cartoon%20pet%20logo%20with%20cat%20and%20dog%20silhouette%2C%20friendly%20style%2C%20blue%20and%20orange%20colors&image_size=square"></el-avatar>
          <span class="logo-text">PetCircle -宠友社</span>
        </div>
        <el-menu
          mode="horizontal"
          :default-active="activeMenu"
          class="top-menu"
          @select="handleMenuSelect"
        >
          <el-menu-item index="/dashboard">
            <el-icon><House /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <el-menu-item index="/pets">
            <el-icon><Collection /></el-icon>
            <template #title>宠友圈</template>
          </el-menu-item>
          <el-sub-menu index="/my">
            <template #title>
              <el-icon><User /></el-icon>
              <span>我的</span>
            </template>
            <el-menu-item index="/pets/my-posts">我的发布</el-menu-item>
            <el-menu-item index="/pets/collections">我的收藏</el-menu-item>
            <el-menu-item index="/recycle">回收站</el-menu-item>
          </el-sub-menu>
          <el-menu-item index="/daily">
            <el-icon><Postcard /></el-icon>
            <template #title>宠友日记</template>
          </el-menu-item>
          <el-menu-item index="/feed">
            <el-icon><Link /></el-icon>
            <template #title>关注</template>
          </el-menu-item>
          <el-menu-item index="/audit" v-if="userInfo?.role === 1">
            <el-icon><Check /></el-icon>
            <template #title>管理员后台</template>
          </el-menu-item>

        </el-menu>
      </div>
      <div class="header-right">
        <!-- 登录状态下显示消息和用户信息 -->
        <template v-if="userInfo">
          <el-dropdown @command="handleMessageCommand" class="message-dropdown">
            <span class="message-icon" @click="goToMessage">
              <el-icon><BellFilled /></el-icon>
              <el-badge v-if="unreadCount > 0" :value="unreadCount" type="danger" class="message-badge"></el-badge>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="message">查看消息</el-dropdown-item>
                <el-dropdown-item command="markAllRead">全部标记已读</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span class="message-dropdown" @click="goToPrivateMessage">
            <span class="message-icon">
              <el-icon><ChatDotRound /></el-icon>
            </span>
          </span>
          <el-dropdown>
            <span class="user-dropdown">
              <el-avatar :size="32" :src="userInfo?.avatar || 'https://via.placeholder.com/32'">
              </el-avatar>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goToProfile">
                  <el-icon><User /></el-icon>
                  <span>个人中心</span>
                </el-dropdown-item>
                <el-dropdown-item @click="logout">
                  <el-icon><SwitchButton /></el-icon>
                  <span>退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <!-- 未登录状态下显示登录按钮 -->
        <template v-else>
          <el-button type="primary" @click="goToLogin">登录</el-button>
        </template>
      </div>
    </el-header>

    <!-- 主体内容 -->
    <el-main class="content">
      <router-view />
    </el-main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Menu, ArrowDown, User, House, Collection, Ticket, Setting, SwitchButton, Postcard, Check, Delete, Star, BellFilled, Link, ChatDotRound } from '@element-plus/icons-vue'
import emitter from '../utils/eventBus'
import { getUnreadCount, markAllMessagesAsRead } from '../api/message'
import websocketService from '../utils/websocket'

const router = useRouter()
const route = useRoute()

// 当前活跃菜单
const activeMenu = computed(() => {
  return route.path
})

// 用户信息
const userInfo = ref<any>(null)
// 未读消息数量
const unreadCount = ref(0)

// 处理菜单选择
const handleMenuSelect = (key: string) => {
  router.push(key)
}

// 跳转到个人中心
const goToProfile = () => {
  router.push('/profile')
}

// 跳转到消息中心
const goToMessage = () => {
  router.push('/message')
}

// 跳转到私信页面
const goToPrivateMessage = () => {
  router.push('/private-message')
}

// 跳转到登录页面
const goToLogin = () => {
  router.push('/login')
}

// 退出登录
const logout = () => {
  // 清除本地存储
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  // 关闭WebSocket连接
  websocketService.close()
  // 跳转到登录页面
  router.push('/login')
  ElMessage.success('退出登录成功')
}

// 处理消息中心下拉菜单命令
const handleMessageCommand = (command: string) => {
  if (command === 'message') {
    router.push('/message')
  } else if (command === 'markAllRead') {
        markAllMessagesAsRead()
          .then(() => {
            ElMessage.success('全部标记已读成功')
            unreadCount.value = 0
            // 通知消息中心页面刷新消息列表
            emitter.emit('refresh-messages')
          })
          .catch(() => {
            ElMessage.error('全部标记已读失败')
          })
      }
}

// 加载未读消息数量
const loadUnreadCount = async () => {
  try {
    const response = await getUnreadCount()
    unreadCount.value = response.data || 0
  } catch (error) {
    console.error('获取未读消息数量失败:', error)
  }
}

// 处理WebSocket消息
const handleWebSocketMessage = (message: any) => {
  console.log('收到WebSocket消息:', message)
  // 显示系统通知
  if (message.title) {
    ElMessage({
      message: message.title,
      type: 'info',
      duration: 3000
    })
  }
  // 更新未读消息数量
  loadUnreadCount()
  // 通过EventBus发射新消息事件
  emitter.emit('new-message', message)
}

// 初始化WebSocket连接
const initWebSocket = () => {
  const token = localStorage.getItem('token')
  if (token) {
    websocketService.init(token)
    websocketService.setMessageCallback(handleWebSocketMessage)
  }
}

// 刷新用户信息
const refreshUserInfo = () => {
  const storedUserInfo = localStorage.getItem('userInfo')
  if (storedUserInfo) {
    userInfo.value = JSON.parse(storedUserInfo)
    console.log('刷新用户信息:', userInfo.value)
  }
}

// 页面加载时获取用户信息
onMounted(() => {
  refreshUserInfo()
  
  // 检查用户是否登录
  const token = localStorage.getItem('token')
  if (token) {
    // 加载未读消息数量
    loadUnreadCount()
    // 初始化WebSocket连接
    initWebSocket()
  }
  
  // 监听localStorage变化，实时更新用户信息
  window.addEventListener('storage', (event) => {
    if (event.key === 'userInfo' && event.newValue) {
      refreshUserInfo()
    } else if (event.key === 'token') {
      // 当token变化时，重新检查登录状态
      const newToken = localStorage.getItem('token')
      if (newToken) {
        loadUnreadCount()
        initWebSocket()
      } else {
        websocketService.close()
      }
    }
  })
  
  // 监听头像更新事件
  const handleAvatarUpdated = () => {
    console.log('收到头像更新事件，刷新用户信息')
    refreshUserInfo()
  }
  
  // 监听未读消息数量刷新事件
  const handleRefreshUnreadCount = () => {
    console.log('收到未读消息数量刷新事件，更新未读消息数量')
    loadUnreadCount()
  }
  
  emitter.on('avatar-updated', handleAvatarUpdated)
  emitter.on('refresh-unread-count', handleRefreshUnreadCount)
  
  // 清理监听器
  onUnmounted(() => {
    emitter.off('avatar-updated', handleAvatarUpdated)
    emitter.off('refresh-unread-count', handleRefreshUnreadCount)
    // 关闭WebSocket连接
    websocketService.close()
  })
  
  // 每5秒自动刷新一次用户信息，确保头像能够及时更新
  setInterval(refreshUserInfo, 5000)
  
  // 每30秒自动刷新一次未读消息数量
  setInterval(() => {
    const token = localStorage.getItem('token')
    if (token) {
      loadUnreadCount()
    }
  }, 30000)
})
</script>

<style scoped>
.main-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

.header {
  background-color: #ffffff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 30px;
  flex: 1;
}

.logo {
  display: flex;
  align-items: center;
  white-space: nowrap;
  gap: 12px;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.top-menu {
  flex: 1;
  border-bottom: none;
}

.top-menu .el-menu-item {
  margin: 0;
  height: 60px;
  line-height: 60px;
  min-width: 100px;
  text-align: center;
  border-radius: 8px;
  transition: all 0.3s ease;
  font-size: 14px;
  font-weight: 500;
}

.top-menu .el-menu-item:hover {
  background-color: #ecf5ff !important;
  color: #409eff !important;
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

.top-menu .el-menu-item.is-active {
  background-color: #409eff !important;
  color: white !important;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.4);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
  white-space: nowrap;
}

.message-dropdown {
  position: relative;
}

.message-icon {
  font-size: 22px;
  color: #606266;
  cursor: pointer;
  padding: 12px;
  border-radius: 50%;
  transition: all 0.3s ease;
  background-color: #f8f9fa;
  border: 1px solid #e9ecef;
}

.message-icon:hover {
  background-color: #ecf5ff;
  color: #409eff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

.message-badge {
  position: absolute;
  top: 0;
  right: 0;
  transform: translate(50%, -50%);
}

.user-dropdown {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0 12px;
  height: 44px;
  border-radius: 22px;
  transition: all 0.3s ease;
  background-color: #f8f9fa;
  border: 1px solid #e9ecef;
}

.user-dropdown:hover {
  background-color: #e9ecef;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.user-dropdown .el-avatar {
  transition: all 0.3s ease;
}

.user-dropdown:hover .el-avatar {
  transform: scale(1.05);
}

.user-name {
  margin: 0 8px;
  font-size: 14px;
  color: #606266;
}

.content {
  flex: 1;
  background-color: #f5f7fa;
  overflow-y: auto;
  padding: 20px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .top-menu-header {
    padding: 0 10px;
  }
  
  .top-menu .el-menu-item {
    min-width: 80px;
    font-size: 12px;
  }
  
  .top-menu .el-menu-item .el-icon {
    font-size: 14px;
  }
  
  .content {
    padding: 10px;
  }
}
</style>
<template>
  <div class="main-layout">
    <!-- 顶部导航栏 -->
    <el-header height="60px" class="header">
      <div class="header-left">
        <el-button
          type="text"
          class="menu-toggle"
          @click="toggleMenu"
        >
          <el-icon><Menu /></el-icon>
        </el-button>
        <div class="logo">
          <span class="logo-text">宠物管理系统</span>
        </div>
      </div>
      <div class="header-right">
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
      </div>
    </el-header>

    <!-- 主体内容 -->
    <div class="main-content">
      <!-- 左侧菜单栏 -->
      <el-aside :width="isCollapsed ? '64px' : '200px'" class="sidebar">
        <el-menu
          :collapse="isCollapsed"
          :default-active="activeMenu"
          class="menu"
          @select="handleMenuSelect"
        >
          <el-menu-item index="/dashboard">
            <el-icon><House /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <el-menu-item index="/pets">
            <el-icon><Collection /></el-icon>
            <template #title>领养/救助</template>
          </el-menu-item>
          <el-menu-item index="/pets/my-posts">
            <el-icon><Postcard /></el-icon>
            <template #title>我的发布</template>
          </el-menu-item>
          <el-menu-item index="/pets/collections">
            <el-icon><Star /></el-icon>
            <template #title>我的收藏</template>
          </el-menu-item>
          <el-menu-item index="/recycle">
            <el-icon><Delete /></el-icon>
            <template #title>回收站</template>
          </el-menu-item>
          <el-menu-item index="/audit" v-if="userInfo?.role === 1">
            <el-icon><Check /></el-icon>
            <template #title>审核</template>
          </el-menu-item>
          <el-menu-item index="/activities">
            <el-icon><Ticket /></el-icon>
            <template #title>活动管理</template>
          </el-menu-item>
          <el-menu-item index="/profile">
            <el-icon><User /></el-icon>
            <template #title>个人中心</template>
          </el-menu-item>
          <el-menu-item index="/settings">
            <el-icon><Setting /></el-icon>
            <template #title>系统设置</template>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- 右侧内容区 -->
      <el-main class="content">
        <router-view />
      </el-main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Menu, ArrowDown, User, House, Collection, Ticket, Setting, SwitchButton, Postcard, Check, Delete, Star } from '@element-plus/icons-vue'
import emitter from '../utils/eventBus'

const router = useRouter()
const route = useRoute()

// 菜单折叠状态
const isCollapsed = ref(false)

// 当前活跃菜单
const activeMenu = computed(() => {
  return route.path
})

// 用户信息
const userInfo = ref<any>(null)

// 切换菜单折叠状态
const toggleMenu = () => {
  isCollapsed.value = !isCollapsed.value
}

// 处理菜单选择
const handleMenuSelect = (key: string) => {
  router.push(key)
}

// 跳转到个人中心
const goToProfile = () => {
  router.push('/profile')
}

// 退出登录
const logout = () => {
  // 清除本地存储
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  // 跳转到登录页面
  router.push('/login')
  ElMessage.success('退出登录成功')
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
  
  // 监听localStorage变化，实时更新用户信息
  window.addEventListener('storage', (event) => {
    if (event.key === 'userInfo' && event.newValue) {
      refreshUserInfo()
    }
  })
  
  // 监听头像更新事件
  const handleAvatarUpdated = () => {
    console.log('收到头像更新事件，刷新用户信息')
    refreshUserInfo()
  }
  
  emitter.on('avatar-updated', handleAvatarUpdated)
  
  // 清理监听器
  onUnmounted(() => {
    emitter.off('avatar-updated', handleAvatarUpdated)
  })
  
  // 每5秒自动刷新一次用户信息，确保头像能够及时更新
  setInterval(refreshUserInfo, 5000)
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
}

.menu-toggle {
  font-size: 20px;
  margin-right: 20px;
  color: #606266;
}

.logo {
  display: flex;
  align-items: center;
}

.logo-img {
  width: 32px;
  height: 32px;
  margin-right: 10px;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #409eff;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-dropdown {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0 10px;
  height: 40px;
  border-radius: 20px;
  transition: background-color 0.3s;
}

.user-dropdown:hover {
  background-color: #f5f7fa;
}

.user-name {
  margin: 0 8px;
  font-size: 14px;
  color: #606266;
}

.main-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  background-color: #ffffff;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);
  transition: width 0.3s;
  overflow: hidden;
}

.menu {
  height: 100%;
  border-right: none;
}

.content {
  flex: 1;
  background-color: #f5f7fa;
  overflow-y: auto;
  padding: 20px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 60px;
    height: calc(100vh - 60px);
    z-index: 99;
    transform: translateX(-100%);
  }

  .sidebar.el-aside {
    transform: translateX(0);
  }

  .content {
    padding: 10px;
  }
}
</style>
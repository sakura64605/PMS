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
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const username = ref('')
const role = ref(0)

onMounted(() => {
  // 从localStorage获取用户信息
  const userInfo = localStorage.getItem('userInfo')
  if (userInfo) {
    const info = JSON.parse(userInfo)
    username.value = info.username
    role.value = info.role
  }
})

const logout = () => {
  // 清除本地存储的token和用户信息
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  // 跳转到登录页面
  router.push('/login')
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
</style>
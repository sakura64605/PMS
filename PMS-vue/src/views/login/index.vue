<template>
  <div class="login-container">
    <!-- 背景图片层 -->
    <div class="background-layer">
      <div class="background-overlay"></div>
    </div>
    
    <!-- 登录表单层 -->
    <div class="login-form-wrapper">
      <div class="login-header">
        <h2>宠物管理系统</h2>
        <p>欢迎回来，请登录</p>
      </div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        label-position="top"
      >
        <el-form-item label="账号" prop="account">
            <el-input
              v-model="loginForm.account"
              placeholder="请输入用户名或手机号"
              clearable
              @input="handleAccountChange"
            />
          </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            :show-password="!isAutoFilled"
          />
        </el-form-item>
        <el-form-item>
          <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
            <el-checkbox v-model="loginForm.remember" @change="handleRememberChange">记住密码</el-checkbox>
            <el-button type="text" @click="handleForgotPassword" style="padding: 0; margin: 0;">忘记密码？</el-button>
          </div>
        </el-form-item>
        <el-form-item style="margin-bottom: 15px;">
          <el-button
            type="primary"
            class="login-button"
            :loading="loading"
            @click="handleLogin"
            :disabled="loading"
          >
            登录
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-button
            type="default"
            class="register-button"
            @click="handleRegister"
          >
            立即注册
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../../api/user'

const router = useRouter()
const loginFormRef = ref()
const loading = ref(false)
const isAutoFilled = ref(false)

// 登录表单数据
const loginForm = reactive({
  account: '',
  password: '',
  remember: false
})

// 表单验证规则
const loginRules = {
  account: [
    { required: true, message: '请输入账号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少为6位', trigger: 'blur' }
  ]
}

// 处理登录
const handleLogin = async () => {
  // 表单验证
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid: boolean) => {
    if (valid) {
      try {
        loading.value = true
        // 调用登录接口
        const response = await login({
          account: loginForm.account,
          password: loginForm.password
        })
        
        // 保存token和用户信息
        localStorage.setItem('token', response.data.token)
        localStorage.setItem('userInfo', JSON.stringify({
          userId: response.data.userId,
          username: response.data.username,
          nickname: response.data.nickname,
          avatar: response.data.avatar,
          role: response.data.role
        }))
        
        // 记住密码
        if (loginForm.remember) {
          localStorage.setItem('rememberAccount', loginForm.account)
          // 使用base64加密密码
          const encryptedPassword = btoa(loginForm.password)
          localStorage.setItem('rememberPassword', encryptedPassword)
        } else {
          localStorage.removeItem('rememberAccount')
          localStorage.removeItem('rememberPassword')
        }
        
        ElMessage.success('登录成功')
        // 跳转到首页
        router.push('/dashboard')
      } catch (error) {
        console.error('登录失败:', error)
      } finally {
        loading.value = false
      }
    }
  })
}

// 处理注册
const handleRegister = () => {
  // 跳转到注册页面
  router.push('/register')
}

// 处理忘记密码
const handleForgotPassword = () => {
  // 忘记密码逻辑待实现
  ElMessage.info('忘记密码功能即将开放')
}

// 处理账号变化
const handleAccountChange = () => {
  // 当账号信息发生变化时，清空密码
  loginForm.password = ''
  // 恢复密码可见性切换按钮
  isAutoFilled.value = false
}

// 处理记住密码状态变化
const handleRememberChange = (value: boolean) => {
  if (!value) {
    // 取消记住密码时，清空密码并清除localStorage
    loginForm.password = ''
    localStorage.removeItem('rememberAccount')
    localStorage.removeItem('rememberPassword')
    // 恢复密码可见性切换按钮
    isAutoFilled.value = false
  }
}

// 页面加载时，检查是否有记住的账号和密码
onMounted(() => {
  const rememberAccount = localStorage.getItem('rememberAccount')
  const rememberPassword = localStorage.getItem('rememberPassword')
  if (rememberAccount && rememberPassword) {
    loginForm.account = rememberAccount
    // 使用base64解密密码
    const decryptedPassword = atob(rememberPassword)
    loginForm.password = decryptedPassword
    loginForm.remember = true
    // 设置为自动填充状态
    isAutoFilled.value = true
  }
})
</script>

<style scoped>
.login-container {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

/* 背景图片层 */
.background-layer {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: url('https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cute%20cartoon%20pets%2C%20soft%20colors%2C%20high%20quality&image_size=landscape_16_9');
  background-size: cover;
  background-position: center;
  animation: fadeIn 0.8s ease-in-out;
}

.background-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.2);
}

/* 登录表单层 */
.login-form-wrapper {
  position: relative;
  z-index: 10;
  width: 35%;
  max-width: 360px;
  margin-right: 12%;
  padding: 40px;
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(8px);
  border-radius: 10px;
  box-shadow: 0 5px 20px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.3);
  animation: slideIn 0.8s ease-in-out;
}

/* 调整输入框样式，使起始位置更靠左 */
:deep(.el-input__wrapper) {
  padding-left: 0 !important;
  text-align: left !important;
  margin: 0 !important;
}

:deep(.el-input__inner) {
  text-align: left !important;
  padding-left: 10px !important;
  margin: 0 !important;
}

:deep(.el-input__prefix) {
  display: none !important;
}

:deep(.el-input__suffix) {
  margin-right: 10px !important;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h2 {
  color: #409eff;
  margin-bottom: 10px;
  font-size: 24px;
}

.login-header p {
  color: #606266;
  font-size: 14px;
}

.login-form {
  width: 100%;
}

.login-button,
.register-button {
  width: 100%;
  height: 40px;
  font-size: 16px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .login-container {
    justify-content: center;
  }
  
  .login-form-wrapper {
    width: 90%;
    max-width: 400px;
    margin-right: 0;
    padding: 30px;
  }
}

/* 动画效果 */
@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(50px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>
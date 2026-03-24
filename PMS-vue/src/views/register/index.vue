<template>
  <div class="login-container">
    <!-- 背景图片层 -->
    <div class="background-layer">
      <div class="background-overlay"></div>
    </div>
    
    <!-- 注册表单层 -->
    <div class="login-form-wrapper">
      <div class="login-header">
        <h2>宠物管理系统</h2>
        <p>欢迎注册</p>
      </div>
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        class="login-form"
        label-position="top"
      >
        <el-form-item label="用户名" prop="userName">
          <el-input
            v-model="registerForm.userName"
            placeholder="请输入用户名（3-20位字母数字下划线）"
            clearable
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickName">
          <el-input
            v-model="registerForm.nickName"
            placeholder="请输入昵称（可选）"
            clearable
          />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="registerForm.phone"
            placeholder="请输入手机号"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码（至少6位）"
            show-password
          />
        </el-form-item>
        <el-form-item style="margin-bottom: 15px;">
          <el-button
            type="primary"
            class="login-button"
            :loading="loading"
            @click="handleRegister"
            :disabled="loading"
          >
            注册
          </el-button>
        </el-form-item>
        <el-form-item>
          <div style="text-align: center;">
            <span>已有账号？</span>
            <el-button type="text" @click="goToLogin" style="padding: 0;">立即登录</el-button>
          </div>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../../api/user'

const router = useRouter()
const registerFormRef = ref()
const loading = ref(false)

// 注册表单数据
const registerForm = reactive({
  userName: '',
  nickName: '',
  phone: '',
  password: ''
})

// 表单验证规则
const registerRules = {
  userName: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20位', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  phone: [
    {
      required: true,
      message: '请输入手机号',
      trigger: 'blur'
    },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号',
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少为6位', trigger: 'blur' }
  ]
}

// 处理注册
const handleRegister = async () => {
  // 表单验证
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid: boolean) => {
    if (valid) {
      try {
        loading.value = true
        // 调用注册接口
        const response = await register(registerForm)
        
        // 保存token和用户信息
        localStorage.setItem('token', response.data.token)
        localStorage.setItem('userInfo', JSON.stringify({
          userId: response.data.userId,
          username: response.data.userName,
          nickname: response.data.nickName,
          avatar: response.data.avatar,
          role: response.data.role
        }))
        
        ElMessage.success('注册成功')
        // 跳转到首页
        router.push('/dashboard')
      } catch (error) {
        console.error('注册失败:', error)
      } finally {
        loading.value = false
      }
    }
  })
}

// 跳转到登录页面
const goToLogin = () => {
  router.push('/login')
}
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

.login-button {
  width: 100%;
  height: 40px;
  font-size: 16px;
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
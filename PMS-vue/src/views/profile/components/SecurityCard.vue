<template>
  <div class="info-card">
    <div class="card-title">账号安全</div>
    <div class="info-list">
      <div class="info-item">
        <span class="info-label">密码：</span>
        <span class="info-value">******</span>
        <el-button type="primary" plain size="small" @click="dialogVisible = true">
          修改密码
        </el-button>
      </div>
      <div class="info-item">
        <span class="info-label">账号状态：</span>
        <span class="info-value">
          <el-tag :type="userInfo?.status === 1 ? 'success' : 'danger'">
            {{ userInfo?.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </span>
      </div>
      <div class="info-item">
        <span class="info-label">允许通过用户名/手机号找到我：</span>
        <span class="info-value">
          <span v-if="!isEditing">{{ searchable ? '是' : '否' }}</span>
          <el-switch
            v-else
            v-model="searchable"
            @change="handleSearchableChange"
            size="small"
          />
        </span>
      </div>
    </div>

    <!-- 修改密码弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      title="修改密码"
      width="400px"
    >
      <el-form
        :model="passwordForm"
        :rules="rules"
        ref="passwordFormRef"
        label-width="80px"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="请输入原密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="请输入新密码（最少6位）"
            show-password
            @input="checkPasswordStrength"
          />
          <div v-if="passwordForm.newPassword" class="password-strength">
            <span class="strength-label">密码强度：</span>
            <el-progress
              :percentage="passwordStrength"
              :status="getStrengthStatus(passwordStrength)"
              :stroke-width="8"
              :format="formatStrength"
            />
          </div>
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请确认新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="loading"
            @click="handleChangePassword"
          >
            确认修改
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage, ElForm } from 'element-plus';
import { useRouter } from 'vue-router';
import { changePassword } from '../../../api/user';

interface UserInfo {
  status: number;
  searchable?: number;
}

const props = defineProps<{
  userInfo: UserInfo | null;
  isEditing?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:searchable', value: number): void;
}>();

const router = useRouter();

// 弹窗状态
const dialogVisible = ref(false);
// 加载状态
const loading = ref(false);
// 密码表单
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});
// 密码强度
const passwordStrength = ref(0);
// 表单引用
const passwordFormRef = ref<InstanceType<typeof ElForm>>();

// 表单验证规则
const rules = ref({
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码长度最少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule: any, value: string, callback: any) => {
        if (value !== passwordForm.value.newPassword) {
          callback(new Error('两次输入的密码不一致'));
        } else {
          callback();
        }
      },
      trigger: 'blur'
    }
  ]
});

const searchable = ref(props.userInfo?.searchable === 1 || false);

// 监听userInfo变化，更新searchable状态
watch(
  () => props.userInfo,
  (newVal) => {
    if (newVal) {
      searchable.value = newVal.searchable === 1 || false;
    }
  },
  { immediate: true, deep: true }
);

// 处理searchable变化
const handleSearchableChange = () => {
  emit('update:searchable', searchable.value ? 1 : 0);
};

// 检查密码强度
const checkPasswordStrength = () => {
  const password = passwordForm.value.newPassword;
  let strength = 0;
  
  if (password.length >= 6) strength += 33;
  if (/[A-Z]/.test(password)) strength += 33;
  if (/[0-9]/.test(password) && /[^A-Za-z0-9]/.test(password)) strength += 34;
  
  passwordStrength.value = strength;
};

// 获取强度状态
const getStrengthStatus = (strength: number) => {
  if (strength < 33) return '';
  if (strength < 66) return 'warning';
  return 'success';
};

// 格式化强度显示
const formatStrength = (percentage: number) => {
  if (percentage < 33) return '弱';
  if (percentage < 66) return '中';
  return '强';
};

// 处理修改密码
const handleChangePassword = async () => {
  if (!passwordFormRef.value) return;
  
  try {
    await passwordFormRef.value.validate();
    loading.value = true;
    
    const response = await changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
      confirmPassword: passwordForm.value.confirmPassword
    });
    
    ElMessage.success('密码修改成功，请重新登录');
    dialogVisible.value = false;
    
    // 清除token并跳转到登录页
    localStorage.removeItem('token');
    router.push('/login');
  } catch (error: any) {
    ElMessage.error(error.message || '修改密码失败');
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.info-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #333;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 12px;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f5f7fa;
  margin-bottom: 8px;
}

.info-item:last-child {
  padding-bottom: 0;
  border-bottom: none;
  margin-bottom: 0;
}

.info-label {
  width: 200px;
  font-size: 14px;
  color: #606266;
  flex-shrink: 0;
  white-space: nowrap;
}

.info-value {
  flex: 1;
  font-size: 14px;
  color: #333;
}

.action-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}
.password-strength {
  margin-top: 8px;
}

.strength-label {
  font-size: 12px;
  color: #606266;
  margin-right: 8px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
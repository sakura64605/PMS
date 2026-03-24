<template>
  <div class="info-card">
    <div class="card-title">基本信息</div>
    <div class="info-list">
      <div class="info-item">
        <span class="info-label">昵称：</span>
        <span v-if="!isEditing" class="info-value">{{ userInfo?.nickName || '未设置' }}</span>
        <el-input
          v-else
          v-model="editForm.nickName"
          placeholder="请输入昵称"
          class="info-input"
        />
      </div>
      <div class="info-item">
        <span class="info-label">手机：</span>
        <div class="info-value-with-eye">
          <span>{{ showPhone ? (userInfo?.phone || '未设置') : maskPhone(userInfo?.phone || null) }}</span>
          <div class="info-actions">
            <el-button
              type="text"
              @click="showPhone = !showPhone"
              class="eye-button"
            >
              <el-icon v-if="showPhone"><Hide /></el-icon>
              <el-icon v-else><View /></el-icon>
            </el-button>
            <div v-if="isEditing" class="privacy-switch">
              <span class="privacy-label">公开：</span>
              <el-switch
                v-model="phonePublic"
                @change="handlePhonePrivacyChange"
                size="small"
              />
            </div>
          </div>
        </div>
      </div>
      <div class="info-item">
        <span class="info-label">邮箱：</span>
        <div v-if="!isEditing" class="info-value-with-eye">
          <span>{{ showEmail ? (userInfo?.email || '未设置') : maskEmail(userInfo?.email || null) }}</span>
          <div class="info-actions">
            <el-button
              type="text"
              @click="showEmail = !showEmail"
              class="eye-button"
            >
              <el-icon v-if="showEmail"><Hide /></el-icon>
              <el-icon v-else><View /></el-icon>
            </el-button>
            <div v-if="isEditing" class="privacy-switch">
              <span class="privacy-label">公开：</span>
              <el-switch
                v-model="emailPublic"
                @change="handleEmailPrivacyChange"
                size="small"
              />
            </div>
          </div>
        </div>
        <div v-else class="info-value-with-eye">
          <el-input
            v-model="editForm.email"
            placeholder="请输入邮箱"
            class="info-input"
          />
          <div class="privacy-switch">
            <span class="privacy-label">公开：</span>
            <el-switch
              v-model="emailPublic"
              @change="handleEmailPrivacyChange"
              size="small"
            />
          </div>
        </div>
      </div>
      <div class="info-item">
        <span class="info-label">性别：</span>
        <span v-if="!isEditing" class="info-value">{{ getGenderText(userInfo?.gender || 0) }}</span>
        <el-select
          v-else
          v-model="editForm.gender"
          class="info-select"
        >
          <el-option label="未知" :value="0" />
          <el-option label="男" :value="1" />
          <el-option label="女" :value="2" />
        </el-select>
      </div>
      <div class="info-item">
        <span class="info-label">签名：</span>
        <span v-if="!isEditing" class="info-value">{{ userInfo?.signature || '暂无签名' }}</span>
        <el-input
          v-else
          v-model="editForm.signature"
          placeholder="请输入个性签名"
          type="textarea"
          class="info-textarea"
          :rows="2"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { maskPhone, maskEmail, getGenderText } from '../../../utils/format';
import { View, Hide } from '@element-plus/icons-vue';

interface UserInfo {
  userId: number;
  userName: string;
  nickName: string;
  phone: string | null;
  email: string | null;
  gender: number;
  signature: string | null;
}

const props = defineProps<{
  userInfo: UserInfo | null;
  isEditing: boolean;
  phonePublic?: boolean;
  emailPublic?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:form', form: any): void;
  (e: 'update:phonePublic', value: boolean): void;
  (e: 'update:emailPublic', value: boolean): void;
}>();

const editForm = ref({
  nickName: '',
  gender: 0,
  signature: '',
  email: ''
});

// 控制是否显示完整手机号
const showPhone = ref(false);
// 控制是否显示完整邮箱
const showEmail = ref(false);
// 控制手机是否公开
const phonePublic = ref(props.phonePublic || false);
// 控制邮箱是否公开
const emailPublic = ref(props.emailPublic || false);

// 监听userInfo变化，更新编辑表单
watch(
  () => props.userInfo,
  (newVal) => {
    if (newVal) {
      editForm.value = {
        nickName: newVal.nickName || '',
        gender: newVal.gender || 0,
        signature: newVal.signature || '',
        email: newVal.email || ''
      };
    }
  },
  { immediate: true, deep: true }
);

// 监听phonePublic变化
watch(
  () => props.phonePublic,
  (newVal) => {
    if (newVal !== undefined) {
      phonePublic.value = newVal;
    }
  },
  { immediate: true }
);

// 监听emailPublic变化
watch(
  () => props.emailPublic,
  (newVal) => {
    if (newVal !== undefined) {
      emailPublic.value = newVal;
    }
  },
  { immediate: true }
);

// 监听编辑表单变化，通知父组件
watch(
  () => editForm.value,
  (newVal) => {
    emit('update:form', newVal);
  },
  { deep: true }
);

// 处理手机公开设置变化
const handlePhonePrivacyChange = () => {
  emit('update:phonePublic', phonePublic.value);
};

// 处理邮箱公开设置变化
const handleEmailPrivacyChange = () => {
  emit('update:emailPublic', emailPublic.value);
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
}

.info-item {
  display: flex;
  align-items: center;
  gap: 12px;
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
  width: 80px;
  font-size: 14px;
  color: #606266;
  flex-shrink: 0;
}

.info-value {
  flex: 1;
  font-size: 14px;
  color: #333;
  line-height: 24px;
}

.info-value-with-eye {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.info-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.eye-button {
  padding: 0;
  font-size: 16px;
  color: #909399;
  transition: color 0.3s;
}

.eye-button:hover {
  color: #409eff;
}

.privacy-switch {
  display: flex;
  align-items: center;
  gap: 8px;
}

.privacy-label {
  font-size: 14px;
  color: #606266;
}

.info-input,
.info-select {
  flex: 1;
  width: 100%;
}

.info-textarea {
  flex: 1;
  width: 100%;
  resize: none;
}
</style>
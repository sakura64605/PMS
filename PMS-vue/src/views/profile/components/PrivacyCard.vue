<template>
  <div class="info-card">
    <div class="card-title">隐私设置</div>
    <div class="info-list">
      <div class="info-item">
        <span class="info-label">标签公开：</span>
        <span class="info-value">
          <el-switch
            v-model="privacySettings.tags"
            :disabled="!isEditing"
            @change="handlePrivacyChange"
          />
        </span>
      </div>
      <div class="info-item">
        <span class="info-label">手机公开：</span>
        <span class="info-value">
          <el-switch
            v-model="privacySettings.phone"
            :disabled="!isEditing"
            @change="handlePrivacyChange"
          />
        </span>
      </div>
      <div class="info-item">
        <span class="info-label">邮箱公开：</span>
        <span class="info-value">
          <el-switch
            v-model="privacySettings.email"
            :disabled="!isEditing"
            @change="handlePrivacyChange"
          />
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

interface PrivacySettings {
  tags: boolean;
  email: boolean;
  phone: boolean;
}

interface UserInfo {
  privacySettings: PrivacySettings;
}

const props = defineProps<{
  userInfo: UserInfo | null;
  isEditing: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:privacy', privacy: PrivacySettings): void;
}>();

const privacySettings = ref<PrivacySettings>({
  tags: false,
  email: false,
  phone: false
});

// 监听userInfo变化，更新隐私设置
watch(
  () => props.userInfo,
  (newVal) => {
    if (newVal && newVal.privacySettings) {
      privacySettings.value = {
        ...newVal.privacySettings
      };
    }
  },
  { immediate: true, deep: true }
);

// 处理隐私设置变化
const handlePrivacyChange = () => {
  emit('update:privacy', privacySettings.value);
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
}

.info-label {
  width: 100px;
  font-size: 14px;
  color: #606266;
  flex-shrink: 0;
}

.info-value {
  flex: 1;
  font-size: 14px;
  color: #333;
}
</style>
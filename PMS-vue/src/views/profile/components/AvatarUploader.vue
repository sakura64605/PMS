<template>
  <div class="avatar-uploader">
    <el-upload
      class="avatar-uploader"
      action="/pet-system/avatar/upload"
      :show-file-list="false"
      :on-success="handleAvatarSuccess"
      :on-error="handleAvatarError"
      :before-upload="beforeAvatarUpload"
      :headers="uploadHeaders"
      name="file"
    >
      <el-avatar  
        :size="100"  
        :src="avatarUrl || defaultAvatar"
        class="avatar"
      >
        {{ avatarUrl ? '' : '头' }}
      </el-avatar>
      <div class="avatar-overlay">
        <el-icon><Camera /></el-icon>
        <span>更换头像</span>
      </div>
    </el-upload>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { Camera } from '@element-plus/icons-vue';

// Props
const props = defineProps({
  // 当前头像URL
  modelValue: {
    type: String,
    default: ''
  }
});

// Events
const emit = defineEmits(['update:modelValue', 'success']);

// 上传请求头
const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token');
  return {
    'Authorization': `Bearer ${token}`
  };
});

// 默认头像
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1e.png';

// 上传前验证
const beforeAvatarUpload = (file: File) => {
  const isImage = file.type.startsWith('image/');
  const isLt2M = file.size / 1024 / 1024 < 2;
  const isAcceptType = ['image/jpeg', 'image/png', 'image/gif'].includes(file.type);

  if (!isImage) {
    ElMessage.error('只能上传图片文件!');
    return false;
  }
  if (!isAcceptType) {
    ElMessage.error('只能上传jpg、png、gif格式!');
    return false;
  }
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过2MB!');
    return false;
  }
  return true;
};

// 上传成功
const handleAvatarSuccess = (res: any) => {
  console.log('上传成功响应:', res);
  if (res.code === 200 && res.data && res.data.avatarUrl) {
    // 移除avatarUrl中的多余引号
    const avatarUrl = res.data.avatarUrl.replace(/^"|"$/g, '').replace(/^\s+|\s+$/g, '');
    console.log('处理后的avatarUrl:', avatarUrl);
    emit('update:modelValue', avatarUrl);
    ElMessage.success('头像上传成功');
    emit('success', avatarUrl);
  } else {
    ElMessage.error(res.message || '上传失败');
  }
};

// 上传失败
const handleAvatarError = () => {
  ElMessage.error('头像上传失败，请重试');
};

// 头像URL
const avatarUrl = ref(props.modelValue);
</script>

<style scoped>
.avatar-uploader {
  position: relative;
  display: inline-block;
  cursor: pointer;
}

.avatar {
  border: 2px solid #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
  cursor: pointer;
}

.avatar-uploader:hover .avatar-overlay {
  opacity: 1;
}

.avatar-overlay .el-icon {
  font-size: 24px;
  margin-bottom: 4px;
}

.avatar-overlay span {
  font-size: 12px;
}
</style>
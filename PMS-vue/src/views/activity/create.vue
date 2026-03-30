<template>
  <div class="activity-create-container">
    <h2 class="page-title">发布活动</h2>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      class="create-form"
    >
      <!-- 标题 -->
      <el-form-item label="活动标题" prop="title">
        <el-input
          v-model="form.title"
          placeholder="请输入活动标题"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <!-- 活动内容 -->
      <el-form-item label="活动内容" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          rows="3"
          placeholder="请输入活动内容"
        />
      </el-form-item>

      <!-- 活动图片 -->
      <el-form-item label="活动图片" prop="images">
        <el-upload
          v-model:file-list="fileList"
          action="/pet-system/pet/upload"
          :headers="uploadHeaders"
          :on-success="handleUploadSuccess"
          :on-error="handleUploadError"
          :before-upload="beforeUpload"
          list-type="picture-card"
          :limit="9"
          :auto-upload="true"
        >
          <template #trigger>
            <el-icon><Plus /></el-icon>
          </template>
          <template #file="{ file }">
            <div class="upload-file">
              <img :src="file.url" alt="活动图片" class="upload-image" />
              <div class="upload-file-actions">
                <el-button type="text" size="small" @click="handlePreview(file)">
                  <el-icon><View /></el-icon>
                </el-button>
                <el-button type="text" size="small" @click="handleRemove(file)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </template>
        </el-upload>
        <div class="upload-tip">最多上传9张图片</div>
      </el-form-item>

      <!-- 活动地点 -->
      <el-form-item label="活动地点" prop="location">
        <el-input
          v-model="form.location"
          placeholder="请输入活动地点"
        />
      </el-form-item>

      <!-- 人数限制 -->
      <el-form-item label="人数限制" prop="maxPeople">
        <el-input-number
          v-model="form.maxPeople"
          :min="1"
          placeholder="请输入人数限制"
          style="width: 100%"
        />
      </el-form-item>

      <!-- 开始时间 -->
      <el-form-item label="开始时间" prop="startTime">
        <el-date-picker
          v-model="form.startTime"
          type="datetime"
          placeholder="选择开始时间"
          style="width: 100%"
          :disabled-date="disabledDate"
        />
      </el-form-item>

      <!-- 结束时间 -->
      <el-form-item label="结束时间" prop="endTime">
        <el-date-picker
          v-model="form.endTime"
          type="datetime"
          placeholder="选择结束时间"
          style="width: 100%"
          :disabled-date="(time) => disabledEndDate(time, form.startTime)"
        />
      </el-form-item>

      <!-- 提交按钮 -->
      <el-form-item>
        <el-button type="primary" @click="handleSubmit" :loading="loading">
          发布
        </el-button>
        <el-button @click="handleCancel">
          取消
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 图片预览弹窗 -->
    <el-dialog v-model="previewVisible" title="图片预览" width="80%">
      <img :src="previewImage" alt="预览图片" class="preview-image" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Plus, View, Delete } from '@element-plus/icons-vue';
import { createActivity } from '../../api/activity';

// 路由
const router = useRouter();

// 表单
const formRef = ref();
const loading = ref(false);

// 表单数据
const form = reactive({
  title: '',
  content: '',
  images: [] as string[],
  location: '',
  maxPeople: 1,
  startTime: '',
  endTime: ''
});

// 表单验证规则
const rules = {
  title: [
    { required: true, message: '请输入活动标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过100字', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入活动内容', trigger: 'blur' }
  ],
  location: [
    { required: true, message: '请输入活动地点', trigger: 'blur' }
  ],
  maxPeople: [
    { required: true, message: '请输入人数限制', trigger: 'blur' },
    { type: 'number', min: 1, message: '人数限制至少为1', trigger: 'blur' }
  ],
  startTime: [
    { required: true, message: '请选择开始时间', trigger: 'change' }
  ],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' }
  ]
};

// 图片上传
const fileList = ref<any[]>([]);
const previewVisible = ref(false);
const previewImage = ref('');

// 上传请求头
const uploadHeaders = computed(() => ({
  'Authorization': `Bearer ${localStorage.getItem('token')}`
}));

// 禁用日期（只能选择今天及以后的日期）
const disabledDate = (time: Date) => {
  return time.getTime() < Date.now() - 8.64e7; // 8.64e7是一天的毫秒数
};

// 禁用结束日期（只能选择开始时间之后的日期）
const disabledEndDate = (time: Date, startTime: string) => {
  if (!startTime) return false;
  return time.getTime() < new Date(startTime).getTime();
};

// 方法
const handlePreview = (file: any) => {
  previewImage.value = file.url || file.response?.data?.avatarUrl;
  previewVisible.value = true;
};

const handleRemove = (file: any) => {
  const index = fileList.value.findIndex(f => f.uid === file.uid);
  if (index !== -1) {
    fileList.value.splice(index, 1);
    // 从 form.images 中移除对应的URL
    const url = file.url;
    const urlIndex = form.images.findIndex(img => img === url);
    if (urlIndex !== -1) {
      form.images.splice(urlIndex, 1);
    }
  }
};

const handleUploadSuccess = (response: any, file: any, uploadFileList: any[]) => {
  if (response.code === 200 && response.data?.avatarUrl) {
    // 去除可能的多余引号
    const imageUrl = response.data.avatarUrl.replace(/^"|"$/g, '');
    // 将URL存入 form.images
    form.images.push(imageUrl);
    // 更新 fileList 中的 url 用于显示
    const targetFile = fileList.value.find(f => f.uid === file.uid);
    if (targetFile) {
      targetFile.url = imageUrl;
    }
    ElMessage.success('图片上传成功');
  } else {
    ElMessage.error(response.message || '上传失败');
    // 上传失败，移除该文件
    const index = fileList.value.findIndex(f => f.uid === file.uid);
    if (index !== -1) {
      fileList.value.splice(index, 1);
    }
  }
};

const handleUploadError = (error: any, file: any, uploadFileList: any[]) => {
  ElMessage.error('上传失败，请重试');
  // 上传失败，移除该文件
  const index = fileList.value.findIndex(f => f.uid === file.uid);
  if (index !== -1) {
    fileList.value.splice(index, 1);
  }
};

const beforeUpload = (file: any) => {
  // 可以添加文件类型和大小验证
  const isImage = file.type.startsWith('image/');
  if (!isImage) {
    ElMessage.error('只能上传图片文件');
    return false;
  }
  const isLt2M = file.size / 1024 / 1024 < 2;
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过2MB');
    return false;
  }
  return true;
};

const handleSubmit = async () => {
  if (!formRef.value) return;

  try {
    await formRef.value.validate();
    loading.value = true;
    
    const response = await createActivity({
      title: form.title,
      content: form.content,
      images: form.images,
      location: form.location,
      maxPeople: form.maxPeople,
      startTime: form.startTime,
      endTime: form.endTime
    });
    
    ElMessage.success('活动发布成功');
    router.push('/activities');
  } catch (error) {
    console.error('发布活动失败:', error);
    // 错误已经在响应拦截器中处理了
  } finally {
    loading.value = false;
  }
};

const handleCancel = () => {
  router.push('/activities');
};
</script>

<style scoped>
.activity-create-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0 0 24px 0;
}

.create-form {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.upload-file {
  position: relative;
  width: 100%;
  height: 100%;
}

.upload-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-file-actions {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: space-around;
  padding: 8px;
  opacity: 0;
  transition: opacity 0.3s;
}

.upload-file:hover .upload-file-actions {
  opacity: 1;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

.preview-image {
  width: 100%;
  height: auto;
  max-height: 80vh;
  object-fit: contain;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .activity-create-container {
    padding: 16px;
  }

  .create-form {
    padding: 16px;
  }
}
</style>
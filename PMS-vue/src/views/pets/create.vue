<template>
  <div class="pet-create-container">
    <h2 class="page-title">发布宠物</h2>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      class="create-form"
    >
      <!-- 类型 -->
      <el-form-item label="类型" prop="type">
        <el-radio-group v-model="form.type">
          <el-radio label="0">领养</el-radio>
          <el-radio label="1">救助</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 标题 -->
      <el-form-item label="标题" prop="title">
        <el-input
          v-model="form.title"
          placeholder="请输入标题"
          maxlength="50"
          show-word-limit
        />
      </el-form-item>

      <!-- 宠物名 -->
      <el-form-item label="宠物名" prop="petName">
        <el-input
          v-model="form.petName"
          placeholder="请输入宠物名"
        />
      </el-form-item>

      <!-- 品种 -->
      <el-form-item label="品种" prop="petType">
        <el-select
          v-model="form.petType"
          placeholder="请选择品种"
          allow-create
          filterable
        >
          <el-option label="猫" value="猫" />
          <el-option label="狗" value="狗" />
          <el-option label="兔子" value="兔子" />
          <el-option label="仓鼠" value="仓鼠" />
          <el-option label="其他" value="其他" />
        </el-select>
      </el-form-item>

      <!-- 年龄 -->
      <el-form-item label="年龄" prop="petAge">
        <el-input
          v-model="form.petAge"
          placeholder="如：3个月、2岁"
        />
      </el-form-item>

      <!-- 性别 -->
      <el-form-item label="性别" prop="petGender">
        <el-radio-group v-model="form.petGender">
          <el-radio label="0">未知</el-radio>
          <el-radio label="1">公</el-radio>
          <el-radio label="2">母</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 宠物图片 -->
      <el-form-item label="宠物图片">
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
          <el-icon><Plus /></el-icon>
          <template #file="{ file }">
            <div class="upload-file">
              <img :src="file.url" alt="" class="upload-image" />
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

      <!-- 详细描述 -->
      <el-form-item label="详细描述" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          rows="5"
          placeholder="请输入详细描述"
        />
      </el-form-item>

      <!-- 地址 -->
      <el-form-item label="地址" prop="address">
        <el-input
          v-model="form.address"
          placeholder="请输入地址"
        />
      </el-form-item>

      <!-- 联系电话 -->
      <el-form-item label="联系电话" prop="contactPhone">
        <el-input
          v-model="form.contactPhone"
          placeholder="请输入联系电话"
        />
      </el-form-item>

      <!-- 微信号 -->
      <el-form-item label="微信号" prop="contactWechat">
        <el-input
          v-model="form.contactWechat"
          placeholder="请输入微信号"
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
import { createPet } from '../../api/pet';

// 路由
const router = useRouter();

// 表单
const formRef = ref();
const loading = ref(false);

// 表单数据
const form = reactive({
  type: 0,
  title: '',
  petName: '',
  petType: '',
  petAge: '',
  petGender: 0,
  content: '',
  address: '',
  contactPhone: '',
  contactWechat: '',
  images: [] as string[]
});

// 表单验证规则
const rules = {
  type: [
    { required: true, message: '请选择类型', trigger: 'change' }
  ],
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 50, message: '标题最多50字', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入详细描述', trigger: 'blur' }
  ],
  address: [
    { required: true, message: '请输入地址', trigger: 'blur' }
  ],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
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
    console.log('删除后图片URL列表:', form.images);
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
    console.log('图片URL列表:', form.images);
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

  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true;
      try {
        // 直接发送 JSON，包含 images 数组
        const response = await createPet({
          type: form.type,
          title: form.title,
          petName: form.petName,
          petType: form.petType,
          petAge: form.petAge,
          petGender: form.petGender,
          content: form.content,
          address: form.address,
          contactPhone: form.contactPhone,
          contactWechat: form.contactWechat,
          images: form.images
        });
        
        if (response.code === 200) {
          ElMessage.success('发布成功');
          router.push(`/pets/${response.data.id}`);
        } else {
          ElMessage.error(response.message || '发布失败');
        }
      } catch (error) {
        ElMessage.error('发布失败，请重试');
        console.error('发布失败:', error);
      } finally {
        loading.value = false;
      }
    }
  });
};

const handleCancel = () => {
  router.push('/pets');
};
</script>

<style scoped>
.pet-create-container {
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
  .pet-create-container {
    padding: 16px;
  }

  .create-form {
    padding: 16px;
  }
}
</style>
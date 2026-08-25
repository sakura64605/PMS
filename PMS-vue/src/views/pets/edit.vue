<template>
  <div class="pet-edit-container">
    <h2 class="page-title">编辑宠物</h2>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="15" animated />
    </div>
    <el-form
      v-else
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      class="edit-form"
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
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          保存
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
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Plus, View, Delete } from '@element-plus/icons-vue';
import { getPetDetail, updatePet } from '../../api/pet';
import { useImageUpload } from '../../composables/useImageUpload';

// 路由
const route = useRoute();
const router = useRouter();

// 状态
const loading = ref(true);
const submitting = ref(false);
const formRef = ref();

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
const { uploadHeaders, handleUploadSuccess, handleUploadError, handleRemove, beforeUpload, handlePreview, previewVisible, previewImage } = useImageUpload({
  images: form.images,
  fileList
});

const handleSubmit = async () => {
  if (!formRef.value) return;

  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      submitting.value = true;
      try {
        const id = route.params.id;
        const response = await updatePet({
          id: Number(id),
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
          ElMessage.success('保存成功');
          router.push(`/pets/${id}`);
        } else {
          ElMessage.error(response.message || '保存失败');
        }
      } catch (error) {
        ElMessage.error('保存失败，请重试');
        console.error('保存失败:', error);
      } finally {
        submitting.value = false;
      }
    }
  });
};

const handleCancel = () => {
  const id = route.params.id;
  router.push(`/pets/${id}`);
};

const fetchPetDetail = async () => {
  const id = route.params.id;
  if (!id) {
    ElMessage.error('宠物ID不存在');
    router.push('/pets');
    return;
  }

  loading.value = true;
  try {
    const response = await getPetDetail(Number(id));
    if (response.code === 200 && response.data) {
      const pet = response.data;
      form.type = pet.type;
      form.title = pet.title;
      form.petName = pet.petName || '';
      form.petType = pet.petType || '';
      form.petAge = pet.petAge || '';
      form.petGender = pet.petGender;
      form.content = pet.content;
      form.address = pet.address;
      form.contactPhone = pet.contactPhone;
      form.contactWechat = pet.contactWechat || '';

      if (pet.images && pet.images.length > 0) {
        form.images = pet.images;
        fileList.value = pet.images.map((url: string, index: number) => ({
          uid: index,
          name: `image${index + 1}.jpg`,
          url: url
        }));
      }
    } else {
      ElMessage.error(response.message || '获取宠物信息失败');
      router.push('/pets');
    }
  } catch (error) {
    ElMessage.error('获取宠物信息失败，请重试');
    console.error('获取宠物信息失败:', error);
    router.push('/pets');
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchPetDetail();
});
</script>

<style scoped>
.pet-edit-container {
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

.loading-container {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.edit-form {
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

@media (max-width: 768px) {
  .pet-edit-container {
    padding: 16px;
  }

  .edit-form {
    padding: 16px;
  }
}
</style>
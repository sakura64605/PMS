<template>
  <div class="publish-container">
    <h2>发布日记</h2>
    
    <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
      <!-- 内容输入 -->
      <el-form-item label="内容" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="6"
          placeholder="分享你的宠物日常..."
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <!-- 图片上传 -->
      <el-form-item label="图片">
        <el-upload
          class="upload-demo"
          action="/pet-system/oss/upload"
          :headers="{ 'Authorization': `Bearer ${token}` }"
          :on-success="handleImageUploadSuccess"
          :on-error="handleImageUploadError"
          :limit="9"
          :on-exceed="handleImageExceed"
          list-type="picture-card"
        >
          <template #default>
            <el-icon><Plus /></el-icon>
            <div class="el-upload__text">上传图片</div>
          </template>
          <template #file="{ file }">
            <div class="uploaded-file">
              <el-image
                :src="file.response.data.url"
                :preview-src-list="[file.response.data.url]"
              />
              <el-button
                type="text"
                size="small"
                @click="handleRemoveImage(file)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </template>
        </el-upload>
      </el-form-item>

      <!-- 位置 -->
      <el-form-item label="位置">
        <el-input
          v-model="form.location"
          placeholder="添加位置（可选）"
        />
      </el-form-item>

      <!-- 话题 -->
      <el-form-item label="话题" prop="topicIds">
        <el-tag
          v-for="topic in selectedTopics"
          :key="topic.id"
          closable
          @close="handleRemoveTopic(topic)"
          class="selected-topic"
        >
          {{ topic.name }}
        </el-tag>
        <el-autocomplete
          v-model="topicInput"
          :fetch-suggestions="queryTopics"
          placeholder="搜索话题（必选）"
          @select="handleSelectTopic"
          class="topic-input"
        />
        <el-button type="primary" size="small" @click="handleCreateTopic" v-if="topicInput">
          创建话题
        </el-button>
        <div v-if="selectedTopics.length === 0" class="topic-hint">
          请至少选择一个话题
        </div>
      </el-form-item>

      <!-- 热门话题推荐 -->
      <el-form-item label="热门话题">
        <div class="hot-topics">
          <el-tag
            v-for="topic in hotTopics"
            :key="topic.id"
            type="info"
            size="small"
            @click="handleAddTopic(topic)"
            class="hot-topic"
          >
            {{ topic.name }}
          </el-tag>
        </div>
      </el-form-item>

      <!-- 操作按钮 -->
      <el-form-item>
        <el-button type="primary" @click="handleSubmit" :loading="loading">
          发布
        </el-button>
        <el-button @click="handleCancel">
          取消
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { publishDaily, getHotTopics, searchTopics, createTopic } from '../../api/daily';
import { ElMessage } from 'element-plus';
import { Plus, Delete } from '@element-plus/icons-vue';

const router = useRouter();
const formRef = ref<any>(null);
const loading = ref(false);
const token = localStorage.getItem('token');

// 表单数据
const form = reactive({
  content: '',
  images: [] as string[],
  location: '',
  topicIds: [] as number[]
});

// 表单验证规则
const rules = {
  content: [
    { required: true, message: '请输入内容', trigger: 'blur' },
    { min: 1, max: 500, message: '内容长度在 1 到 500 个字符', trigger: 'blur' }
  ],
  topicIds: [
    { 
      required: true, 
      validator: (rule: any, value: any, callback: any) => {
        if (!value || value.length === 0) {
          callback(new Error('请至少选择一个话题'));
        } else {
          callback();
        }
      },
      trigger: 'change'
    }
  ]
};

// 话题相关
const selectedTopics = ref<any[]>([]);
const topicInput = ref('');
const hotTopics = ref<any[]>([]);

// 处理图片上传成功
const handleImageUploadSuccess = (response: any, uploadFile: any) => {
  if (response.code === 200) {
    form.images.push(response.data.url);
    ElMessage.success('图片上传成功');
  } else {
    ElMessage.error('图片上传失败');
  }
};

// 处理图片上传失败
const handleImageUploadError = () => {
  ElMessage.error('图片上传失败');
};

// 处理图片超出限制
const handleImageExceed = () => {
  ElMessage.error('最多上传9张图片');
};

// 处理移除图片
const handleRemoveImage = (file: any) => {
  const index = form.images.findIndex(img => img === file.response.data.url);
  if (index > -1) {
    form.images.splice(index, 1);
  }
};

// 搜索话题
const queryTopics = async (query: string, callback: (data: any[]) => void) => {
  if (query) {
    try {
      const response = await searchTopics({ keyword: query });
      const topics = (response.data || []).map((item: any) => ({
        ...item,
        value: item.name
      }));
      callback(topics);
    } catch (error) {
      console.error('搜索话题失败:', error);
      callback([]);
    }
  } else {
    callback([]);
  }
};

// 选择话题
const handleSelectTopic = (topic: any) => {
  if (!selectedTopics.value.some(t => t.id === topic.id)) {
    selectedTopics.value.push(topic);
    form.topicIds.push(topic.id);
  }
  topicInput.value = '';
};

// 移除话题
const handleRemoveTopic = (topic: any) => {
  const index = selectedTopics.value.findIndex(t => t.id === topic.id);
  if (index > -1) {
    selectedTopics.value.splice(index, 1);
    form.topicIds.splice(index, 1);
  }
};

// 添加热门话题
const handleAddTopic = (topic: any) => {
  if (!selectedTopics.value.some(t => t.id === topic.id)) {
    selectedTopics.value.push(topic);
    form.topicIds.push(topic.id);
  }
};

// 创建话题
const handleCreateTopic = async () => {
  if (!topicInput.value) return;
  
  try {
    const response = await createTopic({
      name: topicInput.value,
      description: topicInput.value
    });
    if (response.data) {
      selectedTopics.value.push(response.data);
      form.topicIds.push(response.data.id);
      topicInput.value = '';
      ElMessage.success('话题创建成功');
    }
  } catch (error) {
    ElMessage.error('话题创建失败');
    console.error('话题创建失败:', error);
  }
};

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return;
  
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true;
      try {
        const response = await publishDaily({
          dailyPost: {
            content: form.content,
            images: form.images,
            location: form.location
          },
          topicIds: form.topicIds
        });
        
        if (response.data) {
          ElMessage.success('发布成功');
          router.push('/daily');
        }
      } catch (error) {
        ElMessage.error('发布失败');
        console.error('发布失败:', error);
      } finally {
        loading.value = false;
      }
    }
  });
};

// 取消
const handleCancel = () => {
  router.push('/daily');
};

// 加载热门话题
const loadHotTopics = async () => {
  try {
    const response = await getHotTopics({ limit: 10 });
    hotTopics.value = response.data || [];
  } catch (error) {
    console.error('获取热门话题失败:', error);
  }
};

// 初始化
onMounted(() => {
  loadHotTopics();
});
</script>

<style scoped>
.publish-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px 0;
}

.publish-container h2 {
  margin: 0 0 20px 0;
  font-size: 24px;
  color: #303133;
}

.upload-demo .el-upload-list {
  margin-bottom: 10px;
}

.uploaded-file {
  position: relative;
  display: inline-block;
  margin-right: 10px;
  margin-bottom: 10px;
}

.uploaded-file .el-button {
  position: absolute;
  bottom: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.5);
  color: white;
  padding: 2px;
  border-radius: 0 0 4px 0;
}

.uploaded-file .el-button:hover {
  background-color: rgba(0, 0, 0, 0.7);
}

.selected-topics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.selected-topic {
  margin-bottom: 8px;
}

.topic-input {
  width: 300px;
  margin-right: 10px;
}

.hot-topics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hot-topic {
  cursor: pointer;
  transition: all 0.3s;
}

.hot-topic:hover {
  transform: scale(1.05);
}

.topic-hint {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 8px;
  padding: 4px 0;
}

@media (max-width: 768px) {
  .publish-container {
    padding: 10px;
  }
  
  .topic-input {
    width: 200px;
  }
}
</style>
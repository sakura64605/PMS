<template>
  <div class="audit-detail-container">
    <el-button
      class="back-button"
      @click="handleBack"
    >
      <el-icon><ArrowLeft /></el-icon>
      返回审核列表
    </el-button>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>
    <div v-else-if="pet" class="detail-content">
      <!-- 图片轮播区 -->
      <div v-if="pet.images && pet.images.length > 0" class="image-carousel">
        <el-carousel :interval="5000" type="card" height="400px">
          <el-carousel-item v-for="(image, index) in pet.images" :key="index">
            <img :src="image" alt="宠物图片" class="carousel-image" />
          </el-carousel-item>
        </el-carousel>
      </div>

      <!-- 基本信息卡片 -->
      <div class="info-card">
        <div class="info-header">
          <div class="tags">
            <div class="type-tag" :class="pet.type === 0 ? 'adopt' : 'rescue'">
              {{ pet.type === 0 ? '领养' : '救助' }}
            </div>
            <div class="status-tag" :class="getStatusClass(pet.status)">
              {{ getStatusText(pet.status) }}
            </div>
          </div>
          <h1 class="title">{{ pet.title }}</h1>
        </div>

        <div class="pet-info">
          <div class="info-item">
            <span class="info-label">宠物名：</span>
            <span class="info-value">{{ pet.petName || '未知' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">品种：</span>
            <span class="info-value">{{ pet.petType || '未知' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">年龄：</span>
            <span class="info-value">{{ pet.petAge || '未知' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">性别：</span>
            <span class="info-value">
              <el-icon v-if="pet.petGender === 1"><Male /></el-icon>
              <el-icon v-else-if="pet.petGender === 2"><Female /></el-icon>
              <el-icon v-else><QuestionFilled /></el-icon>
              {{ getGenderText(pet.petGender) }}
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">浏览次数：</span>
            <span class="info-value">
              <el-icon><View /></el-icon>
              {{ pet.viewCount }}
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">发布时间：</span>
            <span class="info-value">{{ formatDate(pet.createTime) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">编辑时间：</span>
            <span class="info-value">{{ formatDate(pet.updateTime) }}</span>
          </div>
        </div>

        <div class="content-section">
          <h3 class="section-title">详细描述</h3>
          <div class="content">{{ pet.content }}</div>
        </div>
      </div>

      <!-- 联系方式卡片 -->
      <div class="contact-card">
        <h3 class="section-title">联系方式</h3>
        <div class="contact-info">
          <div class="contact-item">
            <span class="contact-label">联系电话：</span>
            <div class="contact-value">
              <span>{{ pet.contactPhone }}</span>
              <el-button
                type="text"
                size="small"
                @click="copyToClipboard(pet.contactPhone)"
              >
                <el-icon><DocumentCopy /></el-icon>
                复制
              </el-button>
            </div>
          </div>
          <div class="contact-item" v-if="pet.contactWechat">
            <span class="contact-label">微信号：</span>
            <div class="contact-value">
              <span>{{ pet.contactWechat }}</span>
              <el-button
                type="text"
                size="small"
                @click="copyToClipboard(pet.contactWechat)"
              >
                <el-icon><DocumentCopy /></el-icon>
                复制
              </el-button>
            </div>
          </div>
          <div class="contact-item">
            <span class="contact-label">地址：</span>
            <span class="contact-value">{{ pet.address }}</span>
          </div>
        </div>
      </div>

      <!-- 发布者卡片 -->
      <div class="user-card">
        <h3 class="section-title">发布者信息</h3>
        <div class="user-info">
          <el-avatar :size="48" :src="pet.user.avatar || ''">
            {{ pet.user.nickname?.charAt(0) || '用' }}
          </el-avatar>
          <div class="user-details">
            <div class="nickname">{{ pet.user.nickname }}</div>
            <div class="username">{{ pet.user.username }}</div>
          </div>
        </div>
      </div>

      <!-- 审核操作按钮 -->
      <div class="action-buttons">
        <el-button
          type="success"
          @click="handleApprove"
          :disabled="!pet || pet.status !== 0"
        >
          审核通过
        </el-button>
        <el-button
          type="danger"
          @click="handleReject"
          :disabled="!pet || pet.status !== 0"
        >
          审核拒绝
        </el-button>
        <el-button
          @click="handleBack"
        >
          返回列表
        </el-button>
      </div>
    </div>
    <div v-else class="empty-state">
      <el-empty description="宠物信息不存在" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, View, Male, Female, QuestionFilled, DocumentCopy } from '@element-plus/icons-vue';
import { getPetDetail, acceptPet, rejectPet } from '../../api/pet';

// 路由
const route = useRoute();
const router = useRouter();

// 状态
const loading = ref(false);
const pet = ref<any>(null);

// 方法
const handleBack = () => {
  router.push('/audit');
};

// 处理审核通过
const handleApprove = async () => {
  if (!pet.value) return;
  
  try {
    const response = await acceptPet(pet.value.id);
    if (response.code === 200) {
      ElMessage.success('审核通过');
      // 重新获取宠物详情
      fetchPetDetail();
    } else {
      ElMessage.error(response.message || '审核通过失败');
    }
  } catch (error) {
    ElMessage.error('审核通过失败');
    console.error('审核通过失败:', error);
  }
};

// 处理审核拒绝
const handleReject = async () => {
  if (!pet.value) return;
  
  try {
    const response = await rejectPet(pet.value.id);
    if (response.code === 200) {
      ElMessage.success('审核拒绝');
      // 重新获取宠物详情
      fetchPetDetail();
    } else {
      ElMessage.error(response.message || '审核拒绝失败');
    }
  } catch (error) {
    ElMessage.error('审核拒绝失败');
    console.error('审核拒绝失败:', error);
  }
};

const getStatusClass = (status: number) => {
  switch (status) {
    case 0: return 'pending';
    case 1: return 'published';
    case 2: return 'completed';
    case 3: return 'offline';
    case 4: return 'rejected';
    default: return '';
  }
};

const getStatusText = (status: number) => {
  switch (status) {
    case 0: return '待审核';
    case 1: return '已发布';
    case 2: return '已完成';
    case 3: return '已下架';
    case 4: return '审核未通过';
    default: return '未知';
  }
};

const getGenderText = (gender: number) => {
  switch (gender) {
    case 1: return '公';
    case 2: return '母';
    default: return '未知';
  }
};

const formatDate = (dateStr: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

const copyToClipboard = (text: string) => {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('复制成功');
  }).catch(() => {
    ElMessage.error('复制失败');
  });
};

const fetchPetDetail = async () => {
  const id = route.params.id;
  if (!id) {
    ElMessage.error('宠物ID不存在');
    return;
  }

  loading.value = true;
  try {
    const response = await getPetDetail(Number(id));
    if (response.code === 200 && response.data) {
      pet.value = response.data;
    } else {
      ElMessage.error(response.message || '获取宠物详情失败');
    }
  } catch (error) {
    ElMessage.error('获取宠物详情失败，请重试');
    console.error('获取宠物详情失败:', error);
  } finally {
    loading.value = false;
  }
};

// 生命周期
onMounted(() => {
  fetchPetDetail();
});
</script>

<style scoped>
.audit-detail-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.back-button {
  margin-bottom: 24px;
}

.loading-container {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.image-carousel {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.carousel-image {
  width: 100%;
  height: 350px;
  object-fit: cover;
  border-radius: 8px;
}

.info-card,
.contact-card,
.user-card {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.info-header {
  margin-bottom: 24px;
}

.tags {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.type-tag,
.status-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
}

.type-tag.adopt {
  background-color: #67c23a;
}

.type-tag.rescue {
  background-color: #e6a23c;
}

.status-tag.pending {
  background-color: #909399;
}

.status-tag.published {
  background-color: #409eff;
}

.status-tag.completed {
  background-color: #67c23a;
}

.status-tag.rejected {
  background-color: #e6a23c;
}

.status-tag.offline {
  background-color: #f56c6c;
}

.title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.pet-info {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 32px;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid #f0f0f0;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-label {
  font-size: 14px;
  color: #606266;
  min-width: 80px;
}

.info-value {
  font-size: 14px;
  color: #333;
  display: flex;
  align-items: center;
  gap: 4px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 16px 0;
}

.content-section {
  margin-top: 24px;
}

.content {
  font-size: 14px;
  line-height: 1.6;
  color: #333;
  white-space: pre-wrap;
}

.contact-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.contact-label {
  font-size: 14px;
  color: #606266;
  min-width: 80px;
}

.contact-value {
  font-size: 14px;
  color: #333;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nickname {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.username {
  font-size: 14px;
  color: #909399;
}

.action-buttons {
  display: flex;
  gap: 12px;
  justify-content: flex-start;
  margin-top: 12px;
}

.empty-state {
  background-color: white;
  border-radius: 12px;
  padding: 48px 24px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .audit-detail-container {
    padding: 16px;
  }

  .carousel-image {
    height: 200px;
  }

  .info-card,
  .contact-card,
  .user-card {
    padding: 16px;
  }

  .pet-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .contact-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .action-buttons {
    flex-direction: column;
  }
}
</style>
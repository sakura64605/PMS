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
      <!-- 内容详情卡片 -->
      <div class="detail-card">
        <div class="detail-header">
          <div class="detail-tags">
            <span class="type-tag" :class="targetType">
              {{ getTypeIcon(targetType) }} {{ getTypeText(targetType) }}
            </span>
            <span class="status-tag" :class="'status-' + pet.auditStatus">
              {{ getStatusText(pet.auditStatus) }}
            </span>
          </div>
          <h1 class="detail-title">{{ pet.title }}</h1>
          <div class="detail-meta">
            <span class="detail-publisher">发布者：{{ pet.user.nickname }}</span>
            <span class="detail-time">发布时间：{{ formatDate(pet.createTime) }}</span>
          </div>
          <div class="detail-contact">
            <span class="contact-item">联系电话：{{ pet.contactPhone }}</span>
            <span class="contact-item" v-if="pet.contactWechat">微信号：{{ pet.contactWechat }}</span>
          </div>
        </div>

        <!-- 宠物信息 -->
        <div v-if="targetType !== 'activity'" class="pet-info-section">
          <h3 class="section-title">宠物信息</h3>
          <div class="pet-info-content">
            <span class="info-item">品种：{{ pet.petType }}</span>
            <span class="info-item">名字：{{ pet.petName }}</span>
            <span class="info-item">年龄：{{ pet.petAge }}</span>
            <span class="info-item">性别：{{ getGenderText(pet.petGender) }}</span>
            <span class="info-item">地址：{{ pet.address }}</span>
          </div>
        </div>

        <!-- 活动信息 -->
        <div v-else class="activity-info-section">
          <h3 class="section-title">活动信息</h3>
          <div class="activity-info-content">
            <span class="info-item">地点：{{ pet.address }}</span>
            <span class="info-item">时间：{{ pet.activityTime }}</span>
            <span class="info-item">人数：{{ pet.participantCount }}/{{ pet.maxParticipants }}</span>
          </div>
        </div>

        <!-- 内容 -->
        <div class="content-section">
          <h3 class="section-title">内容</h3>
          <div class="content">{{ pet.content }}</div>
        </div>

        <!-- 图片 -->
        <div v-if="pet.images && pet.images.length > 0" class="images-section">
          <h3 class="section-title">图片</h3>
          <div class="images-grid">
            <img v-for="(image, index) in pet.images" :key="index" :src="image" :alt="`图片${index+1}`" class="grid-image" />
          </div>
        </div>

        <!-- 审核意见 -->
        <div class="audit-section">
          <h3 class="section-title">审核意见</h3>
          <el-input
            v-model="auditReason"
            type="textarea"
            rows="4"
            placeholder="拒绝时必填"
            class="audit-reason-input"
          />
        </div>

        <!-- 审核操作按钮 -->
        <div class="action-buttons">
          <el-button
            type="success"
            @click="handleApprove"
            :disabled="!pet || pet.auditStatus !== 0"
          >
            ✅ 通过
          </el-button>
          <el-button
            type="danger"
            @click="handleReject"
            :disabled="!pet || pet.auditStatus !== 0"
          >
            ❌ 拒绝
          </el-button>
          <el-button
            @click="handleBack"
          >
            返回列表
          </el-button>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      <el-empty description="内容不存在" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft } from '@element-plus/icons-vue';
import { approveAudit, rejectAudit, getAuditDetail } from '../../api/audit';

// 路由
const route = useRoute();
const router = useRouter();

// 状态
const loading = ref(false);
const pet = ref<any>(null);
const targetType = ref('pet');
const auditReason = ref('');

// 方法
const handleBack = () => {
  router.push('/audit');
};

// 处理审核通过
const handleApprove = async () => {
  if (!pet.value) return;
  
  try {
    const response = await approveAudit(targetType.value, pet.value.id);
    if (response.code === 200) {
      ElMessage.success('审核通过');
      // 重新获取审核详情
      fetchAuditDetail();
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
  
  if (!auditReason.value.trim()) {
    ElMessage.error('拒绝原因不能为空');
    return;
  }
  
  try {
    const response = await rejectAudit(targetType.value, pet.value.id, auditReason.value);
    if (response.code === 200) {
      ElMessage.success('审核拒绝');
      // 重新获取审核详情
      fetchAuditDetail();
    } else {
      ElMessage.error(response.message || '审核拒绝失败');
    }
  } catch (error) {
    ElMessage.error('审核拒绝失败');
    console.error('审核拒绝失败:', error);
  }
};

// 获取类型图标
const getTypeIcon = (type: string) => {
  switch (type) {
    case 'adopt': return '🐱';
    case 'help': return '🐕';
    case 'activity': return '📍';
    default: return '';
  }
};

// 获取类型文本
const getTypeText = (type: string) => {
  switch (type) {
    case 'adopt': return '领养';
    case 'help': return '救助';
    case 'activity': return '活动';
    default: return '未知';
  }
};

// 获取状态文本
const getStatusText = (status: number) => {
  switch (status) {
    case 0: return '待审核';
    case 1: return '已通过';
    case 2: return '已拒绝';
    default: return '未知';
  }
};

// 获取性别文本
const getGenderText = (gender: number) => {
  switch (gender) {
    case 1: return '公';
    case 2: return '母';
    default: return '未知';
  }
};

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

// 获取审核详情
const fetchAuditDetail = async () => {
  const id = route.params.id;
  const type = route.query.targetType as string;
  
  if (!id) {
    ElMessage.error('审核ID不存在');
    return;
  }
  
  if (type) {
    targetType.value = type;
  }

  loading.value = true;
  try {
    const response = await getAuditDetail({
      targetType: targetType.value,
      id: Number(id)
    });
    if (response.code === 200 && response.data) {
      pet.value = response.data;
    } else {
      ElMessage.error(response.message || '获取审核详情失败');
    }
  } catch (error) {
    ElMessage.error('获取审核详情失败，请重试');
    console.error('获取审核详情失败:', error);
  } finally {
    loading.value = false;
  }
};

// 生命周期
onMounted(() => {
  fetchAuditDetail();
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
  justify-content: center;
}

.detail-card {
  background-color: white;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  width: 100%;
  max-width: 800px;
}

.detail-header {
  margin-bottom: 30px;
}

.detail-tags {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.type-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
}

.type-tag.adopt {
  background-color: #67c23a;
}

.type-tag.help {
  background-color: #e6a23c;
}

.type-tag.activity {
  background-color: #409eff;
}

.status-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
}

.status-tag.status-0 {
  background-color: #909399;
}

.status-tag.status-1 {
  background-color: #67c23a;
}

.status-tag.status-2 {
  background-color: #f56c6c;
}

.detail-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0 0 16px 0;
}

.detail-meta {
  display: flex;
  gap: 30px;
  margin-bottom: 12px;
  font-size: 14px;
  color: #606266;
  flex-wrap: wrap;
}

.detail-contact {
  display: flex;
  gap: 30px;
  font-size: 14px;
  color: #606266;
  flex-wrap: wrap;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.pet-info-section,
.activity-info-section,
.content-section,
.images-section,
.audit-section {
  margin-bottom: 30px;
}

.pet-info-content,
.activity-info-content {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  font-size: 14px;
  color: #333;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.content {
  font-size: 14px;
  line-height: 1.6;
  color: #333;
  white-space: pre-wrap;
}

.images-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
}

.grid-image {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.grid-image:hover {
  transform: scale(1.05);
}

.audit-reason-input {
  width: 100%;
}

.action-buttons {
  display: flex;
  gap: 12px;
  justify-content: flex-start;
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.empty-state {
  background-color: white;
  border-radius: 12px;
  padding: 48px 24px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  width: 100%;
  max-width: 800px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .audit-detail-container {
    padding: 16px;
  }

  .detail-card {
    padding: 20px;
  }

  .detail-meta,
  .detail-contact {
    flex-direction: column;
    gap: 8px;
    align-items: flex-start;
  }

  .pet-info-content,
  .activity-info-content {
    flex-direction: column;
    gap: 8px;
    align-items: flex-start;
  }

  .action-buttons {
    flex-direction: column;
  }

  .images-grid {
    grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  }

  .grid-image {
    height: 100px;
  }
}
</style>
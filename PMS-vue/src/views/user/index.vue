<template>
  <div class="user-info-container">
    <el-button
      class="back-button"
      @click="handleBack"
    >
      <el-icon><ArrowLeft /></el-icon>
      返回
    </el-button>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>
    <div v-else-if="userInfo" class="user-content">
      <!-- 用户基本信息 -->
      <div class="user-header">
        <div class="avatar-section">
          <el-avatar :size="100" :src="userInfo.avatar || ''">
            {{ userInfo.nickname?.charAt(0) || '用' }}
          </el-avatar>
          <div class="stats-section">
            <span class="stat-item">{{ formatNumber(userInfo.followers || 0) }}粉丝</span>
            <span class="stat-divider"></span>
            <span class="stat-item">{{ formatNumber(userInfo.following || 0) }}关注</span>
            <span class="stat-divider"></span>
            <span class="stat-item">{{ formatNumber(userInfo.likes || 0) }}点赞</span>
          </div>
        </div>
        <div class="info-section">
          <h2 class="nickname">{{ userInfo.nickname }}</h2>
          <p class="username">{{ userInfo.username }}</p>
          <p class="signature" v-if="userInfo.signature">{{ userInfo.signature }}</p>
          <div class="user-meta">
            <span class="meta-item">注册时间：{{ formatDate(userInfo.joinTime || '') }}</span>
            <span class="meta-item">最后活跃：{{ formatDate(userInfo.lastActiveTime || '') }}</span>
            <span class="meta-item">性别：{{ getGenderText(userInfo.gender || 0) }}</span>
          </div>
          <div class="action-section">
            <el-button type="primary" @click="handleFollow">
              {{ isFollowing ? '已关注' : '关注' }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- 标签墙 -->
      <div class="tags-card" v-if="userInfo.tags && userInfo.tags.length > 0">
        <h3 class="section-title">标签</h3>
        <div class="tags">
          <el-tag v-for="tag in userInfo.tags" :key="tag" size="medium">
            {{ tag }}
          </el-tag>
        </div>
      </div>

      <!-- 发布的宠物 -->
      <div class="pets-card">
        <h3 class="section-title">发布的宠物</h3>
        <div v-if="userPets.length > 0" class="pets-list">
          <div v-for="pet in userPets" :key="pet.id" class="pet-card" @click="navigateToPetDetail(pet.id)">
            <img :src="pet.images?.[0] || 'https://via.placeholder.com/200'" alt="宠物图片" class="pet-image" />
            <div class="pet-info">
              <h4 class="pet-title">{{ pet.title }}</h4>
              <div class="pet-meta">
                <span class="pet-type">{{ pet.type === 0 ? '领养' : '救助' }}</span>
                <span class="pet-date">{{ formatDate(pet.createTime) }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">
          <el-empty description="暂无发布的宠物" />
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      <el-empty description="用户信息不存在" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft } from '@element-plus/icons-vue';
import { getUserInfoByUserId } from '../../api/user';

// 路由
const route = useRoute();
const router = useRouter();

// 状态
const loading = ref(false);
const userInfo = ref<any>(null);
const userPets = ref<any[]>([]);
const isFollowing = ref(false);

// 方法
const handleBack = () => {
  router.back();
};

const formatDate = (dateStr: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN');
};

const formatNumber = (num: number): string => {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + 'w';
  }
  return num.toString();
};

const navigateToPetDetail = (petId: number) => {
  router.push(`/pets/${petId}`);
};

const handleFollow = () => {
  // 关注/取消关注功能
  ElMessage.info('功能开发中');
};

const getGenderText = (gender: number): string => {
  switch (gender) {
    case 1:
      return '男';
    case 2:
      return '女';
    default:
      return '未知';
  }
};

const fetchUserInfo = async () => {
  const userId = route.params.id;
  if (!userId) {
    ElMessage.error('用户ID不存在');
    return;
  }

  loading.value = true;
  try {
    // 调用后端的用户信息接口
    const response = await getUserInfoByUserId(Number(userId));
    if (response.code === 200 && response.data) {
      userInfo.value = response.data;
    } else {
      ElMessage.error(response.message || '获取用户信息失败');
    }
    
    // 模拟用户发布的宠物
    userPets.value = [
      {
        id: 1,
        title: '可爱的小猫寻找新家',
        type: 0,
        images: ['https://via.placeholder.com/200'],
        createTime: new Date().toISOString()
      },
      {
        id: 2,
        title: '流浪狗救助，需要好心人',
        type: 1,
        images: ['https://via.placeholder.com/200'],
        createTime: new Date().toISOString()
      }
    ];
  } catch (error) {
    ElMessage.error('获取用户信息失败，请重试');
    console.error('获取用户信息失败:', error);
  } finally {
    loading.value = false;
  }
};

// 生命周期
onMounted(() => {
  fetchUserInfo();
});
</script>

<style scoped>
.user-info-container {
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

.user-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.user-header {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  gap: 32px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.stats-section {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #606266;
}

.stat-item {
  display: flex;
  align-items: center;
}

.stat-divider {
  width: 1px;
  height: 12px;
  background-color: #e4e7ed;
}

.info-section {
  flex: 1;
}

.nickname {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px 0;
}

.username {
  font-size: 14px;
  color: #909399;
  margin: 0 0 8px 0;
}

.signature {
  font-size: 14px;
  color: #606266;
  margin: 0 0 16px 0;
  line-height: 1.5;
}

.user-meta {
  display: flex;
  gap: 24px;
  margin: 0 0 16px 0;
  font-size: 14px;
  color: #909399;
}

.meta-item {
  display: flex;
  align-items: center;
}

.action-section {
  margin-top: 16px;
}

.tags-card,
.pets-card {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 16px 0;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pets-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
}

.pet-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.3s;
  cursor: pointer;
}

.pet-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.pet-image {
  width: 100%;
  height: 180px;
  object-fit: cover;
}

.pet-info {
  padding: 12px;
}

.pet-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin: 0 0 8px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pet-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
}

.pet-type {
  padding: 2px 8px;
  border-radius: 12px;
  background-color: #f5f7fa;
}

.empty-state {
  padding: 48px 24px;
  text-align: center;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .user-info-container {
    padding: 16px;
  }

  .user-header {
    flex-direction: column;
    text-align: center;
    gap: 16px;
  }

  .info-section {
    order: -1;
  }

  .pets-list {
    grid-template-columns: 1fr;
  }
}
</style>
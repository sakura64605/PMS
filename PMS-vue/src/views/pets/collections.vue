<template>
  <div class="collections-container">

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="8" animated />
    </div>
    <div v-else-if="pets.length > 0" class="pets-grid">
      <div
        v-for="pet in pets"
        :key="pet.id"
        class="pet-card"
        @click="handleView(pet.id)"
      >
        <div v-if="pet.images && pet.images.length > 0" class="card-image">
          <img :src="pet.images[0]" alt="宠物图片" />
        </div>
        <div class="card-content">
          <div class="card-header">
            <h3 class="card-title">{{ pet.title || '' }}</h3>
            <span class="type-tag" :class="pet.type === 0 ? 'adopt' : 'rescue'">
              {{ pet.type === 0 ? '领养' : '救助' }}
            </span>
          </div>
          <div class="pet-info">
            <div class="info-left">
              <span class="pet-name">{{ pet.petName || '未知' }}</span>
              <span class="info-divider">·</span>
              <span class="pet-type">{{ pet.petType || '未知' }}</span>
              <span class="info-divider">·</span>
              <span class="pet-age">{{ pet.petAge || '未知' }}</span>
              <span class="info-divider">·</span>
              <span class="pet-gender">
                <el-icon v-if="pet.petGender === 1"><Male /></el-icon>
                <el-icon v-else-if="pet.petGender === 2"><Female /></el-icon>
                <el-icon v-else><QuestionFilled /></el-icon>
              </span>
            </div>
          </div>
          <div class="card-footer">
            <div class="view-count">
              <el-icon><View /></el-icon>
              <span>{{ pet.viewCount || 0 }}</span>
            </div>
            <div class="create-time">
              {{ formatDate(pet.createTime) }}
            </div>
          </div>
          <div class="action-buttons">
            <el-button
              size="small"
              @click.stop="handleView(pet.id)"
            >
              查看
            </el-button>
            <el-button
              size="small"
              type="danger"
              @click.stop="handleCancelCollect(pet.id)"
            >
              取消收藏
            </el-button>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      <el-icon class="empty-icon"><Star /></el-icon>
      <p class="empty-text">暂无收藏内容</p>
      <el-button type="primary" @click="goToPets">去浏览宠物</el-button>
    </div>

    <!-- 分页组件 -->
    <div v-if="pets.length > 0" class="pagination-section">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[12, 24, 36]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Star, View, Male, Female, QuestionFilled } from '@element-plus/icons-vue';
import { getCollections, collectPet } from '../../api/pet';

// 路由
const router = useRouter();

// 状态
const loading = ref(false);
const pets = ref<any[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(12);

// 方法
const handleView = (id: number) => {
  router.push({ path: `/pets/${id}`, query: { from: 'collections' } });
};

const handleCancelCollect = async (id: number) => {
  try {
    const response = await collectPet(id);
    if (response.code === 200) {
      ElMessage.success('取消收藏成功');
      fetchCollections();
    } else {
      ElMessage.error(response.message || '取消收藏失败');
    }
  } catch (error) {
    ElMessage.error('取消收藏失败，请重试');
    console.error('取消收藏失败:', error);
  }
};

const goToPets = () => {
  router.push('/pets');
};

const formatDate = (dateStr: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  fetchCollections();
};

const handleCurrentChange = (current: number) => {
  pageNum.value = current;
  fetchCollections();
};

const fetchCollections = async () => {
  loading.value = true;
  try {
    const response = await getCollections({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    if (response.code === 200 && response.data) {
      pets.value = response.data.records || [];
      total.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || '获取收藏列表失败');
    }
  } catch (error) {
    ElMessage.error('获取收藏列表失败，请重试');
    console.error('获取收藏列表失败:', error);
  } finally {
    loading.value = false;
  }
};

// 生命周期
onMounted(() => {
  fetchCollections();
});
</script>

<style scoped>
.collections-container {
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
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.pets-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
  margin-bottom: 24px;
}

.pet-card {
  background-color: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
}

.pet-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.card-image {
  position: relative;
  height: 200px;
  overflow: hidden;
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-content {
  padding: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0;
  flex: 1;
  margin-right: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pet-info {
  font-size: 14px;
  color: #606266;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.info-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.info-left > span {
  margin-right: 4px;
}

.pet-name {
  font-weight: 500;
}

.info-divider {
  margin: 0 4px;
  color: #c0c4cc;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}

.view-count {
  display: flex;
  align-items: center;
  gap: 4px;
}

.create-time {
  flex: 1;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-left: 12px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.type-tag {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  color: white;
  white-space: nowrap;
}

.type-tag.adopt {
  background-color: #67c23a;
}

.type-tag.rescue {
  background-color: #e6a23c;
}

.empty-state {
  background-color: white;
  border-radius: 12px;
  padding: 48px 24px;
  text-align: center;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.empty-icon {
  font-size: 48px;
  color: #c0c4cc;
  margin-bottom: 20px;
}

.empty-text {
  font-size: 16px;
  color: #909399;
  margin-bottom: 20px;
}

.pagination-section {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .collections-container {
    padding: 16px;
  }

  .pets-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 16px;
  }

  .card-image {
    height: 150px;
  }

  .action-buttons {
    flex-direction: column;
  }
}
</style>

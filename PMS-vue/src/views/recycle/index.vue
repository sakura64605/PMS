<template>
  <div class="recycle-container">
    <el-card class="recycle-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">回收站</span>
        </div>
      </template>

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
            <h3 class="card-title">{{ pet.title || '' }}</h3>
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
              <span class="type-tag" :class="pet.type === 0 ? 'adopt' : 'rescue'">
                {{ pet.type === 0 ? '领养' : '救助' }}
              </span>
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
                type="success"
                @click.stop="handleRecover(pet.id)"
              >
                恢复
              </el-button>
              <el-button
                size="small"
                type="danger"
                @click.stop="handleDelete(pet.id)"
              >
                彻底删除
              </el-button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="回收站为空" />
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

      <!-- 恢复确认弹窗 -->
      <el-dialog
        v-model="recoverDialogVisible"
        title="确认恢复"
        width="400px"
      >
        <span>确定要恢复该宠物信息吗？</span>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="recoverDialogVisible = false">取消</el-button>
            <el-button type="success" @click="confirmRecover">确定</el-button>
          </span>
        </template>
      </el-dialog>

      <!-- 删除确认弹窗 -->
      <el-dialog
        v-model="deleteDialogVisible"
        title="确认删除"
        width="400px"
      >
        <span>确定要彻底删除该宠物信息吗？此操作不可恢复。</span>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="deleteDialogVisible = false">取消</el-button>
            <el-button type="danger" @click="confirmDelete">确定</el-button>
          </span>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { View, Male, Female, QuestionFilled } from '@element-plus/icons-vue';
import { getRecycleList, recoverPet, deletePetReally } from '../../api/pet';

// 路由
const router = useRouter();

// 状态
const loading = ref(false);
const pets = ref<any[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(12);
const recoverDialogVisible = ref(false);
const recoverId = ref<number | null>(null);
const deleteDialogVisible = ref(false);
const deleteId = ref<number | null>(null);

// 方法
const handleView = (id: number) => {
  router.push(`/pets/${id}`);
};

const handleRecover = (id: number) => {
  recoverId.value = id;
  recoverDialogVisible.value = true;
};

const confirmRecover = async () => {
  if (!recoverId.value) return;

  try {
    const response = await recoverPet(recoverId.value);
    if (response.code === 200) {
      ElMessage.success('恢复成功');
      recoverDialogVisible.value = false;
      fetchRecycleList();
    } else {
      ElMessage.error(response.message || '恢复失败');
    }
  } catch (error) {
    ElMessage.error('恢复失败，请重试');
    console.error('恢复失败:', error);
  }
};

const handleDelete = (id: number) => {
  deleteId.value = id;
  deleteDialogVisible.value = true;
};

const confirmDelete = async () => {
  if (!deleteId.value) return;

  try {
    const response = await deletePetReally(deleteId.value);
    if (response.code === 200) {
      ElMessage.success('删除成功');
      deleteDialogVisible.value = false;
      fetchRecycleList();
    } else {
      ElMessage.error(response.message || '删除失败');
    }
  } catch (error) {
    ElMessage.error('删除失败，请重试');
    console.error('删除失败:', error);
  }
};

const formatDate = (dateStr: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  fetchRecycleList();
};

const handleCurrentChange = (current: number) => {
  pageNum.value = current;
  fetchRecycleList();
};

const fetchRecycleList = async () => {
  loading.value = true;
  try {
    const response = await getRecycleList({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    if (response.code === 200) {
      pets.value = response.data.records || [];
      total.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || '获取回收站列表失败');
    }
  } catch (error) {
    ElMessage.error('获取回收站列表失败，请重试');
    console.error('获取回收站列表失败:', error);
  } finally {
    loading.value = false;
  }
};

// 页面加载时获取回收站列表
onMounted(() => {
  fetchRecycleList();
});
</script>

<style scoped>
.recycle-container {
  padding: 20px;
}

.recycle-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.loading-container {
  padding: 20px 0;
}

.pets-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
  margin-bottom: 24px;
  align-items: start;
}

.pet-card {
  background-color: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
  cursor: pointer;
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

.card-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pet-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}

.info-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.pet-name {
  font-weight: 500;
  color: #303133;
}

.info-divider {
  color: #c0c4cc;
  margin: 0 4px;
}

.pet-type, .pet-age {
  color: #606266;
  font-size: 14px;
}

.pet-gender {
  display: flex;
  align-items: center;
  color: #606266;
}

.type-tag {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.type-tag.adopt {
  background-color: #ecf5ff;
  color: #409eff;
}

.type-tag.rescue {
  background-color: #f0f9eb;
  color: #67c23a;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 12px;
  color: #909399;
}

.view-count {
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.empty-state {
  padding: 60px 0;
  text-align: center;
}

.pagination-section {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.dialog-footer {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
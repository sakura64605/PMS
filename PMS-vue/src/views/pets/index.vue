<template>
  <div class="pets-container">
    <!-- 顶部操作栏 -->
    <div class="filter-section">
      <!-- 类型切换和发布按钮 -->
      <div class="type-tabs-container">
        <el-tabs v-model="activeType" class="type-tabs" @tab-click="handleTypeChange">
          <el-tab-pane label="全部" name="-1"></el-tab-pane>
          <el-tab-pane label="领养" name="0"></el-tab-pane>
          <el-tab-pane label="救助" name="1"></el-tab-pane>
        </el-tabs>
        <el-button
          v-if="isLoggedIn"
          type="primary"
          class="create-button"
          @click="handleCreate"
        >
          发布信息
        </el-button>
      </div>

      <!-- 搜索和排序 -->
      <div class="search-sort-section">
        <el-select
          v-model="petType"
          placeholder="选择品种"
          class="filter-select"
          @change="handlePetTypeChange"
        >
          <el-option label="全部" value=""></el-option>
          <el-option label="猫" value="猫"></el-option>
          <el-option label="狗" value="狗"></el-option>
          <el-option label="兔子" value="兔子"></el-option>
          <el-option label="仓鼠" value="仓鼠"></el-option>
          <el-option label="其他" value="其他"></el-option>
        </el-select>

        <el-select
          v-model="gender"
          placeholder="选择性别"
          class="filter-select"
          @change="handleGenderChange"
        >
          <el-option label="全部" value="-1"></el-option>
          <el-option label="公" value="1"></el-option>
          <el-option label="母" value="2"></el-option>
          <el-option label="未知" value="0"></el-option>
        </el-select>

        <!-- 用户搜索 -->
        <div class="user-search-container">
          <el-select
            v-model="selectedUser"
            placeholder="选择用户"
            filterable
            remote
            :remote-method="handleUserSearch"
            :loading="userLoading"
            class="filter-select user-select"
            @change="handleUserSelect"
            value-key="userId"
          >
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
            <el-option
              v-for="user in userOptions"
              :key="user.userId"
              :label="user.nickname || user.username"
              :value="user"
            >
              <div class="user-option">
                <el-avatar :size="24" :src="user.avatar || ''">
                  {{ (user.nickname || user.username || '用').charAt(0) }}
                </el-avatar>
                <div class="user-info">
                  <div class="user-nickname">{{ user.nickname || user.username }}</div>
                  <div class="user-username">{{ user.username }}</div>
                </div>
              </div>
            </el-option>
          </el-select>
          <el-button
            v-if="selectedUser"
            type="text"
            class="clear-user-button"
            @click="clearUserFilter"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </div>

        <el-input
          v-model="searchKeyword"
          placeholder="搜索标题或内容"
          class="search-input"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch">
              <el-icon><Search /></el-icon>
            </el-button>
          </template>
        </el-input>

        <el-select
          v-model="sortOption"
          class="sort-select"
          @change="handleSortChange"
        >
          <el-option label="最新发布" value="createTime-desc"></el-option>
          <el-option label="最多浏览" value="viewCount-desc"></el-option>
        </el-select>

        <el-button
          type="default"
          class="reset-button"
          @click="handleReset"
        >
          重置
        </el-button>
      </div>
    </div>

    <!-- 宠物卡片网格 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="8" animated />
    </div>
    <div v-else-if="pets.length > 0" class="pets-grid">
      <div
        v-for="pet in pets"
        :key="pet.id"
        class="pet-card"
        @click="handleCardClick(pet.id)"
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
            <div class="stats">
              <div class="stat-item">
                <el-icon><View /></el-icon>
                <span>{{ pet.viewCount || 0 }}</span>
              </div>
              <div class="stat-item">
                <el-icon><Top /></el-icon>
                <span>{{ pet.likeCount || 0 }}</span>
              </div>
              <div class="stat-item">
                <el-icon><ChatLineSquare /></el-icon>
                <span>{{ pet.commentCount || 0 }}</span>
              </div>
              <div class="stat-item">
                <el-icon><Share /></el-icon>
                <span>{{ pet.shareCount || 0 }}</span>
              </div>
            </div>
            <div class="user-info">
              <el-avatar :size="24" :src="pet.user?.avatar || ''">
                {{ (pet.user?.nickname || pet.user?.username || '用').charAt(0) }}
              </el-avatar>
              <span class="nickname">{{ pet.user?.nickname || pet.user?.username || '未知用户' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      <el-empty description="暂无宠物信息" />
    </div>

    <!-- 分页组件 -->
    <div v-if="pets.length > 0" class="pagination-section">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 30]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Search, View, Male, Female, QuestionFilled, User, Close, Star, ChatLineSquare, Share, Top } from '@element-plus/icons-vue';
import { getPetList } from '../../api/pet';
import request from '../../utils/request';

// 路由
const router = useRouter();

// 状态
const activeType = ref('-1');
const searchKeyword = ref('');
const sortOption = ref('createTime-desc');
const petType = ref('');
const gender = ref('-1');
const pageNum = ref(1);
const pageSize = ref(10);
const loading = ref(false);
const pets = ref<any[]>([]);
const total = ref(0);
// 用户搜索相关
const selectedUser = ref<any>(null);
const userOptions = ref<any[]>([]);
const userLoading = ref(false);

// 计算属性
const isLoggedIn = computed(() => {
  return !!localStorage.getItem('token');
});

// 方法
const handleCreate = () => {
  router.push('/pets/create');
};

const handleCardClick = (id: number) => {
  router.push(`/pets/${id}`);
};

const getStatusClass = (status: number) => {
  if (status === undefined || status === null) return '';
  switch (status) {
    case 0: return 'pending';
    case 1: return 'published';
    case 2: return 'completed';
    default: return '';
  }
};

const getStatusText = (status: number) => {
  if (status === undefined || status === null) return '未知';
  switch (status) {
    case 0: return '待审核';
    case 1: return '已发布';
    case 2: return '已完成';
    default: return '未知';
  }
};

const formatDate = (dateStr: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

// 新增：带参数的请求函数
const fetchPetsWithType = async (type?: number) => {
  loading.value = true;
  try {
    const [orderBy, order] = sortOption.value.split('-');
    const requestParams = {
      type,
      gender: gender.value === '-1' ? undefined : parseInt(gender.value),
      petType: petType.value || undefined,
      userId: selectedUser.value?.userId || undefined,
      keyword: searchKeyword.value || undefined,
      orderBy,
      order,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    };
    
    console.log('=== 发送请求 ===');
    console.log('请求参数:', requestParams);
    
    const response = await getPetList(requestParams);
    
    if (response.code === 200 && response.data) {
      pets.value = response.data.records || [];
      total.value = response.data.total || 0;
      console.log('返回数据数量:', response.data.total);
      if (response.data.records?.length > 0) {
        console.log('第一条数据type:', response.data.records[0]?.type);
      }
    } else {
      ElMessage.error(response.message || '获取宠物列表失败');
    }
  } catch (error) {
    ElMessage.error('获取宠物列表失败，请重试');
    console.error('获取宠物列表失败:', error);
  } finally {
    loading.value = false;
  }
};

// 修改原来的 fetchPets
const fetchPets = () => {
  const currentType = activeType.value === '-1' ? undefined : parseInt(activeType.value);
  fetchPetsWithType(currentType);
};

// 修改 handleTypeChange - 关键修复
const handleTypeChange = (tab: any) => {
  console.log('=== 选项卡切换 ===');
  console.log('点击的选项卡name:', tab.props.name);
  
  // 直接根据点击的选项卡计算类型，不使用 activeType
  let newType: number | undefined;
  if (tab.props.name === '0') {
    newType = 0;  // 领养
  } else if (tab.props.name === '1') {
    newType = 1;  // 救助
  } else {
    newType = undefined;  // 全部
  }
  
  console.log('请求的type:', newType === undefined ? '全部' : newType);
  
  pageNum.value = 1;
  fetchPetsWithType(newType);
};

const handleSearch = () => {
  pageNum.value = 1;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const handleSortChange = () => {
  pageNum.value = 1;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const handleCurrentChange = (current: number) => {
  pageNum.value = current;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const handlePetTypeChange = () => {
  pageNum.value = 1;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const handleGenderChange = () => {
  pageNum.value = 1;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const handleUserSearch = async (keyword: string) => {
  if (keyword.trim().length < 1) {
    userOptions.value = [];
    return;
  }
  
  userLoading.value = true;
  try {
    const response = await request({
      url: '/user/search',
      method: 'get',
      params: { keyword }
    });
    
    if (response.code === 200) {
      userOptions.value = response.data || [];
    } else {
      ElMessage.error(response.message || '搜索用户失败');
      userOptions.value = [];
    }
  } catch (error) {
    ElMessage.error('搜索用户失败，请重试');
    userOptions.value = [];
    console.error('搜索用户失败:', error);
  } finally {
    userLoading.value = false;
  }
};

const handleUserSelect = () => {
  pageNum.value = 1;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const clearUserFilter = () => {
  selectedUser.value = null;
  pageNum.value = 1;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const handleReset = () => {
  // 重置所有筛选条件
  petType.value = '';
  gender.value = '-1';
  selectedUser.value = null;
  searchKeyword.value = '';
  pageNum.value = 1;
  // 刷新列表
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

// 生命周期
onMounted(() => {
  fetchPets();
});
</script>

<style scoped>
.pets-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.pets-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.create-button {
  font-size: 14px;
  padding: 8px 16px;
}

.filter-section {
  background-color: white;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.type-tabs-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.type-tabs {
  flex: 1;
}

.create-button {
  margin-left: 16px;
  white-space: nowrap;
}

.search-sort-section {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.filter-select {
  width: 120px;
}

.user-select {
  width: 200px;
}

.user-search-container {
  position: relative;
  display: flex;
  align-items: center;
}

.clear-user-button {
  position: absolute;
  right: 10px;
  z-index: 1;
  color: #909399;
}

.user-option {
  display: flex;
  align-items: center;
  padding: 8px 0;
}

.user-info {
  margin-left: 10px;
  flex: 1;
}

.user-nickname {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.user-username {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.search-input {
  flex: 1;
  max-width: 400px;
}

.sort-select {
  width: 150px;
}

.reset-button {
  white-space: nowrap;
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

.status-tag {
  position: absolute;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
  top: 12px;
  right: 12px;
}

.type-tag {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  color: white;
  margin-left: 8px;
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

.card-content {
  padding: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
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

.type-tag {
  white-space: nowrap;
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
}

.stats {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
}



.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nickname {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loading-container {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.empty-state {
  background-color: white;
  border-radius: 12px;
  padding: 48px 24px;
  text-align: center;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.pagination-section {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .pets-container {
    padding: 16px;
  }

  .pets-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .search-sort-section {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input {
    max-width: none;
  }

  .sort-select {
    width: 100%;
  }

  .pets-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 16px;
  }

  .card-image {
    height: 150px;
  }
}
</style>
<template>
  <div class="feed-container">
    
    <!-- 筛选和搜索 -->
    <div class="feed-filter">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索内容"
        clearable
        class="search-input"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select
        v-model="feedType"
        placeholder="内容类型"
        class="type-select"
        @change="fetchFeedList"
      >
        <el-option label="全部" value="all"></el-option>
        <el-option label="活动" value="activity"></el-option>
        <el-option label="宠物" value="pet"></el-option>
      </el-select>
    </div>
    
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>
    
    <!-- 空状态 -->
    <div v-else-if="feedList.length === 0" class="empty-state">
      <el-empty description="暂无关注内容" />
    </div>
    
    <!-- 内容列表 -->
    <div v-else class="feed-list">
      <div v-for="item in feedList" :key="item.id" class="feed-item">
        <!-- 活动卡片 -->
        <div v-if="item.type === 'activity'" class="feed-card activity-card">
          <div class="card-header">
            <img :src="item.user?.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="用户头像" class="user-avatar" />
            <div class="user-info">
              <h3 class="user-name">{{ item.user?.nickname || '未知用户' }}</h3>
              <p class="post-time">{{ formatDate(item.createTime) }}</p>
            </div>
            <div class="card-tag activity">活动</div>
          </div>
          <h2 class="card-title">{{ item.title }}</h2>
          <p class="card-content">{{ item.content }}</p>
          <div v-if="item.images && item.images.length > 0" class="card-images">
            <img v-for="(image, index) in item.images" :key="index" :src="image" alt="活动图片" class="card-image" />
          </div>
          <div class="card-footer">
            <div class="card-info">
              <span class="info-item">
                <el-icon><View /></el-icon>
                {{ item.viewCount || 0 }}次浏览
              </span>
              <span class="info-item">
                <el-icon><ChatLineSquare /></el-icon>
                {{ item.commentCount || 0 }}条评论
              </span>
            </div>
            <div class="card-actions">
              <el-button
                type="text"
                @click="handleLike(item.id, 'activity')"
                :type="item.isLiked ? 'primary' : 'default'"
              >
                <el-icon><Top /></el-icon>
                {{ item.isLiked ? '已点赞' : '点赞' }}({{ item.likeCount || 0 }})
              </el-button>
              <el-button type="text" @click="navigateToDetail(item.id, 'activity')">
                <el-icon><View /></el-icon>
                查看详情
              </el-button>
            </div>
          </div>
        </div>
        
        <!-- 宠物卡片 -->
        <div v-else-if="item.type === 'pet'" class="feed-card pet-card">
          <div class="card-header">
            <img :src="item.user?.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="用户头像" class="user-avatar" />
            <div class="user-info">
              <h3 class="user-name">{{ item.user?.nickname || '未知用户' }}</h3>
              <p class="post-time">{{ formatDate(item.createTime) }}</p>
            </div>
            <div class="card-tag adopt">
              宠物
            </div>
          </div>
          <h2 class="card-title">{{ item.title }}</h2>
          <p class="card-content">{{ item.content }}</p>
          <div v-if="item.images && item.images.length > 0" class="card-images">
            <img v-for="(image, index) in item.images" :key="index" :src="image" alt="宠物图片" class="card-image" />
          </div>
          <div class="card-footer">
            <div class="card-info">
              <span class="info-item">
                <el-icon><View /></el-icon>
                {{ item.viewCount || 0 }}次浏览
              </span>
              <span class="info-item">
                <el-icon><ChatLineSquare /></el-icon>
                {{ item.commentCount || 0 }}条评论
              </span>
            </div>
            <div class="card-actions">
              <el-button
                type="text"
                @click="handleLike(item.id, 'pet')"
                :type="item.isLiked ? 'primary' : 'default'"
              >
                <el-icon><Top /></el-icon>
                {{ item.isLiked ? '已点赞' : '点赞' }}({{ item.likeCount || 0 }})
              </el-button>
              <el-button type="text" @click="navigateToDetail(item.id, 'pet')">
                <el-icon><View /></el-icon>
                查看详情
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 分页 -->
    <div v-if="feedList.length > 0" class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
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
import { Search, Place, Clock, UserFilled, Top, View, House, Collection, ChatLineSquare } from '@element-plus/icons-vue';
import request from '../../utils/request';

// 路由
const router = useRouter();

// 状态
const loading = ref(false);
const feedList = ref<any[]>([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const searchKeyword = ref('');
const feedType = ref('all');

// 方法
const formatDate = (dateStr: string | undefined) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchFeedList();
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  currentPage.value = 1;
  fetchFeedList();
};

const handleCurrentChange = (page: number) => {
  currentPage.value = page;
  fetchFeedList();
};

const navigateToDetail = (id: number, type: string) => {
  if (type === 'activity') {
    router.push(`/pets/activity/${id}`);
  } else {
    router.push(`/pets/${id}`);
  }
};

const handleLike = async (id: number, type: string) => {
  try {
    const response = await request({
      url: '/like',
      method: 'post',
      data: {
        targetId: id,
        targetType: type === 'activity' ? 'pet_activity' : 'pet_post'
      }
    });
    if (response.code === 200 && response.data) {
      // 更新本地数据
      const item = feedList.value.find(item => item.id === id);
      if (item) {
        item.isLiked = response.data.isLiked;
        item.likeCount = response.data.likeCount;
      }
      ElMessage.success(response.data.isLiked ? '点赞成功' : '取消点赞成功');
    } else {
      ElMessage.error(response.message || '操作失败');
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试');
    console.error('点赞操作失败:', error);
  }
};

const fetchFeedList = async () => {
  loading.value = true;
  try {
    const response = await request({
      url: '/feed/home',
      method: 'get',
      params: {
        pageNum: currentPage.value,
        pageSize: pageSize.value
      }
    });
    if (response.code === 200 && response.data) {
      // 转换后端返回的数据结构为前端期望的结构
      feedList.value = (response.data.records || []).map((item: any) => ({
        id: item.postId,
        type: item.postType,
        title: item.title,
        content: item.content,
        images: item.coverImage ? [item.coverImage] : [],
        viewCount: item.viewCount,
        likeCount: item.likeCount,
        commentCount: item.commentCount,
        isLiked: item.isLiked,
        isFavorite: item.isFavorite,
        createTime: item.createTime,
        user: {
          userId: item.posterId,
          nickname: item.posterName,
          avatar: item.posterAvatar
        }
      }));
      total.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || '获取关注内容失败');
    }
  } catch (error) {
    ElMessage.error('获取关注内容失败，请重试');
    console.error('获取关注内容失败:', error);
  } finally {
    loading.value = false;
  }
};

// 生命周期
onMounted(() => {
  fetchFeedList();
});
</script>

<style scoped>
.feed-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin-bottom: 24px;
}

.feed-filter {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  background-color: white;
  padding: 16px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.search-input {
  flex: 1;
}

.type-select {
  width: 160px;
}

.loading-container {
  background-color: white;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.empty-state {
  background-color: white;
  border-radius: 8px;
  padding: 60px 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  text-align: center;
}

.feed-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feed-card {
  background-color: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.feed-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  position: relative;
}

.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  margin-right: 12px;
  object-fit: cover;
}

.user-info {
  flex: 1;
}

.user-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 4px 0;
}

.post-time {
  font-size: 12px;
  color: #909399;
  margin: 0;
}

.card-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
  position: absolute;
  right: 0;
  top: 0;
}

.card-tag.activity {
  background-color: #409eff;
}

.card-tag.adopt {
  background-color: #67c23a;
}

.card-tag.help {
  background-color: #e6a23c;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.card-content {
  font-size: 14px;
  line-height: 1.5;
  color: #606266;
  margin: 0 0 16px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-images {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 16px;
}

.card-image {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: 4px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #f0f0f0;
  padding-top: 16px;
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-item {
  font-size: 12px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 4px;
}

.card-actions {
  display: flex;
  gap: 12px;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .feed-container {
    padding: 16px;
  }

  .feed-filter {
    flex-direction: column;
  }

  .type-select {
    width: 100%;
  }

  .card-images {
    grid-template-columns: repeat(2, 1fr);
  }

  .card-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .card-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
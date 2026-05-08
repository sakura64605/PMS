<template>
  <div class="daily-container">
    <!-- 顶部操作栏 -->
    <div class="daily-header">
      <h2>宠友日记</h2>
      <el-button type="primary" @click="goToPublish">
        <el-icon><Plus /></el-icon>
        发布日记
      </el-button>
    </div>

    <!-- 日记列表 -->
    <div class="daily-list">
      <el-card v-for="item in dailyList" :key="item.id" class="daily-card" @click="goToDetail(item.id)">
        <!-- 作者信息 -->
        <div class="daily-header-info">
          <el-avatar :size="40" :src="item.user.avatar || 'https://via.placeholder.com/40'">
          </el-avatar>
          <div class="author-info">
            <div class="author-name">{{ item.user.nickname }}</div>
            <div class="publish-time">{{ formatTime(item.createTime) }}</div>
          </div>
        </div>

        <!-- 内容 -->
        <div class="daily-content">{{ item.content }}</div>

        <!-- 图片 -->
        <div v-if="item.images && item.images.length > 0" class="daily-images">
          <el-image
            v-for="(image, index) in item.images"
            :key="index"
            :src="image"
            class="daily-image"
            fit="cover"
            :preview-src-list="item.images"
          />
        </div>

        <!-- 位置 -->
        <div v-if="item.location" class="daily-location">
          <el-icon><Position /></el-icon>
          {{ item.location }}
        </div>

        <!-- 话题 -->
        <div v-if="item.topics && item.topics.length > 0" class="daily-topics">
          <el-tag
            v-for="topic in item.topics"
            :key="topic.id"
            type="info"
            size="small"
            @click="goToTopic(topic.id)"
          >
            {{ topic.name }}
          </el-tag>
        </div>

        <!-- 操作栏 -->
        <div class="daily-actions">
          <div class="action-item" @click.stop="handleLike(item)">
            <el-icon :class="(item.isLiked || false) ? 'liked' : ''">
              <component :is="(item.isLiked || false) ? 'StarFilled' : 'Star'" />
            </el-icon>
            <span>{{ item.likeCount }}</span>
          </div>
          <div class="action-item" @click.stop="goToDetail(item.id)">
            <el-icon><ChatDotRound /></el-icon>
            <span>{{ item.commentCount }}</span>
          </div>
          <div class="action-item" @click.stop="handleShare(item.id)">
            <el-icon><Share /></el-icon>
            <span>分享</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 加载更多 -->
    <div class="load-more" v-if="hasMore">
      <el-button @click="loadMore">加载更多</el-button>
    </div>
    <div v-else class="no-more" v-if="dailyList.length > 0">
      没有更多内容了
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getDailyFeed, likeDaily, recordDailyAction } from '../../api/daily';
import { ElMessage } from 'element-plus';
import { Plus, Star, StarFilled, ChatDotRound, Share, Position } from '@element-plus/icons-vue';

const router = useRouter();
const dailyList = ref<any[]>([]);
const hasMore = ref(true);
const page = ref(1);
const limit = 20;

// 格式化时间
const formatTime = (time: string) => {
  const date = new Date(time);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

// 加载日记列表
const loadDailyList = async (isLoadMore = false) => {
  try {
    const response = await getDailyFeed({
      pageSize: limit,
      pageNum: isLoadMore ? page.value + 1 : 1
    });
    
    if (response.data && response.data.records && response.data.records.length > 0) {
      if (isLoadMore) {
        dailyList.value = [...dailyList.value, ...response.data.records];
        page.value++;
      } else {
        dailyList.value = response.data.records;
        page.value = 1;
      }
      
      // 检查是否还有更多数据
      hasMore.value = response.data.records.length === limit;
    } else {
      if (isLoadMore) {
        hasMore.value = false;
      } else {
        dailyList.value = [];
      }
    }
  } catch (error) {
    ElMessage.error('获取日记列表失败');
    console.error('获取日记列表失败:', error);
  }
};

// 加载更多
const loadMore = () => {
  if (hasMore.value) {
    loadDailyList(true);
  }
};

// 跳转到发布页面
const goToPublish = () => {
  router.push('/daily/publish');
};

// 跳转到详情页面
const goToDetail = (id: number) => {
  router.push(`/daily/${id}`);
};

// 点赞/取消点赞
const handleLike = async (item: any) => {
  try {
    const response = await likeDaily(item.id);
    if (response.data) {
      // 更新本地状态
      item.isLiked = !item.isLiked;
      item.likeCount += item.isLiked ? 1 : -1;
      // 记录点赞行为
      if (item.isLiked) {
        recordDailyAction({
          targetId: item.id,
          actionType: 'like'
        }).catch(error => {
          console.error('记录点赞行为失败:', error);
        });
      }
      ElMessage.success(item.isLiked ? '点赞成功' : '取消点赞成功');
    }
  } catch (error) {
    ElMessage.error('操作失败');
    console.error('点赞操作失败:', error);
  }
};

// 分享
const handleShare = (id: number) => {
  // 模拟分享功能
  ElMessage.success('分享成功');
  
  // 记录分享行为
  recordDailyAction({
    targetId: id,
    actionType: 'share'
  }).catch(error => {
    console.error('记录分享行为失败:', error);
  });
};

// 跳转到话题页面
const goToTopic = (topicId: number) => {
  // 这里可以跳转到话题详情页面
  ElMessage.info('话题功能开发中');
};

// 初始化
onMounted(() => {
  loadDailyList();
});
</script>

<style scoped>
.daily-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px 0;
}

.daily-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding: 20px;
  background-color: #ffffff;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(10px);
}

.daily-header h2 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.daily-header .el-button {
  border-radius: 24px;
  padding: 10px 24px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
  transition: all 0.3s ease;
}

.daily-header .el-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
}

.daily-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.daily-card {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  background-color: #ffffff;
  cursor: pointer;
}

.daily-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.daily-header-info {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  padding: 16px 16px 0 16px;
}

.author-info {
  margin-left: 12px;
  flex: 1;
}

.author-name {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.publish-time {
  font-size: 12px;
  color: #909399;
}

.daily-content {
  padding: 0 16px 12px;
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
}

.daily-images {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 0 16px 12px;
}

.daily-image {
  width: 100%;
  height: 120px;
  border-radius: 8px;
  cursor: pointer;
}

.daily-location {
  padding: 0 16px 8px;
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}

.daily-topics {
  padding: 0 16px 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.daily-actions {
  display: flex;
  justify-content: space-around;
  padding: 16px 0;
  border-top: 1px solid #f0f0f0;
  margin-top: 12px;
  background-color: #f8f9fa;
  border-radius: 0 0 16px 16px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #606266;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 20px;
  transition: all 0.3s ease;
  font-size: 14px;
  font-weight: 500;
  background-color: #ffffff;
  border: 1px solid #e9ecef;
}

.action-item:hover {
  color: #409eff;
  background-color: #ecf5ff;
  border-color: #c6e2ff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

.action-item.liked {
  color: #f56c6c;
  background-color: #fef0f0;
  border-color: #fbc4c4;
}

.action-item.liked:hover {
  background-color: #fde2e2;
  box-shadow: 0 2px 8px rgba(245, 108, 108, 0.2);
}

.load-more {
  text-align: center;
  margin-top: 40px;
  margin-bottom: 20px;
}

.load-more .el-button {
  border-radius: 24px;
  padding: 10px 24px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.load-more .el-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.no-more {
  text-align: center;
  margin-top: 30px;
  margin-bottom: 20px;
  color: #909399;
  font-size: 14px;
  padding: 16px;
  background-color: #f8f9fa;
  border-radius: 12px;
  border: 1px solid #e9ecef;
}

@media (max-width: 768px) {
  .daily-container {
    padding: 10px;
  }
  
  .daily-images {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .daily-image {
    height: 100px;
  }
}
</style>
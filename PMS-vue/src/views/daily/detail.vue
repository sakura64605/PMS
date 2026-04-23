<template>
  <div class="daily-detail-container">
    <!-- 日记内容 -->
    <el-card class="daily-detail-card">
      <!-- 作者信息 -->
      <div class="daily-header-info">
        <el-avatar :size="48" :src="dailyDetail.user?.avatar || 'https://via.placeholder.com/48'">
        </el-avatar>
        <div class="author-info">
          <div class="author-name">{{ dailyDetail.user?.nickname }}</div>
          <div class="publish-time">{{ formatTime(dailyDetail.createTime) }}</div>
        </div>
        <div class="author-actions">
          <el-button size="small" @click="followUser(dailyDetail.user?.userId)">
            {{ isFollowed ? '已关注' : '关注' }}
          </el-button>
        </div>
      </div>

      <!-- 内容 -->
      <div class="daily-content">{{ dailyDetail.content }}</div>

      <!-- 图片 -->
      <div v-if="dailyDetail.images && dailyDetail.images.length > 0" class="daily-images">
        <el-image
          v-for="(image, index) in dailyDetail.images"
          :key="index"
          :src="image"
          class="daily-image"
          fit="cover"
          :preview-src-list="dailyDetail.images"
        />
      </div>

      <!-- 位置 -->
      <div v-if="dailyDetail.location" class="daily-location">
        <el-icon><Position /></el-icon>
        {{ dailyDetail.location }}
      </div>

      <!-- 话题 -->
      <div v-if="dailyDetail.topics && dailyDetail.topics.length > 0" class="daily-topics">
        <el-tag
          v-for="topic in dailyDetail.topics"
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
        <div class="action-item" @click="handleLike">
          <el-icon :class="dailyDetail.isLiked ? 'liked' : ''">
            <component :is="dailyDetail.isLiked ? 'StarFilled' : 'Star'" />
          </el-icon>
          <span>{{ dailyDetail.likeCount }}</span>
        </div>
        <div class="action-item" @click="scrollToComments">
          <el-icon><ChatDotRound /></el-icon>
          <span>{{ dailyDetail.commentCount }}</span>
        </div>
        <div class="action-item" @click="handleShare">
          <el-icon><Share /></el-icon>
          <span>分享</span>
        </div>
      </div>
    </el-card>

    <!-- 评论区域 -->
    <div class="comments-section" ref="commentsSection">
      <h3>评论 ({{ dailyDetail.commentCount || 0 }})</h3>
      
      <!-- 评论输入框 -->
      <el-card class="comment-input-card">
        <el-avatar :size="32" :src="currentUser?.avatar || 'https://via.placeholder.com/32'">
        </el-avatar>
        <el-input
          v-model="commentContent"
          placeholder="写下你的评论..."
          @keyup.enter="handleComment"
        />
        <el-button type="primary" @click="handleComment" :loading="commentLoading">
          评论
        </el-button>
      </el-card>

      <!-- 评论列表 -->
      <div class="comments-list">
        <el-card v-for="comment in comments" :key="comment.id" class="comment-card">
          <div class="comment-header">
            <el-avatar :size="32" :src="comment.user?.avatar || 'https://via.placeholder.com/32'">
            </el-avatar>
            <div class="comment-author-info">
              <div class="comment-author-name">{{ comment.user?.nickname }}</div>
              <div class="comment-time">{{ formatTime(comment.createTime) }}</div>
            </div>
          </div>
          <div class="comment-content">{{ comment.content }}</div>
          <div class="comment-actions">
            <div class="comment-action-item" @click="handleCommentLike(comment)">
              <el-icon :class="comment.isLiked ? 'liked' : ''">
                <component :is="comment.isLiked ? 'StarFilled' : 'Star'" />
              </el-icon>
              <span>{{ comment.likeCount || 0 }}</span>
            </div>
            <div class="comment-action-item" @click="replyToComment(comment)">
              <el-icon><ChatDotRound /></el-icon>
              <span>回复</span>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 加载更多评论 -->
      <div class="load-more-comments" v-if="hasMoreComments">
        <el-button @click="loadMoreComments">加载更多评论</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { getDailyDetail, likeDaily, recordDailyAction } from '../../api/daily';
import { ElMessage } from 'element-plus';
import { Star, StarFilled, ChatDotRound, Share, Position } from '@element-plus/icons-vue';

const router = useRouter();
const route = useRoute();
const dailyDetail = ref<any>({});
const comments = ref<any[]>([]);
const commentContent = ref('');
const commentLoading = ref(false);
const hasMoreComments = ref(true);
const commentsSection = ref<HTMLElement | null>(null);

// 当前用户信息
const currentUser = computed(() => {
  const userInfo = localStorage.getItem('userInfo');
  return userInfo ? JSON.parse(userInfo) : null;
});

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

// 加载日记详情
const loadDailyDetail = async () => {
  const id = route.params.id;
  if (!id) return;
  
  try {
    const response = await getDailyDetail(Number(id));
    if (response.data) {
      dailyDetail.value = response.data;
    }
  } catch (error) {
    ElMessage.error('获取日记详情失败');
    console.error('获取日记详情失败:', error);
  }
};

// 点赞/取消点赞
const handleLike = async () => {
  const id = route.params.id;
  if (!id) return;
  
  try {
    const response = await likeDaily(Number(id));
    if (response.data) {
      // 更新本地状态
      dailyDetail.value.isLiked = !dailyDetail.value.isLiked;
      dailyDetail.value.likeCount += dailyDetail.value.isLiked ? 1 : -1;
      ElMessage.success(dailyDetail.value.isLiked ? '点赞成功' : '取消点赞成功');
    }
  } catch (error) {
    ElMessage.error('操作失败');
    console.error('点赞操作失败:', error);
  }
};

// 分享
const handleShare = () => {
  // 模拟分享功能
  ElMessage.success('分享成功');
  
  // 记录分享行为
  const id = route.params.id;
  if (id) {
    recordDailyAction({
      targetId: Number(id),
      actionType: 'share'
    }).catch(error => {
      console.error('记录分享行为失败:', error);
    });
  }
};

// 关注用户
const followUser = (userId: number) => {
  // 模拟关注功能
  ElMessage.success('关注成功');
};

// 跳转到话题页面
const goToTopic = (topicId: number) => {
  // 这里可以跳转到话题详情页面
  ElMessage.info('话题功能开发中');
};

// 滚动到评论区
const scrollToComments = () => {
  if (commentsSection.value) {
    commentsSection.value.scrollIntoView({ behavior: 'smooth' });
  }
};

// 评论
const handleComment = async () => {
  if (!commentContent.value.trim()) {
    ElMessage.warning('请输入评论内容');
    return;
  }
  
  commentLoading.value = true;
  try {
    // 模拟评论功能
    setTimeout(() => {
      const newComment = {
        id: Date.now(),
        user: currentUser.value,
        content: commentContent.value,
        createTime: new Date().toISOString(),
        likeCount: 0,
        isLiked: false
      };
      comments.value.unshift(newComment);
      dailyDetail.value.commentCount += 1;
      commentContent.value = '';
      ElMessage.success('评论成功');
      commentLoading.value = false;
    }, 500);
  } catch (error) {
    ElMessage.error('评论失败');
    console.error('评论失败:', error);
    commentLoading.value = false;
  }
};

// 评论点赞
const handleCommentLike = (comment: any) => {
  comment.isLiked = !comment.isLiked;
  comment.likeCount += comment.isLiked ? 1 : -1;
};

// 回复评论
const replyToComment = (comment: any) => {
  commentContent.value = `@${comment.user?.nickname} `;
  // 滚动到评论输入框
  window.scrollTo({ top: 0, behavior: 'smooth' });
};

// 加载更多评论
const loadMoreComments = () => {
  // 模拟加载更多评论
  setTimeout(() => {
    hasMoreComments.value = false;
    ElMessage.info('没有更多评论了');
  }, 500);
};

// 初始化
onMounted(() => {
  loadDailyDetail();
  
  // 记录浏览行为
  const id = route.params.id;
  if (id) {
    recordDailyAction({
      targetId: Number(id),
      actionType: 'view'
    }).catch(error => {
      console.error('记录浏览行为失败:', error);
    });
  }
});
</script>

<style scoped>
.daily-detail-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px 0;
}

.daily-detail-card {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.08);
  margin-bottom: 20px;
}

.daily-header-info {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px;
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

.author-actions {
  margin-left: auto;
}

.daily-content {
  padding: 0 16px 16px;
  font-size: 16px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
}

.daily-images {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 0 16px 16px;
}

.daily-image {
  width: 100%;
  height: 150px;
  border-radius: 8px;
  cursor: pointer;
}

.daily-location {
  padding: 0 16px 12px;
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}

.daily-topics {
  padding: 0 16px 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.daily-actions {
  display: flex;
  justify-content: space-around;
  padding: 16px 0;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #909399;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 16px;
  transition: all 0.3s;
}

.action-item:hover {
  color: #409eff;
  background-color: #ecf5ff;
}

.action-item.liked {
  color: #f56c6c;
}

.comments-section {
  margin-top: 30px;
}

.comments-section h3 {
  margin: 0 0 16px 0;
  font-size: 18px;
  color: #303133;
}

.comment-input-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  margin-bottom: 20px;
  border-radius: 12px;
}

.comment-input-card .el-input {
  flex: 1;
}

.comments-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comment-card {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px 0 rgba(0, 0, 0, 0.08);
}

.comment-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  padding: 12px;
}

.comment-author-info {
  margin-left: 12px;
  flex: 1;
}

.comment-author-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.comment-time {
  font-size: 12px;
  color: #909399;
}

.comment-content {
  padding: 0 12px 12px;
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
}

.comment-actions {
  display: flex;
  gap: 20px;
  padding: 12px;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}

.comment-action-item {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #909399;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.3s;
}

.comment-action-item:hover {
  color: #409eff;
}

.comment-action-item.liked {
  color: #f56c6c;
}

.load-more-comments {
  text-align: center;
  margin-top: 20px;
}

@media (max-width: 768px) {
  .daily-detail-container {
    padding: 10px;
  }
  
  .daily-images {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .daily-image {
    height: 120px;
  }
  
  .comment-input-card {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .comment-input-card .el-input {
    width: 100%;
  }
}
</style>
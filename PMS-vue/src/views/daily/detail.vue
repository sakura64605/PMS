<template>
  <div class="daily-detail-container">
    <!-- 返回按钮 -->
    <el-button
      class="back-button"
      @click="handleBack"
    >
      <el-icon><ArrowLeft /></el-icon>
      返回列表
    </el-button>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>

    <div v-else-if="dailyDetail" class="detail-content">
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
          <el-icon :class="(dailyDetail.isLiked || false) ? 'liked' : ''">
            <component :is="(dailyDetail.isLiked || false) ? 'StarFilled' : 'Star'" />
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

      <!-- 评论区域 -->
      <div class="comment-section" ref="commentsSection">
        <h3 class="section-title">评论（{{ dailyDetail.commentCount || 0 }}）</h3>
        <div class="comment-input-area">
          <el-input
            type="textarea"
            placeholder="写下你的评论..."
            :rows="3"
            v-model="commentContent"
          ></el-input>
          <el-button
            type="primary"
            @click="handleSubmitComment"
            class="submit-comment-btn"
          >
            提交评论
          </el-button>
        </div>
        <div class="comment-list">
          <div v-if="comments.length === 0" class="no-comments">
            暂无评论，快来抢沙发~
          </div>
          <div v-else class="comment-item" v-for="comment in comments" :key="comment.id" :id="`comment-${comment.id}`">
            <img :src="comment.user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="用户头像" class="comment-avatar" />
            <div class="comment-content">
              <div class="comment-header">
                <div class="comment-author-info">
                  <span class="comment-nickname">{{ comment.user.nickname }}</span>
                  <span v-if="comment.replyTo" class="comment-reply-to"> &gt; {{ comment.replyTo.nickname }}</span>
                </div>
                <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
              </div>
              <div class="comment-text">
                {{ comment.content }}
              </div>
              <div class="comment-footer">
                <el-button
                  type="text"
                  size="small"
                  @click="comment.showReplyBox = !comment.showReplyBox; comment.replyToId = comment.user.id"
                >
                  回复
                </el-button>
                <el-button
                  type="text"
                  size="small"
                  @click="handleLikeComment(comment.id)"
                  :type="comment.isLiked ? 'primary' : 'default'"
                >
                  <el-icon><Top /></el-icon>
                  {{ comment.likeCount || 0 }}
                </el-button>
              </div>
              
              <!-- 回复输入框 -->
              <div v-if="comment.showReplyBox" class="reply-input-box">
                <el-input
                  v-model="comment.replyContent"
                  type="textarea"
                  placeholder="写下你的回复..."
                  :rows="2"
                />
                <div class="reply-input-actions">
                  <el-button
                    type="primary"
                    size="small"
                    @click="handleSubmitReply(comment.id, comment.user.nickname, comment.replyContent)"
                  >
                    发送
                  </el-button>
                  <el-button
                    type="default"
                    size="small"
                    @click="comment.showReplyBox = false; comment.replyContent = ''"
                  >
                    取消
                  </el-button>
                </div>
              </div>
              
              <!-- 子评论 -->
              <div v-if="comment.replies && comment.replies.length > 0" class="replies-list">
                <div v-for="(reply, index) in comment.replies.slice(0, comment.showAllReplies ? comment.replies.length : 1)" :key="reply.id" class="reply-item" :id="`comment-${reply.id}`">
                  <img :src="reply.user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="用户头像" class="reply-avatar" />
                  <div class="reply-content">
                    <div class="reply-header">
                      <div class="reply-author-info">
                        <span class="reply-nickname">{{ reply.user.nickname }}</span>
                        <span v-if="reply.replyTo" class="reply-reply-to"> &gt; {{ reply.replyTo.nickname }}</span>
                      </div>
                      <span class="reply-time">{{ formatTime(reply.createTime) }}</span>
                    </div>
                    <div class="reply-text">
                      {{ reply.content }}
                    </div>
                    <div class="reply-footer">
                      <el-button
                        type="text"
                        size="small"
                        @click="reply.showReplyBox = !reply.showReplyBox; reply.replyToId = reply.user.id"
                      >
                        回复
                      </el-button>
                      <el-button
                        type="text"
                        size="small"
                        @click="handleLikeComment(reply.id)"
                        :type="reply.isLiked ? 'primary' : 'default'"
                      >
                        <el-icon><Top /></el-icon>
                        {{ reply.likeCount || 0 }}
                      </el-button>
                    </div>
                    
                    <!-- 回复输入框 -->
                    <div v-if="reply.showReplyBox" class="reply-input-box">
                      <el-input
                        v-model="reply.replyContent"
                        type="textarea"
                        placeholder="写下你的回复..."
                        :rows="2"
                      />
                      <div class="reply-input-actions">
                        <el-button
                          type="primary"
                          size="small"
                          @click="handleSubmitReply(reply.id, reply.user.nickname, reply.replyContent)"
                        >
                          发送
                        </el-button>
                        <el-button
                          type="default"
                          size="small"
                          @click="reply.showReplyBox = false; reply.replyContent = ''"
                        >
                          取消
                        </el-button>
                      </div>
                    </div>
                  </div>
                </div>
                <div v-if="comment.replies.length > 1" class="reply-expand">
                  <el-button
                    type="text"
                    size="small"
                    @click="comment.showAllReplies = !comment.showAllReplies"
                  >
                    {{ comment.showAllReplies ? '收起回复' : `查看${comment.replies.length - 1}条回复 >` }}
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      <el-empty description="日记不存在" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { getDailyDetail, likeDaily, recordDailyAction } from '../../api/daily';
import { getCommentList, createComment } from '../../api/activity';
import { ElMessage } from 'element-plus';
import { ArrowLeft, Star, StarFilled, ChatDotRound, Share, Position, Top } from '@element-plus/icons-vue';

const router = useRouter();
const route = useRoute();
const dailyDetail = ref<any>({});
const comments = ref<any[]>([]);
const commentContent = ref('');
const commentsSection = ref<HTMLElement | null>(null);
const isFollowed = ref(false);

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

// 返回
const handleBack = () => {
  router.push('/daily');
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
  ElMessage.success('分享成功');
  
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
  ElMessage.success(isFollowed.value ? '取消关注成功' : '关注成功');
  isFollowed.value = !isFollowed.value;
};

// 跳转到话题页面
const goToTopic = (topicId: number) => {
  ElMessage.info('话题功能开发中');
};

// 滚动到评论区
const scrollToComments = () => {
  if (commentsSection.value) {
    commentsSection.value.scrollIntoView({ behavior: 'smooth' });
  }
};

// 提交评论
const handleSubmitComment = async () => {
  if (!commentContent.value.trim()) {
    ElMessage.warning('请输入评论内容');
    return;
  }
  
  const id = route.params.id;
  if (!id) {
    ElMessage.error('日记ID不存在');
    return;
  }
  
  const dailyId = Number(id);
  
  try {
    const response = await createComment({
      targetType: 'daily',
      targetId: dailyId,
      content: commentContent.value.trim(),
      parentId: 0
    });
    if (response.code === 200) {
      ElMessage.success('评论发布成功');
      commentContent.value = '';
      await fetchComments();
      await loadDailyDetail();
    } else {
      ElMessage.error(response.message || '评论发布失败');
    }
  } catch (error) {
    ElMessage.error('评论发布失败，请重试');
    console.error('评论发布失败:', error);
  }
};

// 点赞评论
const handleLikeComment = async (id: number) => {
  try {
    const response = await createComment({
      targetType: 'daily',
      targetId: Number(route.params.id),
      content: '',
      parentId: id
    });
    if (response.code === 200) {
      await fetchComments();
    } else {
      ElMessage.error(response.message || '操作失败');
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试');
    console.error('点赞评论失败:', error);
  }
};

// 提交回复
const handleSubmitReply = async (id: number, nickname: string, content: string) => {
  if (!content.trim()) {
    ElMessage.warning('请输入回复内容');
    return;
  }
  
  const dailyId = Number(route.params.id);
  if (!dailyId) return;
  
  try {
    let replyToId = null;
    const findReplyToId = (comments: any[]) => {
      for (const comment of comments) {
        if (comment.id === id) {
          replyToId = comment.replyToId;
          return true;
        }
        if (comment.replies && comment.replies.length > 0) {
          if (findReplyToId(comment.replies)) {
            return true;
          }
        }
      }
      return false;
    };
    findReplyToId(comments.value);
    
    const response = await createComment({
      targetType: 'daily',
      targetId: dailyId,
      content: content.trim(),
      parentId: id,
      replyTo: replyToId
    });
    if (response.code === 200) {
      ElMessage.success('回复发布成功');
      await fetchComments();
    } else {
      ElMessage.error(response.message || '回复发布失败');
    }
  } catch (error) {
    ElMessage.error('回复发布失败，请重试');
    console.error('回复发布失败:', error);
  }
};

// 获取评论列表
const fetchComments = async () => {
  const id = route.params.id;
  if (!id) return;
  
  try {
    const response = await getCommentList({
      targetType: 'daily',
      targetId: Number(id),
      pageNum: 1,
      pageSize: 100
    });
    if (response.code === 200) {
      if (response.data.records) {
        const flattenReplies = (replies: any[]) => {
          let result: any[] = [];
          (replies || []).forEach(reply => {
            result.push(reply);
            if (reply.replies && reply.replies.length > 0) {
              result = result.concat(flattenReplies(reply.replies));
            }
          });
          result.sort((a, b) => new Date(a.createTime).getTime() - new Date(b.createTime).getTime());
          return result;
        };
        
        comments.value = (response.data.records || []).map((comment: any) => ({
          ...comment,
          showAllReplies: false,
          showReplyBox: false,
          replyContent: '',
          replies: flattenReplies(comment.replies).map((reply: any) => ({
            ...reply,
            showReplyBox: false,
            replyContent: ''
          }))
        }));
      } else if (Array.isArray(response.data)) {
        const flattenReplies = (replies: any[]) => {
          let result: any[] = [];
          (replies || []).forEach(reply => {
            result.push(reply);
            if (reply.replies && reply.replies.length > 0) {
              result = result.concat(flattenReplies(reply.replies));
            }
          });
          result.sort((a, b) => new Date(a.createTime).getTime() - new Date(b.createTime).getTime());
          return result;
        };
        
        comments.value = response.data.map((comment: any) => ({
          ...comment,
          showAllReplies: false,
          showReplyBox: false,
          replyContent: '',
          replies: flattenReplies(comment.replies).map((reply: any) => ({
            ...reply,
            showReplyBox: false,
            replyContent: ''
          }))
        }));
      } else {
        comments.value = [];
      }
    } else {
      ElMessage.error(response.message || '获取评论列表失败');
      comments.value = [];
    }
  } catch (error) {
    ElMessage.error('获取评论列表失败，请重试');
    console.error('获取评论列表失败:', error);
    comments.value = [];
  }
};

// 初始化
onMounted(() => {
  loadDailyDetail();
  fetchComments();
  
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

.back-button {
  margin-bottom: 20px;
}

.loading-container {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
}

.detail-content {
  background-color: white;
  border-radius: 12px;
  padding: 20px;
}

.daily-header-info {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
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
  padding: 16px 0;
  font-size: 16px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
}

.daily-images {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 16px 0;
}

.daily-image {
  width: 100%;
  height: 150px;
  border-radius: 8px;
  cursor: pointer;
}

.daily-location {
  padding: 8px 0;
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}

.daily-topics {
  padding: 8px 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.daily-actions {
  display: flex;
  justify-content: space-around;
  padding: 16px 0;
  border-top: 1px solid #f0f0f0;
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

/* 评论区 */
.comment-section {
  margin-top: 24px;
  background-color: white;
  border-radius: 12px;
  padding: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 16px 0;
}

.comment-input-area {
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background-color: #f9f9f9;
  padding: 16px;
  border-radius: 8px;
}

.submit-comment-btn {
  align-self: flex-end;
}

.comment-list {
  margin-top: 20px;
}

.no-comments {
  text-align: center;
  padding: 40px 24px;
  color: #909399;
  font-size: 14px;
  background-color: #f9f9f9;
  border-radius: 8px;
}

.comment-item {
  display: flex;
  margin-bottom: 16px;
  padding: 0 0 0 40px;
  position: relative;
}

.comment-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  position: absolute;
  left: -16px;
  top: 2px;
}

.comment-content {
  flex: 1;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.comment-author-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-nickname {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.comment-reply-to {
  font-size: 13px;
  color: #606266;
}

.comment-time {
  font-size: 12px;
  color: #909399;
}

.comment-text {
  font-size: 14px;
  line-height: 1.5;
  color: #303133;
  margin-bottom: 8px;
  word-break: break-word;
}

.comment-footer {
  display: flex;
  gap: 16px;
}

.comment-footer .el-button {
  font-size: 12px;
  padding: 0;
  height: 24px;
  line-height: 24px;
}

/* 子评论样式 */
.replies-list {
  margin-top: 16px;
  border-left: 2px solid #e4e7ed;
  padding-left: 20px;
}

.reply-item {
  display: flex;
  margin-bottom: 16px;
}

.reply-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  margin-right: 12px;
  object-fit: cover;
}

.reply-content {
  flex: 1;
}

.reply-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.reply-author-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reply-nickname {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.reply-reply-to {
  font-size: 12px;
  color: #606266;
}

.reply-time {
  font-size: 11px;
  color: #909399;
}

.reply-text {
  font-size: 13px;
  line-height: 1.5;
  color: #303133;
  margin-bottom: 8px;
  word-break: break-word;
}

.reply-footer {
  display: flex;
  gap: 16px;
}

.reply-footer .el-button {
  font-size: 11px;
  padding: 0;
  height: 20px;
  line-height: 20px;
}

.reply-expand {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}

.reply-expand .el-button {
  font-size: 12px;
  padding: 0;
  color: #909399;
}

/* 回复输入框样式 */
.reply-input-box {
  margin-top: 16px;
  padding: 16px;
  background-color: #f9f9f9;
  border-radius: 8px;
}

.reply-input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
}

.reply-input-actions .el-button {
  font-size: 12px;
  padding: 0 12px;
  height: 28px;
  line-height: 28px;
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
}
</style>
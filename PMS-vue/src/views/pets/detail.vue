<template>
  <div class="pet-detail-container">
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
    <div v-else-if="pet" class="detail-content">
      <!-- 顶部标题栏 -->
      <div class="top-header">
        <div class="title-section">
          <h1 class="main-title">{{ pet.title }}</h1>
          <div class="pet-name">宠物名：{{ pet.petName || '未知' }}</div>
        </div>
        <div class="type-tag" :class="pet.type === 0 ? 'adopt' : 'rescue'">
          {{ pet.type === 0 ? '领养' : '救助' }}
        </div>
        <div class="time-section">
          <div class="time-item">发布：{{ formatDate(pet.createTime) }}</div>
          <div class="time-item">编辑：{{ formatDate(pet.updateTime) }}</div>
        </div>
      </div>

      <!-- 宠物图片区 -->
      <div v-if="pet.images && pet.images.length > 0" class="image-section">
        <el-carousel
          :interval="5000"
          type="card"
          height="300px"
          @click="handleImageClick"
        >
          <el-carousel-item v-for="(image, index) in pet.images" :key="index">
            <img :src="image" alt="宠物图片" class="pet-image" />
          </el-carousel-item>
        </el-carousel>
      </div>

      <!-- 基本信息卡片 -->
      <div class="info-card">
        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">品种：</span>
            <span class="info-value">{{ pet.petType || '未知' }}</span>
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
            <span class="info-label">年龄：</span>
            <span class="info-value">{{ pet.petAge || '未知' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">浏览：</span>
            <span class="info-value">{{ pet.viewCount || 0 }}</span>
          </div>
        </div>
      </div>

      <!-- 详细描述 + 联系方式 -->
      <div class="detail-section">
        <div class="content-part">
          <h3 class="section-title">详细描述</h3>
          <div class="content">{{ pet.content }}</div>
        </div>
        
        <div class="contact-part">
          <h3 class="section-title">联系方式</h3>
          <div class="contact-info">
            <div class="contact-item">
              <span class="contact-label">电话：</span>
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
              <span class="contact-label">微信：</span>
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
      </div>

      <!-- 底部互动栏 -->
      <div class="interaction-bar">
        <div class="action-buttons">
          <el-button
            v-if="isOwner"
            type="primary"
            @click="handleEdit"
          >
            编辑
          </el-button>
          <el-button
            v-if="isOwner"
            type="danger"
            @click="handleDelete"
          >
            下架/删除
          </el-button>
          <el-button
            v-else
            @click="handleLike"
            :type="isLiked ? 'primary' : 'default'"
          >
            <el-icon><Top /></el-icon>
            {{ isLiked ? '已点赞' : '点赞' }}({{ pet.likeCount || 0 }})
          </el-button>
          <el-button
            v-if="!isOwner"
            @click="handleCollect"
            :type="isCollected ? 'primary' : 'default'"
          >
            <el-icon><Star /></el-icon>
            {{ isCollected ? '已收藏' : '收藏' }}
          </el-button>
        </div>
      </div>

      <!-- 评论区 -->
      <div class="comment-section">
        <h3 class="section-title">网友评论（{{ pet.commentCount || 0 }}）</h3>
        <div class="comment-input-area">
          <el-input
            type="textarea"
            placeholder="想对小可爱说点什么…"
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
          <div v-if="(pet.comments && pet.comments.length > 0)" class="comment-item">
            <!-- 评论列表项 -->
            <div v-for="(comment, index) in pet.comments" :key="index" class="comment">
              <div class="comment-header">
                <span class="comment-author">{{ comment.userNickname }}</span>
                <span class="comment-time">{{ formatDate(comment.createTime) }}</span>
              </div>
              <div class="comment-content">{{ comment.content }}</div>
            </div>
          </div>
          <div v-else class="no-comments">
            暂无评论，快来抢沙发~
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      <el-empty description="宠物信息不存在" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, View, Male, Female, QuestionFilled, DocumentCopy, Star, ChatLineSquare, Share, Top } from '@element-plus/icons-vue';
import { getPetDetail, likePet, collectPet } from '../../api/pet';

// 路由
const route = useRoute();
const router = useRouter();

// 状态
const loading = ref(false);
const pet = ref<any>(null);
const isLiked = ref(false);
const isCollected = ref(false);
const commentContent = ref('');

// 计算属性
const isOwner = computed(() => {
  const userInfo = localStorage.getItem('userInfo');
  if (!userInfo) return false;
  try {
    const user = JSON.parse(userInfo);
    return pet.value && user.userId === pet.value.user.userId;
  } catch (e) {
    return false;
  }
});

// 方法
const handleBack = () => {
  // 从路由参数中获取来源页面
  const from = route.query.from as string;
  const type = route.query.type as string;
  if (from === 'my-posts') {
    router.push('/pets/my-posts');
  } else if (from === 'collections') {
    router.push('/pets/collections');
  } else if (from === 'pets-index') {
    // 从领养或救助标签页进入，返回时保持原标签页
    router.push({ path: '/pets', query: { type } });
  } else {
    router.push('/pets');
  }
};

const handleEdit = () => {
  ElMessage.info('功能开发中');
  // router.push(`/pets/${pet.value.id}/edit`);
};

const handleDelete = () => {
  ElMessage.info('功能开发中');
};

const handleLike = async () => {
  if (!pet.value) return;
  
  try {
    const response = await likePet(pet.value.id);
    if (response.code === 200 && response.data) {
      isLiked.value = response.data.isLiked;
      // 更新点赞数
      if (response.data.likeCount !== undefined) {
        pet.value.likeCount = response.data.likeCount;
      }
      ElMessage.success(isLiked.value ? '点赞成功' : '取消点赞成功');
    } else {
      ElMessage.error(response.message || '操作失败');
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试');
    console.error('点赞操作失败:', error);
  }
};

const handleCollect = async () => {
  if (!pet.value) return;
  
  // 先切换本地状态，提高用户体验
  const newState = !isCollected.value;
  isCollected.value = newState;
  pet.value.isFavorite = newState;
  
  try {
    const response = await collectPet(pet.value.id);
    if (response.code === 200) {
      ElMessage.success(newState ? '收藏成功' : '取消收藏成功');
    } else {
      // 操作失败，恢复原状态
      isCollected.value = !newState;
      pet.value.isFavorite = !newState;
      ElMessage.error(response.message || '操作失败');
    }
  } catch (error) {
    // 操作失败，恢复原状态
    isCollected.value = !newState;
    pet.value.isFavorite = !newState;
    ElMessage.error('操作失败，请重试');
    console.error('收藏操作失败:', error);
  }
};

const navigateToUserInfo = (userId: number) => {
  router.push(`/user/${userId}`);
};

const getStatusClass = (status: number) => {
  switch (status) {
    case 0: return 'pending';
    case 1: return 'published';
    case 2: return 'completed';
    default: return '';
  }
};

const getStatusText = (status: number) => {
  switch (status) {
    case 0: return '待审核';
    case 1: return '已发布';
    case 2: return '已完成';
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

const handleSubmitComment = () => {
  if (!commentContent.value.trim()) {
    ElMessage.warning('请输入评论内容');
    return;
  }
  
  // 这里需要调用后端API提交评论
  ElMessage.info('评论功能开发中');
  commentContent.value = '';
};

const handleImageClick = (event: any) => {
  // 这里可以实现图片点击放大查看的功能
  // 例如使用Element Plus的Image组件或第三方库
  ElMessage.info('图片查看功能开发中');
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
      // 初始化点赞和收藏状态，使用后端返回的字段
      isLiked.value = pet.value.isLiked || false;
      isCollected.value = pet.value.isFavorite || false;
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
.pet-detail-container {
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

/* 顶部标题栏 */
.top-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  background-color: white;
  padding: 24px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  position: relative;
}

.title-section {
  flex: 1;
  min-width: 200px;
}

.main-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px 0;
}

.pet-name {
  font-size: 14px;
  color: #909399;
}

.type-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
  position: absolute;
  top: 24px;
  right: 24px;
  z-index: 1;
}

.time-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #909399;
  position: absolute;
  bottom: 16px;
  right: 24px;
  z-index: 0;
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

/* 宠物图片区 */
.image-section {
  background-color: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.pet-image {
  width: 100%;
  height: 400px;
  object-fit: cover;
}

/* 基本信息卡片 */
.info-card {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-label {
  font-size: 14px;
  color: #606266;
  min-width: 60px;
}

.info-value {
  font-size: 14px;
  color: #333;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 详细描述 + 联系方式 */
.detail-section {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.content-part {
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid #f0f0f0;
}

.contact-part {
  margin-top: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 16px 0;
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
  min-width: 60px;
}

.contact-value {
  font-size: 14px;
  color: #333;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.contact-value.privacy {
  color: #909399;
  font-style: italic;
}

/* 底部互动栏 */
.interaction-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: white;
  border-radius: 12px;
  padding: 16px 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.publish-time {
  font-size: 14px;
  color: #909399;
}

/* 评论区 */
.comment-section {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.comment-input-area {
  margin-bottom: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.submit-comment-btn {
  align-self: flex-end;
}

.comment-list {
  margin-top: 24px;
}

.comment {
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.comment:last-child {
  border-bottom: none;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.comment-author {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.comment-time {
  font-size: 12px;
  color: #909399;
}

.comment-content {
  font-size: 14px;
  line-height: 1.6;
  color: #333;
}

.no-comments {
  text-align: center;
  padding: 48px 24px;
  color: #909399;
  font-size: 14px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .pet-detail-container {
    padding: 16px;
  }

  .top-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 16px;
  }

  .status-section {
    align-self: flex-start;
  }

  .pet-image {
    height: 200px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .info-card,
  .detail-section,
  .interaction-bar,
  .comment-section {
    padding: 16px;
  }

  .interaction-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .action-buttons {
    width: 100%;
    justify-content: space-between;
  }

  .publish-time {
    align-self: flex-end;
  }

  .contact-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
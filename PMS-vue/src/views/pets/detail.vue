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
          <div v-if="comments.length === 0" class="no-comments">
            暂无评论，快来抢沙发~
          </div>
          <div v-else class="comment-item" v-for="comment in comments" :key="comment.id">
            <img :src="comment.user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="用户头像" class="comment-avatar" />
            <div class="comment-content">
              <div class="comment-header">
                <div class="comment-author-info">
                  <span class="comment-nickname">{{ comment.user.nickname }}</span>
                  <span v-if="comment.replyTo" class="comment-reply-to"> &gt; {{ comment.replyTo.nickname }}</span>
                </div>
                <span class="comment-time">{{ formatDate(comment.createTime) }}</span>
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
                <!-- 显示第一条子评论 -->
                <div v-for="(reply, index) in comment.replies.slice(0, comment.showAllReplies ? comment.replies.length : 1)" :key="reply.id" class="reply-item">
                  <img :src="reply.user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="用户头像" class="reply-avatar" />
                  <div class="reply-content">
                    <div class="reply-header">
                      <div class="reply-author-info">
                        <span class="reply-nickname">{{ reply.user.nickname }}</span>
                        <span v-if="reply.replyTo" class="reply-reply-to"> &gt; {{ reply.replyTo.nickname }}</span>
                      </div>
                      <span class="reply-time">{{ formatDate(reply.createTime) }}</span>
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
                <!-- 显示展开/收起按钮 -->
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
import { getCommentList, createComment } from '../../api/activity';
import request from '../../utils/request';

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

// 回复评论
const replyToId = ref(0);
const replyToName = ref('');

const handleReply = (id: number, name: string) => {
  replyToId.value = id;
  replyToName.value = name;
  commentContent.value = `@${name} `;
  // 聚焦评论输入框
  setTimeout(() => {
    const textarea = document.querySelector('.comment-input-area textarea') as HTMLTextAreaElement;
    textarea?.focus();
  }, 100);
};

// 点赞评论
const handleLikeComment = async (id: number) => {
  try {
    const response = await request({
      url: '/like',
      method: 'post',
      data: {
        targetId: id,
        targetType: 'pet_comment'
      }
    });
    if (response.code === 200 && response.data) {
      const message = response.data.isLiked ? '点赞成功' : '取消点赞成功';
      ElMessage.success(message);
      // 刷新评论列表
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
  
  const petId = Number(route.params.id);
  if (!petId) return;
  
  try {
    // 查找评论对象，获取replyToId
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
      targetType: 'pet_post',
      targetId: petId,
      content: content.trim(),
      parentId: id, // 回复给的评论的id
      replyTo: replyToId // 回复给的人的ID
    });
    if (response.code === 200) {
      ElMessage.success('回复发布成功');
      // 刷新评论列表
      await fetchComments();
    } else {
      ElMessage.error(response.message || '回复发布失败');
    }
  } catch (error) {
    ElMessage.error('回复发布失败，请重试');
    console.error('回复发布失败:', error);
  }
};

const handleSubmitComment = async () => {
  if (!commentContent.value.trim()) {
    ElMessage.warning('请输入评论内容');
    return;
  }
  
  const id = route.params.id;
  if (!id) {
    ElMessage.error('宠物ID不存在');
    return;
  }
  
  const petId = Number(id);
  
  try {
    const response = await createComment({
      targetType: 'pet_post',
      targetId: petId,
      content: commentContent.value.trim(),
      parentId: 0 // 顶级评论的parentId为0
    });
    if (response.code === 200) {
      ElMessage.success('评论发布成功');
      commentContent.value = '';
      replyToId.value = 0;
      replyToName.value = '';
      // 刷新评论列表
      await fetchComments();
      // 刷新宠物详情，更新评论数
      await fetchPetDetail();
    } else {
      ElMessage.error(response.message || '评论发布失败');
    }
  } catch (error) {
    ElMessage.error('评论发布失败，请重试');
    console.error('评论发布失败:', error);
  }
};

const handleImageClick = (event: any) => {
  // 这里可以实现图片点击放大查看的功能
  // 例如使用Element Plus的Image组件或第三方库
  ElMessage.info('图片查看功能开发中');
};

// 评论列表
const comments = ref<any[]>([]);

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
      // 获取评论列表
      await fetchComments();
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

// 获取评论列表
const fetchComments = async () => {
  const id = route.params.id;
  if (!id) return;
  
  try {
    const response = await getCommentList({
      targetType: 'pet_post',
      targetId: Number(id),
      pageNum: 1,
      pageSize: 100
    });
    console.log('评论列表响应:', response);
    if (response.code === 200) {
      console.log('评论数据:', response.data);
      // 尝试不同的数据结构
      if (response.data.records) {
        // 为每个评论添加showAllReplies、showReplyBox和replyContent属性，默认为false和空字符串
        // 扁平化所有嵌套回复，将所有回复都放在顶级评论的replies数组中
        const flattenReplies = (replies: any[]) => {
          let result: any[] = [];
          (replies || []).forEach(reply => {
            result.push(reply);
            if (reply.replies && reply.replies.length > 0) {
              result = result.concat(flattenReplies(reply.replies));
            }
          });
          // 按时间从先到后排序
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
        // 为每个评论添加showAllReplies、showReplyBox和replyContent属性，默认为false和空字符串
        // 扁平化所有嵌套回复，将所有回复都放在顶级评论的replies数组中
        const flattenReplies = (replies: any[]) => {
          let result: any[] = [];
          (replies || []).forEach(reply => {
            result.push(reply);
            if (reply.replies && reply.replies.length > 0) {
              result = result.concat(flattenReplies(reply.replies));
            }
          });
          // 按时间从先到后排序
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
      console.log('最终评论列表:', comments.value);
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
  background-color: #f9f9f9;
  padding: 20px;
  border-radius: 8px;
}

.submit-comment-btn {
  align-self: flex-end;
}

.comment-list {
  margin-top: 24px;
}

.comment-item {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comment-item {
  display: flex;
  margin-bottom: 16px;
  padding: 0 0 0 40px;
  background-color: transparent;
  border-radius: 0;
  transition: all 0.3s ease;
  position: relative;
}

.comment-item:hover {
  background-color: #f9f9f9;
}

.comment-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  flex-shrink: 0;
  position: absolute;
  left: -16px;
  top: 2px;
}

.comment-content {
  flex: 1;
  min-width: 0;
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
  color: #333;
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
  font-size: 13px;
  line-height: 1.5;
  color: #333;
  margin-bottom: 8px;
  word-break: break-word;
}

.comment-footer {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-top: 4px;
}

.comment-footer .el-button {
  font-size: 12px;
  padding: 0;
  height: 24px;
  line-height: 24px;
}

.no-comments {
  text-align: center;
  padding: 60px 24px;
  color: #909399;
  font-size: 14px;
  background-color: #f9f9f9;
  border-radius: 8px;
  margin-top: 24px;
}

/* 子评论样式 */
.replies-list {
  margin-top: 16px;
  margin-left: 0;
  border-left: 2px solid #e4e7ed;
  padding-left: 20px;
}

.reply-item {
  display: flex;
  margin-bottom: 16px;
  padding: 0;
  background-color: transparent;
  border-radius: 0;
  transition: all 0.3s ease;
}

.reply-item:hover {
  background-color: #f9f9f9;
}

.reply-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  margin-right: 12px;
  object-fit: cover;
  border: 2px solid #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
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
  color: #333;
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
  color: #333;
  margin-bottom: 8px;
  word-break: break-word;
}

.reply-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 8px;
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
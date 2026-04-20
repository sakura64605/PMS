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
        <!-- 发布者信息 -->
        <div class="publisher-card">
          <img :src="pet.user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="发布者头像" class="publisher-avatar" @click="navigateToUserInfo(pet.user.userId)" style="cursor: pointer;" />
          <div class="publisher-info" @click="navigateToUserInfo(pet.user.userId)" style="cursor: pointer;">
            <h3 class="nickname">{{ pet.user.nickname }}</h3>
            <p class="username">@{{ pet.user.username }}</p>
          </div>
          <el-button type="primary" size="small" :type="isFollowing ? 'info' : 'primary'" @click="handleFollow(pet.user.userId)">{{ isFollowing ? '已关注' : '关注' }}</el-button>
        </div>
        
        <!-- 标题和标签部分 -->
        <div class="title-and-tags">
          <div class="title-section">
            <h1 class="main-title">{{ pet.title }}</h1>
            <div class="pet-name" v-if="pet.type !== 2">宠物名：{{ pet.petName || '未知' }}</div>
          </div>
          <div class="type-tag" :class="pet.type === 0 ? 'adopt' : pet.type === 1 ? 'rescue' : 'activity'">
            {{ pet.type === 0 ? '领养' : pet.type === 1 ? '救助' : '活动' }}
          </div>
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
        <!-- 宠物信息 -->
        <div v-if="pet.type !== 2" class="info-grid">
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
        
        <!-- 活动信息 -->
        <div v-else class="info-grid">
          <div class="info-item">
            <span class="info-label">地点：</span>
            <span class="info-value">{{ pet.location || pet.address || '未知' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">时间：</span>
            <span class="info-value">
              {{ pet.startTime ? formatDate(pet.startTime) : '未知' }} - {{ pet.endTime ? formatDate(pet.endTime) : '未知' }}
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">人数：</span>
            <span class="info-value">{{ pet.participantCount || pet.signUpCount || 0 }}/{{ pet.maxParticipants || pet.maxPeople || 0 }}</span>
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
          <!-- 审核按钮 -->
          <el-button
            v-if="needAudit"
            type="success"
            @click="handleApprove"
          >
            ✅ 审核通过
          </el-button>
          <el-button
            v-if="needAudit"
            type="danger"
            @click="handleReject"
          >
            ❌ 审核拒绝
          </el-button>
          
          <!-- 活动操作按钮 -->
          <template v-if="pet.type === 2">
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
              v-if="!isOwner"
              type="primary"
              @click="handleJoinActivity"
            >
              报名参加
            </el-button>
            <el-button
              v-if="!isOwner"
              @click="handleLike"
              :type="isLiked ? 'primary' : 'default'"
            >
              <el-icon><Top /></el-icon>
              {{ isLiked ? '已点赞' : '点赞' }}({{ pet.likeCount || 0 }})
            </el-button>
          </template>
          
          <!-- 宠物操作按钮 -->
          <template v-else>
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
          </template>
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
          <div v-else class="comment-item" v-for="comment in comments" :key="comment.id" :id="`comment-${comment.id}`">
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
                <div v-for="(reply, index) in comment.replies.slice(0, comment.showAllReplies ? comment.replies.length : 1)" :key="reply.id" class="reply-item" :id="`comment-${reply.id}`">
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
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft, View, Male, Female, QuestionFilled, DocumentCopy, Star, ChatLineSquare, Share, Top } from '@element-plus/icons-vue';
import { getPetDetail, likePet, collectPet } from '../../api/pet';
import { getActivityDetail, signupActivity, getCommentList, createComment } from '../../api/activity';
import { approveAudit, rejectAudit } from '../../api/audit';
import request from '../../utils/request';

// 路由
const route = useRoute();
const router = useRouter();

// 状态
const loading = ref(false);
const pet = ref<any>(null);
const isLiked = ref(false);
const isCollected = ref(false);
const isFollowing = ref(false);
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

// 是否是管理员
const isAdmin = computed(() => {
  const userInfo = localStorage.getItem('userInfo');
  if (!userInfo) return false;
  try {
    const user = JSON.parse(userInfo);
    return user.role === 1; // 1 表示管理员
  } catch (e) {
    return false;
  }
});

// 是否需要审核
const needAudit = computed(() => {
  return isAdmin.value && pet.value && pet.value.auditStatus === 0;
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

// 处理审核通过
const handleApprove = async () => {
  if (!pet.value) return;
  
  try {
    const response = await approveAudit(pet.value.type === 2 ? 'activity' : 'adopt', pet.value.id);
    if (response.code === 200) {
      ElMessage.success('审核通过');
      // 重新获取宠物详情
      await fetchPetDetail();
    } else {
      ElMessage.error(response.message || '审核通过失败');
    }
  } catch (error) {
    ElMessage.error('审核通过失败');
    console.error('审核通过失败:', error);
  }
};

// 处理审核拒绝
const handleReject = async () => {
  if (!pet.value) return;
  
  // 弹出输入框，让审核人员输入拒绝原因
  const { value: reason } = await ElMessageBox.prompt('请输入拒绝原因', '审核拒绝', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '请输入拒绝原因',
    inputValidator: (value) => {
      if (!value || value.trim() === '') {
        return '拒绝原因不能为空';
      }
      return true;
    }
  });
  
  if (reason) {
    try {
      const response = await rejectAudit(pet.value.type === 2 ? 'activity' : 'adopt', pet.value.id, reason);
      if (response.code === 200) {
        ElMessage.success('审核拒绝');
        // 重新获取宠物详情
        await fetchPetDetail();
      } else {
        ElMessage.error(response.message || '审核拒绝失败');
      }
    } catch (error) {
      ElMessage.error('审核拒绝失败');
      console.error('审核拒绝失败:', error);
    }
  }
};

// 处理活动报名
const handleJoinActivity = async () => {
  if (!pet.value) return;
  
  // 弹出确认对话框
  const { value: confirmed } = await ElMessageBox.confirm(
    '确定要报名参加此活动吗？',
    '活动报名',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  );
  
  if (confirmed) {
    try {
      // 尝试从本地存储获取用户信息
      const userInfo = localStorage.getItem('userInfo');
      let realName = '用户';
      let phone = '';
      
      if (userInfo) {
        try {
          const user = JSON.parse(userInfo);
          realName = user.nickname || realName;
          phone = user.phone || phone;
        } catch (e) {
          console.error('解析用户信息失败:', e);
        }
      }
      
      const response = await signupActivity({
        activityId: pet.value.id,
        realName: realName,
        phone: phone
      });
      if (response.code === 200) {
        ElMessage.success('报名成功');
        // 重新获取活动详情
        await fetchPetDetail();
      } else {
        ElMessage.error(response.message || '报名失败');
      }
    } catch (error) {
      ElMessage.error('报名失败，请重试');
      console.error('报名失败:', error);
    }
  }
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

// 关注用户
const handleFollow = async (userId: number) => {
  try {
    const response = await request({
      url: '/follow',
      method: 'post',
      params: {
        userId: userId
      }
    });
    if (response.code === 200) {
      isFollowing.value = !isFollowing.value;
      const message = isFollowing.value ? '关注成功' : '取消关注成功';
      ElMessage.success(message);
    } else {
      ElMessage.error(response.message || '操作失败');
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试');
    console.error('关注操作失败:', error);
  }
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

// 是否已完成初始滚动
const hasScrolled = ref(false);

// 滚动到指定评论 - 增强版
const scrollToComment = () => {
  const commentId = route.query.commentId as string;
  
  if (!commentId) {
    console.log('没有 commentId 参数，跳过滚动');
    return;
  }
  
  if (hasScrolled.value) {
    console.log('已经执行过滚动，跳过');
    return;
  }
  
  console.log('准备滚动到评论:', commentId);
  
  // 滚动到目标元素的函数
  const doScroll = (element: HTMLElement) => {
    console.log('找到目标元素，执行滚动');
    // 使用 setTimeout 确保在 DOM 完全稳定后滚动
    setTimeout(() => {
      element.scrollIntoView({ 
        behavior: 'smooth', 
        block: 'center' 
      });
      
      // 高亮效果
      element.style.backgroundColor = '#f0f9ff';
      element.style.transition = 'background-color 0.5s';
      setTimeout(() => {
        element.style.backgroundColor = '';
      }, 2000);
      
      hasScrolled.value = true;
    }, 100);
  };
  
  // 查找元素的函数
  const findAndScroll = () => {
    // 尝试多种选择器
    const selectors = [
      `#comment-${commentId}`,
      `[id="comment-${commentId}"]`,
      `.comment-item[id="comment-${commentId}"]`,
      `.reply-item[id="comment-${commentId}"]`,
      `#reply-${commentId}`,
      `[id="reply-${commentId}"]`,
      `.comment-item[data-id="${commentId}"]`,
      `.reply-item[data-id="${commentId}"]`
    ];
    
    for (const selector of selectors) {
      const element = document.querySelector(selector) as HTMLElement;
      if (element) {
        console.log('通过选择器找到元素:', selector);
        doScroll(element);
        return true;
      }
    }
    
    // 尝试查找所有包含评论 ID 的元素
    console.log('尝试查找所有包含评论 ID 的元素');
    const allElements = document.querySelectorAll('*');
    for (const element of allElements) {
      if (element.id && element.id.includes(commentId)) {
        console.log('找到包含评论 ID 的元素:', element.id);
        doScroll(element);
        return true;
      }
      if (element.dataset && element.dataset.id === commentId) {
        console.log('找到包含评论 ID 的元素 (data-id):', element.dataset.id);
        doScroll(element);
        return true;
      }
    }
    
    return false;
  };
  
  // 立即尝试查找
  if (findAndScroll()) {
    return;
  }
  
  // 如果没找到，设置定时器重试
  console.log('未立即找到元素，开始轮询查找');
  let attempts = 0;
  const maxAttempts = 30; // 最多尝试 30 次
  const interval = 200; // 每 200ms 尝试一次
  
  const timer = setInterval(() => {
    attempts++;
    console.log(`第 ${attempts} 次尝试查找评论元素`);
    
    if (findAndScroll()) {
      clearInterval(timer);
      return;
    }
    
    if (attempts >= maxAttempts) {
      clearInterval(timer);
      console.log('达到最大尝试次数，未找到评论元素');
      
      // 打印所有可能的评论 ID，帮助调试
      const allCommentItems = document.querySelectorAll('.comment-item, .reply-item');
      console.log('页面中所有评论元素的 ID:');
      allCommentItems.forEach(el => {
        console.log('  -', el.id, 'data-id:', el.dataset?.id);
      });
      
      // 打印所有元素的 ID，看看是否有包含评论 ID 的元素
      console.log('页面中所有元素的 ID (包含 comment 或 reply):');
      const allElements = document.querySelectorAll('[id*="comment"], [id*="reply"]');
      allElements.forEach(el => {
        console.log('  -', el.id);
      });
    }
  }, interval);
};

const fetchPetDetail = async () => {
  const id = route.params.id;
  if (!id) {
    ElMessage.error('ID不存在');
    return;
  }

  loading.value = true;
  try {
    // 先尝试获取宠物详情
    try {
      const petResponse = await getPetDetail(Number(id));
      if (petResponse.code === 200 && petResponse.data) {
        pet.value = petResponse.data;
        // 初始化点赞和收藏状态，使用后端返回的字段
        isLiked.value = pet.value.isLiked || false;
        isCollected.value = pet.value.isFavorite || false;
        // 初始化关注状态
        isFollowing.value = pet.value.user.isFollow || false;
        // 获取评论列表
        await fetchComments();
        // 滚动到指定评论
        scrollToComment();
        return;
      }
    } catch (petError) {
      // 宠物详情获取失败，尝试获取活动详情
      console.log('宠物详情获取失败，尝试获取活动详情:', petError);
    }
    
    // 尝试获取活动详情
    try {
      const activityResponse = await getActivityDetail(Number(id));
      if (activityResponse.code === 200 && activityResponse.data) {
        pet.value = activityResponse.data;
        // 初始化点赞和收藏状态，使用后端返回的字段
        isLiked.value = pet.value.isLiked || false;
        isCollected.value = pet.value.isFavorite || false;
        // 初始化关注状态
        isFollowing.value = pet.value.user.isFollow || false;
        // 获取评论列表
        await fetchComments();
        // 滚动到指定评论
        scrollToComment();
        return;
      }
    } catch (activityError) {
      // 活动详情获取失败
      console.log('活动详情获取失败:', activityError);
    }
    
    // 两种详情都获取失败
    ElMessage.error('获取详情失败');
  } catch (error) {
    ElMessage.error('获取详情失败，请重试');
    console.error('获取详情失败:', error);
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
      console.log('评论数据示例:', comments.value[0]);
      console.log('评论 ID 列表:', comments.value.map(c => ({ id: c.id, replies: c.replies?.map(r => r.id) })));
      
      // 自动展开包含目标评论的回复列表
      const targetCommentId = route.query.commentId as string;
      if (targetCommentId) {
        console.log('尝试展开包含评论', targetCommentId, '的回复列表');
        const expandReplies = (comments: any[]) => {
          for (const comment of comments) {
            // 检查当前评论是否是目标评论
            if (comment.id === Number(targetCommentId)) {
              console.log('找到目标评论，展开其父评论的回复');
              return true;
            }
            // 检查当前评论的回复中是否有目标评论
            if (comment.replies && comment.replies.length > 0) {
              const found = comment.replies.some((reply: any) => reply.id === Number(targetCommentId));
              if (found) {
                console.log('在评论', comment.id, '的回复中找到目标评论，展开回复列表');
                comment.showAllReplies = true;
                return true;
              }
              // 递归检查嵌套回复
              const foundInNested = expandReplies(comment.replies);
              if (foundInNested) {
                comment.showAllReplies = true;
                return true;
              }
            }
          }
          return false;
        };
        expandReplies(comments.value);
      }
      
      // 等待 DOM 更新后滚动
      await import('vue').then(({ nextTick }) => nextTick());
      scrollToComment();
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
  console.log('=== 详情页 mounted ===');
  console.log('路由参数:', route.params);
  console.log('路由查询:', route.query);
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
  background-color: white;
  padding: 24px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  position: relative;
}

/* 标题和标签部分 */
.title-and-tags {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

/* 发布者信息卡片 */
.publisher-card {
  display: flex;
  align-items: center;
  padding-bottom: 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
  width: 100%;
}

.publisher-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  margin-right: 16px;
  object-fit: cover;
}

.publisher-info {
  flex: 1;
}

.nickname {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.username {
  margin: 0;
  font-size: 12px;
  color: #909399;
}

/* 标题部分 */
.title-section {
  flex: 1;
  min-width: 200px;
  margin-bottom: 16px;
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
  top: 120px;
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

.type-tag.activity {
  background-color: #409eff;
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
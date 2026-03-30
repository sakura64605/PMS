<template>
  <div class="activity-detail-container">
    <!-- 返回按钮 -->
    <div class="back-button">
      <el-button type="info" @click="navigateBack">返回</el-button>
    </div>

    <!-- 加载中状态 -->
    <el-loading v-loading="loading" element-loading-text="加载中..." />

    <!-- 活动详情 -->
    <div v-if="activity" class="activity-content">
      <!-- 发布者信息卡片 -->
      <div class="publisher-card">
        <img :src="activity.user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="发布者头像" class="publisher-avatar" />
        <div class="publisher-info">
          <h3 class="nickname">{{ activity.user.nickname }}</h3>
          <p class="username">@{{ activity.user.username }}</p>
        </div>
        <el-button type="primary" size="small" @click="navigateToUser(activity.user.userId)">查看主页</el-button>
      </div>

      <!-- 标题和状态 -->
      <div class="activity-header">
        <h1 class="activity-title">{{ activity.title }}</h1>
        <el-tag :type="getStatusType(activity.status)" class="status-tag">
          {{ getStatusText(activity.status) }}
        </el-tag>
      </div>

      <!-- 详细描述 -->
      <div class="activity-description">
        <h3>活动详情</h3>
        <div class="description-content">{{ activity.content }}</div>
      </div>

      <!-- 图片展示 -->
      <div v-if="activity.images && activity.images.length > 0">
        <!-- 单张图片 -->
        <div v-if="activity.images.length === 1" class="single-image">
          <img :src="activity.images[0]" alt="活动图片" class="single-image-item" />
        </div>
        <!-- 多张图片 - 九宫格 -->
        <div v-else class="image-grid">
          <div 
            v-for="(image, index) in activity.images.slice(0, 9)" 
            :key="index" 
            class="grid-image-item"
            :class="{
              'grid-image-2': activity.images.length === 2,
              'grid-image-3': activity.images.length === 3,
              'grid-image-4': activity.images.length === 4,
              'grid-image-5': activity.images.length === 5,
              'grid-image-6': activity.images.length === 6,
              'grid-image-7': activity.images.length === 7,
              'grid-image-8': activity.images.length === 8,
              'grid-image-9': activity.images.length === 9
            }"
          >
            <img :src="image" alt="活动图片" class="grid-image" />
          </div>
          <!-- 超过9张时显示剩余数量 -->
          <div v-if="activity.images.length > 9" class="grid-image-more">
            <span class="more-text">+{{ activity.images.length - 9 }}</span>
          </div>
        </div>
      </div>


      <!-- 活动信息卡片 -->
      <div class="activity-info-card">
        <div class="info-item">
          <el-icon class="info-icon"><MapLocation /></el-icon>
          <span class="info-label">活动地点：</span>
          <span class="info-value">{{ activity.location }}</span>
        </div>
        <div class="info-item">
          <el-icon class="info-icon"><Timer /></el-icon>
          <span class="info-label">活动时间：</span>
          <span class="info-value">{{ formatDateTime(activity.startTime) }} - {{ formatDateTime(activity.endTime) }}</span>
        </div>
        <div class="info-item">
          <el-icon class="info-icon"><UserFilled /></el-icon>
          <span class="info-label">报名进度：</span>
          <span class="info-value">{{ activity.currentPeople }}/{{ activity.maxPeople }}</span>
        </div>
        <div class="progress-container">
          <el-progress
            :percentage="(activity.currentPeople / activity.maxPeople) * 100"
            :stroke-width="10"
          />
        </div>
        <div class="interaction-info">
          <span class="interaction-item">
            <el-icon><View /></el-icon>
            {{ activity.viewCount }} 浏览
          </span>
          <span class="interaction-item">
            <el-icon><Star /></el-icon>
            {{ activity.likeCount }} 点赞
          </span>
          <span class="interaction-item">
            <el-icon><ChatLineRound /></el-icon>
            {{ activity.commentCount }} 评论
          </span>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <!-- 自己是发布者 -->
        <template v-if="isPublisher">
          <el-button type="primary" @click="navigateToEdit">编辑活动</el-button>
          <el-button type="danger" @click="showCancelDialog = true">取消活动</el-button>
        </template>
        <!-- 他人发布 -->
        <template v-else>
          <el-button
            v-if="activity.status === 0 && activity.isSignUp === 0"
            type="primary"
            @click="showSignupDialog = true"
            style="width: 100%; height: 48px; font-size: 16px;"
          >
            立即报名
          </el-button>
          <el-button
            v-else-if="activity.status === 0 && activity.isSignUp === 1"
            type="info"
            @click="showCancelSignupDialog = true"
          >
            取消报名
          </el-button>
          <el-button
            v-else
            type="default"
            disabled
          >
            {{ getStatusText(activity.status) }}
          </el-button>
        </template>
      </div>

      <!-- 评论区 -->
      <div class="comment-section">
        <h3>评论 ({{ activity.commentCount }})</h3>
        <!-- 评论输入框 -->
        <div class="comment-input">
          <el-input
            v-model="commentContent"
            type="textarea"
            placeholder="写下你的评论..."
            :rows="3"
          />
          <el-button type="primary" @click="submitComment" style="margin-top: 10px">发布评论</el-button>
        </div>
        <!-- 评论列表 -->
        <div class="comment-list">
          <div v-if="comments.length === 0" class="no-comments">
            <el-empty description="暂无评论" />
          </div>
          <div v-else class="comment-item" v-for="comment in comments" :key="comment.id">
            <img :src="comment.user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="用户头像" class="comment-avatar" />
            <div class="comment-content">
              <div class="comment-header">
                <div class="comment-author-info">
                  <span class="comment-nickname">{{ comment.user.nickname }}</span>
                  <span v-if="comment.replyTo" class="comment-reply-to"> &gt; {{ comment.replyTo.nickname }}</span>
                </div>
                <span class="comment-time">{{ formatDateTime(comment.createTime) }}</span>
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
                      <span class="reply-time">{{ formatDateTime(reply.createTime) }}</span>
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

    <!-- 报名弹窗 -->
    <el-dialog
      v-model="showSignupDialog"
      title="报名活动"
      width="500px"
    >
      <el-form :model="signupForm" :rules="signupRules" ref="signupFormRef">
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="signupForm.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="signupForm.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="signupForm.remark" type="textarea" placeholder="请输入备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showSignupDialog = false">取消</el-button>
          <el-button type="primary" @click="submitSignup">确认报名</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 取消报名弹窗 -->
    <el-dialog
      v-model="showCancelSignupDialog"
      title="取消报名"
      width="400px"
    >
      <p>确定要取消报名该活动吗？</p>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showCancelSignupDialog = false">取消</el-button>
          <el-button type="danger" @click="submitCancelSignup">确认取消</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 取消活动弹窗 -->
    <el-dialog
      v-model="showCancelDialog"
      title="取消活动"
      width="400px"
    >
      <p>确定要取消该活动吗？取消后将无法恢复。</p>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showCancelDialog = false">取消</el-button>
          <el-button type="danger" @click="submitCancelActivity">确认取消</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getActivityDetail, signupActivity, cancelSignup, getCommentList, createComment } from '../../api/activity'
import { MapLocation, Timer, UserFilled, View, Star, ChatLineRound } from '@element-plus/icons-vue'
import request from '../../utils/request'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const activity = ref<any>(null)
const comments = ref<any[]>([])
const commentContent = ref('')

// 弹窗状态
const showSignupDialog = ref(false)
const showCancelSignupDialog = ref(false)
const showCancelDialog = ref(false)

// 报名表单
const signupFormRef = ref()
const signupForm = reactive({
  realName: '',
  phone: '',
  remark: ''
})

// 报名表单验证规则
const signupRules = {
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: 'blur' }
  ]
}

// 判断是否为发布者（假设当前用户ID存储在localStorage中）
const isPublisher = computed(() => {
  if (!activity.value) return false
  const currentUserId = Number(localStorage.getItem('userId'))
  return activity.value.user.userId === currentUserId
})

// 格式化日期时间
const formatDateTime = (dateString: string) => {
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 获取状态类型
const getStatusType = (status: number) => {
  switch (status) {
    case 0: return 'success' // 报名中-绿
    case 1: return 'primary' // 进行中-蓝
    case 2: return 'info' // 已结束-灰
    case 3: return 'danger' // 已取消-红
    default: return 'default'
  }
}

// 获取状态文本
const getStatusText = (status: number) => {
  switch (status) {
    case 0: return '报名中'
    case 1: return '进行中'
    case 2: return '已结束'
    case 3: return '已取消'
    default: return '未知状态'
  }
}

// 返回上一页或指定页面
const navigateBack = () => {
  const from = route.query.from as string
  if (from === 'my-activities' || from === 'edit') {
    router.push({ path: '/pets/my-posts', query: { tab: 'activities' } })
  } else if (from === 'my-posts-activities') {
    router.push({ path: '/pets/my-posts', query: { from: 'my-posts-activities' } })
  } else if (from === 'activity-index') {
    router.push('/activities')
  } else {
    router.back()
  }
}

// 导航到用户主页
const navigateToUser = (userId: number) => {
  router.push(`/user/${userId}`)
}

// 导航到编辑页面
const navigateToEdit = () => {
  if (activity.value) {
    router.push(`/activities/${activity.value.id}/edit`)
  }
}

// 回复评论
const replyToId = ref(0)
const replyToName = ref('')

const handleReply = (id: number, name: string) => {
  replyToId.value = id
  replyToName.value = name
  commentContent.value = `@${name} `
  // 聚焦评论输入框
  setTimeout(() => {
    const textarea = document.querySelector('.comment-input textarea') as HTMLTextAreaElement
    textarea?.focus()
  }, 100)
}

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
    })
    if (response.code === 200 && response.data) {
      const message = response.data.isLiked ? '点赞成功' : '取消点赞成功'
      ElMessage.success(message)
      // 刷新评论列表
      await fetchComments()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试')
    console.error('点赞评论失败:', error)
  }
}

// 提交回复
const handleSubmitReply = async (id: number, nickname: string, content: string) => {
  if (!content.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  
  const activityId = activity.value.id
  if (!activityId) return
  
  try {
    // 查找评论对象，获取replyToId
    let replyToId = null
    const findReplyToId = (comments: any[]) => {
      for (const comment of comments) {
        if (comment.id === id) {
          replyToId = comment.replyToId
          return true
        }
        if (comment.replies && comment.replies.length > 0) {
          if (findReplyToId(comment.replies)) {
            return true
          }
        }
      }
      return false
    }
    findReplyToId(comments.value)
    
      const response = await createComment({
        targetType: 'activity',
        targetId: activityId,
        content: commentContent.value.trim(),
        parentId: 0 // 顶级评论的parentId为0
      })
    if (response.code === 200) {
      ElMessage.success('回复发布成功')
      // 刷新评论列表
      await fetchComments()
    } else {
      ElMessage.error(response.message || '回复发布失败')
    }
  } catch (error) {
    ElMessage.error('回复发布失败，请重试')
    console.error('回复发布失败:', error)
  }
}

// 提交评论
const submitComment = async () => {
  if (!commentContent.value.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  
  const id = Number(route.params.id)
  if (!id) {
    ElMessage.error('活动ID不存在')
    return
  }
  
  try {
    const response = await createComment({
      targetType: 'activity',
      targetId: id,
      content: commentContent.value.trim(),
      parentId: 0 // 顶级评论的parentId为0
    })
    if (response.code === 200) {
      ElMessage.success('评论发布成功')
      commentContent.value = ''
      replyToId.value = 0
      replyToName.value = ''
      // 刷新评论列表
      await fetchComments()
      // 刷新活动详情，更新评论数
      await fetchActivityDetail()
    } else {
      ElMessage.error(response.message || '评论发布失败')
    }
  } catch (error) {
    ElMessage.error('评论发布失败，请重试')
    console.error('评论发布失败:', error)
  }
}

// 提交报名
const submitSignup = async () => {
  if (!signupFormRef.value) return
  
  // 使用 Promise 包装 validate 方法
  const valid = await new Promise<boolean>((resolve) => {
    signupFormRef.value.validate((valid: boolean) => {
      resolve(valid)
    })
  })
  
  if (valid && activity.value) {
    try {
      await signupActivity({
        activityId: activity.value.id,
        realName: signupForm.realName,
        phone: signupForm.phone,
        remark: signupForm.remark
      })
      ElMessage.success('报名成功')
      showSignupDialog.value = false
      // 刷新页面数据
      fetchActivityDetail()
    } catch (error) {
      ElMessage.error('报名失败')
      console.error('报名失败:', error)
    }
  }
}

// 提交取消报名
const submitCancelSignup = async () => {
  if (activity.value) {
    try {
      await cancelSignup(activity.value.id)
      ElMessage.success('取消报名成功')
      showCancelSignupDialog.value = false
      // 刷新页面数据
      fetchActivityDetail()
    } catch (error) {
      ElMessage.error('取消报名失败')
      console.error('取消报名失败:', error)
    }
  }
}

// 提交取消活动
const submitCancelActivity = async () => {
  // 这里应该调用取消活动接口，暂时模拟
  ElMessage.success('活动已取消')
  showCancelDialog.value = false
  // 刷新页面数据
  fetchActivityDetail()
}

// 获取活动详情
const fetchActivityDetail = async () => {
  const id = Number(route.params.id)
  if (!id) {
    ElMessage.error('活动ID不存在')
    router.push('/activities')
    return
  }
  
  loading.value = true
  try {
    const response = await getActivityDetail(id)
    // 确保活动对象有 isSignUp 字段，默认值为 0
    activity.value = {
      ...response.data,
      isSignUp: response.data.isSignUp || 0
    }
    // 获取评论列表
    await fetchComments()
  } catch (error) {
    ElMessage.error('获取活动详情失败')
    console.error('获取活动详情失败:', error)
  } finally {
    loading.value = false
  }
}

// 获取评论列表
const fetchComments = async () => {
  const id = Number(route.params.id)
  if (!id) return
  
  try {
    const response = await getCommentList({
      targetType: 'activity',
      targetId: id,
      pageNum: 1,
      pageSize: 100
    })
    console.log('评论列表响应:', response)
    if (response.code === 200) {
      console.log('评论数据:', response.data)
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
          }))
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
          }))
        } else {
          comments.value = []
        }
      console.log('最终评论列表:', comments.value)
    } else {
      ElMessage.error(response.message || '获取评论列表失败')
      comments.value = []
    }
  } catch (error) {
    ElMessage.error('获取评论列表失败，请重试')
    console.error('获取评论列表失败:', error)
    comments.value = []
  }
}

// 页面挂载时获取数据
onMounted(() => {
  fetchActivityDetail()
})
</script>

<style scoped>
.activity-detail-container {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}

.back-button {
  margin-bottom: 20px;
}

.activity-content {
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  padding: 30px;
}

.publisher-card {
  display: flex;
  align-items: center;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 30px;
}

.publisher-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  margin-right: 20px;
  object-fit: cover;
}

.publisher-info {
  flex: 1;
}

.nickname {
  margin: 0 0 5px 0;
  font-size: 18px;
  font-weight: 600;
}

.username {
  margin: 0;
  font-size: 14px;
  color: #606266;
}

.activity-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.activity-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
  flex: 1;
}

.status-tag {
  margin-left: 20px;
}

.single-image {
  margin-bottom: 30px;
  max-width: 100px;
  overflow: hidden;
  border-radius: 8px;
}

.single-image-item {
  width: 100%;
  height: auto;
  object-fit: cover;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 4px;
  margin-bottom: 30px;
}

.grid-image-item {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 4px;
}

.grid-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 不同数量图片的布局 */
.grid-image-2 {
  grid-column: span 1;
}

.grid-image-3 {
  grid-column: span 1;
}

.grid-image-4 {
  grid-column: span 1;
}

.grid-image-5 {
  grid-column: span 1;
}

.grid-image-6 {
  grid-column: span 1;
}

.grid-image-7 {
  grid-column: span 1;
}

.grid-image-8 {
  grid-column: span 1;
}

.grid-image-9 {
  grid-column: span 1;
}

.grid-image-more {
  grid-column: span 1;
  aspect-ratio: 1;
  background-color: rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #606266;
}

.default-image {
  max-width: 100px;
  height: auto;
  margin-bottom: 30px;
  overflow: hidden;
  border-radius: 8px;
}

.default-image img {
  width: 100%;
  height: auto;
  object-fit: cover;
}

.activity-info-card {
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 30px;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
}

.info-icon {
  margin-right: 10px;
  color: #409eff;
}

.info-label {
  font-weight: 500;
  margin-right: 10px;
}

.progress-container {
  margin: 20px 0;
}

.interaction-info {
  display: flex;
  gap: 30px;
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #e4e7ed;
}

.interaction-item {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #606266;
}

.activity-description {
  margin-bottom: 30px;
}

.activity-description h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 15px;
}

.description-content {
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
}

.action-buttons {
  display: flex;
  gap: 10px;
  margin-bottom: 30px;
}

.comment-section {
  border-top: 1px solid #e4e7ed;
  padding-top: 30px;
}

.comment-section h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 20px;
}

.comment-input {
  margin-bottom: 30px;
  background-color: #f9f9f9;
  padding: 20px;
  border-radius: 8px;
}

.comment-list {
  margin-top: 20px;
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
  padding: 60px 0;
  background-color: #f9f9f9;
  border-radius: 8px;
  margin-top: 20px;
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
  .activity-content {
    padding: 20px;
  }
  
  .publisher-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .activity-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .status-tag {
    margin-left: 0;
  }
  
  .image-carousel {
    height: 200px;
  }
  
  .default-image {
    height: 200px;
  }
  
  .action-buttons {
    flex-direction: column;
  }
  
  .action-buttons > * {
    width: 100%;
  }
}
</style>
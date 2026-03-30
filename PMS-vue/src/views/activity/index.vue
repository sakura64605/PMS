<template>
  <div class="activity-list-container">
    <!-- 顶部操作栏 -->
    <div class="top-bar">
      <h2>活动管理</h2>
      <div class="top-buttons">
        <el-button type="info" @click="navigateToRecycle">回收站</el-button>
        <el-button type="primary" @click="navigateToCreate">发布活动</el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-tabs v-model="activeStatus" @tab-click="handleStatusChange">
        <el-tab-pane label="报名中" name="0"></el-tab-pane>
        <el-tab-pane label="进行中" name="1"></el-tab-pane>
        <el-tab-pane label="已结束" name="2"></el-tab-pane>
      </el-tabs>
      
      <div class="filter-form">
        <el-input
          v-model="searchForm.keyword"
          placeholder="关键词搜索（标题+内容）"
          clearable
          style="width: 200px; margin-right: 10px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-input
          v-model="searchForm.location"
          placeholder="地点搜索"
          clearable
          style="width: 200px; margin-right: 10px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="searchForm.orderBy"
          placeholder="排序"
          style="width: 150px; margin-right: 10px"
          @change="handleSortChange"
        >
          <el-option label="最新发布" value="createTime"></el-option>
          <el-option label="最早开始" value="startTime"></el-option>
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button type="info" @click="resetForm">重置</el-button>
      </div>
    </div>

    <!-- 活动卡片网格 -->
    <div class="activity-grid" v-if="activityList.length > 0">
      <div class="activity-card" v-for="activity in activityList" :key="activity.id" @click="navigateToDetail(activity.id)" style="cursor: pointer;">
        <!-- 活动封面图 -->
        <div v-if="activity.images" class="card-image">
          <img :src="activity.images" alt="活动封面" />
        </div>
        
        <!-- 卡片内容 -->
        <div class="card-content">
          <!-- 活动标题和状态标签 -->
          <div class="title-with-status">
            <h3 class="activity-title">📌 {{ activity.title }}</h3>
            <el-tag :type="getStatusType(activity.status)" class="title-status-tag">
              {{ getStatusText(activity.status) }}
            </el-tag>
          </div>
          
          <!-- 地点和时间 -->
          <div class="location-time">
            <span class="location">📍 {{ activity.location }}</span>
            <span class="time">⏰ {{ formatDateTime(activity.startTime) }} - {{ formatDateTime(activity.endTime) }}</span>
          </div>
          
          <!-- 报名进度 -->
          <div class="progress-section">
            <div class="progress-label">👥 报名进度：{{ activity.currentPeople }} / {{ activity.maxPeople }} 人</div>
            <el-progress
              :percentage="(activity.currentPeople / activity.maxPeople) * 100"
              :stroke-width="8"
            />
          </div>
          
          <!-- 发布者信息 -->
          <div class="publisher-section">
            <img :src="activity.user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="发布者头像" class="publisher-avatar" />
            <span class="publisher-info">{{ activity.user.nickname }} 发布于 {{ formatDate(activity.createTime) }}</span>
          </div>
          
          <!-- 互动数据 -->
          <div class="interaction-section">
            <span class="interaction-item">👁️ {{ activity.viewCount }}</span>
            <span class="interaction-item">·</span>
            <span class="interaction-item">⭐ {{ activity.likeCount }}</span>
            <span class="interaction-item">·</span>
            <span class="interaction-item">💬 {{ activity.commentCount }}</span>
          </div>
        </div>
        
        <!-- 底部操作栏 -->
        <div class="bottom-action-bar">
          <el-button
            v-if="activity.status === 0 && activity.isSignUp === 0"
            type="primary"
            class="signup-button"
            @click.stop="showSignupDialog = true; selectedActivityId = activity.id"
          >
            立即报名
          </el-button>
          <el-button
            v-else-if="activity.status === 0 && activity.isSignUp === 1"
            type="info"
            class="signup-button"
            @click.stop="showCancelSignupDialog = true; selectedActivityId = activity.id"
          >
            取消报名
          </el-button>
          <el-button
            v-else
            type="default"
            class="signup-button"
            disabled
          >
            {{ getStatusText(activity.status) }}
          </el-button>
        </div>
      </div>
    </div>
    
    <!-- 空状态 -->
    <div v-else class="empty-state">
      <el-empty description="暂无活动" />
    </div>

    <!-- 分页组件 -->
    <div class="pagination-container" v-if="total > 0">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getActivityList, signupActivity, cancelSignup } from '../../api/activity'
import { View, Star, ChatLineRound } from '@element-plus/icons-vue'

const router = useRouter()

// 状态管理
const activeStatus = ref('0')
const searchForm = ref({
  keyword: '',
  location: '',
  orderBy: 'createTime',
  order: 'desc'
})
const activityList = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

// 报名弹窗状态
const showSignupDialog = ref(false)
const showCancelSignupDialog = ref(false)
const selectedActivityId = ref(0)

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

// 格式化日期
const formatDate = (dateString: string) => {
  const date = new Date(dateString)
  if (isNaN(date.getTime())) {
    return '未知时间'
  }
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
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

// 导航到发布页面
const navigateToCreate = () => {
  router.push('/activities/create')
}

// 导航到回收站页面
const navigateToRecycle = () => {
  router.push('/activities/recycle')
}

// 导航到详情页面
const navigateToDetail = (id: number) => {
  router.push({ path: `/activities/${id}`, query: { from: 'activity-index' } })
}

// 处理状态切换
const handleStatusChange = (tab) => {
  const newStatus = parseInt(tab.props.name)
  pageNum.value = 1
  fetchActivityList(newStatus)
}

// 重置表单
const resetForm = () => {
  searchForm.value = {
    keyword: '',
    location: '',
    orderBy: 'createTime',
    order: 'desc'
  }
  activeStatus.value = '0'
  pageNum.value = 1
  fetchActivityList(0)
}

// 处理分页大小变化
const handleSizeChange = (size: number) => {
  pageSize.value = size
  fetchActivityList(Number(activeStatus.value))
}

// 处理页码变化
const handleCurrentChange = (current: number) => {
  pageNum.value = current
  fetchActivityList(Number(activeStatus.value))
}

// 处理搜索
const handleSearch = () => {
  pageNum.value = 1
  fetchActivityList(Number(activeStatus.value))
}

// 处理排序变化
const handleSortChange = () => {
  pageNum.value = 1
  fetchActivityList(Number(activeStatus.value))
}

// 获取活动列表
const fetchActivityList = async (status) => {
  try {
    const response = await getActivityList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchForm.value.keyword,
      status: status,
      location: searchForm.value.location,
      orderBy: searchForm.value.orderBy,
      order: searchForm.value.order
    })
    // 确保每个活动对象都有 isSignUp 字段，默认值为 0
    activityList.value = (response.data.records || []).map(activity => ({
      ...activity,
      isSignUp: activity.isSignUp || activity.isSignedUp || 0
    }))
    total.value = response.data.total || 0
  } catch (error) {
    console.error('获取活动列表失败:', error)
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
  
  if (valid && selectedActivityId.value) {
    try {
      await signupActivity({
        activityId: selectedActivityId.value,
        realName: signupForm.realName,
        phone: signupForm.phone,
        remark: signupForm.remark
      })
      ElMessage.success('报名成功')
      showSignupDialog.value = false
      // 刷新页面数据
      fetchActivityList(Number(activeStatus.value))
    } catch (error) {
      ElMessage.error('报名失败')
      console.error('报名失败:', error)
    }
  }
}

// 提交取消报名
const submitCancelSignup = async () => {
  if (selectedActivityId.value) {
    try {
      await cancelSignup(selectedActivityId.value)
      ElMessage.success('取消报名成功')
      showCancelSignupDialog.value = false
      // 刷新页面数据
      fetchActivityList(Number(activeStatus.value))
    } catch (error) {
      ElMessage.error('取消报名失败')
      console.error('取消报名失败:', error)
    }
  }
}

// 页面挂载时获取数据
onMounted(() => {
  fetchActivityList(0)
})
</script>

<style scoped>
.activity-list-container {
  padding: 20px;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.top-bar h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.top-buttons {
  display: flex;
  gap: 10px;
}

.filter-bar {
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.filter-form {
  display: flex;
  align-items: center;
  margin-top: 15px;
}

.activity-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.activity-card {
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  overflow: hidden;
  transition: transform 0.3s ease;
  display: flex;
  flex-direction: column;
}

.activity-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.15);
}

/* 活动封面图 */
.card-image {
  position: relative;
  height: 200px;
  overflow: hidden;
  border-radius: 12px 12px 0 0;
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 有图片时的卡片内容 */
.card-image + .card-content {
  padding: 15px;
}

/* 无图片时的卡片内容 */
.card-content {
  padding: 15px;
}

/* 卡片内容 */
.card-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 标题和状态标签容器 */
.title-with-status {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

/* 标题状态标签 */
.title-status-tag {
  border-radius: 12px;
  font-size: 12px;
  padding: 4px 12px;
  white-space: nowrap;
}

/* 活动标题 */
.activity-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  flex: 1;
  min-width: 0;
}

/* 地点和时间 */
.location-time {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
  color: #606266;
}

.location {
  display: block;
}

.time {
  display: block;
  margin-top: 2px;
}

/* 报名进度 */
.progress-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.progress-label {
  font-size: 14px;
  color: #606266;
}

/* 发布者信息 */
.publisher-section {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.publisher-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.publisher-info {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 互动数据 */
.interaction-section {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #909399;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}

.interaction-item {
  display: flex;
  align-items: center;
}

/* 底部操作栏 */
.bottom-action-bar {
  padding: 15px;
  border-top: 1px solid #e4e7ed;
}

.signup-button {
  width: 100%;
  height: 40px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .card-image {
    height: 180px;
  }
  
  .activity-title {
    font-size: 16px;
  }
  
  .location-time {
    font-size: 13px;
  }
  
  .progress-label {
    font-size: 13px;
  }
  
  .signup-button {
    height: 36px;
    font-size: 13px;
  }
}

.empty-state {
  text-align: center;
  padding: 60px 0;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .activity-grid {
    grid-template-columns: 1fr;
  }
  
  .filter-form {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  
  .filter-form > * {
    width: 100% !important;
    margin-right: 0 !important;
  }
}
</style>
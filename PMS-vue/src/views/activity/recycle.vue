<template>
  <div class="activity-recycle-container">
    <!-- 顶部操作栏 -->
    <div class="top-bar">
      <div class="back-section">
        <el-button type="info" @click="navigateBack">返回活动列表</el-button>
        <h2>活动回收站</h2>
      </div>
      <el-button type="primary" @click="navigateToCreate">发布活动</el-button>
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
            type="primary"
            class="recover-button"
            @click.stop="handleRecover(activity.id)"
          >
            恢复活动
          </el-button>
          <el-button
            type="danger"
            class="delete-button"
            @click.stop="handleDeleteReally(activity.id)"
          >
            彻底删除
          </el-button>
        </div>
      </div>
    </div>
    
    <!-- 空状态 -->
    <div v-else class="empty-state">
      <el-empty description="回收站为空" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getActivityRecycleList, recoverActivity, deleteActivityReally } from '../../api/activity'

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

// 导航回活动列表页面
const navigateBack = () => {
  router.push('/activities')
}

// 导航到详情页面
const navigateToDetail = (id: number) => {
  router.push({ path: `/activities/${id}`, query: { from: 'activity-recycle' } })
}

// 处理状态切换
const handleStatusChange = (tab) => {
  const newStatus = parseInt(tab.props.name)
  pageNum.value = 1
  fetchRecycleBinList(newStatus)
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
  fetchRecycleBinList(0)
}

// 处理分页大小变化
const handleSizeChange = (size: number) => {
  pageSize.value = size
  fetchRecycleBinList(Number(activeStatus.value))
}

// 处理页码变化
const handleCurrentChange = (current: number) => {
  pageNum.value = current
  fetchRecycleBinList(Number(activeStatus.value))
}

// 处理搜索
const handleSearch = () => {
  pageNum.value = 1
  fetchRecycleBinList(Number(activeStatus.value))
}

// 处理排序变化
const handleSortChange = () => {
  pageNum.value = 1
  fetchRecycleBinList(Number(activeStatus.value))
}

// 获取回收站列表
const fetchRecycleBinList = async (status) => {
  try {
    const response = await getActivityRecycleList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchForm.value.keyword,
      status: status,
      location: searchForm.value.location,
      orderBy: searchForm.value.orderBy,
      order: searchForm.value.order
    })
    activityList.value = response.data.records || []
    total.value = response.data.total || 0
  } catch (error) {
    console.error('获取回收站列表失败:', error)
  }
}

// 恢复活动
const handleRecover = async (id: number) => {
  try {
    await recoverActivity(id)
    ElMessage.success('恢复成功')
    // 刷新页面数据
    fetchRecycleBinList(Number(activeStatus.value))
  } catch (error) {
    ElMessage.error('恢复失败')
    console.error('恢复失败:', error)
  }
}

// 彻底删除
const handleDeleteReally = async (id: number) => {
  try {
    await deleteActivityReally(id)
    ElMessage.success('彻底删除成功')
    // 刷新页面数据
    fetchRecycleBinList(Number(activeStatus.value))
  } catch (error) {
    ElMessage.error('删除失败')
    console.error('删除失败:', error)
  }
}

// 页面挂载时获取数据
onMounted(() => {
  fetchRecycleBinList(0)
})
</script>

<style scoped>
.activity-recycle-container {
  padding: 20px;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.back-section {
  display: flex;
  align-items: center;
  gap: 15px;
}

.top-bar h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
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
  display: flex;
  gap: 10px;
}

.recover-button,
.delete-button {
  flex: 1;
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
  
  .recover-button,
  .delete-button {
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
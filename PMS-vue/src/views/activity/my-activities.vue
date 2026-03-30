<template>
  <div class="my-activities-container">
    <h2>我的活动</h2>
    
    <el-tabs v-model="activeTab" @tab-click="handleTabChange">
      <el-tab-pane label="我发布的" name="published"></el-tab-pane>
      <el-tab-pane label="我参与的" name="joined"></el-tab-pane>
      <el-tab-pane label="领养/救助" name="adoption"></el-tab-pane>
      <el-tab-pane label="我的活动" name="myActivities"></el-tab-pane>
    </el-tabs>

    <!-- 我发布的活动 -->
    <div v-if="activeTab === 'published'" class="activity-section">
      <div class="activity-grid" v-if="publishedActivities.length > 0">
        <div class="activity-card" v-for="activity in publishedActivities" :key="activity.id">
          <!-- 活动封面图 -->
          <div class="card-image">
            <img v-if="activity.images" :src="activity.images" alt="活动封面" />
            <!-- 状态标签 -->
            <el-tag :type="getStatusType(activity.status)" class="status-tag">
              {{ getStatusText(activity.status) }}
            </el-tag>
          </div>
          
          <!-- 卡片内容 -->
          <div class="card-content">
            <!-- 活动标题 -->
            <h3 class="activity-title">📌 {{ activity.title }}</h3>
            
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
              <img :src="activity.user?.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="发布者头像" class="publisher-avatar" />
              <span class="publisher-info">发布于 {{ formatDate(activity.createTime) }}</span>
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
            <div class="action-buttons">
              <el-button type="primary" class="action-button" @click="navigateToEdit(activity.id)">编辑</el-button>
              <el-button type="danger" class="action-button" @click="showCancelDialog(activity.id)">取消活动</el-button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="暂无发布的活动" />
      </div>
    </div>

    <!-- 我参与的活动 -->
    <div v-else-if="activeTab === 'joined'" class="activity-section">
      <div class="activity-grid" v-if="joinedActivities.length > 0">
        <div class="activity-card" v-for="activity in joinedActivities" :key="activity.id">
          <!-- 活动封面图 -->
          <div class="card-image">
            <img v-if="activity.images" :src="activity.images" alt="活动封面" />
            <!-- 状态标签 -->
            <el-tag :type="getStatusType(activity.status)" class="status-tag">
              {{ getStatusText(activity.status) }}
            </el-tag>
          </div>
          
          <!-- 卡片内容 -->
          <div class="card-content">
            <!-- 活动标题 -->
            <h3 class="activity-title">📌 {{ activity.title }}</h3>
            
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
              v-if="activity.status === 0"
              type="danger"
              class="signup-button"
              @click="showCancelSignupDialog(activity.id)"
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
      <div v-else class="empty-state">
        <el-empty description="暂无参与的活动" />
      </div>
    </div>

    <!-- 领养/救助 -->
    <div v-else-if="activeTab === 'adoption'" class="activity-section">
      <div class="activity-grid" v-if="adoptionActivities.length > 0">
        <div class="activity-card" v-for="activity in adoptionActivities" :key="activity.id">
          <!-- 活动封面图 -->
          <div class="card-image">
            <img v-if="activity.images" :src="activity.images" alt="活动封面" />
            <!-- 状态标签 -->
            <el-tag :type="getStatusType(activity.status)" class="status-tag">
              {{ getStatusText(activity.status) }}
            </el-tag>
          </div>
          
          <!-- 卡片内容 -->
          <div class="card-content">
            <!-- 活动标题 -->
            <h3 class="activity-title">🏠 {{ activity.title }}</h3>
            
            <!-- 地点和时间 -->
            <div class="location-time">
              <span class="location">📍 {{ activity.location }}</span>
              <span class="time">⏰ {{ formatDateTime(activity.startTime) }} - {{ formatDateTime(activity.endTime) }}</span>
            </div>
            
            <!-- 报名进度 -->
            <div class="progress-section">
              <div class="progress-label">🐾 领养进度：{{ activity.currentPeople }} / {{ activity.maxPeople }} 人</div>
              <el-progress
                :percentage="(activity.currentPeople / activity.maxPeople) * 100"
                :stroke-width="8"
              />
            </div>
            
            <!-- 发布者信息 -->
            <div class="publisher-section">
              <img :src="activity.user?.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="发布者头像" class="publisher-avatar" />
              <span class="publisher-info">发布于 {{ formatDate(activity.createTime) }}</span>
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
            <div class="action-buttons">
              <el-button type="primary" class="action-button" @click="navigateToEdit(activity.id)">编辑</el-button>
              <el-button type="danger" class="action-button" @click="showCancelDialog(activity.id)">取消活动</el-button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="暂无领养/救助活动" />
      </div>
    </div>

    <!-- 我的活动 -->
    <div v-else-if="activeTab === 'myActivities'" class="activity-section">
      <div class="activity-grid" v-if="myActivitiesList.length > 0">
        <div class="activity-card" v-for="activity in myActivitiesList" :key="activity.id">
          <!-- 活动封面图 -->
          <div class="card-image">
            <img v-if="activity.images" :src="activity.images" alt="活动封面" />
            <!-- 状态标签 -->
            <el-tag :type="getStatusType(activity.status)" class="status-tag">
              {{ getStatusText(activity.status) }}
            </el-tag>
          </div>
          
          <!-- 卡片内容 -->
          <div class="card-content">
            <!-- 活动标题 -->
            <h3 class="activity-title">📌 {{ activity.title }}</h3>
            
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
              <img :src="activity.user?.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="发布者头像" class="publisher-avatar" />
              <span class="publisher-info">发布于 {{ formatDate(activity.createTime) }}</span>
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
            <div class="action-buttons">
              <el-button type="primary" class="action-button" @click="navigateToEdit(activity.id)">编辑</el-button>
              <el-button type="info" class="action-button" @click="navigateToDetail(activity.id)">查看详情</el-button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="暂无我的活动" />
      </div>
    </div>

    <!-- 取消活动弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      title="取消活动"
      width="400px"
    >
      <p>确定要取消该活动吗？取消后将无法恢复。</p>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="danger" @click="submitCancelActivity">确认取消</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 取消报名弹窗 -->
    <el-dialog
      v-model="cancelSignupVisible"
      title="取消报名"
      width="400px"
    >
      <p>确定要取消报名该活动吗？</p>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="cancelSignupVisible = false">取消</el-button>
          <el-button type="danger" @click="submitCancelSignup">确认取消</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { View, Star, ChatLineRound } from '@element-plus/icons-vue'
import { getMyActivityList } from '../../api/activity'

const router = useRouter()
const route = useRoute()

// 状态管理
const activeTab = ref(route.query.tab as string || 'published')

// 监听路由变化，更新标签页
watch(() => route.query.tab, (newTab) => {
  if (newTab) {
    activeTab.value = newTab as string
  }
})
const publishedActivities = ref<any[]>([])
const joinedActivities = ref<any[]>([])
const adoptionActivities = ref<any[]>([])
const myActivitiesList = ref<any[]>([])
const dialogVisible = ref(false)
const cancelSignupVisible = ref(false)
const currentActivityId = ref(0)
const loading = ref(false)
const activitiesTotal = ref(0)
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

// 导航到编辑页面
const navigateToEdit = (id: number) => {
  router.push({ path: `/activities/${id}/edit`, query: { from: activeTab } })
}

// 导航到详情页面
const navigateToDetail = (id: number) => {
  router.push({ path: `/activities/${id}`, query: { from: 'my-activities' } })
}

// 处理标签页切换
const handleTabChange = (tab: any) => {
  pageNum.value = 1;  // 重置页码
  const currentTab = tab.props.name;
  if (currentTab === 'published') {
    fetchPublishedActivities()
  } else if (currentTab === 'joined') {
    fetchJoinedActivities()
  } else if (currentTab === 'adoption') {
    fetchAdoptionActivities()
  } else if (currentTab === 'myActivities') {
    fetchMyActivities()
  }
}

// 显示取消活动弹窗
const showCancelDialog = (id: number) => {
  currentActivityId.value = id
  dialogVisible.value = true
}

// 显示取消报名弹窗
const showCancelSignupDialog = (id: number) => {
  currentActivityId.value = id
  cancelSignupVisible.value = true
}

// 提交取消活动
const submitCancelActivity = () => {
  // 这里应该调用取消活动接口，暂时模拟
  ElMessage.success('活动已取消')
  dialogVisible.value = false
  // 刷新数据
  fetchPublishedActivities()
}

// 提交取消报名
const submitCancelSignup = () => {
  // 这里应该调用取消报名接口，暂时模拟
  ElMessage.success('已取消报名')
  cancelSignupVisible.value = false
  // 刷新数据
  fetchJoinedActivities()
}

// 处理分页大小变化
const handleSizeChange = (size: number) => {
  pageSize.value = size;
  pageNum.value = 1;  // 重置页码
  if (activeTab.value === 'published') {
    fetchPublishedActivities();
  } else if (activeTab.value === 'joined') {
    fetchJoinedActivities();
  } else if (activeTab.value === 'adoption') {
    fetchAdoptionActivities();
  } else if (activeTab.value === 'myActivities') {
    fetchMyActivities();
  }
};

// 处理页码变化
const handleCurrentChange = (current: number) => {
  pageNum.value = current;
  if (activeTab.value === 'published') {
    fetchPublishedActivities();
  } else if (activeTab.value === 'joined') {
    fetchJoinedActivities();
  } else if (activeTab.value === 'adoption') {
    fetchAdoptionActivities();
  } else if (activeTab.value === 'myActivities') {
    fetchMyActivities();
  }
};

// 获取我发布的活动
const fetchPublishedActivities = async () => {
  // 这里应该调用API接口，暂时模拟数据
  publishedActivities.value = [
    {
      id: 1,
      title: '宠物爱好者聚会',
      images: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=pet%20lovers%20gathering&image_size=square',
      location: '宠物公园',
      maxPeople: 50,
      currentPeople: 25,
      status: 0,
      startTime: '2026-04-01 14:00:00',
      endTime: '2026-04-01 16:00:00',
      viewCount: 120,
      likeCount: 30,
      commentCount: 15
    },
    {
      id: 2,
      title: '宠物训练课程',
      images: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=pet%20training%20class&image_size=square',
      location: '宠物训练中心',
      maxPeople: 20,
      currentPeople: 15,
      status: 1,
      startTime: '2026-03-25 10:00:00',
      endTime: '2026-03-25 12:00:00',
      viewCount: 80,
      likeCount: 20,
      commentCount: 10
    }
  ]
}

// 获取我参与的活动
const fetchJoinedActivities = async () => {
  console.log('===== 获取我参与的活动开始请求 =====');
  
  loading.value = true;
  try {
    const response = await getMyActivityList({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    console.log('===== 响应 =====', response);
    if (response.code === 200 && response.data) {
      joinedActivities.value = response.data.records || [];
      activitiesTotal.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || '获取我参与的活动失败');
    }
  } catch (error) {
    ElMessage.error('获取我参与的活动失败，请重试');
    console.error('获取我参与的活动失败:', error);
  } finally {
    loading.value = false;
  }
}

// 获取领养/救助活动
const fetchAdoptionActivities = async () => {
  // 这里应该调用API接口，暂时模拟数据
  adoptionActivities.value = [
    {
      id: 5,
      title: '流浪猫领养活动',
      images: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cat%20adoption%20event&image_size=square',
      location: '宠物收容所',
      maxPeople: 20,
      currentPeople: 15,
      status: 0,
      startTime: '2026-04-15 10:00:00',
      endTime: '2026-04-15 16:00:00',
      viewCount: 180,
      likeCount: 50,
      commentCount: 25,
      createTime: '2026-03-20 10:00:00'
    },
    {
      id: 6,
      title: '流浪狗救助活动',
      images: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=dog%20rescue%20event&image_size=square',
      location: '城市公园',
      maxPeople: 30,
      currentPeople: 25,
      status: 1,
      startTime: '2026-03-26 14:00:00',
      endTime: '2026-03-26 18:00:00',
      viewCount: 220,
      likeCount: 65,
      commentCount: 35,
      createTime: '2026-03-15 14:00:00'
    }
  ]
}

// 获取我的活动
const fetchMyActivities = async () => {
  // 这里应该调用API接口，暂时模拟数据
  myActivitiesList.value = [
    {
      id: 1,
      title: '宠物爱好者聚会',
      images: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=pet%20lovers%20gathering&image_size=square',
      location: '宠物公园',
      maxPeople: 50,
      currentPeople: 25,
      status: 0,
      startTime: '2026-04-01 14:00:00',
      endTime: '2026-04-01 16:00:00',
      viewCount: 120,
      likeCount: 30,
      commentCount: 15,
      createTime: '2026-03-10 09:00:00'
    },
    {
      id: 3,
      title: '宠物摄影活动',
      images: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=pet%20photography%20event&image_size=square',
      location: '城市广场',
      maxPeople: 30,
      currentPeople: 28,
      status: 0,
      startTime: '2026-04-08 09:00:00',
      endTime: '2026-04-08 11:00:00',
      viewCount: 150,
      likeCount: 45,
      commentCount: 20,
      createTime: '2026-03-18 11:00:00'
    }
  ]
}

// 页面挂载时获取数据
onMounted(() => {
  if (activeTab.value === 'published') {
    fetchPublishedActivities()
  } else if (activeTab.value === 'joined') {
    fetchJoinedActivities()
  } else if (activeTab.value === 'adoption') {
    fetchAdoptionActivities()
  } else if (activeTab.value === 'myActivities') {
    fetchMyActivities()
  }
})
</script>

<style scoped>
.my-activities-container {
  padding: 20px;
}

.my-activities-container h2 {
  margin: 0 0 20px 0;
  font-size: 24px;
  font-weight: 600;
}

.activity-section {
  margin-top: 20px;
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

.no-image {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f7fa;
}

.status-tag {
  position: absolute;
  top: 10px;
  right: 10px;
  border-radius: 12px;
  font-size: 12px;
  padding: 4px 12px;
}

/* 卡片内容 */
.card-content {
  padding: 15px;
  display: flex;
  flex-direction: column;
  gap: 12px;
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

/* 报名按钮 */
.signup-button {
  width: 100%;
  height: 40px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
}

/* 操作按钮组 */
.action-buttons {
  display: flex;
  gap: 10px;
  width: 100%;
}

.action-button {
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
  
  .signup-button,
  .action-button {
    height: 36px;
    font-size: 13px;
  }
  
  .action-buttons {
    flex-direction: column;
  }
}

.empty-state {
  text-align: center;
  padding: 60px 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .activity-grid {
    grid-template-columns: 1fr;
  }
  
  .action-buttons {
    flex-direction: column;
  }
  
  .action-buttons > * {
    width: 100%;
  }
}
</style>
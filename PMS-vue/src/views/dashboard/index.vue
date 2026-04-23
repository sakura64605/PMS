<template>
  <div class="dashboard">
    <el-container>
      <el-header height="60px">
        <div class="header">
          <h1>PetCircle -宠友社</h1>
          <el-button type="primary" @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main>
        <el-card>
          <template #header>
            <div class="card-header">
              <span>欢迎回来，{{ username }}</span>
            </div>
          </template>
          <div class="welcome">
            <p>这是PetCircle -宠友社的首页</p>
            <p>您的角色：{{ role === 1 ? '系统管理员' : '普通用户' }}</p>
          </div>
        </el-card>
        
        <!-- 数据统计概览 -->
        <el-card class="statistics-card">
          <template #header>
            <div class="card-header">
              <span>数据统计概览</span>
              <div class="filter-container">
                <el-date-picker
                  v-model="dateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  style="width: 300px; margin-right: 10px"
                />
                <el-select v-model="period" placeholder="统计周期" style="width: 120px; margin-right: 10px">
                  <el-option label="日" value="day" />
                  <el-option label="周" value="week" />
                  <el-option label="月" value="month" />
                  <el-option label="年" value="year" />
                </el-select>
                <el-button type="primary" @click="fetchOverviewData">查询</el-button>
              </div>
            </div>
          </template>
          
          <div v-if="loading" class="loading-container">
            <el-skeleton :rows="4" animated />
          </div>
          <div v-else class="statistics-overview">
            <div class="stat-card">
              <div class="stat-value">{{ overviewData?.newUsers || 0 }}</div>
              <div class="stat-label">新增用户</div>
            </div>
            <div class="stat-card">
              <div class="stat-value">{{ overviewData?.activeUsers || 0 }}</div>
              <div class="stat-label">活跃用户</div>
            </div>
            <div class="stat-card">
              <div class="stat-value">{{ overviewData?.newPosts || 0 }}</div>
              <div class="stat-label">新增帖子</div>
            </div>
            <div class="stat-card">
              <div class="stat-value">{{ overviewData?.newComments || 0 }}</div>
              <div class="stat-label">新增评论</div>
            </div>
          </div>
        </el-card>
        
        <!-- 实时统计 -->
        <el-card class="realtime-card">
          <template #header>
            <div class="card-header">
              <span>实时统计</span>
              <el-button type="info" @click="fetchRealtimeData">刷新</el-button>
            </div>
          </template>
          
          <div v-if="realtimeLoading" class="loading-container">
            <el-skeleton :rows="3" animated />
          </div>
          <div v-else class="realtime-data">
            <div class="realtime-item">
              <span class="realtime-label">今日用户数：</span>
              <span class="realtime-value">{{ realtimeData?.todayUsers || 0 }}</span>
            </div>
            <div class="realtime-item">
              <span class="realtime-label">今日帖子数：</span>
              <span class="realtime-value">{{ realtimeData?.todayPosts || 0 }}</span>
            </div>
            <div class="realtime-item">
              <span class="realtime-label">今日评论数：</span>
              <span class="realtime-value">{{ realtimeData?.todayComments || 0 }}</span>
            </div>
          </div>
        </el-card>
        
        <!-- 趋势图表 -->
        <el-card class="trend-card">
          <template #header>
            <div class="card-header">
              <span>数据趋势</span>
            </div>
          </template>
          
          <div v-if="trendLoading" class="loading-container">
            <el-skeleton :rows="6" animated />
          </div>
          <div v-else class="trend-chart">
            <div ref="trendChartRef" class="chart-container"></div>
          </div>
        </el-card>
        
        <el-card class="notice-card">
          <template #header>
            <div class="card-header">
              <span>系统公告</span>
              <el-badge v-if="unreadCount > 0" :value="unreadCount" type="danger" />
            </div>
          </template>
          
          <div class="notice-list">
            <div v-for="notice in noticeList" :key="notice.id" class="notice-item" @click="handleNoticeClick(notice.id)">
              <div class="notice-header">
                <h3 :class="['notice-title', { 'unread': !notice.isRead, 'top': notice.isTop === 1 }]">
                  {{ notice.title }}
                  <span v-if="notice.isTop === 1" class="top-tag">置顶</span>
                </h3>
                <span :class="['notice-type', getNoticeTypeClass(notice.type)]">
                  {{ getNoticeTypeText(notice.type) }}
                </span>
              </div>
              <div class="notice-meta">
                <span class="notice-time">{{ notice.publishTime }}</span>
                <span v-if="notice.priority > 0" :class="['priority-tag', getPriorityClass(notice.priority)]">
                  {{ getPriorityText(notice.priority) }}
                </span>
              </div>
            </div>
            <div v-if="noticeList.length === 0" class="empty-section">
              <p>暂无公告</p>
            </div>
          </div>
          
          <!-- 分页组件 -->
          <div class="pagination" v-if="total > 0">
            <el-pagination
              :current-page="pageNum"
              :page-size="pageSize"
              :total="total"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
              layout="total, sizes, prev, pager, next, jumper"
            />
          </div>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getNoticeList, getUnreadCount } from '../../api/notice'
import { getOverviewStatistics, getRealtimeStatistics } from '../../api/statistics'
import * as echarts from 'echarts'

const router = useRouter()
const username = ref('')
const role = ref(0)
const noticeList = ref<any[]>([])
const unreadCount = ref(0)

// 分页参数
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 统计数据相关
const dateRange = ref<[Date, Date] | null>(null)
const period = ref('week')
const loading = ref(false)
const realtimeLoading = ref(false)
const trendLoading = ref(false)
const overviewData = ref<any>(null)
const realtimeData = ref<any>(null)
const trendChartRef = ref<HTMLElement | null>(null)
let trendChart: echarts.ECharts | null = null

onMounted(() => {
  // 从localStorage获取用户信息
  const userInfo = localStorage.getItem('userInfo')
  if (userInfo) {
    const info = JSON.parse(userInfo)
    username.value = info.username
    role.value = info.role
  }
  
  // 设置默认日期范围为最近7天
  const endDate = new Date()
  const startDate = new Date()
  startDate.setDate(startDate.getDate() - 6)
  dateRange.value = [startDate, endDate]
  
  // 获取公告列表和未读数量
  fetchNoticeList()
  fetchUnreadCount()
  
  // 获取统计数据
  fetchOverviewData()
  fetchRealtimeData()
  
  // 监听窗口大小变化，调整图表大小
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  // 清理事件监听器
  window.removeEventListener('resize', handleResize)
  // 销毁图表实例
  if (trendChart) {
    trendChart.dispose()
  }
})

// 处理窗口大小变化
const handleResize = () => {
  if (trendChart) {
    trendChart.resize()
  }
}

// 获取统计概览数据
const fetchOverviewData = async () => {
  if (!dateRange.value) return
  
  loading.value = true
  trendLoading.value = true
  try {
    const [start, end] = dateRange.value
    const startDate = start.toISOString().split('T')[0]
    const endDate = end.toISOString().split('T')[0]
    
    const response = await getOverviewStatistics({
      startDate,
      endDate,
      period: period.value
    })
    
    if (response.code === 200) {
      overviewData.value = response.data
      // 初始化趋势图表
      initTrendChart()
    }
  } catch (error) {
    console.error('获取统计概览失败:', error)
  } finally {
    loading.value = false
    trendLoading.value = false
  }
}

// 获取实时统计数据
const fetchRealtimeData = async () => {
  realtimeLoading.value = true
  try {
    const response = await getRealtimeStatistics()
    if (response.code === 200) {
      realtimeData.value = response.data
    }
  } catch (error) {
    console.error('获取实时统计失败:', error)
  } finally {
    realtimeLoading.value = false
  }
}

// 初始化趋势图表
const initTrendChart = () => {
  if (!trendChartRef.value || !overviewData.value?.trendData) return
  
  if (trendChart) {
    trendChart.dispose()
  }
  
  trendChart = echarts.init(trendChartRef.value)
  const trendData = overviewData.value.trendData
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        label: {
          backgroundColor: '#6a7985'
        }
      }
    },
    legend: {
      data: ['DAU', '新增用户', '新增帖子', '新增评论']
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: [
      {
        type: 'category',
        boundaryGap: false,
        data: trendData.dates
      }
    ],
    yAxis: [
      {
        type: 'value'
      }
    ],
    series: [
      {
        name: 'DAU',
        type: 'line',
        stack: 'Total',
        areaStyle: {
          opacity: 0.3
        },
        data: trendData.dauList || []
      },
      {
        name: '新增用户',
        type: 'line',
        stack: 'Total',
        data: trendData.newUserList || []
      },
      {
        name: '新增帖子',
        type: 'line',
        stack: 'Total',
        data: trendData.newPostList || []
      },
      {
        name: '新增评论',
        type: 'line',
        stack: 'Total',
        data: trendData.newCommentList || []
      }
    ]
  }
  
  trendChart.setOption(option)
}

const logout = () => {
  // 清除本地存储的token和用户信息
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  // 跳转到登录页面
  router.push('/login')
}

// 获取公告列表
const fetchNoticeList = async () => {
  try {
    const response = await getNoticeList({ 
      pageNum: pageNum.value, 
      pageSize: pageSize.value 
    })
    if (response.code === 200) {
      noticeList.value = response.data.records
      total.value = response.data.total
    }
  } catch (error) {
    console.error('获取公告列表失败:', error)
  }
}

// 获取未读公告数量
const fetchUnreadCount = async () => {
  try {
    const response = await getUnreadCount()
    if (response.code === 200) {
      unreadCount.value = response.data
    }
  } catch (error) {
    console.error('获取未读公告数量失败:', error)
  }
}

// 处理公告点击
const handleNoticeClick = (id: number) => {
  // 跳转到公告详情页面
  router.push(`/notice/${id}`)
}

// 处理分页大小变化
const handleSizeChange = (size: number) => {
  pageSize.value = size
  pageNum.value = 1
  fetchNoticeList()
}

// 处理页码变化
const handleCurrentChange = (current: number) => {
  pageNum.value = current
  fetchNoticeList()
}

// 获取公告类型样式
const getNoticeTypeClass = (type: number) => {
  switch (type) {
    case 1: return 'system'
    case 2: return 'activity'
    case 3: return 'important'
    default: return ''
  }
}

// 获取公告类型文本
const getNoticeTypeText = (type: number) => {
  switch (type) {
    case 1: return '系统公告'
    case 2: return '活动通知'
    case 3: return '重要提醒'
    default: return ''
  }
}

// 获取优先级样式
const getPriorityClass = (priority: number) => {
  switch (priority) {
    case 1: return 'important'
    case 2: return 'urgent'
    default: return ''
  }
}

// 获取优先级文本
const getPriorityText = (priority: number) => {
  switch (priority) {
    case 1: return '重要'
    case 2: return '紧急'
    default: return ''
  }
}
</script>

<style scoped>
.dashboard {
  height: 100vh;
  background-color: #f5f7fa;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  padding: 0 20px;
  background-color: #409eff;
  color: white;
}

.header h1 {
  font-size: 20px;
  margin: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.welcome {
  padding: 20px 0;
}

.welcome p {
  margin: 10px 0;
  font-size: 16px;
}

.statistics-card,
.realtime-card,
.trend-card,
.notice-card {
  margin-top: 20px;
}

.filter-container {
  display: flex;
  align-items: center;
}

.loading-container {
  padding: 20px 0;
}

.statistics-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.stat-card {
  background-color: #f9f9f9;
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s, box-shadow 0.3s;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 10px;
}

.stat-label {
  font-size: 14px;
  color: #606266;
}

.realtime-data {
  display: flex;
  gap: 40px;
  margin-top: 20px;
}

.realtime-item {
  display: flex;
  align-items: center;
  font-size: 16px;
}

.realtime-label {
  color: #606266;
  margin-right: 10px;
}

.realtime-value {
  font-weight: bold;
  color: #409eff;
  font-size: 18px;
}

.chart-container {
  width: 100%;
  height: 400px;
  margin-top: 20px;
}

.notice-list {
  padding: 10px 0;
  min-height: 200px;
}

.notice-item {
  padding: 15px 0;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  transition: all 0.3s;
}

.notice-item:hover {
  background-color: #f5f7fa;
  padding-left: 10px;
}

.notice-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}

.notice-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0;
  flex: 1;
}

.notice-title.unread {
  font-weight: bold;
}

.notice-title.top {
  color: #f56c6c;
}

.top-tag {
  display: inline-block;
  margin-left: 10px;
  padding: 2px 6px;
  font-size: 12px;
  background-color: #f56c6c;
  color: white;
  border-radius: 4px;
}

.notice-type {
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 12px;
  color: white;
}

.notice-type.system {
  background-color: #409eff;
}

.notice-type.activity {
  background-color: #67c23a;
}

.notice-type.important {
  background-color: #f56c6c;
}

.notice-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #909399;
}

.priority-tag {
  padding: 2px 8px;
  border-radius: 12px;
  color: white;
  font-size: 12px;
}

.priority-tag.important {
  background-color: #e6a23c;
}

.priority-tag.urgent {
  background-color: #f56c6c;
}

.empty-section {
  padding: 40px 0;
  text-align: center;
  color: #909399;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
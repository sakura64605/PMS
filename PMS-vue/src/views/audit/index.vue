<template>
  <div class="audit-container">
    <el-card class="audit-card">
      <template #header>
        <div class="card-header">
          <h2 class="audit-title">审核管理</h2>
          <el-tabs v-model="activeTab" class="audit-tabs">
            <el-tab-pane label="内容审核" name="content"></el-tab-pane>
            <el-tab-pane label="用户管理" name="user"></el-tab-pane>
            <el-tab-pane label="举报管理" name="report"></el-tab-pane>
            <el-tab-pane label="数据统计" name="stats"></el-tab-pane>
            <el-tab-pane label="公告管理" name="announcement"></el-tab-pane>
          </el-tabs>
        </div>
      </template>
      
      <div class="audit-content">
        <!-- 内容审核 -->
        <div v-if="activeTab === 'content'">
          <!-- 操作功能区 -->
          <div class="operation-bar">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索标题/发布者"
              prefix-icon="el-icon-search"
              class="search-input"
              @keyup.enter="fetchAuditList"
            />
            <el-select v-model="typeFilter" placeholder="全部类型" class="filter-select">
              <el-option label="全部类型" value="-1" />
              <el-option label="领养" value="0" />
              <el-option label="救助" value="1" />
            </el-select>
            <el-select v-model="statusFilter" placeholder="全部状态" class="filter-select">
              <el-option label="全部状态" value="-1" />
              <el-option label="待审核" value="0" />
              <el-option label="已通过" value="1" />
              <el-option label="已拒绝" value="2" />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              class="date-picker"
            />
            <el-button type="primary" icon="el-icon-refresh" @click="fetchAuditList" class="refresh-btn">
              刷新
            </el-button>
          </div>
          
          <el-table :data="auditList" style="width: 100%" :empty-text="emptyText">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="title" label="标题" min-width="200" />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="scope">
                <span class="type-tag" :class="scope.row.type === 0 ? 'adopt' : 'rescue'">
                  {{ scope.row.type === 0 ? '领养' : '救助' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <span class="status-tag" :class="getStatusClass(scope.row.status)">
                  {{ getStatusText(scope.row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180" />
            <el-table-column prop="user.username" label="发布者" width="120" />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="scope">
                <el-button 
                  size="small" 
                  type="primary" 
                  @click="handleView(scope.row.id)"
                >
                  查看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="total"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
        </div>
        
        <!-- 用户管理 -->
        <div v-else-if="activeTab === 'user'" class="empty-section">
          <el-empty description="用户管理功能开发中" />
        </div>
        
        <!-- 举报管理 -->
        <div v-else-if="activeTab === 'report'" class="empty-section">
          <el-empty description="举报管理功能开发中" />
        </div>
        
        <!-- 数据统计 -->
        <div v-else-if="activeTab === 'stats'" class="empty-section">
          <el-empty description="数据统计功能开发中" />
        </div>
        
        <!-- 公告管理 -->
        <div v-else-if="activeTab === 'announcement'" class="empty-section">
          <el-empty description="公告管理功能开发中" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { getPendingList } from '../../api/pet'

const router = useRouter()

// 当前选中的选项卡
const activeTab = ref('content')

// 审核列表数据
const auditList = ref<any[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 搜索和筛选条件
const searchKeyword = ref('')
const typeFilter = ref('-1')
const statusFilter = ref('-1')
const dateRange = ref<[Date, Date] | null>(null)
const emptyText = '📭 暂无数据 (No Data)'

// 获取审核列表
const fetchAuditList = async () => {
  loading.value = true
  try {
    const response = await getPendingList({
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    if (response.code === 200 && response.data) {
      auditList.value = response.data.records || []
      total.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取审核列表失败')
    }
  } catch (error) {
    ElMessage.error('获取审核列表失败')
    console.error('获取审核列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 处理查看
const handleView = (id: number) => {
  router.push(`/audit/${id}`)
}

// 获取状态类名
const getStatusClass = (status: number) => {
  switch (status) {
    case 0: return 'pending'
    case 1: return 'approved'
    case 2: return 'rejected'
    default: return ''
  }
}

// 获取状态文本
const getStatusText = (status: number) => {
  switch (status) {
    case 0: return '待审核'
    case 1: return '已通过'
    case 2: return '已拒绝'
    default: return '未知'
  }
}

// 处理页码变化
const handleSizeChange = (size: number) => {
  pageSize.value = size
  fetchAuditList()
}

// 处理页数变化
const handleCurrentChange = (current: number) => {
  currentPage.value = current
  fetchAuditList()
}

// 页面加载时获取审核列表
onMounted(() => {
  fetchAuditList()
})
</script>

<style scoped>
.audit-container {
  padding: 5px;
}

.audit-card {
  margin-bottom: 10px;
  border: none !important;
  box-shadow: none !important;
}

.card-header {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  padding: 5px 0;
  border-bottom: none !important;
}

.audit-title {
  font-size: 18px;
  font-weight: bold;
  margin: 0;
  color: #303133;
}

.audit-tabs {
  /* 调整宽度，避免线条过长 */
  display: inline-block;
  border-bottom: none !important;
}

/* 优化选项卡高亮效果 */
:deep(.el-tabs__active-bar) {
  background-color: #409eff;
}

:deep(.el-tabs__item.is-active) {
  color: #409eff;
  font-weight: 500;
}

.audit-content {
  padding: 5px 0;
}

/* 操作功能区样式 */
.operation-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 15px 0;
  margin-bottom: 15px;
  flex-wrap: wrap;
}

.search-input {
  width: 250px;
}

.filter-select {
  width: 120px;
}

.date-picker {
  width: 200px;
}

.refresh-btn {
  flex-shrink: 0;
}

.empty-section {
  padding: 60px 20px;
  text-align: center;
}

.pagination-container {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

.type-tag {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  color: white;
  white-space: nowrap;
}

.type-tag.adopt {
  background-color: #67c23a;
}

.type-tag.rescue {
  background-color: #e6a23c;
}

.status-tag {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  color: white;
  white-space: nowrap;
}

.status-tag.pending {
  background-color: #909399;
}

.status-tag.approved {
  background-color: #67c23a;
}

.status-tag.rejected {
  background-color: #f56c6c;
}
</style>
<template>
  <div class="audit-container">
    <el-card class="audit-card">

      
      <!-- 类型切换 -->
      <div class="type-tabs-container">
        <el-tabs v-model="activeTab" class="type-tabs" @tab-click="handleTabChange">
          <el-tab-pane label="待审核" name="pending"></el-tab-pane>
          <el-tab-pane label="审核历史" name="history"></el-tab-pane>
          <el-tab-pane label="用户管理" name="user"></el-tab-pane>
          <el-tab-pane label="举报管理" name="report"></el-tab-pane>
          <el-tab-pane label="数据统计" name="stats"></el-tab-pane>
          <el-tab-pane label="公告管理" name="announcement"></el-tab-pane>
        </el-tabs>
      </div>
      
      <div class="audit-content">
        <!-- 待审核列表 -->
        <div v-if="activeTab === 'pending'">
          <!-- 操作功能区 -->
          <div class="operation-bar">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索标题/发布者"
              prefix-icon="el-icon-search"
              class="search-input"
              @keyup.enter="fetchAuditList"
            />
            <el-select v-model="dateRange" placeholder="时间范围" class="filter-select">
              <el-option label="今天" value="today" />
              <el-option label="本周" value="week" />
              <el-option label="本月" value="month" />
            </el-select>
            <el-select v-model="typeFilter" placeholder="全部类型" class="filter-select">
              <el-option label="全部类型" value="" />
              <el-option label="领养" value="adopt" />
              <el-option label="救助" value="help" />
              <el-option label="活动" value="activity" />
            </el-select>
            <el-button type="primary" icon="el-icon-search" @click="fetchAuditList" class="search-btn">
              搜索
            </el-button>
          </div>
          
          <!-- 批量操作栏 -->
          <div v-if="selectedItems.length > 0" class="batch-operations">
            <el-checkbox v-model="selectAll" @change="handleSelectAll">全选</el-checkbox>
            <div class="batch-buttons">
              <el-button type="success" @click="handleBatchApprove">
                ✅ 批量通过
              </el-button>
              <el-button type="danger" @click="handleBatchReject">
                ❌ 批量拒绝
              </el-button>
            </div>
          </div>
          
          <!-- 审核列表 -->
          <div v-if="loading" class="loading-container">
            <el-skeleton :rows="10" animated />
          </div>
          <div v-else-if="auditList.length > 0" class="audit-list">
            <div v-for="item in auditList" :key="item.id" class="audit-card-item">
              <el-checkbox v-model="selectedItems" :label="item.id" @change="handleSelectChange" class="item-checkbox" />
              <div class="item-content">
                <div class="item-header">
                  <div class="item-tags">
                    <span class="type-tag" :class="item.targetType">
                      {{ getTypeIcon(item.targetType) }} {{ getTypeText(item.targetType) }}
                    </span>
                    <span class="status-tag" :class="'status-' + item.auditStatus">
                      {{ getStatusText(item.auditStatus) }}
                    </span>
                  </div>
                  <h3 class="item-title">{{ item.title }}</h3>
                </div>
                <div class="item-meta">
                  <span class="item-publisher">发布者：{{ item.user.nickname }} · {{ item.address }}</span>
                  <span class="item-time">{{ formatTime(item.createTime) }}</span>
                </div>
                <div class="item-info" v-if="item.targetType !== 'activity'">
                  <span class="item-pet-info">
                    宠物：{{ item.petType }} / {{ item.petName }} / {{ item.petAge }} / {{ getGenderText(item.petGender) }}
                  </span>
                </div>
                <div class="item-info" v-else>
                  <span class="item-activity-info">
                    地点：{{ item.address }} · {{ item.activityTime }}
                  </span>
                  <span class="item-activity-info">
                    人数：{{ item.participantCount }}/{{ item.maxParticipants }}
                  </span>
                </div>
                <div class="item-content-text">{{ item.content }}</div>
                <div v-if="item.images && item.images.length > 0" class="item-images">
                  <div class="image-preview">
                    <img v-for="(image, index) in item.images.slice(0, 3)" :key="index" :src="image" :alt="`图片${index+1}`" class="preview-image" />
                  </div>
                  <span v-if="item.images.length > 3" class="image-count">+{{ item.images.length - 3 }}</span>
                </div>
                <div class="item-actions">
                  <el-button size="small" type="info" @click="handleViewDetail(item.id, item.targetType)">
                    查看详情
                  </el-button>
                  <el-button 
                    size="small" 
                    type="success" 
                    @click="handleQuickApprove(item.id, item.targetType)"
                    :disabled="item.auditStatus !== 0"
                  >
                    通过
                  </el-button>
                  <el-button 
                    size="small" 
                    type="danger" 
                    @click="handleQuickReject(item.id, item.targetType)"
                    :disabled="item.auditStatus !== 0"
                  >
                    拒绝
                  </el-button>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="empty-section">
            <el-empty description="暂无待审核内容" />
          </div>
          
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
            <el-button v-if="activeTab === 'pending'" type="info" @click="switchToHistory">
              📋 审核历史
            </el-button>
          </div>
        </div>
        
        <!-- 审核历史 -->
        <div v-if="activeTab === 'history'">
          <div class="operation-bar">
            <el-button type="info" @click="switchToPending">
              ← 返回待审核列表
            </el-button>
            <el-input
              v-model="historyKeyword"
              placeholder="搜索标题/发布者"
              prefix-icon="el-icon-search"
              class="search-input"
              @keyup.enter="fetchAuditHistory"
            />
            <el-select v-model="historyTypeFilter" placeholder="全部类型" class="filter-select">
              <el-option label="全部类型" value="" />
              <el-option label="领养" value="adopt" />
              <el-option label="救助" value="help" />
              <el-option label="活动" value="activity" />
            </el-select>
            <el-select v-model="historyStatusFilter" placeholder="全部状态" class="filter-select">
              <el-option label="全部状态" value="" />
              <el-option label="待审核" value="0" />
              <el-option label="已通过" value="1" />
              <el-option label="已拒绝" value="2" />
            </el-select>
            <el-button type="primary" icon="el-icon-search" @click="fetchAuditHistory" class="search-btn">
              搜索
            </el-button>
          </div>
          
          <div v-if="historyLoading" class="loading-container">
            <el-skeleton :rows="10" animated />
          </div>
          <div v-else-if="historyList.length > 0" class="history-list">
            <div v-for="item in historyList" :key="item.id" class="history-card">
              <div class="history-header">
                <span class="history-status" :class="'status-' + item.auditStatus">
                  {{ item.auditStatus === 1 ? '✅ 已通过' : '❌ 已拒绝' }}
                </span>
                <span class="history-title">{{ item.title }}</span>
                <span class="history-time">{{ formatDate(item.auditTime) }}</span>
              </div>
              <div class="history-meta">
                <span class="history-auditor">审核人：{{ item.auditorName }}</span>
                <span class="history-publisher">发布者：{{ item.user.nickname }}</span>
              </div>
              <div v-if="item.rejectReason" class="history-reason">
                拒绝理由：{{ item.rejectReason }}
              </div>
            </div>
          </div>
          <div v-else class="empty-section">
            <el-empty description="暂无审核历史" />
          </div>
          
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="historyCurrentPage"
              v-model:page-size="historyPageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="historyTotal"
              @size-change="handleHistorySizeChange"
              @current-change="handleHistoryCurrentChange"
            />
          </div>
        </div>
        
        <!-- 用户管理 -->
        <div v-else-if="activeTab === 'user'">
          <!-- 操作功能区 -->
          <div class="operation-bar">
            <el-input
              v-model="userSearchKeyword"
              placeholder="搜索用户名/昵称/手机号"
              prefix-icon="el-icon-search"
              class="search-input"
              @keyup.enter="fetchUserList"
            />
            <el-select v-model="userStatusFilter" placeholder="全部状态" class="filter-select">
              <el-option label="全部状态" value="-1" />
              <el-option label="正常" value="1" />
              <el-option label="禁用" value="0" />
            </el-select>
            <el-button
              type="primary"
              class="search-btn"
              @click="fetchUserList"
            >
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <div class="batch-operations">
              <el-button
                size="small"
                type="danger"
                @click="handleBatchDisable"
                :disabled="selectedUsers.length === 0"
              >
                批量禁用
              </el-button>
              <el-button
                size="small"
                type="success"
                @click="handleBatchEnable"
                :disabled="selectedUsers.length === 0"
              >
                批量启用
              </el-button>
              <el-button
                size="small"
                type="warning"
                @click="handleBatchResetPassword"
                :disabled="selectedUsers.length === 0"
              >
                批量重置密码
              </el-button>
            </div>
          </div>
          
          <!-- 用户列表 -->
          <div v-if="loading" class="loading-container">
            <el-skeleton :rows="10" animated />
          </div>
          <div v-else-if="userList.length > 0" class="user-list">
            <el-table 
              :data="userList" 
              style="width: 100%"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="55" />
              <el-table-column prop="userId" label="用户ID" width="100" />
              <el-table-column label="用户信息" min-width="200">
                <template #default="scope">
                  <div class="user-info">
                    <el-avatar :size="40" :src="scope.row.avatar || ''">
                      {{ scope.row.nickname?.charAt(0) || '用' }}
                    </el-avatar>
                    <div class="user-details">
                      <div class="nickname">{{ scope.row.nickname }}</div>
                      <div class="username">{{ scope.row.username }}</div>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="scope">
                  <el-tag :type="scope.row.isDisable ? 'danger' : 'success'">
                    {{ scope.row.isDisable ? '禁用' : '正常' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="scope">
                  <el-button
                    size="small"
                    :type="scope.row.isDisable ? 'success' : 'danger'"
                    @click="handleToggleUserStatus(scope.row.userId, scope.row.isDisable)"
                  >
                    {{ scope.row.isDisable ? '启用' : '禁用' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            
            <!-- 分页 -->
            <div class="pagination-container">
              <el-pagination
                v-model:current-page="userCurrentPage"
                v-model:page-size="userPageSize"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="userTotal"
                @size-change="handleUserSizeChange"
                @current-change="handleUserCurrentChange"
              />
            </div>
          </div>
          <div v-else class="empty-section">
            <el-empty description="暂无用户数据" />
          </div>
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
        <div v-else-if="activeTab === 'announcement'">
          <div class="operation-bar">
            <el-input
              v-model="noticeKeyword"
              placeholder="搜索标题/内容"
              prefix-icon="el-icon-search"
              class="search-input"
            />
            <el-select v-model="noticeStatus" placeholder="状态" class="filter-select">
              <el-option label="全部" value="" />
              <el-option label="草稿" value="0" />
              <el-option label="已发布" value="1" />
              <el-option label="已下线" value="2" />
            </el-select>
            <el-button type="primary" @click="fetchNoticeList" class="search-btn">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button type="success" @click="handleCreateNotice">
              <el-icon><Plus /></el-icon>
              新增公告
            </el-button>
          </div>
          
          <el-table :data="noticeList" style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="title" label="标题" min-width="200">
              <template #default="scope">
                <div>
                  <span :class="{ 'top-text': scope.row.isTop === 1 }">{{ scope.row.title }}</span>
                  <el-tag v-if="scope.row.isTop === 1" size="small" type="danger" effect="dark">置顶</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="100">
              <template #default="scope">
                <el-tag :type="getNoticeTypeTagType(scope.row.type)">{{ getNoticeTypeText(scope.row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="100">
              <template #default="scope">
                <el-tag v-if="scope.row.priority > 0" :type="getPriorityTagType(scope.row.priority)">
                  {{ getPriorityText(scope.row.priority) }}
                </el-tag>
                <span v-else>普通</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag :type="getNoticeStatusTagType(scope.row.status)">
                  {{ getNoticeStatusText(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="publishTime" label="发布时间" width="180" />
            <el-table-column prop="expireTime" label="过期时间" width="180" />
            <el-table-column label="操作" width="200">
              <template #default="scope">
                <el-button type="primary" size="small" @click="handleEditNotice(scope.row)">
                  编辑
                </el-button>
                <el-button v-if="scope.row.status === 0" type="success" size="small" @click="handlePublishNotice(scope.row.id)">
                  发布
                </el-button>
                <el-button v-else-if="scope.row.status === 1" type="warning" size="small" @click="handleUnpublishNotice(scope.row.id)">
                  下架
                </el-button>
                <el-button type="danger" size="small" @click="handleDeleteNotice(scope.row.id)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="noticeCurrentPage"
              v-model:page-size="noticePageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="noticeTotal"
              @size-change="handleNoticeSizeChange"
              @current-change="handleNoticeCurrentChange"
            />
          </div>
        </div>
      </div>
      
      <!-- 公告编辑对话框 -->
      <el-dialog
        v-model="noticeDialogVisible"
        :title="noticeForm.id ? '编辑公告' : '新增公告'"
        width="600px"
      >
        <el-form ref="noticeFormRef" :model="noticeForm" label-width="80px">
          <el-form-item label="标题" required>
            <el-input v-model="noticeForm.title" placeholder="请输入公告标题" maxlength="100" />
          </el-form-item>
          <el-form-item label="内容" required>
            <el-input
              v-model="noticeForm.content"
              type="textarea"
              rows="5"
              placeholder="请输入公告内容"
            />
          </el-form-item>
          <el-form-item label="类型">
            <el-radio-group v-model="noticeForm.type">
              <el-radio label="1">系统公告</el-radio>
              <el-radio label="2">活动通知</el-radio>
              <el-radio label="3">重要提醒</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="优先级">
            <el-radio-group v-model="noticeForm.priority">
              <el-radio label="0">普通</el-radio>
              <el-radio label="1">重要</el-radio>
              <el-radio label="2">紧急</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="置顶">
            <el-checkbox v-model="noticeForm.isTop">是否置顶</el-checkbox>
          </el-form-item>
          <el-form-item label="发布时间">
            <el-date-picker
              v-model="noticeForm.schedulePublishTime"
              type="datetime"
              placeholder="选择发布时间"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="noticeDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleSaveNotice">保存</el-button>
          </span>
        </template>
      </el-dialog>
      
      <!-- 批量拒绝对话框 -->
      <el-dialog
        v-model="batchRejectDialogVisible"
        title="❌ 批量拒绝"
        width="500px"
      >
        <div class="batch-reject-content">
          <p class="selected-count">已选择 {{ selectedItems.length }} 条内容</p>
          <el-form-item label="拒绝理由" required>
            <el-input
              v-model="rejectReason"
              type="textarea"
              rows="4"
              placeholder="请填写拒绝原因..."
            />
          </el-form-item>
          <div class="common-reasons">
            <p class="reasons-title">📌 常用理由：</p>
            <div class="reasons-list">
              <el-button
                v-for="(reason, index) in commonReasons"
                :key="index"
                type="info"
                size="small"
                plain
                @click="rejectReason = reason"
              >
                {{ reason }}
              </el-button>
            </div>
          </div>
        </div>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="batchRejectDialogVisible = false">取消</el-button>
            <el-button type="danger" @click="confirmBatchReject">确认拒绝</el-button>
          </span>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElRadioGroup, ElRadio, ElCheckbox, ElDatePicker, ElButton } from 'element-plus'
import { useRouter } from 'vue-router'
import { getAuditList, batchApproveAudit, batchRejectAudit, getAuditHistory } from '../../api/audit'
import { getAdminUserList, disableUser, enableUser, batchDisableUsers, batchEnableUsers, batchResetPassword } from '../../api/user'
import { getAdminNoticeList, createNotice, updateNotice, deleteNotice, publishNotice, unpublishNotice } from '../../api/notice'
import { Search, Plus } from '@element-plus/icons-vue'

const router = useRouter()

// 当前选中的选项卡
const activeTab = ref('pending')

// 审核列表数据
const auditList = ref<any[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 搜索和筛选条件
const searchKeyword = ref('')
const typeFilter = ref('')
const dateRange = ref('')

// 批量选择
const selectedItems = ref<number[]>([])
const selectAll = ref(false)

// 审核历史数据
const historyList = ref<any[]>([])
const historyLoading = ref(false)
const historyCurrentPage = ref(1)
const historyPageSize = ref(10)
const historyTotal = ref(0)
const historyKeyword = ref('')
const historyTypeFilter = ref('')
const historyStatusFilter = ref('')

// 用户管理相关数据
const userList = ref<any[]>([])
const userCurrentPage = ref(1)
const userPageSize = ref(10)
const userTotal = ref(0)
const userSearchKeyword = ref('')
const userStatusFilter = ref('-1')
const selectedUsers = ref<any[]>([])

// 公告管理相关数据
const noticeList = ref<any[]>([])
const noticeCurrentPage = ref(1)
const noticePageSize = ref(10)
const noticeTotal = ref(0)
const noticeKeyword = ref('')
const noticeStatus = ref('')

// 公告编辑对话框
const noticeDialogVisible = ref(false)
const noticeForm = ref({
  id: 0,
  title: '',
  content: '',
  type: 1,
  priority: 0,
  isTop: 0,
  schedulePublishTime: ''
})
const noticeFormRef = ref<any>(null)

// 批量拒绝对话框
const batchRejectDialogVisible = ref(false)
const rejectReason = ref('')
const commonReasons = [
  '内容不符合规范',
  '图片违规',
  '联系方式无效',
  '重复发布',
  '信息不完整'
]

// 获取审核列表
const fetchAuditList = async () => {
  loading.value = true
  try {
    const response = await getAuditList({
      targetType: typeFilter.value || undefined,
      keyword: searchKeyword.value || undefined,
      dateRange: dateRange.value || undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    if (response.code === 200 && response.data) {
      auditList.value = response.data.records || []
      total.value = response.data.total || 0
      selectedItems.value = []
      selectAll.value = false
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

// 获取审核历史
const fetchAuditHistory = async () => {
  historyLoading.value = true
  try {
    const response = await getAuditHistory({
      targetType: historyTypeFilter.value || undefined,
      keyword: historyKeyword.value || undefined,
      auditStatus: historyStatusFilter.value ? parseInt(historyStatusFilter.value) : undefined,
      pageNum: historyCurrentPage.value,
      pageSize: historyPageSize.value
    })
    if (response.code === 200 && response.data) {
      historyList.value = response.data.records || []
      historyTotal.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取审核历史失败')
    }
  } catch (error) {
    ElMessage.error('获取审核历史失败')
    console.error('获取审核历史失败:', error)
  } finally {
    historyLoading.value = false
  }
}

// 处理查看详情
const handleViewDetail = (id: number, targetType: string) => {
  if (targetType === 'activity') {
    // 活动类型跳转到活动详情页面
    router.push(`/pets/activity/${id}`)
  } else {
    // 其他类型跳转到宠物详情页面
    router.push(`/pets/${id}`)
  }
};

// 快速审核通过
const handleQuickApprove = async (id: number, targetType: string) => {
  try {
    const response = await batchApproveAudit(targetType, [id])
    if (response.code === 200) {
      ElMessage.success('审核通过')
      fetchAuditList()
    } else {
      ElMessage.error(response.message || '审核通过失败')
    }
  } catch (error) {
    ElMessage.error('审核通过失败')
    console.error('审核通过失败:', error)
  }
}

// 快速审核拒绝
const handleQuickReject = async (id: number, targetType: string) => {
  // 弹出输入框，让审核人员输入拒绝原因
  const { value: reason } = await ElMessageBox.prompt('请输入拒绝原因', '审核拒绝', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '请输入拒绝原因',
    inputValidator: (value) => {
      if (!value || value.trim() === '') {
        return '拒绝原因不能为空'
      }
      return true
    }
  })
  
  if (reason) {
    try {
      const response = await batchRejectAudit(targetType, [id], reason)
      if (response.code === 200) {
        ElMessage.success('审核拒绝')
        fetchAuditList()
      } else {
        ElMessage.error(response.message || '审核拒绝失败')
      }
    } catch (error) {
      ElMessage.error('审核拒绝失败')
      console.error('审核拒绝失败:', error)
    }
  }
}

// 处理批量通过
const handleBatchApprove = async () => {
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请选择要审核通过的内容')
    return
  }
  
  try {
    // 假设所有选中项类型相同，取第一个的类型
    const firstItem = auditList.value.find(item => item.id === selectedItems.value[0])
    if (!firstItem) return
    
    const response = await batchApproveAudit(firstItem.targetType, selectedItems.value)
    if (response.code === 200) {
      ElMessage.success('批量审核通过')
      fetchAuditList()
    } else {
      ElMessage.error(response.message || '批量审核通过失败')
    }
  } catch (error) {
    ElMessage.error('批量审核通过失败')
    console.error('批量审核通过失败:', error)
  }
}

// 处理批量拒绝
const handleBatchReject = () => {
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请选择要审核拒绝的内容')
    return
  }
  rejectReason.value = ''
  batchRejectDialogVisible.value = true
}

// 确认批量拒绝
const confirmBatchReject = async () => {
  if (!rejectReason.value.trim()) {
    ElMessage.error('拒绝原因不能为空')
    return
  }
  
  try {
    // 假设所有选中项类型相同，取第一个的类型
    const firstItem = auditList.value.find(item => item.id === selectedItems.value[0])
    if (!firstItem) return
    
    const response = await batchRejectAudit(firstItem.targetType, selectedItems.value, rejectReason.value)
    if (response.code === 200) {
      ElMessage.success('批量审核拒绝')
      batchRejectDialogVisible.value = false
      fetchAuditList()
    } else {
      ElMessage.error(response.message || '批量审核拒绝失败')
    }
  } catch (error) {
    ElMessage.error('批量审核拒绝失败')
    console.error('批量审核拒绝失败:', error)
  }
}

// 处理全选
const handleSelectAll = (value: boolean) => {
  if (value) {
    selectedItems.value = auditList.value.map(item => item.id)
  } else {
    selectedItems.value = []
  }
}

// 处理选择变化
const handleSelectChange = () => {
  selectAll.value = selectedItems.value.length === auditList.value.length
}

// 切换到历史记录
const switchToHistory = () => {
  activeTab.value = 'history'
  fetchAuditHistory()
}

// 切换到待审核
const switchToPending = () => {
  activeTab.value = 'pending'
  fetchAuditList()
}

// 获取类型图标
const getTypeIcon = (type: string) => {
  switch (type) {
    case 'adopt': return '🐱'
    case 'help': return '🐕'
    case 'activity': return '📍'
    default: return ''
  }
}

// 获取类型文本
const getTypeText = (type: string) => {
  switch (type) {
    case 'adopt': return '领养'
    case 'help': return '救助'
    case 'activity': return '活动'
    default: return '未知'
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

// 获取性别文本
const getGenderText = (gender: number) => {
  switch (gender) {
    case 1: return '公'
    case 2: return '母'
    default: return '未知'
  }
}

// 格式化时间
const formatTime = (time: string) => {
  const now = new Date()
  const target = new Date(time)
  const diff = now.getTime() - target.getTime()
  
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return target.toLocaleDateString('zh-CN')
}

// 格式化日期
const formatDate = (date: string) => {
  const target = new Date(date)
  return target.toLocaleString('zh-CN')
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

// 处理历史记录页码变化
const handleHistorySizeChange = (size: number) => {
  historyPageSize.value = size
  fetchAuditHistory()
}

// 处理历史记录页数变化
const handleHistoryCurrentChange = (current: number) => {
  historyCurrentPage.value = current
  fetchAuditHistory()
}

// 处理选项卡切换
const handleTabChange = () => {
  // 延迟执行，确保activeTab已经更新
  setTimeout(() => {
    if (activeTab.value === 'pending') {
      fetchAuditList()
    } else if (activeTab.value === 'history') {
      fetchAuditHistory()
    } else if (activeTab.value === 'user') {
      fetchUserList()
    } else if (activeTab.value === 'announcement') {
      fetchNoticeList()
    } else if (activeTab.value === 'report') {
      // 跳转到举报管理页面
      router.push('/report')
    }
  }, 0)
}

// 获取用户列表
const fetchUserList = async () => {
  loading.value = true
  try {
    const response = await getAdminUserList({
      pageNum: userCurrentPage.value,
      pageSize: userPageSize.value,
      keyword: userSearchKeyword.value,
      status: userStatusFilter.value === '-1' ? undefined : parseInt(userStatusFilter.value)
    })
    if (response.code === 200) {
      userList.value = response.data.records || []
      userTotal.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取用户列表失败')
    }
  } catch (error) {
    ElMessage.error('获取用户列表失败，请重试')
    console.error('获取用户列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 处理用户状态切换
const handleToggleUserStatus = async (userId: number, isDisable: boolean) => {
  try {
    let response
    if (isDisable) {
      // 当前是禁用状态，调用启用接口
      response = await enableUser(userId)
    } else {
      // 当前是正常状态，调用禁用接口
      response = await disableUser(userId)
    }
    if (response.code === 200) {
      ElMessage.success(isDisable ? '启用成功' : '禁用成功')
      fetchUserList()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试')
    console.error('切换用户状态失败:', error)
  }
}

// 处理选择变化
const handleSelectionChange = (val: any[]) => {
  selectedUsers.value = val
}

// 批量禁用用户
const handleBatchDisable = async () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请选择要禁用的用户')
    return
  }
  
  try {
    const userIds = selectedUsers.value.map(user => user.userId)
    const response = await batchDisableUsers(userIds)
    if (response.code === 200) {
      ElMessage.success('批量禁用成功')
      fetchUserList()
      selectedUsers.value = []
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试')
    console.error('批量禁用用户失败:', error)
  }
}

// 批量启用用户
const handleBatchEnable = async () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请选择要启用的用户')
    return
  }
  
  try {
    const userIds = selectedUsers.value.map(user => user.userId)
    const response = await batchEnableUsers(userIds)
    if (response.code === 200) {
      ElMessage.success('批量启用成功')
      fetchUserList()
      selectedUsers.value = []
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试')
    console.error('批量启用用户失败:', error)
  }
}

// 批量重置密码
const handleBatchResetPassword = async () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请选择要重置密码的用户')
    return
  }
  
  try {
    const userIds = selectedUsers.value.map(user => user.userId)
    const response = await batchResetPassword(userIds)
    if (response.code === 200) {
      ElMessage.success('批量重置密码成功')
      selectedUsers.value = []
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试')
    console.error('批量重置密码失败:', error)
  }
}

// 处理用户列表分页大小变化
const handleUserSizeChange = (size: number) => {
  userPageSize.value = size
  fetchUserList()
}

// 处理用户列表当前页码变化
const handleUserCurrentChange = (current: number) => {
  userCurrentPage.value = current
  fetchUserList()
}

// 获取公告列表
const fetchNoticeList = async () => {
  loading.value = true
  try {
    const response = await getAdminNoticeList({
      pageNum: noticeCurrentPage.value,
      pageSize: noticePageSize.value,
      status: noticeStatus.value ? Number(noticeStatus.value) : undefined,
      keyword: noticeKeyword.value
    })
    if (response.code === 200 && response.data) {
      noticeList.value = response.data.records || []
      noticeTotal.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取公告列表失败')
    }
  } catch (error) {
    ElMessage.error('获取公告列表失败，请重试')
    console.error('获取公告列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 处理公告列表分页大小变化
const handleNoticeSizeChange = (size: number) => {
  noticePageSize.value = size
  fetchNoticeList()
}

// 处理公告列表当前页码变化
const handleNoticeCurrentChange = (current: number) => {
  noticeCurrentPage.value = current
  fetchNoticeList()
}

// 处理创建公告
const handleCreateNotice = () => {
  noticeForm.value = {
    id: 0,
    title: '',
    content: '',
    type: 1,
    priority: 0,
    isTop: 0,
    schedulePublishTime: ''
  }
  noticeDialogVisible.value = true
}

// 处理编辑公告
const handleEditNotice = (notice: any) => {
  noticeForm.value = {
    id: notice.id,
    title: notice.title,
    content: notice.content,
    type: notice.type,
    priority: notice.priority,
    isTop: notice.isTop,
    schedulePublishTime: notice.publishTime
  }
  noticeDialogVisible.value = true
}

// 处理发布公告
const handlePublishNotice = async (id: number) => {
  try {
    const response = await publishNotice(id)
    if (response.code === 200) {
      ElMessage.success('发布成功')
      fetchNoticeList()
    } else {
      ElMessage.error(response.message || '发布失败')
    }
  } catch (error) {
    ElMessage.error('发布失败，请重试')
    console.error('发布公告失败:', error)
  }
}

// 处理下架公告
const handleUnpublishNotice = async (id: number) => {
  try {
    const response = await unpublishNotice(id)
    if (response.code === 200) {
      ElMessage.success('下架成功')
      fetchNoticeList()
    } else {
      ElMessage.error(response.message || '下架失败')
    }
  } catch (error) {
    ElMessage.error('下架失败，请重试')
    console.error('下架公告失败:', error)
  }
}

// 处理删除公告
const handleDeleteNotice = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这个公告吗？', '删除公告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await deleteNotice(id)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      fetchNoticeList()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败，请重试')
      console.error('删除公告失败:', error)
    }
  }
}

// 处理保存公告
const handleSaveNotice = async () => {
  try {
    if (!noticeForm.value.title) {
      ElMessage.error('请输入公告标题')
      return
    }
    if (!noticeForm.value.content) {
      ElMessage.error('请输入公告内容')
      return
    }
    
    let response
    if (noticeForm.value.id) {
      // 更新公告
      response = await updateNotice(noticeForm.value)
    } else {
      // 创建公告
      response = await createNotice(noticeForm.value)
    }
    
    if (response.code === 200) {
      ElMessage.success(noticeForm.value.id ? '更新成功' : '创建成功')
      noticeDialogVisible.value = false
      fetchNoticeList()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试')
    console.error('保存公告失败:', error)
  }
}

// 获取公告类型标签类型
const getNoticeTypeTagType = (type: number) => {
  switch (type) {
    case 1: return 'primary'
    case 2: return 'success'
    case 3: return 'danger'
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

// 获取优先级标签类型
const getPriorityTagType = (priority: number) => {
  switch (priority) {
    case 1: return 'warning'
    case 2: return 'danger'
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

// 获取公告状态标签类型
const getNoticeStatusTagType = (status: number) => {
  switch (status) {
    case 0: return 'info'
    case 1: return 'success'
    case 2: return 'warning'
    default: return ''
  }
}

// 获取公告状态文本
const getNoticeStatusText = (status: number) => {
  switch (status) {
    case 0: return '草稿'
    case 1: return '已发布'
    case 2: return '已下线'
    default: return ''
  }
}

// 页面加载时获取审核列表
onMounted(() => {
  fetchAuditList()
})
</script>

<style scoped>
.audit-container {
  padding: 15px;
}

.audit-card {
  margin-bottom: 20px;
}

.card-header {
  padding: 10px 0;
}

.audit-title {
  font-size: 18px;
  font-weight: bold;
  margin: 0;
  color: #303133;
}

/* 类型切换容器 */
.type-tabs-container {
  margin: 15px 0;
}

.type-tabs {
  width: 100%;
}

:deep(.el-tabs__nav) {
  display: flex;
  width: 100%;
}

:deep(.el-tabs__item) {
  flex: 1;
  text-align: center;
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
  padding: 10px 0;
}

/* 操作功能区样式 */
.operation-bar {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px 0;
  margin-bottom: 20px;
  flex-wrap: wrap;
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
}

.search-input {
  width: 280px;
}

.filter-select {
  width: 140px;
}

.date-picker {
  width: 180px !important; /* 减短日期选择器的宽度 */
}

.search-btn {
  flex-shrink: 0;
}

/* 批量操作栏 */
.batch-operations {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 20px;
  margin-bottom: 20px;
  background-color: #ecf5ff;
  border-radius: 8px;
}

.batch-buttons {
  display: flex;
  gap: 10px;
}

/* 审核列表 */
.audit-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(48%, 1fr));
  gap: 20px;
}

.audit-card-item {
  display: flex;
  flex-direction: column;
  gap: 15px;
  padding: 20px;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  height: 100%;
}

.audit-card-item:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.item-checkbox {
  margin-top: 5px;
}

.item-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.item-header {
  margin-bottom: 10px;
}

.item-tags {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}

.type-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
  white-space: nowrap;
}

.type-tag.adopt {
  background-color: #67c23a;
}

.type-tag.help {
  background-color: #e6a23c;
}

.type-tag.activity {
  background-color: #409eff;
}

.status-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
  white-space: nowrap;
}

.status-tag.status-0 {
  background-color: #909399;
}

.status-tag.status-1 {
  background-color: #67c23a;
}

.status-tag.status-2 {
  background-color: #f56c6c;
}

.item-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 10px 0;
  color: #303133;
}

.item-meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 14px;
  color: #909399;
  flex-wrap: wrap;
  gap: 5px;
}

.item-info {
  margin-bottom: 10px;
  font-size: 14px;
  color: #606266;
  display: flex;
  gap: 15px;
  flex-wrap: wrap;
}

.item-content-text {
  margin-bottom: 15px;
  font-size: 14px;
  line-height: 1.5;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  flex: 1;
}

/* 图片预览 */
.item-images {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
  gap: 10px;
}

.image-preview {
  display: flex;
  gap: 10px;
}

.preview-image {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.preview-image:hover {
  transform: scale(1.05);
}

.image-count {
  font-size: 14px;
  color: #909399;
  margin-left: 5px;
}

.item-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 15px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

/* 历史记录列表 */
.history-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.history-card {
  padding: 15px;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.history-header {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.history-status {
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  color: white;
}

.history-status.status-1 {
  background-color: #67c23a;
}

.history-status.status-2 {
  background-color: #f56c6c;
}

.history-title {
  flex: 1;
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.history-time {
  font-size: 12px;
  color: #909399;
}

.history-meta {
  display: flex;
  gap: 20px;
  margin-bottom: 10px;
  font-size: 14px;
  color: #606266;
}

.history-reason {
  font-size: 14px;
  color: #f56c6c;
  padding: 10px;
  background-color: #fef0f0;
  border-radius: 4px;
  margin-top: 10px;
}

/* 批量拒绝对话框 */
.batch-reject-content {
  padding: 10px 0;
}

.selected-count {
  font-size: 14px;
  margin-bottom: 15px;
  color: #606266;
}

.common-reasons {
  margin-top: 20px;
}

.reasons-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 10px;
  color: #606266;
}

.reasons-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.empty-section {
  padding: 60px 20px;
  text-align: center;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 用户信息样式 */
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-details {
  flex: 1;
  min-width: 0;
}

.nickname {
  font-weight: 500;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.username {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 加载状态 */
.loading-container {
  padding: 40px 0;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .audit-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .audit-container {
    padding: 10px;
  }
  
  .operation-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  
  .search-input {
    width: 100%;
  }
  
  .filter-select {
    width: 100%;
  }
  
  .audit-card-item {
    flex-direction: column;
    gap: 10px;
  }
  
  .item-checkbox {
    align-self: flex-start;
  }
  
  .item-meta {
    flex-direction: column;
    gap: 5px;
    align-items: flex-start;
  }
  
  .item-info {
    flex-direction: column;
    gap: 5px;
  }
  
  .item-actions {
    flex-direction: column;
    align-items: stretch;
  }
  
  .batch-operations {
    flex-direction: column;
    gap: 10px;
    align-items: stretch;
  }
  
  .batch-buttons {
    justify-content: center;
  }
  
  .pagination-container {
    flex-direction: column;
    gap: 10px;
    align-items: stretch;
  }
}
</style>
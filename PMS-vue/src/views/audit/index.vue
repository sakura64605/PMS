<template>
  <div class="audit-container">
    <el-card class="audit-card">

      
      <!-- 类型切换 -->
      <div class="type-tabs-container">
        <el-tabs v-model="activeTab" class="type-tabs" @tab-click="handleTabChange">
          <el-tab-pane label="内容审核" name="content"></el-tab-pane>
          <el-tab-pane label="用户管理" name="user"></el-tab-pane>
          <el-tab-pane label="举报管理" name="report"></el-tab-pane>
          <el-tab-pane label="数据统计" name="stats"></el-tab-pane>
          <el-tab-pane label="公告管理" name="announcement"></el-tab-pane>
        </el-tabs>
      </div>
      
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
              style="width: 180px;"
            />
            <el-button type="primary" icon="el-icon-search" @click="fetchAuditList" class="search-btn">
              搜索
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElRadioGroup, ElRadio, ElCheckbox, ElDatePicker, ElButton } from 'element-plus'
import { useRouter } from 'vue-router'
import { getPendingList } from '../../api/pet'
import { getAdminUserList, disableUser, enableUser, batchDisableUsers, batchEnableUsers, batchResetPassword } from '../../api/user'
import { getAdminNoticeList, createNotice, updateNotice, deleteNotice, publishNotice, unpublishNotice } from '../../api/notice'
import { Search, Plus } from '@element-plus/icons-vue'

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
    case 4: return 'rejected'
    default: return ''
  }
}

// 获取状态文本
const getStatusText = (status: number) => {
  switch (status) {
    case 0: return '待审核'
    case 1: return '已通过'
    case 4: return '已拒绝'
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

// 处理选项卡切换
const handleTabChange = () => {
  // 延迟执行，确保activeTab已经更新
  setTimeout(() => {
    if (activeTab.value === 'content') {
      fetchAuditList()
    } else if (activeTab.value === 'user') {
      fetchUserList()
    } else if (activeTab.value === 'announcement') {
      fetchNoticeList()
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
  padding: 5px;
}

.audit-card {
  margin-bottom: 10px;
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
  margin: 10px 0;
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
  width: 180px !important; /* 减短日期选择器的宽度 */
}

.search-btn {
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
  padding: 20px 0;
}
</style>
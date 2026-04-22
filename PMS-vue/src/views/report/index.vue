<template>
  <div class="report-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>举报管理</span>
        </div>
      </template>
      
      <!-- 筛选条件 -->
      <el-form :inline="true" :model="searchForm" class="mb-4">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" style="width: 120px;">
            <el-option label="全部" value="" />
            <el-option label="待处理" value="0" />
            <el-option label="已处理" value="1" />
            <el-option label="已驳回" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型">
          <el-select v-model="searchForm.targetType" placeholder="请选择类型" style="width: 120px;">
            <el-option label="全部" value="" />
            <el-option label="宠物" value="pet" />
            <el-option label="活动" value="activity" />
            <el-option label="评论" value="comment" />
            <el-option label="用户" value="user" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
      
      <!-- 举报列表 -->
      <el-table :data="reportList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="targetType" label="目标类型" width="100">
          <template #default="scope">
            {{ getTargetTypeDesc(scope.row.targetType) }}
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="目标ID" width="100" />
        <el-table-column prop="targetTitle" label="目标标题" min-width="150" />
        <el-table-column prop="reporterName" label="举报人" width="120" />
        <el-table-column prop="reason" label="举报原因" min-width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ getStatusDesc(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="举报时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="handleDetail(scope.row.id)">
              查看
            </el-button>
            <el-button size="small" type="info" @click="viewTarget(scope.row.targetType, scope.row.targetId)">
              查看内容
            </el-button>
            <el-button 
              v-if="scope.row.status === 0" 
              size="small" 
              type="success" 
              @click="openHandleDialog(scope.row.id)"
            >
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination" style="margin-top: 20px;">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
    
    <!-- 举报详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="举报详情"
      width="600px"
    >
      <el-form :model="reportDetail" label-width="100px">
        <el-form-item label="目标类型">
          <span>{{ getTargetTypeDesc(reportDetail.targetType) }}</span>
        </el-form-item>
        <el-form-item label="目标ID">
          <span>{{ reportDetail.targetId }}</span>
        </el-form-item>
        <el-form-item label="目标标题">
          <span>{{ reportDetail.targetTitle }}</span>
        </el-form-item>
        <el-form-item label="举报人">
          <span>{{ reportDetail.reporterName }}</span>
        </el-form-item>
        <el-form-item label="举报原因">
          <span>{{ reportDetail.reason }}</span>
        </el-form-item>
        <el-form-item label="举报时间">
          <span>{{ reportDetail.createTime }}</span>
        </el-form-item>
        <el-form-item label="处理状态" v-if="reportDetail.status !== 0">
          <el-tag :type="getStatusType(reportDetail.status)">
            {{ getStatusDesc(reportDetail.status) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="处理结果" v-if="reportDetail.handleResult">
          <span>{{ reportDetail.handleResult }}</span>
        </el-form-item>
        <el-form-item label="处理人" v-if="reportDetail.handlerName">
          <span>{{ reportDetail.handlerName }}</span>
        </el-form-item>
        <el-form-item label="处理时间" v-if="reportDetail.handleTime">
          <span>{{ reportDetail.handleTime }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 处理举报对话框 -->
    <el-dialog
      v-model="handleDialogVisible"
      title="处理举报"
      width="400px"
    >
      <el-form :model="handleForm" :rules="handleRules" ref="handleFormRef" label-width="80px">
        <el-form-item label="处理结果" prop="status">
          <el-radio-group v-model="handleForm.status">
            <el-radio label="1">已处理（下架）</el-radio>
            <el-radio label="2">已驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明" prop="handleResult">
          <el-input
            v-model="handleForm.handleResult"
            type="textarea"
            placeholder="请输入处理说明"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitHandle" :loading="handleLoading">
            确认处理
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getReportList, getReportDetail, handleReport } from '../../api/report';
import { ElMessage } from 'element-plus';

const router = useRouter();

// 搜索表单
const searchForm = reactive({
  status: '',
  targetType: ''
});

// 分页
const pageNum = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 举报列表
const reportList = ref<any[]>([]);

// 详情对话框
const detailDialogVisible = ref(false);
const reportDetail = ref({});

// 处理对话框
const handleDialogVisible = ref(false);
const handleFormRef = ref();
const handleLoading = ref(false);
const currentReportId = ref(0);

// 处理表单
const handleForm = reactive({
  status: 1,
  handleResult: ''
});

// 处理表单规则
const handleRules = {
  status: [
    { required: true, message: '请选择处理结果', trigger: 'change' }
  ],
  handleResult: [
    { required: true, message: '请输入处理说明', trigger: 'blur' }
  ]
};

// 目标类型映射
const targetTypeMap = {
  pet: '宠物',
  activity: '活动',
  comment: '评论',
  user: '用户'
};

// 状态映射
const statusMap = {
  0: '待处理',
  1: '已处理',
  2: '已驳回'
};

// 状态类型
const statusTypeMap = {
  0: 'warning',
  1: 'success',
  2: 'danger'
};

// 获取目标类型描述
const getTargetTypeDesc = (targetType: string) => {
  return targetTypeMap[targetType as keyof typeof targetTypeMap] || targetType;
};

// 获取状态描述
const getStatusDesc = (status: number) => {
  return statusMap[status as keyof typeof statusMap] || '未知';
};

// 获取状态类型
const getStatusType = (status: number) => {
  return statusTypeMap[status as keyof typeof statusTypeMap] || '';
};

// 加载举报列表
const loadReportList = async () => {
  try {
    const response = await getReportList({
      status: searchForm.status ? Number(searchForm.status) : undefined,
      targetType: searchForm.targetType || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    reportList.value = response.data.records || [];
    total.value = response.data.total || 0;
  } catch (error) {
    ElMessage.error('获取举报列表失败');
  }
};

// 搜索
const handleSearch = () => {
  pageNum.value = 1;
  loadReportList();
};

// 重置
const resetForm = () => {
  searchForm.status = '';
  searchForm.targetType = '';
  pageNum.value = 1;
  loadReportList();
};

// 分页大小变化
const handleSizeChange = (size: number) => {
  pageSize.value = size;
  loadReportList();
};

// 页码变化
const handleCurrentChange = (current: number) => {
  pageNum.value = current;
  loadReportList();
};

// 查看详情
const handleDetail = async (id: number) => {
  try {
    const response = await getReportDetail(id);
    reportDetail.value = response.data;
    detailDialogVisible.value = true;
  } catch (error) {
    ElMessage.error('获取举报详情失败');
  }
};

// 处理举报
const openHandleDialog = (id: number) => {
  currentReportId.value = id;
  handleForm.status = 1;
  handleForm.handleResult = '';
  handleDialogVisible.value = true;
};

// 提交处理
const submitHandle = async () => {
  if (!handleFormRef.value) return;
  
  await handleFormRef.value.validate(async (valid: boolean) => {
    if (valid) {
      handleLoading.value = true;
      try {
        await handleReport(currentReportId.value, {
          status: handleForm.status,
          handleResult: handleForm.handleResult
        });
        ElMessage.success('处理成功');
        handleDialogVisible.value = false;
        loadReportList();
      } catch (error: any) {
        ElMessage.error(error.message || '处理失败');
      } finally {
        handleLoading.value = false;
      }
    }
  });
};

// 查看目标内容
const viewTarget = (targetType: string, targetId: number) => {
  switch (targetType) {
    case 'pet':
      router.push(`/pets/${targetId}`);
      break;
    case 'activity':
      router.push(`/pets/activity/${targetId}`);
      break;
    case 'comment':
      // 评论需要跳转到对应宠物或活动页面的评论位置
      // 这里简单跳转到首页，实际项目中需要根据评论所属的内容类型和ID进行跳转
      router.push('/pets');
      break;
    case 'user':
      router.push(`/user/${targetId}`);
      break;
    default:
      ElMessage.warning('未知的目标类型');
  }
};

// 初始化
onMounted(() => {
  loadReportList();
});
</script>

<style scoped>
.report-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

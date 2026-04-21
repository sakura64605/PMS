<template>
  <div class="my-posts-container">
    <!-- 类型切换和发布按钮 -->
    <div class="type-tabs-container">
      <el-tabs v-model="activeTab" class="type-tabs" @tab-click="handleTabChange">
        <el-tab-pane label="领养/救助" name="adoption"></el-tab-pane>
        <el-tab-pane label="我的活动" name="activities"></el-tab-pane>
      </el-tabs>
      <el-button
        type="primary"
        class="create-button"
        @click="handleCreate"
      >
        {{ activeTab === 'adoption' ? '发布信息' : '发布活动' }}
      </el-button>
    </div>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="8" animated />
    </div>
    <div v-else-if="activeTab === 'adoption' && pets.length > 0" class="pets-grid">
      <div
        v-for="pet in pets"
        :key="pet.id"
        class="pet-card"
        @click="handleView(pet.id)"
      >
        <div v-if="pet.images && pet.images.length > 0" class="card-image">
          <img :src="pet.images[0]" alt="宠物图片" />
        </div>
        <div class="card-content">
          <div class="card-header">
            <h3 class="card-title">{{ pet.title || '' }}</h3>
            <div class="status-tag" :class="getAuditStatusClass(pet.auditStatus, pet.status)">
              {{ getAuditStatusText(pet.auditStatus, pet.status) }}
            </div>
          </div>
          <div class="pet-info">
            <div class="info-left">
              <span class="pet-name">{{ pet.petName || '未知' }}</span>
              <span class="info-divider">·</span>
              <span class="pet-type">{{ pet.petType || '未知' }}</span>
              <span class="info-divider">·</span>
              <span class="pet-age">{{ pet.petAge || '未知' }}</span>
              <span class="info-divider">·</span>
              <span class="pet-gender">
                <el-icon v-if="pet.petGender === 1"><Male /></el-icon>
                <el-icon v-else-if="pet.petGender === 2"><Female /></el-icon>
                <el-icon v-else><QuestionFilled /></el-icon>
              </span>
            </div>
            <span class="type-tag" :class="pet.type === 0 ? 'adopt' : 'rescue'">
              {{ pet.type === 0 ? '领养' : '救助' }}
            </span>
          </div>
          <div class="card-footer">
            <div class="view-count">
              <el-icon><View /></el-icon>
              <span>{{ pet.viewCount || 0 }}</span>
            </div>
            <div class="create-time">
              {{ formatDate(pet.createTime) }}
            </div>
          </div>
          <div class="action-buttons">
            <el-button
              size="small"
              @click.stop="handleView(pet.id)"
            >
              查看
            </el-button>
            <el-button
              size="small"
              type="primary"
              @click.stop="handleEdit(pet.id)"
            >
              编辑
            </el-button>
            <el-button
              v-if="pet.status === 1"
              size="small"
              type="success"
              @click.stop="handleComplete(pet.id)"
            >
              完成
            </el-button>
            <el-button
              v-if="pet.status === 1 || pet.status === 0"
              size="small"
              type="danger"
              @click.stop="handleOffline(pet.id)"
            >
              下架
            </el-button>
            <el-button
              v-if="pet.status === 3"
              size="small"
              type="success"
              @click.stop="handleRecover(pet.id)"
            >
              恢复
            </el-button>
            <el-button
              v-if="pet.status === 3 || pet.status === 4"
              size="small"
              type="danger"
              @click.stop="handleDelete(pet.id)"
            >
              删除
            </el-button>
            <el-button
              v-if="pet.status === 4"
              size="small"
              type="primary"
              @click.stop="handleRepublish(pet.id)"
            >
              重新发布
            </el-button>
          </div>
        </div>
      </div>
    </div>
    <div v-else-if="activeTab === 'adoption'" class="empty-state">
      <el-empty description="暂无领养/救助记录" />
    </div>

    <!-- 我的活动 -->
    <div v-else-if="activeTab === 'activities' && activities.length > 0" class="pets-grid">
      <div
        v-for="activity in activities"
        :key="activity.id"
        class="pet-card"
        @click="handleActivityView(activity.id)"
      >
        <div v-if="activity.images" class="card-image">
          <img :src="activity.images" alt="活动图片" />
        </div>
        <div class="card-content">
          <div class="card-header">
            <h3 class="card-title">{{ activity.title || '' }}</h3>
            <div class="status-tag" :class="getActivityAuditStatusClass(activity.auditStatus, activity.status)">
              {{ getActivityAuditStatusText(activity.auditStatus, activity.status) }}
            </div>
          </div>
          <div class="pet-info">
            <div class="info-left">
              <span class="pet-name">{{ activity.location || '未知地点' }}</span>
              <span class="info-divider">·</span>
              <span class="pet-type">{{ formatDateTime(activity.startTime) }}</span>
            </div>
          </div>
          <div class="card-footer">
            <div class="view-count">
              <el-icon><View /></el-icon>
              <span>{{ activity.viewCount || 0 }}</span>
            </div>
            <div class="create-time">
              {{ formatDate(activity.createTime) }}
            </div>
          </div>
          <div class="action-buttons">
            <el-button
              size="small"
              @click.stop="handleActivityView(activity.id)"
            >
              查看
            </el-button>
            <el-button
              size="small"
              type="primary"
              @click.stop="handleActivityEdit(activity.id)"
            >
              编辑
            </el-button>
            <el-button
              size="small"
              @click.stop="handleActivitySignUp(activity.id)"
            >
              查看报名人
            </el-button>
            <el-button
              v-if="activity.status === 0 || activity.status === 1"
              size="small"
              type="danger"
              @click.stop="handleActivityDelete(activity.id)"
            >
              删除
            </el-button>
            <el-button
              v-if="activity.status === 2 || activity.status === 3"
              size="small"
              type="danger"
              @click.stop="handleActivityDelete(activity.id)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>
    </div>
    <div v-else-if="activeTab === 'activities'" class="empty-state">
      <el-empty description="暂无活动记录" />
    </div>

    <!-- 分页组件 -->
    <div v-if="(activeTab === 'adoption' && pets.length > 0) || (activeTab === 'activities' && activities.length > 0)" class="pagination-section">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[12, 24, 36]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="activeTab === 'adoption' ? total : activitiesTotal"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 下架确认弹窗 -->
    <el-dialog
      v-model="offlineDialogVisible"
      title="确认下架"
      width="400px"
    >
      <span>确定要下架该宠物信息吗？</span>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="offlineDialogVisible = false">取消</el-button>
          <el-button type="danger" @click="confirmOffline">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 删除确认弹窗 -->
    <el-dialog
      v-model="deleteDialogVisible"
      title="确认删除"
      width="400px"
    >
      <span>确定要删除该宠物信息吗？此操作不可恢复。</span>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="deleteDialogVisible = false">取消</el-button>
          <el-button type="danger" @click="confirmDelete">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 恢复确认弹窗 -->
    <el-dialog
      v-model="recoverDialogVisible"
      title="确认恢复"
      width="400px"
    >
      <span>确定要恢复该宠物信息吗？</span>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="recoverDialogVisible = false">取消</el-button>
          <el-button type="success" @click="confirmRecover">确定</el-button>
        </span>
      </template>
    </el-dialog>



    <!-- 活动删除确认弹窗 -->
    <el-dialog
      v-model="activityDeleteDialogVisible"
      title="确认删除活动"
      width="400px"
    >
      <span>确定要删除该活动吗？此操作不可恢复。</span>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="activityDeleteDialogVisible = false">取消</el-button>
          <el-button type="danger" @click="confirmActivityDelete">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 查看报名人弹窗 -->
    <el-dialog
      v-model="signUpDialogVisible"
      title="活动报名人列表"
      width="900px"
    >
      <div v-if="signUpLoading" class="loading-container">
        <el-skeleton :rows="8" animated />
      </div>
      <div v-else-if="signUpList.length > 0" class="sign-up-list">
        <el-table :data="signUpList" style="width: 100%">
          <el-table-column prop="user.nickname" label="用户昵称" width="160">
            <template #default="scope">
              <div class="user-info">
                <img :src="scope.row.user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="用户头像" class="user-avatar" />
                <span>{{ scope.row.user.nickname }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="realName" label="真实姓名" width="100" />
          <el-table-column prop="phone" label="联系电话" width="140" />
          <el-table-column prop="remark" label="备注" width="150" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="scope">
              <el-tag :type="scope.row.isCheckedIn ? 'success' : (scope.row.status === 1 ? 'primary' : 'info')">
                {{ scope.row.isCheckedIn ? '已签到' : (scope.row.status === 1 ? '已报名' : '未知') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="signupTime" label="报名时间" width="120">
            <template #default="scope">
              {{ formatDate(scope.row.signupTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="scope">
              <el-button
                v-if="!scope.row.isCheckedIn && signUpActivityId"
                size="small"
                type="primary"
                @click="handleSignIn(signUpActivityId, scope.row.user.userId)"
              >
                签到
              </el-button>
              <el-tag v-else-if="scope.row.isCheckedIn" type="success">
                已签到
              </el-tag>
              <el-tag v-else type="info">
                无法签到
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="sign-up-pagination">
          <el-pagination
            v-model:current-page="signUpPageNum"
            v-model:page-size="signUpPageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="signUpTotal"
            @size-change="handleSignUpSizeChange"
            @current-change="handleSignUpCurrentChange"
          />
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="暂无报名记录" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { View, Male, Female, QuestionFilled } from '@element-plus/icons-vue';
import { getMyPosts, offlinePet, deletePet, recoverPet, getPetDetail, updatePet, completePet } from '../../api/pet';
import { getMyActivityList, deleteActivity, getActivitySignUpList, signInActivity } from '../../api/activity';

// 路由
const router = useRouter();
const route = useRoute();

// 处理发布按钮点击
const handleCreate = () => {
  if (activeTab.value === 'adoption') {
    router.push('/pets/create');
  } else {
    router.push({ path: '/pets/create', query: { type: '2' } });
  }
};

// 状态
const loading = ref(false);
const activeTab = ref(route.query.tab as string || 'adoption');
const pets = ref<any[]>([]);
const total = ref(0);
const activities = ref<any[]>([]);
const activitiesTotal = ref(0);
const pageNum = ref(1);
const pageSize = ref(12);
const offlineDialogVisible = ref(false);
const offlineId = ref<number | null>(null);
const deleteDialogVisible = ref(false);
const deleteId = ref<number | null>(null);
const recoverDialogVisible = ref(false);
const recoverId = ref<number | null>(null);
const activityDeleteDialogVisible = ref(false);
const activityDeleteId = ref<number | null>(null);

// 报名人相关状态
const signUpDialogVisible = ref(false);
const signUpList = ref<any[]>([]);
const signUpTotal = ref(0);
const signUpPageNum = ref(1);
const signUpPageSize = ref(10);
const signUpActivityId = ref<number | null>(null);
const signUpLoading = ref(false);

// 方法
const handleView = (id: number) => {
  router.push({ path: `/pets/${id}`, query: { from: 'my-posts' } });
};

const handleEdit = (id: number) => {
  router.push(`/pets/${id}/edit`);
};

// 处理标签页切换
const handleTabChange = (tab: any) => {
  pageNum.value = 1;
  const currentTab = tab.props.name;
  if (currentTab === 'adoption') {
    fetchMyPosts();
  } else if (currentTab === 'activities') {
    fetchMyActivities();
  }
};

// 活动相关方法
const handleActivityView = (id: number) => {
  router.push({ path: `/pets/activity/${id}`, query: { from: 'my-posts-activities' } });
};

const handleActivityEdit = (id: number) => {
  router.push({ path: `/pets/create`, query: { type: '2', id: id.toString() } });
};



const handleActivityDelete = (id: number) => {
  activityDeleteId.value = id;
  activityDeleteDialogVisible.value = true;
};

// 查看活动报名人
const handleActivitySignUp = async (id: number) => {
  signUpActivityId.value = id;
  signUpPageNum.value = 1;
  signUpDialogVisible.value = true;
  await fetchSignUpList();
};

// 获取报名人列表
const fetchSignUpList = async () => {
  if (!signUpActivityId.value) return;
  
  signUpLoading.value = true;
  try {
    const response = await getActivitySignUpList(signUpActivityId.value, {
      pageNum: signUpPageNum.value,
      pageSize: signUpPageSize.value
    });
    if (response.code === 200 && response.data) {
      signUpList.value = response.data.records || [];
      signUpTotal.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || '获取报名人列表失败');
    }
  } catch (error) {
    ElMessage.error('获取报名人列表失败，请重试');
    console.error('获取报名人列表失败:', error);
  } finally {
    signUpLoading.value = false;
  }
};

// 报名人分页相关方法
const handleSignUpSizeChange = (size: number) => {
  signUpPageSize.value = size;
  signUpPageNum.value = 1;
  fetchSignUpList();
};

const handleSignUpCurrentChange = (current: number) => {
  signUpPageNum.value = current;
  fetchSignUpList();
};

// 处理活动签到
const handleSignIn = async (activityId: number, userId: number) => {
  // 检查参数是否有效
  if (!activityId || !userId || isNaN(activityId) || isNaN(userId)) {
    ElMessage.error('无效的参数');
    return;
  }
  try {
    const response = await signInActivity(activityId, userId);
    if (response.code === 200) {
      ElMessage.success('签到成功');
      // 刷新报名人列表
      await fetchSignUpList();
    } else {
      ElMessage.error(response.message || '签到失败');
    }
  } catch (error) {
    ElMessage.error('签到失败，请重试');
    console.error('签到失败:', error);
  }
};

const confirmActivityDelete = async () => {
  if (!activityDeleteId.value) return;

  try {
    const response = await deleteActivity(activityDeleteId.value);
    if (response.code === 200) {
      ElMessage.success('活动已删除');
      activityDeleteDialogVisible.value = false;
      fetchMyActivities();
    } else {
      ElMessage.error(response.message || '删除活动失败');
    }
  } catch (error) {
    ElMessage.error('删除活动失败，请重试');
    console.error('删除活动失败:', error);
  }
};

const handleOffline = (id: number) => {
  offlineId.value = id;
  offlineDialogVisible.value = true;
};

const confirmOffline = async () => {
  if (!offlineId.value) return;

  try {
    const response = await offlinePet(offlineId.value);
    if (response.code === 200) {
      ElMessage.success('下架成功');
      offlineDialogVisible.value = false;
      fetchMyPosts();
    } else {
      ElMessage.error(response.message || '下架失败');
    }
  } catch (error) {
    ElMessage.error('下架失败，请重试');
    console.error('下架失败:', error);
  }
};

const handleDelete = (id: number) => {
  deleteId.value = id;
  deleteDialogVisible.value = true;
};

// 处理重新发布
const handleRepublish = async (id: number) => {
  try {
    // 先获取宠物详情
    const detailResponse = await getPetDetail(id);
    if (detailResponse.code === 200 && detailResponse.data) {
      const petData = detailResponse.data;
      
      // 准备更新数据（包含id，状态设为待审核）
      const updateData = {
        id: petData.id,
        type: petData.type,
        title: petData.title,
        content: petData.content,
        images: petData.images,
        petGender: petData.petGender,
        petAge: petData.petAge,
        petType: petData.petType,
        petName: petData.petName,
        contactPhone: petData.contactPhone,
        contactWechat: petData.contactWechat,
        address: petData.address,
        status: 0 // 设为待审核状态
      };
      
      // 调用更新接口
      const updateResponse = await updatePet(updateData);
      if (updateResponse.code === 200) {
        ElMessage.success('重新发布成功');
        fetchMyPosts();
      } else {
        ElMessage.error(updateResponse.message || '重新发布失败');
      }
    } else {
      ElMessage.error('获取宠物详情失败');
    }
  } catch (error) {
    ElMessage.error('重新发布失败，请重试');
    console.error('重新发布失败:', error);
  }
};

// 处理完成宠物贴
const handleComplete = async (id: number) => {
  try {
    const response = await completePet(id);
    if (response.code === 200) {
      ElMessage.success('标记完成成功');
      fetchMyPosts();
    } else {
      ElMessage.error(response.message || '标记完成失败');
    }
  } catch (error) {
    ElMessage.error('标记完成失败，请重试');
    console.error('标记完成失败:', error);
  }
};

const confirmDelete = async () => {
  if (!deleteId.value) return;

  try {
    const response = await deletePet(deleteId.value);
    if (response.code === 200) {
      ElMessage.success('删除成功');
      deleteDialogVisible.value = false;
      fetchMyPosts();
    } else {
      ElMessage.error(response.message || '删除失败');
    }
  } catch (error) {
    ElMessage.error('删除失败，请重试');
    console.error('删除失败:', error);
  }
};

const handleRecover = (id: number) => {
  recoverId.value = id;
  recoverDialogVisible.value = true;
};

const confirmRecover = async () => {
  if (!recoverId.value) return;

  try {
    const response = await recoverPet(recoverId.value);
    if (response.code === 200) {
      ElMessage.success('恢复成功');
      recoverDialogVisible.value = false;
      fetchMyPosts();
    } else {
      ElMessage.error(response.message || '恢复失败');
    }
  } catch (error) {
    ElMessage.error('恢复失败，请重试');
    console.error('恢复失败:', error);
  }
};

const getStatusClass = (status: number) => {
  switch (status) {
    case 0: return 'pending';
    case 1: return 'published';
    case 2: return 'completed';
    case 3: return 'offline';
    case 4: return 'rejected';
    default: return '';
  }
};

const getStatusText = (status: number) => {
  switch (status) {
    case 0: return '待审核';
    case 1: return '已发布';
    case 2: return '已完成';
    case 3: return '已下架';
    case 4: return '审核未通过';
    default: return '未知';
  }
};

// 审核状态相关函数
const getAuditStatusClass = (auditStatus: number, status: number) => {
  // 如果审核通过，显示帖子状态
  if (auditStatus === 1) {
    return getStatusClass(status);
  }
  // 否则显示审核状态
  switch (auditStatus) {
    case 0: return 'pending';
    case 1: return 'published';
    case 2: return 'rejected';
    default: return '';
  }
};

const getAuditStatusText = (auditStatus: number, status: number) => {
  // 如果审核通过，显示帖子状态
  if (auditStatus === 1) {
    return getStatusText(status);
  }
  // 否则显示审核状态
  switch (auditStatus) {
    case 0: return '待审核';
    case 1: return '审核通过';
    case 2: return '审核未通过';
    default: return '未知';
  }
};

// 活动状态相关函数
const getActivityStatusClass = (status: number) => {
  switch (status) {
    case 0: return 'published'; // 报名中
    case 1: return 'published'; // 进行中
    case 2: return 'completed'; // 已结束
    case 3: return 'offline'; // 已取消
    default: return '';
  }
};

const getActivityStatusText = (status: number) => {
  switch (status) {
    case 0: return '报名中';
    case 1: return '进行中';
    case 2: return '已结束';
    case 3: return '已取消';
    default: return '未知';
  }
};

// 活动审核状态相关函数
const getActivityAuditStatusClass = (auditStatus: number, status: number) => {
  // 如果审核通过，显示活动状态
  if (auditStatus === 1) {
    return getActivityStatusClass(status);
  }
  // 否则显示审核状态
  switch (auditStatus) {
    case 0: return 'pending';
    case 1: return 'published';
    case 2: return 'rejected';
    default: return '';
  }
};

const getActivityAuditStatusText = (auditStatus: number, status: number) => {
  // 如果审核通过，显示活动状态
  if (auditStatus === 1) {
    return getActivityStatusText(status);
  }
  // 否则显示审核状态
  switch (auditStatus) {
    case 0: return '待审核';
    case 1: return '审核通过';
    case 2: return '审核未通过';
    default: return '未知';
  }
};

const formatDate = (dateStr: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });
};

const formatDateTime = (dateStr: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  if (activeTab.value === 'adoption') {
    fetchMyPosts();
  } else if (activeTab.value === 'activities') {
    fetchMyActivities();
  }
};

const handleCurrentChange = (current: number) => {
  pageNum.value = current;
  if (activeTab.value === 'adoption') {
    fetchMyPosts();
  } else if (activeTab.value === 'activities') {
    fetchMyActivities();
  }
};

const fetchMyPosts = async () => {
  loading.value = true;
  try {
    const response = await getMyPosts({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    if (response.code === 200 && response.data) {
      pets.value = response.data.records || [];
      total.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || '获取我的发布失败');
    }
  } catch (error) {
    ElMessage.error('获取我的发布失败，请重试');
  } finally {
    loading.value = false;
  }
};

const fetchMyActivities = async () => {
  loading.value = true;
  try {
    const response = await getMyActivityList({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    if (response.code === 200 && response.data) {
      activities.value = response.data.records || [];
      activitiesTotal.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || '获取我的活动失败');
    }
  } catch (error) {
    ElMessage.error('获取我的活动失败，请重试');
  } finally {
    loading.value = false;
  }
};

// 生命周期
onMounted(() => {
  // 检查是否从活动详情页面返回
  const from = route.query.from as string
  if (from === 'my-posts-activities') {
    activeTab.value = 'activities';
    fetchMyActivities();
  } else if (activeTab.value === 'adoption') {
    fetchMyPosts();
  } else if (activeTab.value === 'activities') {
    fetchMyActivities();
  }
});
</script>

<style scoped>
.my-posts-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.loading-container {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.type-tabs-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  background-color: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.type-tabs {
  flex: 1;
}

.create-button {
  margin-left: 16px;
  white-space: nowrap;
}

.pets-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
  margin-bottom: 24px;
}

.pet-card {
  background-color: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
}

.pet-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.card-image {
  position: relative;
  height: 200px;
  overflow: hidden;
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.type-tag {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  color: white;
  margin-left: 8px;
  white-space: nowrap;
}

.type-tag.adopt {
  background-color: #67c23a;
}

.type-tag.rescue {
  background-color: #e6a23c;
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

.status-tag.offline {
  background-color: #f56c6c;
}

.status-tag.rejected {
  background-color: #e6a23c;
}

.card-content {
  padding: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0;
  flex: 1;
  margin-right: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
  white-space: nowrap;
}

.pet-info {
  font-size: 14px;
  color: #606266;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.info-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.info-left > span {
  margin-right: 4px;
}

.pet-name {
  font-weight: 500;
}

.info-divider {
  margin: 0 4px;
  color: #c0c4cc;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}

.view-count {
  display: flex;
  align-items: center;
  gap: 4px;
}

.create-time {
  flex: 1;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-left: 12px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.empty-state {
  background-color: white;
  border-radius: 12px;
  padding: 48px 24px;
  text-align: center;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.pagination-section {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

/* 响应式设计 */
/* 报名人列表样式 */
.sign-up-list {
  margin: 20px 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.sign-up-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 768px) {
  .my-posts-container {
    padding: 16px;
  }

  .pets-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 16px;
  }

  .card-image {
    height: 150px;
  }

  .action-buttons {
    flex-direction: column;
  }

  .sign-up-list {
    margin: 10px 0;
  }

  .sign-up-pagination {
    margin-top: 10px;
  }
}
</style>
<template>
  <div class="pets-container">
    <!-- 顶部操作栏 -->
    <div class="filter-section">
      <!-- 类型切换和发布按钮 -->
      <div class="type-tabs-container">
        <el-tabs v-model="activeType" class="type-tabs" @tab-click="handleTypeChange">
          <el-tab-pane label="全部" name="-1"></el-tab-pane>
          <el-tab-pane label="领养" name="0"></el-tab-pane>
          <el-tab-pane label="救助" name="1"></el-tab-pane>
          <el-tab-pane label="活动" name="2"></el-tab-pane>
        </el-tabs>
        <el-button
          v-if="isLoggedIn"
          type="primary"
          class="create-button"
          @click="handleCreate"
        >
          {{ activeType === '2' ? '发布活动' : '发布信息' }}
        </el-button>
      </div>

      <!-- 搜索和排序 - 宠物相关 -->
      <div v-if="activeType !== '2'" class="search-sort-section">
        <!-- 全局搜索输入框 -->
        <el-input
          v-model="searchKeyword"
          placeholder="全局搜索（标题、内容）"
          class="search-input"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch">
              <el-icon><Search /></el-icon>
            </el-button>
          </template>
        </el-input>

        <!-- 类型过滤 -->
        <el-select
          v-model="searchTypes"
          placeholder="类型"
          class="filter-select"
          multiple
          @change="handleSearch"
          clearable
          style="display: none"
        >
          <el-option label="日常" value="daily"></el-option>
          <el-option label="活动" value="activity"></el-option>
          <el-option label="宠物" value="pet"></el-option>
        </el-select>

        <!-- 排序方式 -->
        <el-select
          v-model="sortBy"
          class="sort-select"
          @change="handleSearch"
        >
          <el-option label="相关度" value="relevance"></el-option>
          <el-option label="最新发布" value="time"></el-option>
          <el-option label="热度" value="hot"></el-option>
        </el-select>

        <el-button
          type="default"
          class="reset-button"
          @click="handleReset"
        >
          重置
        </el-button>
      </div>

      <!-- 活动筛选栏 -->
      <div v-else class="activity-filter-bar">
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
            @keyup.enter="handleActivitySearch"
            @clear="handleActivitySearch"
          />
          <el-input
            v-model="searchForm.location"
            placeholder="地点搜索"
            clearable
            style="width: 200px; margin-right: 10px"
            @keyup.enter="handleActivitySearch"
            @clear="handleActivitySearch"
          />
          <el-select
            v-model="searchForm.orderBy"
            placeholder="排序"
            style="width: 150px; margin-right: 10px"
            @change="handleActivitySortChange"
          >
            <el-option label="最新发布" value="createTime"></el-option>
            <el-option label="最早开始" value="startTime"></el-option>
          </el-select>
          <el-button type="primary" @click="handleActivitySearch">搜索</el-button>
          <el-button type="info" @click="resetActivityForm">重置</el-button>
        </div>
      </div>
    </div>

    <!-- 宠物卡片网格 -->
    <div v-if="activeType !== '2'">
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="8" animated />
      </div>
      <div v-else-if="pets.length > 0" class="pets-grid">
        <div
          v-for="pet in pets"
          :key="pet.id"
          class="pet-card"
          @click="handleCardClick(pet.id, pet.type === 'activity' || pet.type === 2 ? 'activity' : undefined)"
        >
          <div v-if="pet.images && pet.images.length > 0" class="card-image">
            <img :src="pet.images[0]" alt="宠物图片" />
          </div>
          <div class="card-content">
            <!-- 活动卡片内容 -->
            <template v-if="pet.type === 2 || pet.activityType === 'activity'">
              <div class="title-with-status">
                <h3 class="card-title">📌 {{ pet.title || '' }}</h3>
                <el-tag type="primary" class="title-status-tag">活动</el-tag>
              </div>
              
              <!-- 地点和时间 -->
              <div class="location-time">
                <span class="location">📍 {{ pet.location || '未知地点' }}</span>
                <span class="time">⏰ {{ pet.startTime ? new Date(pet.startTime).toLocaleString('zh-CN') : '未知时间' }}</span>
              </div>
              
              <!-- 报名进度 -->
              <div class="progress-section">
                <div class="progress-label">👥 报名进度：{{ pet.currentPeople || 0 }} / {{ pet.maxPeople || 0 }} 人</div>
                <el-progress
                  :percentage="pet.maxPeople && pet.maxPeople > 0 ? parseFloat(((pet.currentPeople || 0) / pet.maxPeople * 100).toFixed(2)) : 0"
                  :stroke-width="6"
                />
              </div>
              
              <div class="card-footer">
                <div class="stats">
                  <div class="stat-item">
                    <el-icon><View /></el-icon>
                    <span>{{ pet.viewCount || 0 }}</span>
                  </div>
                  <div class="stat-item">
                    <el-icon><Top /></el-icon>
                    <span>{{ pet.likeCount || 0 }}</span>
                  </div>
                  <div class="stat-item">
                    <el-icon><ChatLineSquare /></el-icon>
                    <span>{{ pet.commentCount || 0 }}</span>
                  </div>
                </div>
                <div class="user-info">
                  <el-avatar :size="24" :src="pet.user?.avatar || ''">
                    {{ (pet.user?.nickname || pet.user?.username || '用').charAt(0) }}
                  </el-avatar>
                  <span class="nickname">{{ pet.user?.nickname || pet.user?.username || '未知用户' }}</span>
                </div>
              </div>
            </template>
            
            <!-- 宠物卡片内容 -->
            <template v-else>
              <h3 class="card-title">{{ pet.title || '' }}</h3>
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
                <div class="stats">
                  <div class="stat-item">
                    <el-icon><View /></el-icon>
                    <span>{{ pet.viewCount || 0 }}</span>
                  </div>
                  <div class="stat-item">
                    <el-icon><Top /></el-icon>
                    <span>{{ pet.likeCount || 0 }}</span>
                  </div>
                  <div class="stat-item">
                    <el-icon><ChatLineSquare /></el-icon>
                    <span>{{ pet.commentCount || 0 }}</span>
                  </div>
                  <div class="stat-item">
                    <el-icon><Share /></el-icon>
                    <span>{{ pet.shareCount || 0 }}</span>
                  </div>
                </div>
                <div class="user-info">
                  <el-avatar :size="24" :src="pet.user?.avatar || ''">
                    {{ (pet.user?.nickname || pet.user?.username || '用').charAt(0) }}
                  </el-avatar>
                  <span class="nickname">{{ pet.user?.nickname || pet.user?.username || '未知用户' }}</span>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="暂无宠物信息" />
      </div>

      <!-- 分页组件 -->
      <div v-if="pets.length > 0" class="pagination-section">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 30]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 活动卡片网格 -->
    <div v-else>
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="8" animated />
      </div>
      <div v-else-if="activityList.length > 0" class="activity-grid">
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
              <el-tag :type="getStatusType(activity.auditStatus || activity.status)" class="title-status-tag">
                {{ getStatusText(activity.auditStatus || activity.status) }}
              </el-tag>
            </div>
            
            <!-- 地点和时间 -->
            <div class="location-time">
              <span class="location">📍 {{ activity.location }}</span>
              <span class="time">⏰ {{ formatDateTime(activity.startTime) }} - {{ formatDateTime(activity.endTime) }}</span>
            </div>
            
            <!-- 报名进度 -->
            <div class="progress-section">
              <div class="progress-label">👥 报名进度：{{ activity.currentPeople || 0 }} / {{ activity.maxPeople || 0 }} 人 ({{ activity.maxPeople && activity.maxPeople > 0 ? ((activity.currentPeople || 0) / activity.maxPeople * 100).toFixed(2) : 0 }}%)</div>
              <el-progress
                :percentage="activity.maxPeople && activity.maxPeople > 0 ? parseFloat(((activity.currentPeople || 0) / activity.maxPeople * 100).toFixed(2)) : 0"
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
      <div v-else class="empty-state">
        <el-empty description="暂无活动" />
      </div>

      <!-- 分页组件 -->
      <div v-if="activityList.length > 0" class="pagination-section">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleActivitySizeChange"
          @current-change="handleActivityCurrentChange"
        />
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Search, View, Male, Female, QuestionFilled, User, Close, Star, ChatLineSquare, Share, Top } from '@element-plus/icons-vue';
import { getPetList, globalSearch } from '../../api/pet';
import { getActivityList, signupActivity, cancelSignup } from '../../api/activity';
import request from '../../utils/request';

// 路由
const router = useRouter();
const route = useRoute();

// 状态
const activeType = ref('-1');
const searchKeyword = ref('');
const sortOption = ref('createTime-desc');
const petType = ref('');
const gender = ref('-1');
const pageNum = ref(1);
const pageSize = ref(10);
const loading = ref(false);
const pets = ref<any[]>([]);
const total = ref(0);
// 全局搜索相关
const searchTypes = ref<string[]>([]);
const sortBy = ref('relevance');
// 用户搜索相关
const selectedUser = ref<any>(null);
const userOptions = ref<any[]>([]);
const userLoading = ref(false);

// 活动相关状态
const activeStatus = ref('0');
const searchForm = ref({
  keyword: '',
  location: '',
  orderBy: 'createTime',
  order: 'desc'
});
const activityList = ref<any[]>([]);

// 报名弹窗状态
const showSignupDialog = ref(false);
const showCancelSignupDialog = ref(false);
const selectedActivityId = ref(0);

// 报名表单
const signupFormRef = ref();
const signupForm = ref({
  realName: '',
  phone: '',
  remark: ''
});

// 报名表单验证规则
const signupRules = {
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: 'blur' }
  ]
};

// 计算属性
const isLoggedIn = computed(() => {
  return !!localStorage.getItem('token');
});

// 方法
const handleCreate = () => {
  if (activeType.value === '2') {
    router.push({ path: '/pets/create', query: { type: '2' } });
  } else {
    router.push('/pets/create');
  }
};

const handleCardClick = (id: number, itemType?: string) => {
  if (itemType === 'activity' || activeType.value === '2') {
    router.push({ path: `/pets/activity/${id}`, query: { from: 'pets-index', type: activeType.value } });
  } else {
    router.push({ path: `/pets/${id}`, query: { from: 'pets-index', type: activeType.value } });
  }
};

const getStatusClass = (status: number) => {
  if (status === undefined || status === null) return '';
  switch (status) {
    case 0: return 'pending';
    case 1: return 'published';
    case 2: return 'completed';
    default: return '';
  }
};

const getStatusText = (status: number) => {
  if (status === undefined || status === null) return '未知';
  switch (status) {
    case 0: return '待审核';
    case 1: return '已发布';
    case 2: return '已完成';
    default: return '未知';
  }
};

const formatDate = (dateStr: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

// 活动相关方法
const formatDateTime = (dateString: string) => {
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const getStatusType = (status: number) => {
  switch (status) {
    case 0: return 'success'; // 待审核-绿
    case 1: return 'primary'; // 已发布-蓝
    case 2: return 'info'; // 已完成-灰
    case 3: return 'danger'; // 已取消-红
    default: return 'default';
  }
};

const navigateToDetail = (id: number) => {
  router.push({ path: `/pets/activity/${id}`, query: { from: 'pets-index', type: '2' } });
};

const handleStatusChange = (tab: any) => {
  const newStatus = parseInt(tab.props.name);
  pageNum.value = 1;
  fetchActivityList(newStatus);
};

const resetActivityForm = () => {
  searchForm.value = {
    keyword: '',
    location: '',
    orderBy: 'createTime',
    order: 'desc'
  };
  activeStatus.value = '0';
  pageNum.value = 1;
  fetchActivityList(0);
};

const handleActivitySizeChange = (size: number) => {
  pageSize.value = size;
  fetchActivityList(Number(activeStatus.value));
};

const handleActivityCurrentChange = (current: number) => {
  pageNum.value = current;
  fetchActivityList(Number(activeStatus.value));
};

const handleActivitySearch = () => {
  pageNum.value = 1;
  fetchActivityList(Number(activeStatus.value));
};

const handleActivitySortChange = () => {
  pageNum.value = 1;
  fetchActivityList(Number(activeStatus.value));
};

const fetchActivityList = async (status: number) => {
  loading.value = true;
  try {
    const response = await getActivityList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchForm.value.keyword,
      status: status,
      location: searchForm.value.location,
      orderBy: searchForm.value.orderBy,
      order: searchForm.value.order
    });
    // 确保每个活动对象都有 isSignUp 字段，默认值为 0
    activityList.value = (response.data.records || []).map((activity: any) => ({
      ...activity,
      isSignUp: activity.isSignUp || activity.isSignedUp || 0
    }));
    total.value = response.data.total || 0;
  } catch (error) {
    console.error('获取活动列表失败:', error);
  } finally {
    loading.value = false;
  }
};

const submitSignup = async () => {
  if (!signupFormRef.value) return;
  
  // 使用 Promise 包装 validate 方法
  const valid = await new Promise<boolean>((resolve) => {
    signupFormRef.value.validate((valid: boolean) => {
      resolve(valid);
    });
  });
  
  if (valid && selectedActivityId.value) {
    try {
      await signupActivity({
        activityId: selectedActivityId.value,
        realName: signupForm.value.realName,
        phone: signupForm.value.phone,
        remark: signupForm.value.remark
      });
      ElMessage.success('报名成功');
      showSignupDialog.value = false;
      // 刷新页面数据
      fetchActivityList(Number(activeStatus.value));
    } catch (error) {
      ElMessage.error('报名失败');
      console.error('报名失败:', error);
    }
  }
};

const submitCancelSignup = async () => {
  if (selectedActivityId.value) {
    try {
      await cancelSignup(selectedActivityId.value);
      ElMessage.success('取消报名成功');
      showCancelSignupDialog.value = false;
      // 刷新页面数据
      fetchActivityList(Number(activeStatus.value));
    } catch (error) {
      ElMessage.error('取消报名失败');
      console.error('取消报名失败:', error);
    }
  }
};

// 新增：带参数的请求函数
const fetchPetsWithType = async (type?: number) => {
  loading.value = true;
  try {
    if (searchKeyword.value) {
      // 使用全局搜索
      await fetchGlobalSearch();
    } else {
      // 使用原有的宠物列表接口
      const [orderBy, order] = sortOption.value.split('-');
      const requestParams = {
        type,
        gender: gender.value === '-1' ? undefined : parseInt(gender.value),
        petType: petType.value || undefined,
        userId: selectedUser.value?.userId || undefined,
        keyword: searchKeyword.value || undefined,
        orderBy,
        order,
        pageNum: pageNum.value,
        pageSize: pageSize.value
      };
      
      console.log('=== 发送请求 ===');
      console.log('请求参数:', requestParams);
      
      const response = await getPetList(requestParams);
      
      if (response.code === 200 && response.data) {
        pets.value = response.data.records || [];
        total.value = response.data.total || 0;
        console.log('返回数据数量:', response.data.total);
        if (response.data.records?.length > 0) {
          console.log('第一条数据type:', response.data.records[0]?.type);
        }
      } else {
        ElMessage.error(response.message || '获取宠物列表失败');
      }
    }
  } catch (error) {
    ElMessage.error('获取宠物列表失败，请重试');
    console.error('获取宠物列表失败:', error);
  } finally {
    loading.value = false;
  }
};

// 全局搜索
const fetchGlobalSearch = async () => {
  loading.value = true;
  try {
    const response = await globalSearch({
      keyword: searchKeyword.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      types: searchTypes.value.length > 0 ? searchTypes.value : undefined,
      sortBy: sortBy.value
    });
    console.log('全局搜索请求参数:', {
      keyword: searchKeyword.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      types: searchTypes.value.length > 0 ? searchTypes.value : undefined,
      sortBy: sortBy.value
    });
    
    if (response.code === 200 && response.data) {
      // 转换全局搜索结果为宠物列表格式
      const results = response.data.items || response.data.results || [];
      console.log('全局搜索结果:', results);
      pets.value = results.map((item: any) => {
        // 根据不同类型转换数据结构
        if (item.type === 'pet') {
          return item;
        } else if (item.type === 'daily') {
          // 日常帖子转换为宠物贴格式
          return {
            id: item.id,
            title: item.title,
            content: item.content,
            images: item.images || [],
            viewCount: item.viewCount || 0,
            likeCount: item.likeCount || 0,
            commentCount: item.commentCount || 0,
            shareCount: item.shareCount || 0,
            createTime: item.createTime,
            user: item.user,
            type: 0 // 默认为领养类型
          };
        } else if (item.type === 'activity') {
          // 活动转换为宠物贴格式
          return {
            id: item.id,
            title: item.title,
            content: item.content,
            images: item.images || [],
            viewCount: item.viewCount || 0,
            likeCount: item.likeCount || 0,
            commentCount: item.commentCount || 0,
            shareCount: item.shareCount || 0,
            createTime: item.createTime,
            user: item.user,
            type: 2, // 活动类型
            activityType: item.type, // 标记为活动
            location: item.location, // 活动地点
            startTime: item.startTime, // 活动开始时间
            endTime: item.endTime, // 活动结束时间
            currentPeople: item.currentPeople || 0, // 当前报名人数
            maxPeople: item.maxPeople || 0 // 最大报名人数
          };
        }
        return item;
      });
      total.value = response.data.total || 0;
      console.log('全局搜索返回数据数量:', response.data.total);
    } else {
      ElMessage.error(response.message || '搜索失败');
    }
  } catch (error) {
    ElMessage.error('搜索失败，请重试');
    console.error('搜索失败:', error);
  } finally {
    loading.value = false;
  }
};

// 修改原来的 fetchPets
const fetchPets = () => {
  const currentType = activeType.value === '-1' ? undefined : parseInt(activeType.value);
  fetchPetsWithType(currentType);
};

// 修改 handleTypeChange - 关键修复
const handleTypeChange = (tab: any) => {
  console.log('=== 选项卡切换 ===');
  console.log('点击的选项卡name:', tab.props.name);
  
  pageNum.value = 1;
  
  if (tab.props.name === '2') {
    // 活动选项卡
    fetchActivityList(0);
  } else {
    // 宠物相关选项卡
    let newType: number | undefined;
    if (tab.props.name === '0') {
      newType = 0;  // 领养
    } else if (tab.props.name === '1') {
      newType = 1;  // 救助
    } else {
      newType = undefined;  // 全部
    }
    
    console.log('请求的type:', newType === undefined ? '全部' : newType);
    fetchPetsWithType(newType);
  }
};

const handleSearch = () => {
  console.log('=== 处理搜索 ===');
  console.log('搜索关键词:', searchKeyword.value);
  console.log('已选类型:', searchTypes.value);
  console.log('排序方式:', sortBy.value);
  
  pageNum.value = 1;
  if (searchKeyword.value || searchTypes.value.length > 0) {
    // 使用全局搜索（有关键词或有类型过滤）
    fetchGlobalSearch();
  } else {
    // 使用原有的搜索
    fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
  }
};

const handleSortChange = () => {
  console.log('=== 处理排序 ===');
  console.log('排序方式:', sortBy.value);
  console.log('已选类型:', searchTypes.value);
  
  pageNum.value = 1;
  if (searchKeyword.value || searchTypes.value.length > 0) {
    // 使用全局搜索（有关键词或有类型过滤）
    fetchGlobalSearch();
  } else {
    // 使用原有的搜索
    fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
  }
};

const handleSizeChange = (size: number) => {
  console.log('=== 处理分页大小变化 ===');
  console.log('分页大小:', size);
  console.log('已选类型:', searchTypes.value);
  
  pageSize.value = size;
  if (searchKeyword.value || searchTypes.value.length > 0) {
    // 使用全局搜索（有关键词或有类型过滤）
    fetchGlobalSearch();
  } else {
    fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
  }
};

const handleCurrentChange = (current: number) => {
  console.log('=== 处理页码变化 ===');
  console.log('页码:', current);
  console.log('已选类型:', searchTypes.value);
  
  pageNum.value = current;
  if (searchKeyword.value || searchTypes.value.length > 0) {
    // 使用全局搜索（有关键词或有类型过滤）
    fetchGlobalSearch();
  } else {
    fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
  }
};

const handlePetTypeChange = () => {
  pageNum.value = 1;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const handleGenderChange = () => {
  pageNum.value = 1;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const handleUserSearch = async (keyword: string) => {
  if (keyword.trim().length < 1) {
    userOptions.value = [];
    return;
  }
  
  userLoading.value = true;
  try {
    const response = await request({
      url: '/user/search',
      method: 'get',
      params: { keyword }
    });
    
    if (response.code === 200) {
      userOptions.value = response.data || [];
    } else {
      ElMessage.error(response.message || '搜索用户失败');
      userOptions.value = [];
    }
  } catch (error) {
    ElMessage.error('搜索用户失败，请重试');
    userOptions.value = [];
    console.error('搜索用户失败:', error);
  } finally {
    userLoading.value = false;
  }
};

const handleUserSelect = () => {
  pageNum.value = 1;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const clearUserFilter = () => {
  selectedUser.value = null;
  pageNum.value = 1;
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

const handleReset = () => {
  // 重置所有筛选条件
  petType.value = '';
  gender.value = '-1';
  selectedUser.value = null;
  searchKeyword.value = '';
  searchTypes.value = [];
  sortBy.value = 'relevance';
  pageNum.value = 1;
  // 刷新列表
  fetchPetsWithType(activeType.value === '-1' ? undefined : parseInt(activeType.value));
};

// 生命周期
onMounted(() => {
  // 检查 URL 查询参数中的 type 参数
  const typeParam = route.query.type as string;
  if (typeParam) {
    activeType.value = typeParam;
    if (typeParam === '2') {
      // 活动选项卡
      fetchActivityList(0);
    } else {
      // 宠物相关选项卡
      const newType = typeParam === '-1' ? undefined : parseInt(typeParam);
      fetchPetsWithType(newType);
    }
  } else {
    fetchPets();
  }
});
</script>

<style scoped>
.pets-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.pets-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.create-button {
  font-size: 14px;
  padding: 8px 16px;
}

.filter-section {
  background-color: white;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.type-tabs-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.type-tabs {
  flex: 1;
}

.create-button {
  margin-left: 16px;
  white-space: nowrap;
}

.search-sort-section {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.filter-select {
  width: 120px;
}

.user-select {
  width: 200px;
}

.user-search-container {
  position: relative;
  display: flex;
  align-items: center;
}

.clear-user-button {
  position: absolute;
  right: 10px;
  z-index: 1;
  color: #909399;
}

.user-option {
  display: flex;
  align-items: center;
  padding: 8px 0;
}

.user-info {
  margin-left: 10px;
  flex: 1;
}

.user-nickname {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.user-username {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.search-input {
  flex: 1;
  max-width: 400px;
}

.sort-select {
  width: 150px;
}

.reset-button {
  white-space: nowrap;
}

.pets-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
  margin-bottom: 24px;
  align-items: start;
}

.pet-card {
  background-color: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
  cursor: pointer;
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

.status-tag {
  position: absolute;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
  top: 12px;
  right: 12px;
}

.type-tag {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  color: white;
  margin-left: 8px;
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

.card-content {
  padding: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
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

.type-tag {
  white-space: nowrap;
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
}

.stats {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
}



.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nickname {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 活动卡片样式 */
.title-with-status {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.title-status-tag {
  border-radius: 12px;
  font-size: 12px;
  padding: 4px 12px;
  white-space: nowrap;
}

.location-time {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.location {
  display: block;
}

.time {
  display: block;
  margin-top: 2px;
}

.progress-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.progress-label {
  font-size: 14px;
  color: #606266;
}

.loading-container {
  background-color: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
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

/* 活动相关样式 */
.activity-filter-bar {
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  margin-top: 16px;
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
  .pets-container {
    padding: 16px;
  }

  .pets-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .search-sort-section {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input {
    max-width: none;
  }

  .sort-select {
    width: 100%;
  }

  .pets-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 16px;
  }

  .card-image {
    height: 150px;
  }

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
</style>
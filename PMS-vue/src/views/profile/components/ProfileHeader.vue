<template>
  <div class="profile-header">
    <div class="header-content" v-if="!isEditing">
      <div class="avatar-section">
        <el-avatar :size="80" :src="userInfo?.avatar || ''" class="avatar" @click="showFullAvatar = true">
          {{ userInfo?.nickName?.charAt(0) || '用' }}
        </el-avatar>
        <div class="stats-section">
          <span class="stat-item" @click="showFollowersDialog = true">{{ formatNumber(userInfo?.followerCount || 0) }}粉丝</span>
          <span class="stat-divider"></span>
          <span class="stat-item" @click="showFollowingDialog = true">{{ formatNumber(userInfo?.followingCount || 0) }}关注</span>
          <span class="stat-divider"></span>
          <span class="stat-item">{{ formatNumber(userInfo?.likeCount || 0) }}点赞</span>
        </div>
      </div>
      <div class="info-section">
        <h2 class="welcome-text">欢迎回来，{{ userInfo?.nickName || '用户' }}</h2>
        <p class="user-details">
          {{ userInfo?.userName }} · 注册于 {{ formatDate(userInfo?.createTime || '') }}
        </p>
      </div>
      <div class="action-section">
        <el-button type="primary" @click="emit('edit')">
          编辑资料
        </el-button>
      </div>
    </div>
    <div class="header-content editing" v-else>
      <div class="avatar-section">
        <el-avatar :size="100" :src="userInfo?.avatar || 'https://via.placeholder.com/100'" class="avatar" @click="dialogVisible = true">
          {{ userInfo?.nickName?.charAt(0) || '头' }}
        </el-avatar>
        <div class="avatar-overlay" @click="dialogVisible = true">
          <el-icon><Camera /></el-icon>
          <span>更换头像</span>
        </div>
        
        <!-- 头像操作弹窗 -->
        <el-dialog
          v-model="dialogVisible"
          title="头像设置"
          width="300px"
          center
          :modal="true"
          :close-on-click-modal="false"
        >
          <div class="avatar-dialog-content">
            <div class="button-container">
              <el-button
                type="primary"
                class="dialog-action-btn"
                @click="triggerFileInput"
              >
                <el-icon><Upload /></el-icon>
                上传头像
              </el-button>
            </div>
            <div class="button-container">
              <el-button
                class="dialog-action-btn"
                @click="viewHistoryAvatars"
              >
                <el-icon><Picture /></el-icon>
                查看历史头像
              </el-button>
            </div>
          </div>
        </el-dialog>
        
        <input
          type="file"
          ref="fileInput"
          style="display: none"
          accept="image/*"
          @change="handleFileChange"
        />
      </div>
    </div>
    
    <!-- 完整头像弹窗 -->
    <el-dialog
      v-model="showFullAvatar"
      title="头像预览"
      width="400px"
      center
      :modal="true"
    >
      <div class="full-avatar-container">
        <img
          v-if="userInfo?.avatar"
          :src="userInfo.avatar"
          class="full-avatar"
          alt="头像"
        />
        <div v-else class="no-avatar">
          <el-avatar :size="150">
            {{ userInfo?.nickName?.charAt(0) || '用' }}
          </el-avatar>
          <p style="margin-top: 16px; color: #909399;">暂无头像</p>
        </div>
      </div>
    </el-dialog>
    
    <!-- 历史头像弹窗 -->
    <el-dialog
      v-model="historyDialogVisible"
      title="历史头像"
      width="500px"
      center
      :modal="true"
    >
      <div class="history-avatars-container">
        <div v-if="historyAvatars.length > 0" class="history-avatars-grid">
          <div
            v-for="(avatar, index) in historyAvatars"
            :key="avatar.id || index"
            class="history-avatar-item"
            @click="selectHistoryAvatar(avatar)"
          >
            <img :src="avatar.avatarUrl" class="history-avatar" alt="历史头像" />
          </div>
        </div>
        <div v-else class="no-history-avatars">
          <p style="color: #909399;">暂无历史头像</p>
        </div>
      </div>
    </el-dialog>
    
    <!-- 粉丝列表弹窗 -->
    <el-dialog
      v-model="showFollowersDialog"
      title="粉丝列表"
      width="600px"
      @open="fetchFollowersList"
    >
      <el-loading v-loading="followersLoading" element-loading-text="加载中..." />
      <div v-if="followersList.length > 0" class="user-list">
        <div v-for="user in followersList" :key="user.userId" class="user-item">
          <img :src="user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="用户头像" class="user-avatar" />
          <div class="user-info">
            <div class="user-name" @click="navigateToUser(user.userId)">{{ user.nickname }}</div>
            <div class="user-username">@{{ user.username }}</div>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="暂无粉丝" />
      </div>
      <template #footer>
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="followersPageNum"
            v-model:page-size="followersPageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="followersTotal"
            @size-change="handleFollowersPageChange"
            @current-change="handleFollowersPageChange"
          />
        </div>
      </template>
    </el-dialog>
    
    <!-- 关注列表弹窗 -->
    <el-dialog
      v-model="showFollowingDialog"
      title="关注列表"
      width="600px"
      @open="fetchFollowingList"
    >
      <el-loading v-loading="followingLoading" element-loading-text="加载中..." />
      <div v-if="followingList.length > 0" class="user-list">
        <div v-for="user in followingList" :key="user.userId" class="user-item">
          <img :src="user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar&image_size=square'" alt="用户头像" class="user-avatar" />
          <div class="user-info">
            <div class="user-name" @click="navigateToUser(user.userId)">{{ user.nickname }}</div>
            <div class="user-username">@{{ user.username }}</div>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="暂无关注" />
      </div>
      <template #footer>
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="followingPageNum"
            v-model:page-size="followingPageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="followingTotal"
            @size-change="handleFollowingPageChange"
            @current-change="handleFollowingPageChange"
          />
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { formatDate, formatNumber } from '../../../utils/format';
import { ElMessage } from 'element-plus';
import { Camera, Upload, Picture, UserFilled } from '@element-plus/icons-vue';
import emitter from '../../../utils/eventBus';
import { switchToHistoryAvatar, getFollowerList, getFollowingList } from '../../../api/user';

interface UserInfo {
  userId: number;
  userName: string;
  nickName: string;
  avatar: string | null;
  createTime: string;
  followerCount?: number;
  followingCount?: number;
  likeCount?: number;
}

// 历史头像数据结构
interface HistoryAvatar {
  id: number;
  avatarUrl: string;
  updateTime: string;
}

const props = defineProps<{
  userInfo: UserInfo | null;
  isEditing?: boolean;
}>();

const emit = defineEmits<{
  (e: 'edit'): void;
  (e: 'changeAvatar', avatarUrl: string): void;
}>();

// 文件输入框引用
const fileInput = ref<HTMLInputElement | null>(null);
// 弹窗可见性
const dialogVisible = ref(false);
// 完整头像弹窗可见性
const showFullAvatar = ref(false);

// 触发文件选择
const triggerFileInput = () => {
  fileInput.value?.click();
  // 上传后关闭弹窗
  dialogVisible.value = false;
};

// 历史头像列表
const historyAvatars = ref<HistoryAvatar[]>([]);
// 历史头像弹窗可见性
const historyDialogVisible = ref(false);

// 粉丝列表弹窗
const showFollowersDialog = ref(false);
const followersList = ref<any[]>([]);
const followersTotal = ref(0);
const followersPageNum = ref(1);
const followersPageSize = ref(10);
const followersLoading = ref(false);

// 关注列表弹窗
const showFollowingDialog = ref(false);
const followingList = ref<any[]>([]);
const followingTotal = ref(0);
const followingPageNum = ref(1);
const followingPageSize = ref(10);
const followingLoading = ref(false);

// 查看历史头像
const viewHistoryAvatars = async () => {
  try {
    // 获取token
    const token = localStorage.getItem('token');
    if (!token) {
      ElMessage.error('请先登录');
      return;
    }

    // 调用历史头像接口
    const response = await fetch('/pet-system/avatar/historyAvatar', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    const result = await response.json();
    if (result.code === 200 && result.data) {
      // 处理返回的历史头像列表
      historyAvatars.value = result.data.map((item: any) => ({
        id: item.id,
        avatarUrl: item.avatarUrl.replace(/^"|"$/g, '').replace(/^\s+|\s+$/g, ''),
        updateTime: item.updateTime
      }));
      // 显示历史头像弹窗
      historyDialogVisible.value = true;
    } else {
      ElMessage.error(result.message || '获取历史头像失败');
    }
  } catch (error) {
    ElMessage.error('获取历史头像失败，请重试');
    console.error('获取历史头像失败:', error);
  } finally {
    dialogVisible.value = false;
  }
};

// 选择历史头像
const selectHistoryAvatar = async (avatar: HistoryAvatar) => {
  try {
    // 调用切换历史头像接口
    const response = await switchToHistoryAvatar(avatar.id);
    if (response.code === 200) {
      // 触发头像更新事件，通知 MainLayout 刷新用户信息
      emitter.emit('avatar-updated');
      // 头像选择成功后，通知父组件
      emit('changeAvatar', response.data);
      ElMessage.success('头像切换成功');
      // 关闭历史头像弹窗
      historyDialogVisible.value = false;
    } else {
      ElMessage.error(response.message || '切换失败');
    }
  } catch (error) {
    ElMessage.error('切换失败，请重试');
    console.error('切换历史头像失败:', error);
  }
};



// 获取粉丝列表
const fetchFollowersList = async () => {
  followersLoading.value = true;
  try {
    const response = await getFollowerList(followersPageNum.value, followersPageSize.value);
    if (response.code === 200) {
      followersList.value = response.data.records || [];
      followersTotal.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || '获取粉丝列表失败');
    }
  } catch (error) {
    ElMessage.error('获取粉丝列表失败，请重试');
    console.error('获取粉丝列表失败:', error);
  } finally {
    followersLoading.value = false;
  }
};

// 获取关注列表
const fetchFollowingList = async () => {
  followingLoading.value = true;
  try {
    const response = await getFollowingList(followingPageNum.value, followingPageSize.value);
    if (response.code === 200) {
      followingList.value = response.data.records || [];
      followingTotal.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || '获取关注列表失败');
    }
  } catch (error) {
    ElMessage.error('获取关注列表失败，请重试');
    console.error('获取关注列表失败:', error);
  } finally {
    followingLoading.value = false;
  }
};

// 处理粉丝列表分页
const handleFollowersPageChange = (current: number, size: number) => {
  followersPageNum.value = current;
  followersPageSize.value = size;
  fetchFollowersList();
};

// 处理关注列表分页
const handleFollowingPageChange = (current: number, size: number) => {
  followingPageNum.value = current;
  followingPageSize.value = size;
  fetchFollowingList();
};

// 导航到用户主页
const navigateToUser = (userId: number) => {
  window.location.href = `/user/${userId}`;
};

// 处理文件上传
const handleFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  // 验证文件
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件!');
    return;
  }
  if (file.size / 1024 / 1024 > 2) {
    ElMessage.error('图片大小不能超过2MB!');
    return;
  }

  // 创建FormData
  const formData = new FormData();
  formData.append('file', file);

  try {
    // 获取token
    const token = localStorage.getItem('token');
    if (!token) {
      ElMessage.error('请先登录');
      return;
    }

    // 上传文件
    const response = await fetch('/pet-system/avatar/upload', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`
      },
      body: formData
    });

    const result = await response.json();
    if (result.code === 200 && result.data && result.data.avatarUrl) {
      // 移除avatarUrl中的多余引号
      const avatarUrl = result.data.avatarUrl.replace(/^"|"$/g, '').replace(/^\s+|\s+$/g, '');
      // 触发头像更新事件，通知 MainLayout 刷新用户信息
      emitter.emit('avatar-updated');
      // 头像上传成功后，通知父组件
      emit('changeAvatar', avatarUrl);
      ElMessage.success('头像上传成功');
    } else {
      ElMessage.error(result.message || '上传失败');
    }
  } catch (error) {
    ElMessage.error('上传失败，请重试');
    console.error('上传失败:', error);
  } finally {
    // 清空文件输入
    if (fileInput.value) {
      fileInput.value.value = '';
    }
  }
};
</script>

<style scoped>
.profile-header {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.header-content {
  display: flex;
  align-items: center;
  gap: 24px;
}

.header-content.editing {
  justify-content: space-between;
}

.avatar-section {
  flex-shrink: 0;
}

.avatar {
  border: 3px solid #f5f7fa;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s;
}

.avatar-section {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
}

.stats-section {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  font-size: 14px;
  color: #606266;
}

.stat-item {
  display: flex;
  align-items: center;
}

.stat-divider {
  width: 1px;
  height: 12px;
  background-color: #e4e7ed;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
  cursor: pointer;
}

.avatar-section:hover .avatar-overlay {
  opacity: 1;
}

.avatar-overlay .el-icon {
  font-size: 24px;
  margin-bottom: 4px;
}

.avatar-overlay span {
  font-size: 12px;
}

.info-section {
  flex: 1;
}

.welcome-text {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px 0;
}

.user-details {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.action-section {
  flex-shrink: 0;
}

.avatar-dialog-content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 16px;
  padding: 20px 0;
  margin-left: 30px;
}

.button-container {
  background-color: #f5f7fa;
  border-radius: 8px;
  padding: 0;
  width: 100%;
  max-width: 200px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  transition: all 0.3s;
  overflow: hidden;
}

.button-container:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.dialog-action-btn {
  width: 100%;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  transition: all 0.3s;
}

.dialog-action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.full-avatar-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
}

.full-avatar {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #f5f7fa;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.no-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.history-avatars-container {
  padding: 20px 0;
}

.history-avatars-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 16px;
}

.history-avatar-item {
  cursor: pointer;
  transition: all 0.3s;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.history-avatar-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.history-avatar {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 8px;
}

.no-history-avatars {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    text-align: center;
    gap: 16px;
  }
}

/* 粉丝和关注列表样式 */
.user-list {
  max-height: 400px;
  overflow-y: auto;
  padding: 16px 0;
}

.user-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: all 0.3s;
}

.user-item:hover {
  background-color: #f5f7fa;
}

.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  margin-right: 16px;
  object-fit: cover;
}

.user-info {
  flex: 1;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
  cursor: pointer;
}

.user-name:hover {
  color: #409eff;
}

.user-username {
  font-size: 12px;
  color: #909399;
}

.pagination-container {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.empty-state {
  padding: 40px 0;
  text-align: center;
}
</style>
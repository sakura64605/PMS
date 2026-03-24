<template>
  <div class="profile-container">
    <h1 class="page-title">个人中心</h1>
    
    <!-- 顶部概览 -->
    <ProfileHeader
      :userInfo="userInfo"
      :isEditing="isEditing"
      @edit="toggleEdit"
      @changeAvatar="handleChangeAvatar"
    />
    
    <!-- 信息展示 -->
    <div class="info-list">
      <!-- 基本信息 -->
      <InfoCard
        :userInfo="userInfo"
        :isEditing="isEditing"
        :phonePublic="userInfo?.privacySettings?.phone"
        :emailPublic="userInfo?.privacySettings?.email"
        @update:form="handleFormUpdate"
        @update:phonePublic="handlePhonePrivacyUpdate"
        @update:emailPublic="handleEmailPrivacyUpdate"
      />
      
      <!-- 标签墙 -->
      <TagsCard
        :tags="tags"
        :isEditing="isEditing"
        :tagsPublic="userInfo?.privacySettings?.tags"
        @update:tags="handleTagsUpdate"
        @update:tagsPublic="handleTagsPrivacyUpdate"
      />
      
      <!-- 账号安全 -->
      <SecurityCard
        :userInfo="userInfo"
        :isEditing="isEditing"
        @changePassword="handleChangePassword"
        @update:searchable="handleSearchableUpdate"
      />
    </div>
    
    <!-- 编辑操作按钮 -->
    <div v-if="isEditing" class="edit-actions">
      <el-button type="primary" @click="saveChanges">
        保存修改
      </el-button>
      <el-button @click="cancelEdit">
        取消
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import ProfileHeader from './components/ProfileHeader.vue';
import InfoCard from './components/InfoCard.vue';
import TagsCard from './components/TagsCard.vue';
import SecurityCard from './components/SecurityCard.vue';
import { getUserInfo, updateUserInfo } from '../../api/user';

interface UserInfo {
  userId: number;
  userName: string;
  nickName: string;
  avatar: string | null;
  email: string | null;
  phone: string | null;
  gender: number;
  status: number;
  signature: string | null;
  tags: string[];
  privacySettings: {
    tags: boolean;
    phone: boolean;
    email: boolean;
  };
  searchable: number;
  role: number;
  createTime: string;
  followers?: number;
  following?: number;
  likes?: number;
}

// 用户信息
const userInfo = ref<UserInfo | null>(null);
// 编辑状态
const isEditing = ref(false);
// 标签列表
const tags = ref<string[]>([]);
// 编辑表单数据
const editForm = ref({
  nickName: '',
  gender: 0,
  signature: '',
  email: ''
});

// 页面加载时获取用户信息
onMounted(async () => {
  await fetchUserInfo();
});

// 获取用户信息
const fetchUserInfo = async () => {
  try {
    const response = await getUserInfo();
    userInfo.value = response.data;
    tags.value = response.data.tags || [];
  } catch (error) {
    ElMessage.error('获取用户信息失败');
    console.error('获取用户信息失败:', error);
  }
};

// 切换编辑状态
const toggleEdit = () => {
  isEditing.value = true;
};

// 取消编辑
const cancelEdit = () => {
  isEditing.value = false;
  // 重置编辑表单
  if (userInfo.value) {
    editForm.value = {
      nickName: userInfo.value.nickName || '',
      gender: userInfo.value.gender || 0,
      signature: userInfo.value.signature || '',
      email: userInfo.value.email || ''
    };
  }
};

// 处理表单更新
const handleFormUpdate = (form: any) => {
  editForm.value = form;
};

// 处理标签更新
const handleTagsUpdate = (newTags: string[]) => {
  tags.value = newTags;
};

// 处理手机隐私设置更新
const handlePhonePrivacyUpdate = (value: boolean) => {
  if (userInfo.value) {
    userInfo.value.privacySettings.phone = value;
  }
};

// 处理邮箱隐私设置更新
const handleEmailPrivacyUpdate = (value: boolean) => {
  if (userInfo.value) {
    userInfo.value.privacySettings.email = value;
  }
};

// 处理搜索设置更新
const handleSearchableUpdate = (value: number) => {
  if (userInfo.value) {
    userInfo.value.searchable = value;
  }
};

// 处理头像更换
const handleChangeAvatar = (avatarUrl: string) => {
  console.log('handleChangeAvatar被调用，新的avatarUrl:', avatarUrl);
  if (userInfo.value) {
    console.log('更新userInfo.avatar:', avatarUrl);
    userInfo.value.avatar = avatarUrl;
    console.log('更新后的userInfo.avatar:', userInfo.value.avatar);
    // 更新localStorage中的userInfo，确保右上角头像同步更新
    const storedUserInfo = localStorage.getItem('userInfo');
    if (storedUserInfo) {
      const parsedUserInfo = JSON.parse(storedUserInfo);
      parsedUserInfo.avatar = avatarUrl;
      localStorage.setItem('userInfo', JSON.stringify(parsedUserInfo));
      console.log('更新localStorage中的userInfo.avatar:', avatarUrl);
    }
  }
};

// 处理标签隐私设置更新
const handleTagsPrivacyUpdate = (value: boolean) => {
  if (userInfo.value) {
    userInfo.value.privacySettings.tags = value;
  }
};

// 保存修改
const saveChanges = async () => {
  try {
    if (!userInfo.value) return;
    
    // 准备更新数据
    const updateData = {
      nickName: editForm.value.nickName,
      signature: editForm.value.signature || '',
      gender: Number(editForm.value.gender),
      email: editForm.value.email || '',
      avatar: userInfo.value.avatar,
      tags: tags.value,
      privacySettings: {
        tags: userInfo.value.privacySettings.tags,
        phone: userInfo.value.privacySettings.phone,
        email: userInfo.value.privacySettings.email
      },
      searchable: userInfo.value.searchable || 0
    };
    
    // 调用更新用户信息的接口
    const response = await updateUserInfo(updateData);
    
    ElMessage.success('保存成功');
    isEditing.value = false;
    // 重新获取用户信息
    await fetchUserInfo();
  } catch (error) {
    ElMessage.error('保存失败');
    console.error('保存失败:', error);
  }
};

// 处理修改密码
const handleChangePassword = () => {
  // 跳转到修改密码页面
  // 由于页面尚未实现，这里仅做提示
  ElMessage.info('修改密码功能即将开放');
};
</script>

<style scoped>
.profile-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin-bottom: 24px;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.edit-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 32px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .profile-container {
    padding: 16px;
  }
  
  .info-grid {
    grid-template-columns: 1fr;
  }
  
  .page-title {
    font-size: 20px;
  }
}
</style>
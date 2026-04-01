<template>
  <div class="notice-detail">
    <el-container>
      <el-header height="60px">
        <div class="header">
          <el-button type="primary" @click="goBack">返回</el-button>
          <h1>公告详情</h1>
        </div>
      </el-header>
      <el-main>
        <el-card v-if="notice" class="notice-card">
          <template #header>
            <div class="card-header">
              <h2 :class="['notice-title', { 'top': notice.isTop === 1 }]">
                {{ notice.title }}
                <span v-if="notice.isTop === 1" class="top-tag">置顶</span>
              </h2>
              <span :class="['notice-type', getNoticeTypeClass(notice.type)]">
                {{ getNoticeTypeText(notice.type) }}
              </span>
            </div>
          </template>
          <div class="notice-meta">
            <span class="notice-time">{{ notice.publishTime }}</span>
            <span v-if="notice.priority > 0" :class="['priority-tag', getPriorityClass(notice.priority)]">
              {{ getPriorityText(notice.priority) }}
            </span>
          </div>
          <div class="notice-content" v-html="notice.content"></div>
        </el-card>
        <div v-else class="loading">
          <el-spinner size="large" />
          <p>加载中...</p>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getNoticeDetail } from '../../api/notice'

const router = useRouter()
const route = useRoute()
const notice = ref<any>(null)

onMounted(() => {
  const id = Number(route.params.id)
  if (id) {
    fetchNoticeDetail(id)
  }
})

// 获取公告详情
const fetchNoticeDetail = async (id: number) => {
  try {
    const response = await getNoticeDetail(id)
    if (response.code === 200) {
      notice.value = response.data
    }
  } catch (error) {
    console.error('获取公告详情失败:', error)
    ElMessage.error('获取公告详情失败，请稍后重试')
  }
}

// 返回上一页
const goBack = () => {
  router.back()
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
.notice-detail {
  height: 100vh;
  background-color: #f5f7fa;
}

.header {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 20px;
  background-color: #409eff;
  color: white;
}

.header h1 {
  font-size: 20px;
  margin: 0 0 0 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.notice-title {
  font-size: 20px;
  font-weight: 500;
  margin: 0;
  flex: 1;
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
  margin: 10px 0 20px;
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

.notice-content {
  padding: 20px 0;
  line-height: 1.6;
  font-size: 16px;
}

.notice-content p {
  margin: 10px 0;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  gap: 20px;
  color: #909399;
}
</style>
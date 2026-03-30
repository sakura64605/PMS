<template>
  <div class="activity-edit-container">
    <div class="page-header">
      <el-button type="info" @click="navigateBack">返回</el-button>
      <h2>编辑活动</h2>
    </div>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
      class="activity-form"
    >
      <!-- 标题 -->
      <el-form-item label="活动标题" prop="title">
        <el-input
          v-model="formData.title"
          placeholder="请输入活动标题"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <!-- 活动内容 -->
      <el-form-item label="活动内容" prop="content">
        <el-input
          v-model="formData.content"
          type="textarea"
          placeholder="请输入活动内容"
          :rows="3"
        />
      </el-form-item>

      <!-- 活动地点 -->
      <el-form-item label="活动地点" prop="location">
        <el-input
          v-model="formData.location"
          placeholder="请输入活动地点"
        />
      </el-form-item>

      <!-- 人数限制 -->
      <el-form-item label="人数限制" prop="maxPeople">
        <el-input-number
          v-model="formData.maxPeople"
          :min="1"
          placeholder="请输入人数限制"
          style="width: 100%"
        />
      </el-form-item>

      <!-- 开始时间 -->
      <el-form-item label="开始时间" prop="startTime">
        <el-date-picker
          v-model="formData.startTime"
          type="datetime"
          placeholder="选择开始时间"
          style="width: 100%"
          :disabled-date="disabledDate"
        />
      </el-form-item>

      <!-- 结束时间 -->
      <el-form-item label="结束时间" prop="endTime">
        <el-date-picker
          v-model="formData.endTime"
          type="datetime"
          placeholder="选择结束时间"
          style="width: 100%"
          :disabled-date="(time) => disabledEndDate(time, formData.startTime)"
        />
      </el-form-item>

      <!-- 提交按钮 -->
      <el-form-item>
        <div class="form-actions">
          <el-button @click="navigateBack">取消</el-button>
          <el-button type="primary" @click="submitForm">保存</el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getActivityDetail, updateActivity } from '../../api/activity'

const router = useRouter()
const route = useRoute()
const formRef = ref()

// 表单数据
const formData = reactive({
  id: 0,
  title: '',
  content: '',
  images: [] as string[],
  location: '',
  maxPeople: 1,
  startTime: '',
  endTime: ''
})

// 表单验证规则
const rules = {
  title: [
    { required: true, message: '请输入活动标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过100字', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入活动内容', trigger: 'blur' }
  ],
  location: [
    { required: true, message: '请输入活动地点', trigger: 'blur' }
  ],
  maxPeople: [
    { required: true, message: '请输入人数限制', trigger: 'blur' },
    { type: 'number', min: 1, message: '人数限制至少为1', trigger: 'blur' }
  ],
  startTime: [
    { required: true, message: '请选择开始时间', trigger: 'change' }
  ],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' }
  ]
}

// 禁用日期（只能选择今天及以后的日期）
const disabledDate = (time: Date) => {
  return time.getTime() < Date.now() - 8.64e7 // 8.64e7是一天的毫秒数
}

// 禁用结束日期（只能选择开始时间之后的日期）
const disabledEndDate = (time: Date, startTime: string) => {
  if (!startTime) return false
  return time.getTime() < new Date(startTime).getTime()
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    await updateActivity(formData.id, formData)
    ElMessage.success('活动更新成功')
    const from = route.query.from as string
    console.log('submitForm - from:', from)
    if (from && ['published', 'joined', 'adoption', 'myActivities'].includes(from)) {
      console.log('submitForm - going to my-posts with tab:', from)
      router.push({ path: '/pets/my-posts', query: { tab: 'activities' } })
    } else {
      console.log('submitForm - going to activity detail')
      router.push({ path: `/activities/${formData.id}`, query: { from: 'edit' } })
    }
  } catch (error) {
    console.error('更新活动失败:', error)
  }
}

// 返回详情页或我的活动页面
const navigateBack = () => {
  const from = route.query.from as string
  console.log('navigateBack - from:', from)
  if (from && ['published', 'joined', 'adoption', 'myActivities'].includes(from)) {
    console.log('navigateBack - going to my-posts with tab:', from)
    router.push({ path: '/pets/my-posts', query: { tab: 'activities' } })
  } else if (formData.id) {
    console.log('navigateBack - going to activity detail')
    router.push(`/activities/${formData.id}`)
  } else {
    console.log('navigateBack - going to activities')
    router.push('/activities')
  }
}

// 获取活动详情
const fetchActivityDetail = async () => {
  const id = Number(route.params.id)
  console.log('edit.vue mounted, id:', id)
  
  if (!id) {
    ElMessage.error('活动ID不存在')
    router.push('/activities')
    return
  }
  
  try {
    const response = await getActivityDetail(id)
    console.log('API 响应:', response)
    
    // 填充表单数据
    const data = response.data
    formData.id = data.id
    formData.title = data.title
    formData.content = data.content
    formData.images = data.images || []
    formData.location = data.location
    formData.maxPeople = data.maxPeople
    formData.startTime = data.startTime
    formData.endTime = data.endTime
  } catch (error) {
    ElMessage.error('获取活动详情失败')
    console.error('API 错误:', error)
  }
}

// 页面挂载时获取数据
onMounted(() => {
  fetchActivityDetail()
})
</script>

<style scoped>
.activity-edit-container {
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  margin-bottom: 30px;
}

.page-header h2 {
  margin: 0 0 0 20px;
  font-size: 24px;
  font-weight: 600;
}

.activity-form {
  background-color: #fff;
  padding: 30px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .activity-form {
    padding: 20px;
  }
  
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .page-header h2 {
    margin: 0;
  }
}
</style>
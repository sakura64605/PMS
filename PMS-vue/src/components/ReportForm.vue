<template>
  <el-dialog
    v-model="dialogVisible"
    title="提交举报"
    width="400px"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
      <el-form-item label="举报原因" prop="reasons">
        <el-select v-model="form.reasons" multiple placeholder="请选择举报原因" style="width: 100%">
          <el-option
            v-for="reason in reportReasons"
            :key="reason.value"
            :label="reason.label"
            :value="reason.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="其他原因" prop="otherReason">
        <el-input
          v-model="form.otherReason"
          placeholder="如果以上原因不适用，请输入其他原因"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitReport" :loading="loading">
          提交举报
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue';
import { submitReport } from '../api/report';
import { ElMessage } from 'element-plus';

// Props
const props = defineProps<{
  visible: boolean;
  targetType: string;
  targetId: number;
}>();

// Emits
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
  (e: 'success'): void;
}>();

// 表单状态
const dialogVisible = ref(props.visible);
const formRef = ref();
const loading = ref(false);

// 表单数据
const form = reactive({
  reasons: [] as string[],
  otherReason: ''
});

// 表单规则
const rules = {
  reasons: [
    { required: true, message: '请选择举报原因', trigger: 'change' }
  ]
};

// 举报原因选项
const reportReasons = [
  { value: '色情低俗', label: '色情低俗' },
  { value: '暴力血腥', label: '暴力血腥' },
  { value: '虚假信息', label: '虚假信息' },
  { value: '诈骗信息', label: '诈骗信息' },
  { value: '侵犯隐私', label: '侵犯隐私' },
  { value: '垃圾广告', label: '垃圾广告' },
  { value: '人身攻击', label: '人身攻击' },
  { value: '其他', label: '其他' }
];

// 监听 visible 变化
watch(() => props.visible, (newValue) => {
  dialogVisible.value = newValue;
});

// 监听 dialogVisible 变化
watch(dialogVisible, (newValue) => {
  emit('update:visible', newValue);
  if (!newValue) {
    resetForm();
  }
});

// 重置表单
const resetForm = () => {
  form.reasons = [];
  form.otherReason = '';
  if (formRef.value) {
    formRef.value.resetFields();
  }
};

// 提交举报
const handleSubmitReport = async () => {
  if (!formRef.value) return;
  
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true;
      try {
        // 合并举报原因
        let reason = form.reasons.join('; ');
        if (form.otherReason) {
          reason += (reason ? '; ' : '') + form.otherReason;
        }
        
        await submitReport({
          targetType: props.targetType,
          targetId: props.targetId,
          reason: reason
        });
        ElMessage.success('举报成功，我们会尽快处理');
        dialogVisible.value = false;
        emit('success');
      } catch (error: any) {
        ElMessage.error(error.message || '举报失败');
      } finally {
        loading.value = false;
      }
    }
  });
};
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

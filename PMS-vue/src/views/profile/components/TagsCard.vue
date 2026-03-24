<template>
  <div class="info-card">
    <div class="card-title">
      <span>标签墙</span>
      <el-input
        v-if="isEditing"
        v-model="newTag"
        placeholder="输入标签后回车添加"
        @keyup.enter="handleAddTag"
        class="tag-input"
        size="small"
      />
      <div v-if="isEditing" class="card-title-right">
        <span class="privacy-label">公开：</span>
        <el-switch
          v-model="tagsPublic"
          @change="handlePrivacyChange"
        />
      </div>
    </div>
    <div class="tags-container">
      <el-tag
        v-for="(tag, index) in tags"
        :key="index"
        :closable="isEditing"
        :disable-transitions="false"
        @close="handleTagClose(index)"
        class="tag-item"
      >
        {{ tag }}
      </el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

const props = defineProps<{
  tags: string[];
  isEditing: boolean;
  tagsPublic?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:tags', tags: string[]): void;
  (e: 'update:tagsPublic', value: boolean): void;
}>();

const localTags = ref<string[]>([]);
const newTag = ref('');
const tagsPublic = ref(props.tagsPublic || false);

// 监听tags变化，更新本地标签
watch(
  () => props.tags,
  (newVal) => {
    localTags.value = [...newVal];
  },
  { immediate: true }
);

// 监听tagsPublic变化
watch(
  () => props.tagsPublic,
  (newVal) => {
    if (newVal !== undefined) {
      tagsPublic.value = newVal;
    }
  },
  { immediate: true }
);

// 处理标签关闭
const handleTagClose = (index: number) => {
  localTags.value.splice(index, 1);
  emit('update:tags', localTags.value);
};

// 处理添加标签
const handleAddTag = () => {
  const tag = newTag.value.trim();
  if (tag && !localTags.value.includes(tag)) {
    localTags.value.push(tag);
    newTag.value = '';
    emit('update:tags', localTags.value);
  }
};

// 处理标签公开设置变化
const handlePrivacyChange = () => {
  emit('update:tagsPublic', tagsPublic.value);
};
</script>

<style scoped>
.info-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #333;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-title-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.privacy-label {
  font-size: 14px;
  font-weight: normal;
  color: #606266;
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.tag-item {
  margin-bottom: 8px;
}

.tag-input {
  width: 200px;
  flex-shrink: 0;
}

.card-title-right {
  margin-left: auto;
}
</style>
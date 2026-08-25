import { ref, computed } from 'vue';
import { ElMessage } from 'element-plus';

export function useImageUpload(options: {
  images: string[];
  fileList?: { value: any[] };
  maxCount?: number;
}) {
  const { images, fileList, maxCount = 9 } = options;

  const previewVisible = ref(false);
  const previewImage = ref('');

  const uploadHeaders = computed(() => ({
    'Authorization': `Bearer ${localStorage.getItem('token')}`
  }));

  const handleUploadSuccess = (response: any, file: any) => {
    if (response.code === 200 && response.data?.avatarUrl) {
      const imageUrl = response.data.avatarUrl.replace(/^"|"$/g, '');
      images.push(imageUrl);
      file.url = imageUrl;
      ElMessage.success('图片上传成功');
    } else {
      ElMessage.error(response.message || '上传失败');
      if (fileList) {
        const idx = fileList.value.findIndex(f => f.uid === file.uid);
        if (idx !== -1) fileList.value.splice(idx, 1);
      }
    }
  };

  const handleUploadError = (_error: any, file: any) => {
    ElMessage.error('上传失败，请重试');
    if (fileList) {
      const idx = fileList.value.findIndex(f => f.uid === file.uid);
      if (idx !== -1) fileList.value.splice(idx, 1);
    }
  };

  const handleRemove = (file: any) => {
    const urlIndex = images.findIndex(img => img === file.url);
    if (urlIndex !== -1) images.splice(urlIndex, 1);
    if (fileList) {
      const idx = fileList.value.findIndex(f => f.uid === file.uid);
      if (idx !== -1) fileList.value.splice(idx, 1);
    }
  };

  const handleExceed = () => {
    ElMessage.error(`最多上传${maxCount}张图片`);
  };

  const beforeUpload = (file: any) => {
    const isImage = file.type.startsWith('image/');
    if (!isImage) {
      ElMessage.error('只能上传图片文件');
      return false;
    }
    const isLt2M = file.size / 1024 / 1024 < 2;
    if (!isLt2M) {
      ElMessage.error('图片大小不能超过2MB');
      return false;
    }
    return true;
  };

  const handlePreview = (file: any) => {
    previewImage.value = file.url || file.response?.data?.avatarUrl;
    previewVisible.value = true;
  };

  return {
    uploadHeaders,
    handleUploadSuccess,
    handleUploadError,
    handleRemove,
    handleExceed,
    beforeUpload,
    handlePreview,
    previewVisible,
    previewImage,
  };
}
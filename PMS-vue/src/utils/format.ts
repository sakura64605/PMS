// 手机号脱敏
export const maskPhone = (phone: string | null): string => {
  if (!phone) return '未设置';
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
};

// 邮箱脱敏
export const maskEmail = (email: string | null): string => {
  if (!email) return '未设置';
  const [name, domain] = email.split('@');
  const maskedName = name.charAt(0) + '***' + name.charAt(name.length - 1);
  return maskedName + '@' + domain;
};

// 性别显示
export const getGenderText = (gender: number): string => {
  const map: Record<number, string> = {0: '未知', 1: '男', 2: '女'};
  return map[gender] || '未知';
};

// 日期格式化
export const formatDate = (dateStr: string): string => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return `${date.getFullYear()}-${date.getMonth()+1}-${date.getDate()}`;
};
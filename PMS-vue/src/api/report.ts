import request from '../utils/request';

// 提交举报
export const submitReport = (data: {
  targetType: string;
  targetId: number;
  reason: string;
}) => {
  return request({
    url: '/report/submit',
    method: 'post',
    data
  });
};

// 获取举报列表（管理员）
export const getReportList = (params: {
  status?: number;
  targetType?: string;
  pageNum?: number;
  pageSize?: number;
}) => {
  return request({
    url: '/report/list',
    method: 'get',
    params
  });
};

// 处理举报（管理员）
export const handleReport = (id: number, data: {
  status: number;
  handleResult: string;
}) => {
  return request({
    url: `/report/handle/${id}`,
    method: 'post',
    params: data
  });
};

// 获取举报详情（管理员）
export const getReportDetail = (id: number) => {
  return request({
    url: `/report/detail/${id}`,
    method: 'get'
  });
};

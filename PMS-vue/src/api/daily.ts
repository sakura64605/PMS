import request from '../utils/request';

// 发表日记
export const publishDaily = (data: any) => {
  return request({
    url: '/daily/publish',
    method: 'post',
    data
  });
};

// 获取推荐动态（Feed流）
export const getDailyFeed = (params: any) => {
  return request({
    url: '/daily/feed',
    method: 'get',
    params
  });
};

// 获取帖子详情
export const getDailyDetail = (id: number) => {
  return request({
    url: `/daily/${id}`,
    method: 'get'
  });
};

// 点赞/取消点赞
export const likeDaily = (id: number) => {
  return request({
    url: `/daily/${id}/like`,
    method: 'post'
  });
};

// 删除帖子
export const deleteDaily = (id: number) => {
  return request({
    url: `/daily/${id}`,
    method: 'delete'
  });
};

// 记录用户行为（前端埋点）
export const recordDailyAction = (params: any) => {
  return request({
    url: '/daily/action',
    method: 'post',
    params
  });
};

// 获取热门话题
export const getHotTopics = (params: any) => {
  return request({
    url: '/daily/topics/hot',
    method: 'get',
    params
  });
};

// 搜索话题
export const searchTopics = (params: any) => {
  return request({
    url: '/daily/topics/search',
    method: 'get',
    params
  });
};

// 创建话题（管理员权限）
export const createTopic = (params: any) => {
  return request({
    url: '/daily/topics/create',
    method: 'post',
    params
  });
};
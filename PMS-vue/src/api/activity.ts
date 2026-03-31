import request from '../utils/request'

// 活动列表
export const getActivityList = (params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: number
  location?: string
  orderBy?: string
  order?: string
}) => {
  return request({
    url: '/activity/list',
    method: 'get',
    params
  })
}

// 活动详情
export const getActivityDetail = (id: number) => {
  return request({
    url: `/activity/detail/${id}`,
    method: 'get'
  })
}

// 发布活动
export const createActivity = (data: any) => {
  return request({
    url: '/activity/post',
    method: 'post',
    data
  })
}

// 编辑活动
export const updateActivity = (id: number, data: any) => {
  return request({
    url: `/activity/update/${id}`,
    method: 'post',
    data
  })
}

// 删除活动
export const deleteActivity = (id: number) => {
  return request({
    url: `/activity/delete/${id}`,
    method: 'post'
  })
}

// 报名活动
export const signupActivity = (data: { activityId: number; realName: string; phone: string; remark?: string }) => {
  return request({
    url: '/activity/signUp',
    method: 'post',
    data
  })
}

// 取消报名
export const cancelSignup = (id: number) => {
  return request({
    url: `/activity/cancelSignUp/${id}`,
    method: 'post'
  })
}

// 获取我的活动列表
export const getMyActivityList = (params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: number
  location?: string
  orderBy?: string
  order?: string
}) => {
  return request({
    url: '/activity/myActivity',
    method: 'get',
    params
  })
}

// 获取活动回收站列表
export const getActivityRecycleList = (params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: number
  location?: string
  orderBy?: string
  order?: string
}) => {
  return request({
    url: '/activity/recycle-bin',
    method: 'get',
    params
  })
}

// 恢复活动
export const recoverActivity = (id: number) => {
  return request({
    url: `/activity/recover/${id}`,
    method: 'post'
  })
}

// 彻底删除活动
export const deleteActivityReally = (id: number) => {
  return request({
    url: `/activity/deleteReally/${id}`,
    method: 'post'
  })
}

// 获取活动报名人列表
export const getActivitySignUpList = (id: number, params: {
  pageNum?: number
  pageSize?: number
}) => {
  return request({
    url: `/activity/signUpList/${id}`,
    method: 'post',
    params
  })
}

// 活动签到
export const signInActivity = (activityId: number, userId: number) => {
  // 确保参数是有效的数字
  if (!activityId || !userId || isNaN(activityId) || isNaN(userId)) {
    return Promise.reject(new Error('无效的参数'));
  }
  return request({
    url: `/activity/signIn?activityId=${activityId}&userId=${userId}`,
    method: 'post'
  })
}

// 获取评论列表
export const getCommentList = (params: {
  targetType: string
  targetId: number
  pageNum?: number
  pageSize?: number
}) => {
  return request({
    url: '/comment/list',
    method: 'get',
    params
  })
}

// 创建评论
export const createComment = (data: {
  targetType: string
  targetId: number
  content: string
  parentId?: number
  replyTo?: number
}) => {
  return request({
    url: '/comment/create',
    method: 'post',
    data
  })
}
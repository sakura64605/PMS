import request from '../utils/request'

// 公告类型
export enum NoticeType {
  SYSTEM = 1, // 系统公告
  ACTIVITY = 2, // 活动通知
  IMPORTANT = 3 // 重要提醒
}

// 公告优先级
export enum NoticePriority {
  NORMAL = 0, // 普通
  IMPORTANT = 1, // 重要
  URGENT = 2 // 紧急
}

// 公告状态
export enum NoticeStatus {
  DRAFT = 0, // 草稿
  PUBLISHED = 1, // 已发布
  OFFLINE = 2 // 已下线
}

// 公告列表（用户端）
export const getNoticeList = (params: {
  pageNum?: number
  pageSize?: number
}) => {
  return request({
    url: '/notice/list',
    method: 'get',
    params
  })
}

// 公告详情（用户端）
export const getNoticeDetail = (id: number) => {
  return request({
    url: `/notice/${id}`,
    method: 'get'
  })
}

// 未读公告数量
export const getUnreadCount = () => {
  return request({
    url: '/notice/unread-count',
    method: 'get'
  })
}

// 公告列表（管理员）
export const getAdminNoticeList = (params: {
  pageNum?: number
  pageSize?: number
  status?: number
  keyword?: string
}) => {
  return request({
    url: '/notice/admin/list',
    method: 'get',
    params
  })
}

// 创建公告（管理员）
export const createNotice = (data: {
  title: string
  content: string
  type?: number
  priority?: number
  isTop?: number
  schedulePublishTime?: string
}) => {
  return request({
    url: '/notice/admin/create',
    method: 'post',
    data
  })
}

// 更新公告（管理员）
export const updateNotice = (data: {
  id: number
  title: string
  content: string
  type?: number
  priority?: number
  isTop?: number
  publishTime?: string
  expireTime?: string
}) => {
  return request({
    url: '/notice/admin/update',
    method: 'put',
    data
  })
}

// 删除公告（管理员）
export const deleteNotice = (id: number) => {
  return request({
    url: `/notice/admin/${id}`,
    method: 'delete'
  })
}

// 发布公告（管理员）
export const publishNotice = (id: number) => {
  return request({
    url: `/notice/admin/${id}/publish`,
    method: 'put'
  })
}

// 下架公告（管理员）
export const unpublishNotice = (id: number) => {
  return request({
    url: `/notice/admin/${id}/unpublish`,
    method: 'put'
  })
}

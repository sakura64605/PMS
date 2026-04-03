import request from '../utils/request'

// 获取未读消息数量
export const getUnreadCount = () => {
  return request({
    url: '/message/unread-count',
    method: 'get'
  })
}

// 获取消息列表
export const getMessageList = (params: {
  pageNum: number
  pageSize: number
  type?: string
}) => {
  return request({
    url: '/message/list',
    method: 'get',
    params
  })
}

// 单条消息标记已读
export const markMessageAsRead = (id: number) => {
  return request({
    url: `/message/read/${id}`,
    method: 'put'
  })
}

// 全部标记已读
export const markAllMessagesAsRead = (type?: string) => {
  return request({
    url: '/message/read-all',
    method: 'put',
    params: { type }
  })
}

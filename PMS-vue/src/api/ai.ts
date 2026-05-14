import request from '../utils/request'

// 创建会话
export const createSession = () => {
  return request({
    url: '/ai/session/create',
    method: 'post'
  })
}

// 获取用户会话列表
export const getUserSessions = () => {
  return request({
    url: '/ai/sessions',
    method: 'get'
  })
}

// 删除会话
export const deleteSession = (sessionId: string) => {
  return request({
    url: `/ai/session/${sessionId}`,
    method: 'delete'
  })
}

// 发送消息
export const sendChatMessage = (data: {
  sessionId?: string
  message: string
}) => {
  return request({
    url: '/ai/chat',
    method: 'post',
    data
  })
}

// 获取聊天历史
export const getChatHistory = (sessionId: string) => {
  return request({
    url: `/ai/history/${sessionId}`,
    method: 'get'
  })
}

// 清除会话记忆
export const clearMemory = (sessionId: string) => {
  return request({
    url: `/ai/memory/${sessionId}`,
    method: 'delete'
  })
}

// 转接人工客服
export const transferToHuman = (sessionId: string, reason?: string) => {
  return request({
    url: '/ai/transfer',
    method: 'post',
    params: { sessionId, reason }
  })
}

// 提交反馈
export const submitFeedback = (messageId: string, score: number) => {
  return request({
    url: '/ai/feedback',
    method: 'post',
    params: { messageId, score }
  })
}

// 获取建议问题
export const getSuggestions = () => {
  return request({
    url: '/ai/suggestions',
    method: 'get'
  })
}

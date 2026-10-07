import request from '../utils/request'

// 用户长期记忆：与 AI 对话自动抽取的记忆共用同一套数据

export interface UserMemory {
  id: number
  userId: number
  content: string
  sourceSession?: string
  importance?: number
  status?: number
  createdAt?: string
  updatedAt?: string
}

// 查看我的长期记忆列表
export const listMemories = () => {
  return request({
    url: '/ai/memory',
    method: 'get'
  })
}

// 手动添加一条长期记忆
export const addMemory = (content: string) => {
  return request({
    url: '/ai/memory',
    method: 'post',
    data: { content }
  })
}

// 修改一条长期记忆（内容/重要度）
export const updateMemory = (id: number, data: { content?: string; importance?: number }) => {
  return request({
    url: `/ai/memory/${id}`,
    method: 'put',
    data
  })
}

// 删除一条长期记忆（软删）
export const deleteMemory = (id: number) => {
  return request({
    url: `/ai/memory/${id}`,
    method: 'delete'
  })
}

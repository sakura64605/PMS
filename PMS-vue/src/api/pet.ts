import request from '../utils/request'

// 宠物列表
export const getPetList = (params: {
  type?: number
  keyword?: string
  orderBy?: string
  order?: string
  pageNum?: number
  pageSize?: number
}) => {
  return request({
    url: '/pet/list',
    method: 'get',
    params
  })
}

// 宠物详情
export const getPetDetail = (id: number) => {
  return request({
    url: `/pet/${id}`,
    method: 'get'
  })
}

// 发布宠物
export const createPet = (data: any) => {
  return request({
    url: '/pet/post',
    method: 'post',
    data
  })
}

// 编辑宠物
export const updatePet = (data: any) => {
  return request({
    url: '/pet/update',
    method: 'post',
    data
  })
}

// 下架宠物
export const offlinePet = (id: number) => {
  return request({
    url: '/pet/offline',
    method: 'post',
    params: { id }
  })
}

// 删除宠物（移至回收站）
export const deletePet = (id: number) => {
  return request({
    url: '/pet/delete',
    method: 'post',
    params: { id }
  })
}

// 彻底删除宠物
export const deletePetReally = (id: number) => {
  return request({
    url: '/pet/delete-really',
    method: 'delete',
    params: { id }
  })
}

// 恢复宠物
export const recoverPet = (id: number) => {
  return request({
    url: '/pet/recover',
    method: 'post',
    params: { id }
  })
}

// 获取回收站列表
export const getRecycleList = (params: {
  pageNum?: number
  pageSize?: number
}) => {
  return request({
    url: '/pet/recycle-bin',
    method: 'get',
    params
  })
}

// 我的发布
export const getMyPosts = (params: {
  pageNum?: number
  pageSize?: number
}) => {
  return request({
    url: '/pet/my-posts',
    method: 'get',
    params
  })
}

// 待审核列表
export const getPendingList = (params: {
  pageNum?: number
  pageSize?: number
}) => {
  return request({
    url: '/pet/pending-list',
    method: 'get',
    params
  })
}

// 审核通过
export const acceptPet = (id: number) => {
  return request({
    url: '/admin/pet_post/accept',
    method: 'post',
    params: {
      id
    }
  })
};

// 审核拒绝
export const rejectPet = (id: number, reason: string) => {
  return request({
    url: '/admin/pet_post/reject',
    method: 'post',
    params: {
      id,
      reason
    }
  })
};

// 点赞
export const likePet = (id: number) => {
  return request({
    url: '/like',
    method: 'post',
    data: {
      targetId: id,
      targetType: 'pet_post'
    }
  })
};

// 收藏
export const collectPet = (id: number) => {
  return request({
    url: `/pet/${id}/collect`,
    method: 'post'
  })
};

// 我的收藏
export const getCollections = (params: {
  pageNum?: number
  pageSize?: number
}) => {
  return request({
    url: '/pet/favoriteList',
    method: 'get',
    params
  })
};
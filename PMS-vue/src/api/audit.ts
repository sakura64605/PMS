import request from '../utils/request'

// 获取待审核列表
export const getAuditList = (params: {
  targetType?: string
  keyword?: string
  dateRange?: string
  pageNum?: number
  pageSize?: number
}) => {
  return request({
    url: '/admin/audit/pending',
    method: 'get',
    params
  })
}

// 批量审核通过
export const batchApproveAudit = (targetType: string, ids: number[]) => {
  return request({
    url: `/admin/audit/batch-approve?targetType=${targetType}`,
    method: 'post',
    data: { ids }
  })
}

// 批量审核拒绝
export const batchRejectAudit = (targetType: string, ids: number[], rejectReason: string) => {
  return request({
    url: `/admin/audit/batch-reject?targetType=${targetType}`,
    method: 'post',
    data: { ids, rejectReason }
  })
}

// 获取审核历史
export const getAuditHistory = (params: {
  targetType?: string
  keyword?: string
  auditStatus?: number
  pageNum?: number
  pageSize?: number
}) => {
  return request({
    url: '/admin/audit/history',
    method: 'get',
    params
  })
}

// 获取审核详情
export const getAuditDetail = (params: {
  targetType: string
  id: number
}) => {
  return request({
    url: '/admin/audit/detail',
    method: 'get',
    params
  })
}

// 审核通过（单条，通过批量接口实现）
export const approveAudit = (targetType: string, targetId: number) => {
  return request({
    url: `/admin/audit/batch-approve?targetType=${targetType}`,
    method: 'post',
    data: { ids: [targetId] }
  })
}

// 审核拒绝（单条，通过批量接口实现）
export const rejectAudit = (targetType: string, targetId: number, reason: string) => {
  return request({
    url: `/admin/audit/batch-reject?targetType=${targetType}`,
    method: 'post',
    data: { ids: [targetId], rejectReason: reason }
  })
}

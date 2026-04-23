import request from '../utils/request'

// 获取统计概览
export const getOverviewStatistics = (params: {
  startDate: string
  endDate: string
  period: string // day, week, month, year
}) => {
  return request({
    url: '/admin/statistics/overview',
    method: 'get',
    params
  })
}

// 获取实时统计数据
export const getRealtimeStatistics = () => {
  return request({
    url: '/admin/statistics/realtime',
    method: 'get'
  })
}

// 补录统计数据
export const regenerateStatistics = (date: string) => {
  return request({
    url: '/admin/statistics/regenerate',
    method: 'post',
    params: { date }
  })
}

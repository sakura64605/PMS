import request from './request'

// 获取统计概览
export const getOverviewStatistics = (params: {
  startDate: string
  endDate: string
  period: string // day, week, month, year
}) => {
  return request({
    url: '/statistics/overview',
    method: 'get',
    params
  })
}

// 获取实时统计数据
export const getRealtimeStatistics = () => {
  return request({
    url: '/statistics/realtime',
    method: 'get'
  })
}

// 获取补录统计数据
export const getBackfillStatistics = (params: {
  startDate: string
  endDate: string
  period: string // day, week, month, year
}) => {
  return request({
    url: '/statistics/backfill',
    method: 'get',
    params
  })
}

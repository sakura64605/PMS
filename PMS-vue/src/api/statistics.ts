import request from '../utils/request'

/**
 * 获取统计数据
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getStatistics(params: {
  type: string // daily, weekly, monthly, yearly, range
  date?: string // 日期（日报/周报使用），格式：yyyy-MM-dd
  month?: string // 月份（月报使用），格式：yyyy-MM
  year?: number // 年份（年报使用）
  startDate?: string // 开始日期（自定义范围使用），格式：yyyy-MM-dd
  endDate?: string // 结束日期（自定义范围使用），格式：yyyy-MM-dd
}) {
  return request({
    url: '/admin/statistics/query',
    method: 'get',
    params
  })
}

/**
 * 获取实时数据
 * @returns {Promise}
 */
export function getRealtime() {
  return request({
    url: '/admin/statistics/realtime',
    method: 'get'
  })
}

/**
 * 补录数据
 * @param {String} date - 补录日期
 * @returns {Promise}
 */
export function regenerateStatistics(date: string) {
  return request({
    url: '/admin/statistics/regenerate',
    method: 'post',
    params: { date }
  })
}

/**
 * 清理缓存
 * @returns {Promise}
 */
export function clearStatisticsCache() {
  return request({
    url: '/admin/statistics/cache',
    method: 'delete'
  })
}

/**
 * 批量补录数据
 * @param {String} startDate - 开始日期
 * @param {String} endDate - 结束日期
 * @returns {Promise}
 */
export function regenerateStatisticsRange(startDate: string, endDate: string) {
  return request({
    url: '/admin/statistics/regenerate/range',
    method: 'post',
    params: { startDate, endDate }
  })
}

// 兼容旧接口
export const getOverviewStatistics = (params: {
  startDate: string
  endDate: string
  period: string // day, week, month, year
}) => {
  let type: string = 'daily'
  switch (params.period) {
    case 'day':
      type = 'daily'
      break
    case 'week':
      type = 'weekly'
      break
    case 'month':
      type = 'monthly'
      break
    case 'year':
      type = 'yearly'
      break
    default:
      type = 'daily'
  }
  
  return getStatistics({
    type,
    date: params.startDate
  })
}

export const getRealtimeStatistics = () => {
  return getRealtime()
}

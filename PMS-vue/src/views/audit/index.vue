<template>
  <div class="audit-container">
    <el-card class="audit-card">

      
      <!-- 类型切换 -->
      <div class="type-tabs-container">
        <el-tabs v-model="activeTab" class="type-tabs" @tab-click="handleTabChange">
          <el-tab-pane label="待审核" name="pending"></el-tab-pane>
          <el-tab-pane label="审核历史" name="history"></el-tab-pane>
          <el-tab-pane label="用户管理" name="user"></el-tab-pane>
          <el-tab-pane label="举报管理" name="report"></el-tab-pane>
          <el-tab-pane label="数据统计" name="stats"></el-tab-pane>
          <el-tab-pane label="公告管理" name="announcement"></el-tab-pane>
        </el-tabs>
      </div>
      
      <div class="audit-content">
        <!-- 待审核列表 -->
        <div v-if="activeTab === 'pending'">
          <!-- 操作功能区 -->
          <div class="operation-bar">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索标题/发布者"
              prefix-icon="el-icon-search"
              class="search-input"
              @keyup.enter="fetchAuditList"
            />
            <el-select v-model="dateRange" placeholder="时间范围" class="filter-select">
              <el-option label="今天" value="today" />
              <el-option label="本周" value="week" />
              <el-option label="本月" value="month" />
            </el-select>
            <el-select v-model="typeFilter" placeholder="全部类型" class="filter-select">
              <el-option label="全部类型" value="" />
              <el-option label="领养" value="adopt" />
              <el-option label="救助" value="help" />
              <el-option label="活动" value="activity" />
              <el-option label="日记" value="daily" />
            </el-select>
            <el-button type="primary" icon="el-icon-search" @click="fetchAuditList" class="search-btn">
              搜索
            </el-button>
          </div>
          
          <!-- 批量操作栏 -->
          <div v-if="selectedItems.length > 0" class="batch-operations">
            <el-checkbox v-model="selectAll" @change="handleSelectAll">全选</el-checkbox>
            <div class="batch-buttons">
              <el-button type="success" @click="handleBatchApprove">
                ✅ 批量通过
              </el-button>
              <el-button type="danger" @click="handleBatchReject">
                ❌ 批量拒绝
              </el-button>
            </div>
          </div>
          
          <!-- 审核列表 -->
          <div v-if="loading" class="loading-container">
            <el-skeleton :rows="10" animated />
          </div>
          <div v-else-if="auditList.length > 0" class="audit-list">
            <div v-for="item in auditList" :key="item.id" class="audit-card-item">
              <el-checkbox v-model="selectedItems" :label="item.id" @change="handleSelectChange" class="item-checkbox" />
              <div class="item-content">
                <div class="item-header">
                  <div class="item-tags">
                    <span class="type-tag" :class="item.targetType">
                      {{ getTypeIcon(item.targetType) }} {{ getTypeText(item.targetType) }}
                    </span>
                    <span class="status-tag" :class="'status-' + item.auditStatus">
                      {{ getStatusText(item.auditStatus) }}
                    </span>
                  </div>
                  <h3 class="item-title">{{ item.title }}</h3>
                </div>
                <div class="item-meta">
                  <span class="item-publisher">发布者：{{ item.user.nickname }} · {{ item.address }}</span>
                  <span class="item-time">{{ formatTime(item.createTime) }}</span>
                </div>
                <div class="item-info" v-if="item.targetType === 'daily'">
                  <span class="item-daily-info">
                    日记内容：{{ item.content.substring(0, 30) }}{{ item.content.length > 30 ? '...' : '' }}
                  </span>
                </div>
                <div class="item-info" v-else-if="item.targetType !== 'activity'">
                  <span class="item-pet-info">
                    宠物：{{ item.petType }} / {{ item.petName }} / {{ item.petAge }} / {{ getGenderText(item.petGender) }}
                  </span>
                </div>
                <div class="item-info" v-else>
                  <span class="item-activity-info">
                    地点：{{ item.address }} · {{ item.activityTime }}
                  </span>
                  <span class="item-activity-info">
                    人数：{{ item.participantCount }}/{{ item.maxParticipants }}
                  </span>
                </div>
                <div class="item-content-text">{{ item.content }}</div>
                <div v-if="item.images && item.images.length > 0" class="item-images">
                  <div class="image-preview">
                    <img v-for="(image, index) in item.images.slice(0, 3)" :key="index" :src="image" :alt="`图片${index+1}`" class="preview-image" />
                  </div>
                  <span v-if="item.images.length > 3" class="image-count">+{{ item.images.length - 3 }}</span>
                </div>
                <div class="item-actions">
                  <el-button size="small" type="info" @click="handleViewDetail(item.id, item.targetType)">
                    查看详情
                  </el-button>
                  <el-button 
                    size="small" 
                    type="success" 
                    @click="handleQuickApprove(item.id, item.targetType)"
                    :disabled="item.auditStatus !== 0"
                  >
                    通过
                  </el-button>
                  <el-button 
                    size="small" 
                    type="danger" 
                    @click="handleQuickReject(item.id, item.targetType)"
                    :disabled="item.auditStatus !== 0"
                  >
                    拒绝
                  </el-button>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="empty-section">
            <el-empty description="暂无待审核内容" />
          </div>
          
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="total"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
            <el-button v-if="activeTab === 'pending'" type="info" @click="switchToHistory">
              📋 审核历史
            </el-button>
          </div>
        </div>
        
        <!-- 审核历史 -->
        <div v-if="activeTab === 'history'">
          <div class="operation-bar">
            <el-button type="info" @click="switchToPending">
              ← 返回待审核列表
            </el-button>
            <el-input
              v-model="historyKeyword"
              placeholder="搜索标题/发布者"
              prefix-icon="el-icon-search"
              class="search-input"
              @keyup.enter="fetchAuditHistory"
            />
            <el-select v-model="historyTypeFilter" placeholder="全部类型" class="filter-select">
              <el-option label="全部类型" value="" />
              <el-option label="领养" value="adopt" />
              <el-option label="救助" value="help" />
              <el-option label="活动" value="activity" />
              <el-option label="日记" value="daily" />
            </el-select>
            <el-select v-model="historyStatusFilter" placeholder="全部状态" class="filter-select">
              <el-option label="全部状态" value="" />
              <el-option label="待审核" value="0" />
              <el-option label="已通过" value="1" />
              <el-option label="已拒绝" value="2" />
            </el-select>
            <el-button type="primary" icon="el-icon-search" @click="fetchAuditHistory" class="search-btn">
              搜索
            </el-button>
          </div>
          
          <div v-if="historyLoading" class="loading-container">
            <el-skeleton :rows="10" animated />
          </div>
          <div v-else-if="historyList.length > 0" class="history-list">
            <div v-for="item in historyList" :key="item.id" class="history-card">
              <div class="history-header">
                <span class="history-status" :class="'status-' + item.auditStatus">
                  {{ item.auditStatus === 1 ? '✅ 已通过' : '❌ 已拒绝' }}
                </span>
                <span class="history-title">{{ item.title }}</span>
                <span class="history-time">{{ formatDate(item.auditTime) }}</span>
              </div>
              <div class="history-meta">
                <span class="history-auditor">审核人：{{ item.auditorName }}</span>
                <span class="history-publisher">发布者：{{ item.user.nickname }}</span>
              </div>
              <div v-if="item.rejectReason" class="history-reason">
                拒绝理由：{{ item.rejectReason }}
              </div>
            </div>
          </div>
          <div v-else class="empty-section">
            <el-empty description="暂无审核历史" />
          </div>
          
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="historyCurrentPage"
              v-model:page-size="historyPageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="historyTotal"
              @size-change="handleHistorySizeChange"
              @current-change="handleHistoryCurrentChange"
            />
          </div>
        </div>
        
        <!-- 用户管理 -->
        <div v-else-if="activeTab === 'user'">
          <!-- 操作功能区 -->
          <div class="operation-bar">
            <el-input
              v-model="userSearchKeyword"
              placeholder="搜索用户名/昵称/手机号"
              prefix-icon="el-icon-search"
              class="search-input"
              @keyup.enter="fetchUserList"
            />
            <el-select v-model="userStatusFilter" placeholder="全部状态" class="filter-select">
              <el-option label="全部状态" value="-1" />
              <el-option label="正常" value="1" />
              <el-option label="禁用" value="0" />
            </el-select>
            <el-button
              type="primary"
              class="search-btn"
              @click="fetchUserList"
            >
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <div class="batch-operations">
              <el-button
                size="small"
                type="danger"
                @click="handleBatchDisable"
                :disabled="selectedUsers.length === 0"
              >
                批量禁用
              </el-button>
              <el-button
                size="small"
                type="success"
                @click="handleBatchEnable"
                :disabled="selectedUsers.length === 0"
              >
                批量启用
              </el-button>
              <el-button
                size="small"
                type="warning"
                @click="handleBatchResetPassword"
                :disabled="selectedUsers.length === 0"
              >
                批量重置密码
              </el-button>
            </div>
          </div>
          
          <!-- 用户列表 -->
          <div v-if="loading" class="loading-container">
            <el-skeleton :rows="10" animated />
          </div>
          <div v-else-if="userList.length > 0" class="user-list">
            <el-table 
              :data="userList" 
              style="width: 100%"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="55" />
              <el-table-column prop="userId" label="用户ID" width="100" />
              <el-table-column label="用户信息" min-width="200">
                <template #default="scope">
                  <div class="user-info">
                    <el-avatar :size="40" :src="scope.row.avatar || ''">
                      {{ scope.row.nickname?.charAt(0) || '用' }}
                    </el-avatar>
                    <div class="user-details">
                      <div class="nickname">{{ scope.row.nickname }}</div>
                      <div class="username">{{ scope.row.username }}</div>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="scope">
                  <el-tag :type="scope.row.isDisable ? 'danger' : 'success'">
                    {{ scope.row.isDisable ? '禁用' : '正常' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="scope">
                  <el-button
                    size="small"
                    :type="scope.row.isDisable ? 'success' : 'danger'"
                    @click="handleToggleUserStatus(scope.row.userId, scope.row.isDisable)"
                  >
                    {{ scope.row.isDisable ? '启用' : '禁用' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            
            <!-- 分页 -->
            <div class="pagination-container">
              <el-pagination
                v-model:current-page="userCurrentPage"
                v-model:page-size="userPageSize"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="userTotal"
                @size-change="handleUserSizeChange"
                @current-change="handleUserCurrentChange"
              />
            </div>
          </div>
          <div v-else class="empty-section">
            <el-empty description="暂无用户数据" />
          </div>
        </div>
        
        <!-- 举报管理 -->
        <div v-else-if="activeTab === 'report'">
          <div class="operation-bar">
            <el-input
              v-model="reportKeyword"
              placeholder="搜索关键词"
              prefix-icon="el-icon-search"
              class="search-input"
              @keyup.enter="fetchReportList"
            />
            <el-select v-model="reportStatus" placeholder="状态" class="filter-select">
              <el-option label="全部" value="" />
              <el-option label="待处理" value="0" />
              <el-option label="已处理" value="1" />
              <el-option label="已驳回" value="2" />
            </el-select>
            <el-select v-model="reportTargetType" placeholder="目标类型" class="filter-select">
              <el-option label="全部" value="" />
              <el-option label="宠物" value="pet" />
              <el-option label="活动" value="activity" />
              <el-option label="评论" value="comment" />
              <el-option label="用户" value="user" />
            </el-select>
            <el-button type="primary" @click="fetchReportList" class="search-btn">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
          </div>
          
          <div v-if="reportLoading" class="loading-container">
            <el-skeleton :rows="10" animated />
          </div>
          <div v-else-if="reportList.length > 0" class="report-list">
            <el-table :data="reportList" style="width: 100%">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column label="目标信息" min-width="200">
                <template #default="scope">
                  <div>
                    <div class="report-target-info">
                      <el-tag size="small" :type="getReportTargetTypeTagType(scope.row.targetType)">
                        {{ getReportTargetTypeText(scope.row.targetType) }}
                      </el-tag>
                      <span class="report-target-id">ID: {{ scope.row.targetId }}</span>
                    </div>
                    <div class="report-target-title" v-if="scope.row.targetTitle">
                      {{ scope.row.targetTitle }}
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="举报原因" min-width="150" />
              <el-table-column label="举报者" width="120">
                <template #default="scope">
                  {{ scope.row.reporterName || '未知' }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="scope">
                  <el-tag :type="getReportStatusTagType(scope.row.status)">
                    {{ getReportStatusText(scope.row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="举报时间" width="180" />
              <el-table-column label="操作" width="200">
                <template #default="scope">
                  <el-button size="small" type="info" @click="handleViewReportDetail(scope.row.id)">
                    <el-icon><View /></el-icon>
                    查看
                  </el-button>
                  <el-button 
                    v-if="scope.row.status === 0" 
                    size="small" 
                    type="primary" 
                    @click="handleOpenReportHandleDialog(scope.row.id)"
                  >
                    处理
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            
            <div class="pagination-container">
              <el-pagination
                v-model:current-page="reportCurrentPage"
                v-model:page-size="reportPageSize"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="reportTotal"
                @size-change="handleReportSizeChange"
                @current-change="handleReportCurrentChange"
              />
            </div>
          </div>
          <div v-else class="empty-section">
            <el-empty description="暂无举报记录" />
          </div>
        </div>
        
        <!-- 数据统计 -->
        <div v-else-if="activeTab === 'stats'">
          <!-- 实时统计 -->
          <el-card class="realtime-card">
            <template #header>
              <div class="card-header">
                <span>实时统计</span>
                <el-button type="info" @click="fetchRealtimeData">刷新</el-button>
              </div>
            </template>
            
            <div v-if="realtimeLoading" class="loading-container">
              <el-skeleton :rows="3" animated />
            </div>
            <div v-else class="realtime-data">
              <div class="realtime-item">
                <span class="realtime-label">今日用户数：</span>
                <span class="realtime-value">{{ realtimeData?.newUserCount || 0 }}</span>
              </div>
              <div class="realtime-item">
                <span class="realtime-label">今日帖子数：</span>
                <span class="realtime-value">{{ (realtimeData?.newPetPostCount || 0) + (realtimeData?.newActivityCount || 0) + (realtimeData?.newDailyPostCount || 0) }}</span>
              </div>
              <div class="realtime-item">
                <span class="realtime-label">今日评论数：</span>
                <span class="realtime-value">{{ realtimeData?.newCommentCount || 0 }}</span>
              </div>
              <div class="realtime-item">
                <span class="realtime-label">今日点赞数：</span>
                <span class="realtime-value">{{ realtimeData?.newLikeCount || 0 }}</span>
              </div>
              <div class="realtime-item">
                <span class="realtime-label">待审核数：</span>
                <span class="realtime-value">{{ realtimeData?.pendingAuditCount || 0 }}</span>
              </div>
            </div>
          </el-card>
          
          <!-- 周期切换标签页 -->
          <el-card class="statistics-card">
            <template #header>
              <div class="card-header">
                <span>数据统计概览</span>
              </div>
            </template>
            
            <!-- 周期切换 - 使用 el-radio-group 替代 tabs，更可靠 -->
            <div class="period-switch">
              <el-radio-group v-model="period" @change="handlePeriodChange">
                <el-radio-button value="day">日统计</el-radio-button>
                <el-radio-button value="week">周统计</el-radio-button>
                <el-radio-button value="month">月统计</el-radio-button>
                <el-radio-button value="year">年统计</el-radio-button>
                <el-radio-button value="custom">自定义</el-radio-button>
              </el-radio-group>
            </div>
            
            <!-- 日期选择区 - 使用 v-show 确保 DOM 存在 -->
            <div class="date-filter-area">
              <!-- 日统计 -->
              <div v-show="period === 'day'" class="filter-row">
                <span class="filter-label">选择日期：</span>
                <el-date-picker
                  v-model="dayDate"
                  type="date"
                  placeholder="选择日期"
                  style="width: 200px"
                  value-format="YYYY-MM-DD"
                />
                <el-button type="primary" style="margin-left: 12px" @click="fetchOverviewData">
                  查询
                </el-button>
              </div>
              
              <!-- 周统计 -->
              <div v-show="period === 'week'" class="filter-row">
                <span class="filter-label">选择周：</span>
                <el-date-picker
                  v-model="weekDate"
                  type="week"
                  placeholder="选择周"
                  format="YYYY年 w周"
                  value-format="YYYY-MM-DD"
                  style="width: 200px"
                />
                <el-button type="primary" style="margin-left: 12px" @click="fetchOverviewData">
                  查询
                </el-button>
              </div>
              
              <!-- 月统计 -->
              <div v-show="period === 'month'" class="filter-row">
                <span class="filter-label">选择月份：</span>
                <el-date-picker
                  v-model="monthDate"
                  type="month"
                  placeholder="选择月份"
                  format="YYYY年 MM月"
                  value-format="YYYY-MM"
                  style="width: 200px"
                />
                <el-button type="primary" style="margin-left: 12px" @click="fetchOverviewData">
                  查询
                </el-button>
              </div>
              
              <!-- 年统计 -->
              <div v-show="period === 'year'" class="filter-row">
                <span class="filter-label">选择年份：</span>
                <el-date-picker
                  v-model="yearDate"
                  type="year"
                  placeholder="选择年份"
                  format="YYYY年"
                  value-format="YYYY"
                  style="width: 200px"
                />
                <el-button type="primary" style="margin-left: 12px" @click="fetchOverviewData">
                  查询
                </el-button>
              </div>
              
              <!-- 自定义时间段 -->
              <div v-show="period === 'custom'" class="filter-row">
                <span class="filter-label">选择时间段：</span>
                <el-date-picker
                  v-model="customDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  style="width: 300px"
                />
                <el-button type="primary" style="margin-left: 12px" @click="fetchOverviewData">
                  查询
                </el-button>
              </div>
            </div>
            
            <!-- 补录数据按钮 -->
            <div style="margin-top: 16px; text-align: right">
              <el-button type="warning" @click="openBackfillDialog">补录数据</el-button>
            </div>
            
            <!-- 统计数据卡片 -->
            <div v-if="loading" class="loading-container">
              <el-skeleton :rows="4" animated />
            </div>
            <div v-else>
              <div class="statistics-overview">
                <div class="stat-card">
                  <div class="stat-value">{{ overviewData?.newUserCount || overviewData?.totalNewUsers || overviewData?.dailyStats?.newUserCount || 0 }}</div>
                  <div class="stat-label">新增用户</div>
                </div>
                <div class="stat-card">
                  <div class="stat-value">{{ overviewData?.activeUserCount || overviewData?.dau || overviewData?.totalActiveUsers || overviewData?.avgDailyActiveUsers || overviewData?.dailyStats?.activeUserCount || 0 }}</div>
                  <div class="stat-label">活跃用户</div>
                </div>
                <div class="stat-card">
                  <div class="stat-value">{{ (overviewData?.newPetPostCount || 0) + (overviewData?.newActivityCount || 0) + (overviewData?.newDailyPostCount || 0) + (overviewData?.totalNewPosts || 0) + (overviewData?.dailyStats?.newPetPostCount || 0) + (overviewData?.dailyStats?.newActivityCount || 0) + (overviewData?.dailyStats?.newDailyPostCount || 0) }}</div>
                  <div class="stat-label">新增帖子</div>
                </div>
                <div class="stat-card">
                  <div class="stat-value">{{ overviewData?.newCommentCount || overviewData?.totalNewComments || overviewData?.dailyStats?.newCommentCount || 0 }}</div>
                  <div class="stat-label">新增评论</div>
                </div>
              </div>
            </div>
          </el-card>
          
          <!-- 趋势图表 -->
          <el-card class="trend-card">
            <template #header>
              <div class="card-header">
                <span>数据趋势</span>
              </div>
            </template>
            
            <div v-if="trendLoading" class="loading-container">
              <el-skeleton :rows="6" animated />
            </div>
            <div v-else class="trend-chart" style="width: 100%; height: 400px; background-color: #f5f7fa; border-radius: 8px; padding: 20px;">
              <div ref="trendChartRef" class="chart-container" style="width: 100%; height: 360px;"></div>
              <div style="margin-top: 20px; text-align: center; color: #999;">
                如果图表未显示，请检查浏览器控制台的错误信息
                <el-button type="primary" size="small" @click="testChart" style="margin-left: 20px;">测试图表</el-button>
              </div>
            </div>
          </el-card>
        </div>
        
        <!-- 公告管理 -->
        <div v-else-if="activeTab === 'announcement'">
          <div class="operation-bar">
            <el-input
              v-model="noticeKeyword"
              placeholder="搜索标题/内容"
              prefix-icon="el-icon-search"
              class="search-input"
            />
            <el-select v-model="noticeStatus" placeholder="状态" class="filter-select">
              <el-option label="全部" value="" />
              <el-option label="草稿" value="0" />
              <el-option label="已发布" value="1" />
              <el-option label="已下线" value="2" />
            </el-select>
            <el-button type="primary" @click="fetchNoticeList" class="search-btn">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button type="success" @click="handleCreateNotice">
              <el-icon><Plus /></el-icon>
              新增公告
            </el-button>
          </div>
          
          <el-table :data="noticeList" style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="title" label="标题" min-width="200">
              <template #default="scope">
                <div>
                  <span :class="{ 'top-text': scope.row.isTop === 1 }">{{ scope.row.title }}</span>
                  <el-tag v-if="scope.row.isTop === 1" size="small" type="danger" effect="dark">置顶</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="100">
              <template #default="scope">
                <el-tag :type="getNoticeTypeTagType(scope.row.type)">{{ getNoticeTypeText(scope.row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="100">
              <template #default="scope">
                <el-tag v-if="scope.row.priority > 0" :type="getPriorityTagType(scope.row.priority)">
                  {{ getPriorityText(scope.row.priority) }}
                </el-tag>
                <span v-else>普通</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag :type="getNoticeStatusTagType(scope.row.status)">
                  {{ getNoticeStatusText(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="publishTime" label="发布时间" width="180" />
            <el-table-column label="操作" width="200">
              <template #default="scope">
                <el-button type="primary" size="small" @click="handleEditNotice(scope.row)">
                  编辑
                </el-button>
                <el-button v-if="scope.row.status === 0" type="success" size="small" @click="handlePublishNotice(scope.row.id)">
                  发布
                </el-button>
                <el-button v-else-if="scope.row.status === 1" type="warning" size="small" @click="handleUnpublishNotice(scope.row.id)">
                  下架
                </el-button>
                <el-button type="danger" size="small" @click="handleDeleteNotice(scope.row.id)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="noticeCurrentPage"
              v-model:page-size="noticePageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="noticeTotal"
              @size-change="handleNoticeSizeChange"
              @current-change="handleNoticeCurrentChange"
            />
          </div>
        </div>
      </div>
      
      <!-- 公告编辑对话框 -->
      <el-dialog
        v-model="noticeDialogVisible"
        :title="noticeForm.id ? '编辑公告' : '新增公告'"
        width="600px"
      >
        <el-form ref="noticeFormRef" :model="noticeForm" label-width="80px">
          <el-form-item label="标题" required>
            <el-input v-model="noticeForm.title" placeholder="请输入公告标题" maxlength="100" />
          </el-form-item>
          <el-form-item label="内容" required>
            <el-input
              v-model="noticeForm.content"
              type="textarea"
              rows="5"
              placeholder="请输入公告内容"
            />
          </el-form-item>
          <el-form-item label="类型">
            <el-radio-group v-model="noticeForm.type">
              <el-radio label="1">系统公告</el-radio>
              <el-radio label="2">活动通知</el-radio>
              <el-radio label="3">重要提醒</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="优先级">
            <el-radio-group v-model="noticeForm.priority">
              <el-radio label="0">普通</el-radio>
              <el-radio label="1">重要</el-radio>
              <el-radio label="2">紧急</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="置顶">
            <el-checkbox v-model="noticeForm.isTop">是否置顶</el-checkbox>
          </el-form-item>
          <el-form-item label="发布时间">
            <el-date-picker
              v-model="noticeForm.schedulePublishTime"
              type="datetime"
              placeholder="选择发布时间"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="noticeDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleSaveNotice">保存</el-button>
          </span>
        </template>
      </el-dialog>
      
      <!-- 批量拒绝对话框 -->
      <el-dialog
        v-model="batchRejectDialogVisible"
        title="❌ 批量拒绝"
        width="500px"
      >
        <div class="batch-reject-content">
          <p class="selected-count">已选择 {{ selectedItems.length }} 条内容</p>
          <el-form-item label="拒绝理由" required>
            <el-input
              v-model="rejectReason"
              type="textarea"
              rows="4"
              placeholder="请填写拒绝原因..."
            />
          </el-form-item>
          <div class="common-reasons">
            <p class="reasons-title">📌 常用理由：</p>
            <div class="reasons-list">
              <el-button
                v-for="(reason, index) in commonReasons"
                :key="index"
                type="info"
                size="small"
                plain
                @click="rejectReason = reason"
              >
                {{ reason }}
              </el-button>
            </div>
          </div>
        </div>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="batchRejectDialogVisible = false">取消</el-button>
            <el-button type="danger" @click="confirmBatchReject">确认拒绝</el-button>
          </span>
        </template>
      </el-dialog>
      
      <!-- 举报详情对话框 -->
      <el-dialog
        v-model="reportDetailDialogVisible"
        title="举报详情"
        width="600px"
      >
        <div v-if="reportDetail" class="report-detail-content">
          <el-form label-width="80px">
            <el-form-item label="举报ID">
              {{ reportDetail.id }}
            </el-form-item>
            <el-form-item label="目标类型">
              <el-tag :type="getReportTargetTypeTagType(reportDetail.targetType)">
                {{ getReportTargetTypeText(reportDetail.targetType) }}
              </el-tag>
            </el-form-item>
            <el-form-item label="目标ID">
              {{ reportDetail.targetId }}
            </el-form-item>
            <el-form-item label="目标标题">
              {{ reportDetail.targetTitle || '无' }}
            </el-form-item>
            <el-form-item label="举报原因">
              {{ reportDetail.reason }}
            </el-form-item>
            <el-form-item label="举报者">
              {{ reportDetail.reporterName || '未知' }}
            </el-form-item>
            <el-form-item label="举报时间">
              {{ reportDetail.createTime }}
            </el-form-item>
            <el-form-item label="处理状态">
              <el-tag :type="getReportStatusTagType(reportDetail.status)">
                {{ getReportStatusText(reportDetail.status) }}
              </el-tag>
            </el-form-item>
            <el-form-item label="处理结果" v-if="reportDetail.handleResult">
              {{ reportDetail.handleResult }}
            </el-form-item>
            <el-form-item label="处理人" v-if="reportDetail.handlerName">
              {{ reportDetail.handlerName }}
            </el-form-item>
            <el-form-item label="处理时间" v-if="reportDetail.handleTime">
              {{ reportDetail.handleTime }}
            </el-form-item>
          </el-form>
          <div class="report-action-buttons">
            <el-button type="primary" @click="handleViewReportContent(reportDetail.targetType, reportDetail.targetId)">
              查看举报内容
            </el-button>
          </div>
        </div>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="reportDetailDialogVisible = false">关闭</el-button>
          </span>
        </template>
      </el-dialog>
      
      <!-- 举报处理对话框 -->
      <el-dialog
        v-model="reportHandleDialogVisible"
        title="处理举报"
        width="500px"
      >
        <el-form label-width="80px">
          <el-form-item label="处理结果" required>
            <el-radio-group v-model="handleStatus">
              <el-radio label="1">已处理（下架）</el-radio>
              <el-radio label="2">已驳回</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="处理说明" required>
            <el-input
              v-model="handleResult"
              type="textarea"
              rows="4"
              placeholder="请填写处理说明..."
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="reportHandleDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleSubmitReportHandle">确认处理</el-button>
          </span>
        </template>
      </el-dialog>
      
      <!-- 补录统计数据对话框 - 重构版 -->
      <el-dialog
        v-model="backfillDialogVisible"
        title="补录统计数据"
        width="500px"
        @close="resetBackfillForm"
      >
        <el-form :model="backfillForm" :rules="backfillRules" ref="backfillFormRef" label-width="80px">
          <el-form-item label="补录方式" prop="backfillType">
            <el-radio-group v-model="backfillForm.backfillType" @change="handleBackfillTypeChange">
              <el-radio label="single">单日补录</el-radio>
              <el-radio label="range">区间补录</el-radio>
            </el-radio-group>
          </el-form-item>
          
          <!-- 单日补录 -->
          <template v-if="backfillForm.backfillType === 'single'">
            <el-form-item label="补录日期" prop="singleDate">
              <el-date-picker
                v-model="backfillForm.singleDate"
                type="date"
                placeholder="选择补录日期"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled-date="disabledDate"
              />
            </el-form-item>
          </template>
          
          <!-- 区间补录 -->
          <template v-else>
            <el-form-item label="开始日期" prop="startDate">
              <el-date-picker
                v-model="backfillForm.startDate"
                type="date"
                placeholder="选择开始日期"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled-date="disabledDate"
              />
            </el-form-item>
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker
                v-model="backfillForm.endDate"
                type="date"
                placeholder="选择结束日期"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled-date="disabledDate"
              />
            </el-form-item>
          </template>
          
          <el-form-item v-if="backfillForm.backfillType === 'range'">
            <div class="date-range-hint">
              <el-icon><InfoFilled /></el-icon>
              <span>区间补录将重新计算从 {{ backfillForm.startDate || '开始日期' }} 到 {{ backfillForm.endDate || '结束日期' }} 的所有日期数据</span>
            </div>
          </el-form-item>
          
          <el-form-item>
            <div class="tip-text">
              <el-icon><WarningFilled /></el-icon>
              <span>提示：补录统计数据会重新计算指定日期的统计信息，可能会覆盖原有数据。</span>
            </div>
          </el-form-item>
        </el-form>
        
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="backfillDialogVisible = false">取消</el-button>
            <el-button 
              type="primary" 
              :loading="backfillLoading" 
              @click="submitBackfill"
              :disabled="!isBackfillFormValid"
            >
              确认补录
            </el-button>
          </span>
        </template>
      </el-dialog>
      
      <!-- 批量补录进度对话框 -->
      <el-dialog
        v-model="progressDialogVisible"
        title="批量补录进度"
        width="500px"
        :close-on-click-modal="false"
        :close-on-press-escape="false"
        :show-close="false"
      >
        <div class="progress-container">
          <div class="progress-info">
            <span>正在补录：{{ currentBackfillDate }}</span>
            <span>{{ backfillProgress }}%</span>
          </div>
          <el-progress 
            :percentage="backfillProgress" 
            :status="backfillProgress === 100 ? 'success' : ''"
            :stroke-width="16"
            striped
            striped-flow
          />
          <div class="progress-stats">
            <span>成功：{{ backfillSuccessCount }}</span>
            <span>失败：{{ backfillFailCount }}</span>
            <span v-if="backfillTotalCount > 0">总计：{{ backfillTotalCount }}</span>
          </div>
          <div v-if="backfillErrorMessage" class="progress-error">
            <el-alert :title="backfillErrorMessage" type="error" :closable="false" />
          </div>
        </div>
        <template #footer>
          <span class="dialog-footer">
            <el-button 
              v-if="backfillProgress === 100" 
              type="primary" 
              @click="closeProgressDialog"
            >
              关闭
            </el-button>
          </span>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed, reactive } from 'vue'
import { ElMessage, ElMessageBox, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElRadioGroup, ElRadio, ElCheckbox, ElDatePicker, ElButton } from 'element-plus'
import { useRouter } from 'vue-router'
import { getAuditList, batchApproveAudit, batchRejectAudit, getAuditHistory } from '../../api/audit'
import { getAdminUserList, disableUser, enableUser, batchDisableUsers, batchEnableUsers, batchResetPassword } from '../../api/user'
import { getAdminNoticeList, createNotice, updateNotice, deleteNotice, publishNotice, unpublishNotice } from '../../api/notice'
import { getReportList, getReportDetail, handleReport } from '../../api/report'
import { getStatistics, getRealtime, regenerateStatistics, clearStatisticsCache, regenerateStatisticsRange } from '../../api/statistics'
import * as echarts from 'echarts'
import { Search, Plus, View, Check, Close, InfoFilled, WarningFilled } from '@element-plus/icons-vue'

const router = useRouter()

// 当前选中的选项卡
const activeTab = ref('pending')

// 审核列表数据
const auditList = ref<any[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 搜索和筛选条件
const searchKeyword = ref('')
const typeFilter = ref('')
const dateRange = ref('')

// 批量选择
const selectedItems = ref<number[]>([])
const selectAll = ref(false)

// 审核历史数据
const historyList = ref<any[]>([])
const historyLoading = ref(false)
const historyCurrentPage = ref(1)
const historyPageSize = ref(10)
const historyTotal = ref(0)
const historyKeyword = ref('')
const historyTypeFilter = ref('')
const historyStatusFilter = ref('')

// 用户管理相关数据
const userList = ref<any[]>([])
const userCurrentPage = ref(1)
const userPageSize = ref(10)
const userTotal = ref(0)
const userSearchKeyword = ref('')
const userStatusFilter = ref('-1')
const selectedUsers = ref<any[]>([])

// 公告管理相关数据
const noticeList = ref<any[]>([])
const noticeCurrentPage = ref(1)
const noticePageSize = ref(10)
const noticeTotal = ref(0)
const noticeKeyword = ref('')
const noticeStatus = ref('')

// 公告编辑对话框
const noticeDialogVisible = ref(false)
const noticeForm = ref({
  id: 0,
  title: '',
  content: '',
  type: 1,
  priority: 0,
  isTop: 0,
  schedulePublishTime: ''
})
const noticeFormRef = ref<any>(null)

// 批量拒绝对话框
const batchRejectDialogVisible = ref(false)
const rejectReason = ref('')
const commonReasons = [
  '内容不符合规范',
  '图片违规',
  '联系方式无效',
  '重复发布',
  '信息不完整'
]

// 举报管理相关数据
const reportList = ref<any[]>([])
const reportLoading = ref(false)
const reportCurrentPage = ref(1)
const reportPageSize = ref(10)
const reportTotal = ref(0)
const reportKeyword = ref('')
const reportStatus = ref('')
const reportTargetType = ref('')

// 举报详情对话框
const reportDetailDialogVisible = ref(false)
const reportDetail = ref({})

// 举报处理对话框
const reportHandleDialogVisible = ref(false)
const currentReportId = ref(0)
const handleStatus = ref(1)
const handleResult = ref('')

// 统计数据相关
const period = ref('day')
const dayDate = ref('')
const weekDate = ref('')
const monthDate = ref('')
const yearDate = ref('')
const customDateRange = ref<[Date, Date] | null>(null)
const realtimeLoading = ref(false)
const trendLoading = ref(false)
const overviewData = ref<any>(null)
const realtimeData = ref<any>(null)
const trendChartRef = ref<HTMLElement | null>(null)
let trendChart: echarts.ECharts | null = null

// 获取审核列表
const fetchAuditList = async () => {
  loading.value = true
  try {
    const response = await getAuditList({
      targetType: typeFilter.value || undefined,
      keyword: searchKeyword.value || undefined,
      dateRange: dateRange.value || undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    if (response.code === 200 && response.data) {
      auditList.value = response.data.records || []
      total.value = response.data.total || 0
      selectedItems.value = []
      selectAll.value = false
    } else {
      ElMessage.error(response.message || '获取审核列表失败')
    }
  } catch (error) {
    ElMessage.error('获取审核列表失败')
    console.error('获取审核列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 获取审核历史
const fetchAuditHistory = async () => {
  historyLoading.value = true
  try {
    const response = await getAuditHistory({
      targetType: historyTypeFilter.value || undefined,
      keyword: historyKeyword.value || undefined,
      auditStatus: historyStatusFilter.value ? parseInt(historyStatusFilter.value) : undefined,
      pageNum: historyCurrentPage.value,
      pageSize: historyPageSize.value
    })
    if (response.code === 200 && response.data) {
      historyList.value = response.data.records || []
      historyTotal.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取审核历史失败')
    }
  } catch (error) {
    ElMessage.error('获取审核历史失败')
    console.error('获取审核历史失败:', error)
  } finally {
    historyLoading.value = false
  }
}

// 处理查看详情
const handleViewDetail = (id: number, targetType: string) => {
  if (targetType === 'activity') {
    // 活动类型跳转到活动详情页面
    router.push(`/pets/activity/${id}`)
  } else if (targetType === 'daily') {
    // 日记类型跳转到日记详情页面
    router.push(`/daily/${id}`)
  } else {
    // 其他类型跳转到宠物详情页面
    router.push(`/pets/${id}`)
  }
};

// 快速审核通过
const handleQuickApprove = async (id: number, targetType: string) => {
  try {
    const response = await batchApproveAudit(targetType, [id])
    if (response.code === 200) {
      ElMessage.success('审核通过')
      fetchAuditList()
    } else {
      ElMessage.error(response.message || '审核通过失败')
    }
  } catch (error) {
    ElMessage.error('审核通过失败')
    console.error('审核通过失败:', error)
  }
}

// 快速审核拒绝
const handleQuickReject = async (id: number, targetType: string) => {
  // 弹出输入框，让审核人员输入拒绝原因
  const { value: reason } = await ElMessageBox.prompt('请输入拒绝原因', '审核拒绝', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '请输入拒绝原因',
    inputValidator: (value) => {
      if (!value || value.trim() === '') {
        return '拒绝原因不能为空'
      }
      return true
    }
  })
  
  if (reason) {
    try {
      const response = await batchRejectAudit(targetType, [id], reason)
      if (response.code === 200) {
        ElMessage.success('审核拒绝')
        fetchAuditList()
      } else {
        ElMessage.error(response.message || '审核拒绝失败')
      }
    } catch (error) {
      ElMessage.error('审核拒绝失败')
      console.error('审核拒绝失败:', error)
    }
  }
}

// 处理批量通过
const handleBatchApprove = async () => {
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请选择要审核通过的内容')
    return
  }
  
  try {
    // 假设所有选中项类型相同，取第一个的类型
    const firstItem = auditList.value.find(item => item.id === selectedItems.value[0])
    if (!firstItem) return
    
    const response = await batchApproveAudit(firstItem.targetType, selectedItems.value)
    if (response.code === 200) {
      ElMessage.success('批量审核通过')
      fetchAuditList()
    } else {
      ElMessage.error(response.message || '批量审核通过失败')
    }
  } catch (error) {
    ElMessage.error('批量审核通过失败')
    console.error('批量审核通过失败:', error)
  }
}

// 处理批量拒绝
const handleBatchReject = () => {
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请选择要审核拒绝的内容')
    return
  }
  rejectReason.value = ''
  batchRejectDialogVisible.value = true
}

// 确认批量拒绝
const confirmBatchReject = async () => {
  if (!rejectReason.value.trim()) {
    ElMessage.error('拒绝原因不能为空')
    return
  }
  
  try {
    // 假设所有选中项类型相同，取第一个的类型
    const firstItem = auditList.value.find(item => item.id === selectedItems.value[0])
    if (!firstItem) return
    
    const response = await batchRejectAudit(firstItem.targetType, selectedItems.value, rejectReason.value)
    if (response.code === 200) {
      ElMessage.success('批量审核拒绝')
      batchRejectDialogVisible.value = false
      fetchAuditList()
    } else {
      ElMessage.error(response.message || '批量审核拒绝失败')
    }
  } catch (error) {
    ElMessage.error('批量审核拒绝失败')
    console.error('批量审核拒绝失败:', error)
  }
}

// 处理全选
const handleSelectAll = (value: boolean) => {
  if (value) {
    selectedItems.value = auditList.value.map(item => item.id)
  } else {
    selectedItems.value = []
  }
}

// 处理选择变化
const handleSelectChange = () => {
  selectAll.value = selectedItems.value.length === auditList.value.length
}

// 切换到历史记录
const switchToHistory = () => {
  activeTab.value = 'history'
  fetchAuditHistory()
}

// 切换到待审核
const switchToPending = () => {
  activeTab.value = 'pending'
  fetchAuditList()
}

// 获取类型图标
const getTypeIcon = (type: string) => {
  switch (type) {
    case 'adopt': return '🐱'
    case 'help': return '🐕'
    case 'activity': return '📍'
    case 'daily': return '📝'
    default: return ''
  }
}

// 获取类型文本
const getTypeText = (type: string) => {
  switch (type) {
    case 'adopt': return '领养'
    case 'help': return '救助'
    case 'activity': return '活动'
    case 'daily': return '日记'
    default: return '未知'
  }
}

// 获取状态文本
const getStatusText = (status: number) => {
  switch (status) {
    case 0: return '待审核'
    case 1: return '已通过'
    case 2: return '已拒绝'
    default: return '未知'
  }
}

// 获取性别文本
const getGenderText = (gender: number) => {
  switch (gender) {
    case 1: return '公'
    case 2: return '母'
    default: return '未知'
  }
}

// 格式化时间
const formatTime = (time: string) => {
  const now = new Date()
  const target = new Date(time)
  const diff = now.getTime() - target.getTime()
  
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return target.toLocaleDateString('zh-CN')
}

// 格式化日期
const formatDate = (date: string) => {
  const target = new Date(date)
  return target.toLocaleString('zh-CN')
}

// 处理页码变化
const handleSizeChange = (size: number) => {
  pageSize.value = size
  fetchAuditList()
}

// 处理页数变化
const handleCurrentChange = (current: number) => {
  currentPage.value = current
  fetchAuditList()
}

// 处理历史记录页码变化
const handleHistorySizeChange = (size: number) => {
  historyPageSize.value = size
  fetchAuditHistory()
}

// 处理历史记录页数变化
const handleHistoryCurrentChange = (current: number) => {
  historyCurrentPage.value = current
  fetchAuditHistory()
}

// 处理选项卡切换
const handleTabChange = () => {
  // 延迟执行，确保activeTab已经更新
  setTimeout(() => {
    if (activeTab.value === 'pending') {
      fetchAuditList()
    } else if (activeTab.value === 'history') {
      fetchAuditHistory()
    } else if (activeTab.value === 'user') {
      fetchUserList()
    } else if (activeTab.value === 'announcement') {
      fetchNoticeList()
    } else if (activeTab.value === 'report') {
      fetchReportList()
    } else if (activeTab.value === 'stats') {
      fetchOverviewData()
    }
  }, 0)
}

// 获取用户列表
const fetchUserList = async () => {
  loading.value = true
  try {
    const response = await getAdminUserList({
      pageNum: userCurrentPage.value,
      pageSize: userPageSize.value,
      keyword: userSearchKeyword.value,
      status: userStatusFilter.value === '-1' ? undefined : parseInt(userStatusFilter.value)
    })
    if (response.code === 200) {
      userList.value = response.data.records || []
      userTotal.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取用户列表失败')
    }
  } catch (error) {
    ElMessage.error('获取用户列表失败，请重试')
    console.error('获取用户列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 处理用户状态切换
const handleToggleUserStatus = async (userId: number, isDisable: boolean) => {
  try {
    let response
    if (isDisable) {
      // 当前是禁用状态，调用启用接口
      response = await enableUser(userId)
    } else {
      // 当前是正常状态，调用禁用接口
      response = await disableUser(userId)
    }
    if (response.code === 200) {
      ElMessage.success(isDisable ? '启用成功' : '禁用成功')
      fetchUserList()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试')
    console.error('切换用户状态失败:', error)
  }
}

// 处理选择变化
const handleSelectionChange = (val: any[]) => {
  selectedUsers.value = val
}

// 批量禁用用户
const handleBatchDisable = async () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请选择要禁用的用户')
    return
  }
  
  try {
    const userIds = selectedUsers.value.map(user => user.userId)
    const response = await batchDisableUsers(userIds)
    if (response.code === 200) {
      ElMessage.success('批量禁用成功')
      fetchUserList()
      selectedUsers.value = []
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试')
    console.error('批量禁用用户失败:', error)
  }
}

// 批量启用用户
const handleBatchEnable = async () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请选择要启用的用户')
    return
  }
  
  try {
    const userIds = selectedUsers.value.map(user => user.userId)
    const response = await batchEnableUsers(userIds)
    if (response.code === 200) {
      ElMessage.success('批量启用成功')
      fetchUserList()
      selectedUsers.value = []
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试')
    console.error('批量启用用户失败:', error)
  }
}

// 批量重置密码
const handleBatchResetPassword = async () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请选择要重置密码的用户')
    return
  }
  
  try {
    const userIds = selectedUsers.value.map(user => user.userId)
    const response = await batchResetPassword(userIds)
    if (response.code === 200) {
      ElMessage.success('批量重置密码成功')
      selectedUsers.value = []
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试')
    console.error('批量重置密码失败:', error)
  }
}

// 处理用户列表分页大小变化
const handleUserSizeChange = (size: number) => {
  userPageSize.value = size
  fetchUserList()
}

// 处理用户列表当前页码变化
const handleUserCurrentChange = (current: number) => {
  userCurrentPage.value = current
  fetchUserList()
}

// 获取公告列表
const fetchNoticeList = async () => {
  loading.value = true
  try {
    const response = await getAdminNoticeList({
      pageNum: noticeCurrentPage.value,
      pageSize: noticePageSize.value,
      status: noticeStatus.value ? Number(noticeStatus.value) : undefined,
      keyword: noticeKeyword.value
    })
    if (response.code === 200 && response.data) {
      noticeList.value = response.data.records || []
      noticeTotal.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取公告列表失败')
    }
  } catch (error) {
    ElMessage.error('获取公告列表失败，请重试')
    console.error('获取公告列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 处理公告列表分页大小变化
const handleNoticeSizeChange = (size: number) => {
  noticePageSize.value = size
  fetchNoticeList()
}

// 处理公告列表当前页码变化
const handleNoticeCurrentChange = (current: number) => {
  noticeCurrentPage.value = current
  fetchNoticeList()
}

// 处理创建公告
const handleCreateNotice = () => {
  noticeForm.value = {
    id: 0,
    title: '',
    content: '',
    type: 1,
    priority: 0,
    isTop: 0,
    schedulePublishTime: ''
  }
  noticeDialogVisible.value = true
}

// 处理编辑公告
const handleEditNotice = (notice: any) => {
  noticeForm.value = {
    id: notice.id,
    title: notice.title,
    content: notice.content,
    type: notice.type,
    priority: notice.priority,
    isTop: notice.isTop,
    schedulePublishTime: notice.publishTime
  }
  noticeDialogVisible.value = true
}

// 处理发布公告
const handlePublishNotice = async (id: number) => {
  try {
    const response = await publishNotice(id)
    if (response.code === 200) {
      ElMessage.success('发布成功')
      fetchNoticeList()
    } else {
      ElMessage.error(response.message || '发布失败')
    }
  } catch (error) {
    ElMessage.error('发布失败，请重试')
    console.error('发布公告失败:', error)
  }
}

// 处理下架公告
const handleUnpublishNotice = async (id: number) => {
  try {
    const response = await unpublishNotice(id)
    if (response.code === 200) {
      ElMessage.success('下架成功')
      fetchNoticeList()
    } else {
      ElMessage.error(response.message || '下架失败')
    }
  } catch (error) {
    ElMessage.error('下架失败，请重试')
    console.error('下架公告失败:', error)
  }
}

// 处理删除公告
const handleDeleteNotice = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这个公告吗？', '删除公告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await deleteNotice(id)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      fetchNoticeList()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败，请重试')
      console.error('删除公告失败:', error)
    }
  }
}

// 处理保存公告
const handleSaveNotice = async () => {
  try {
    if (!noticeForm.value.title) {
      ElMessage.error('请输入公告标题')
      return
    }
    if (!noticeForm.value.content) {
      ElMessage.error('请输入公告内容')
      return
    }
    
    let response
    if (noticeForm.value.id) {
      // 更新公告
      response = await updateNotice(noticeForm.value)
    } else {
      // 创建公告
      response = await createNotice(noticeForm.value)
    }
    
    if (response.code === 200) {
      ElMessage.success(noticeForm.value.id ? '更新成功' : '创建成功')
      noticeDialogVisible.value = false
      fetchNoticeList()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败，请重试')
    console.error('保存公告失败:', error)
  }
}

// 获取举报列表
const fetchReportList = async () => {
  reportLoading.value = true
  try {
    const response = await getReportList({
      status: reportStatus.value ? parseInt(reportStatus.value) : undefined,
      targetType: reportTargetType.value || undefined,
      keyword: reportKeyword.value || undefined,
      pageNum: reportCurrentPage.value,
      pageSize: reportPageSize.value
    })
    if (response.code === 200 && response.data) {
      reportList.value = response.data.records || []
      reportTotal.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取举报列表失败')
    }
  } catch (error) {
    ElMessage.error('获取举报列表失败')
    console.error('获取举报列表失败:', error)
  } finally {
    reportLoading.value = false
  }
}

// 查看举报详情
const handleViewReportDetail = async (id: number) => {
  try {
    const response = await getReportDetail(id)
    if (response.code === 200) {
      reportDetail.value = response.data
      reportDetailDialogVisible.value = true
    } else {
      ElMessage.error(response.message || '获取举报详情失败')
    }
  } catch (error) {
    ElMessage.error('获取举报详情失败')
    console.error('获取举报详情失败:', error)
  }
}

// 打开举报处理对话框
const handleOpenReportHandleDialog = (id: number) => {
  currentReportId.value = id
  handleStatus.value = 1
  handleResult.value = ''
  reportHandleDialogVisible.value = true
}

// 提交举报处理
const handleSubmitReportHandle = async () => {
  if (!handleResult.value.trim()) {
    ElMessage.error('处理说明不能为空')
    return
  }
  
  try {
    const response = await handleReport({
      id: currentReportId.value,
      status: handleStatus.value,
      handleResult: handleResult.value
    })
    if (response.code === 200) {
      ElMessage.success('处理成功')
      reportHandleDialogVisible.value = false
      fetchReportList()
    } else {
      ElMessage.error(response.message || '处理失败')
    }
  } catch (error) {
    ElMessage.error('处理失败')
    console.error('处理举报失败:', error)
  }
}

// 查看举报内容
const handleViewReportContent = (targetType: string, targetId: number) => {
  if (targetType === 'pet') {
    // 宠物类型跳转到宠物详情页面
    router.push(`/pets/${targetId}`)
  } else if (targetType === 'activity') {
    // 活动类型跳转到活动详情页面
    router.push(`/pets/activity/${targetId}`)
  } else if (targetType === 'user') {
    // 用户类型跳转到用户简介页面
    router.push(`/user/${targetId}`)
  } else if (targetType === 'comment') {
    // 评论类型暂时跳转到宠物首页
    router.push('/pets')
  }
}

// 获取举报状态标签类型
const getReportStatusTagType = (status: number) => {
  switch (status) {
    case 0: return 'warning' // 待处理-黄色
    case 1: return 'success' // 已处理-绿色
    case 2: return 'danger' // 已驳回-红色
    default: return ''
  }
}

// 获取举报状态文本
const getReportStatusText = (status: number) => {
  switch (status) {
    case 0: return '待处理'
    case 1: return '已处理'
    case 2: return '已驳回'
    default: return '未知'
  }
}

// 获取举报目标类型标签类型
const getReportTargetTypeTagType = (targetType: string) => {
  switch (targetType) {
    case 'pet': return 'primary'
    case 'activity': return 'success'
    case 'comment': return 'info'
    case 'user': return 'warning'
    default: return ''
  }
}

// 获取举报目标类型文本
const getReportTargetTypeText = (targetType: string) => {
  switch (targetType) {
    case 'pet': return '宠物'
    case 'activity': return '活动'
    case 'comment': return '评论'
    case 'user': return '用户'
    default: return '未知'
  }
}

// 处理举报列表分页大小变化
const handleReportSizeChange = (size: number) => {
  reportPageSize.value = size
  fetchReportList()
}

// 处理举报列表当前页码变化
const handleReportCurrentChange = (current: number) => {
  reportCurrentPage.value = current
  fetchReportList()
}

// 获取公告类型标签类型
const getNoticeTypeTagType = (type: number) => {
  switch (type) {
    case 1: return 'primary'
    case 2: return 'success'
    case 3: return 'danger'
    default: return ''
  }
}

// 获取公告类型文本
const getNoticeTypeText = (type: number) => {
  switch (type) {
    case 1: return '系统公告'
    case 2: return '活动通知'
    case 3: return '重要提醒'
    default: return ''
  }
}

// 获取优先级标签类型
const getPriorityTagType = (priority: number) => {
  switch (priority) {
    case 1: return 'warning'
    case 2: return 'danger'
    default: return ''
  }
}

// 获取优先级文本
const getPriorityText = (priority: number) => {
  switch (priority) {
    case 1: return '重要'
    case 2: return '紧急'
    default: return ''
  }
}

// 获取公告状态标签类型
const getNoticeStatusTagType = (status: number) => {
  switch (status) {
    case 0: return 'info'
    case 1: return 'success'
    case 2: return 'warning'
    default: return ''
  }
}

// 获取公告状态文本
const getNoticeStatusText = (status: number) => {
  switch (status) {
    case 0: return '草稿'
    case 1: return '已发布'
    case 2: return '已下线'
    default: return ''
  }
}

// 页面加载时获取审核列表
onMounted(() => {
  fetchAuditList()
  
  // 设置默认日期
  const today = new Date()

  // 日报：默认今天
  dayDate.value = today.toISOString().split('T')[0]

  // 周报：默认本周（Element Plus week 取周四）
  const thursday = new Date(today)
  thursday.setDate(today.getDate() + (4 - today.getDay()))
  weekDate.value = thursday.toISOString().split('T')[0]

  // 月报：默认本月
  monthDate.value = today.toISOString().substring(0, 7)

  // 年报：默认今年
  yearDate.value = today.getFullYear().toString()
  
  const customStart = new Date(today)
  customStart.setDate(today.getDate() - 6)
  customDateRange.value = [customStart, today]
  
  // 初始化时查询数据
  fetchOverviewData()
  
  // 监听窗口大小变化，调整图表大小
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  // 清理事件监听器
  window.removeEventListener('resize', handleResize)
  // 销毁图表实例
  if (trendChart) {
    trendChart.dispose()
  }
})

// 处理窗口大小变化
const handleResize = () => {
  if (trendChart) {
    trendChart.resize()
  }
}

// 获取统计概览数据
const fetchOverviewData = async () => {
  // 根据不同周期验证日期
  if (period.value === 'day' && !dayDate.value) {
    ElMessage.error('请选择日期')
    return
  } else if (period.value === 'week' && !weekDate.value) {
    ElMessage.error('请选择周')
    return
  } else if (period.value === 'month' && !monthDate.value) {
    ElMessage.error('请选择月')
    return
  } else if (period.value === 'year' && !yearDate.value) {
    ElMessage.error('请选择年')
    return
  } else if (period.value === 'custom' && !customDateRange.value) {
    ElMessage.error('请选择日期范围')
    return
  }
  
  loading.value = true
  trendLoading.value = true
  try {
    let params: any = {}
    
    switch (period.value) {
      case 'day':
        params.type = 'daily'
        params.date = dayDate.value
        break
      case 'week':
        params.type = 'weekly'
        // 计算所选日期所在周的周日作为结束日期
        const selectedWeekDate = new Date(weekDate.value)
        // 计算到周日的天数（周日为0，其他为1-6）
        const daysToSunday = 7 - selectedWeekDate.getDay()
        // 设置为周日
        selectedWeekDate.setDate(selectedWeekDate.getDate() + daysToSunday)
        params.date = selectedWeekDate.toISOString().split('T')[0]
        console.log('周统计日期:', params.date)
        break
      case 'month':
        params.type = 'monthly'
        params.month = monthDate.value
        break
      case 'year':
        params.type = 'yearly'
        params.year = parseInt(yearDate.value)
        break
      case 'custom':
        params.type = 'range'
        const [start, end] = customDateRange.value!
        params.startDate = start.toISOString().split('T')[0]
        params.endDate = end.toISOString().split('T')[0]
        break
      default:
        ElMessage.error('无效的统计周期')
        return
    }
    
    // 获取主数据
    console.log('=== 开始获取统计数据 ===')
    console.log('请求参数:', params)
    const response = await getStatistics(params)
    
    console.log('=== 数据获取完成 ===')
    console.log('响应数据:', response)
    
    if (response.code === 200) {
      overviewData.value = response.data
      console.log('=== 数据获取成功 ===')
      console.log('overviewData.value:', overviewData.value)
      console.log('trendData:', overviewData.value?.trendData)
      
      // 确保DOM渲染完成后初始化图表
      await nextTick()
      console.log('DOM渲染完成，开始初始化图表')
      
      // 确保图表容器存在
      if (trendChartRef.value) {
        console.log('图表容器存在，初始化图表')
        initTrendChart()
      } else {
        console.log('图表容器不存在，延迟初始化')
        // 延迟一秒后再次尝试
        setTimeout(() => {
          if (trendChartRef.value) {
            console.log('延迟后图表容器存在，初始化图表')
            initTrendChart()
          } else {
            console.log('延迟后图表容器仍不存在')
          }
        }, 1000)
      }
    } else {
      console.log('=== 数据获取失败 ===')
      console.log('错误信息:', response.message)
    }
  } catch (error) {
    console.error('获取统计概览失败:', error)
    ElMessage.error('获取统计数据失败，请重试')
  } finally {
    loading.value = false
    trendLoading.value = false
  }
}

// 辅助函数：获取趋势数据的开始日期
const getStartDateForTrend = (period: string): string => {
  const today = new Date()
  switch (period) {
    case 'day':
    case 'week':
    case 'month':
      // 最近30天
      const start = new Date(today)
      start.setDate(today.getDate() - 30)
      return start.toISOString().split('T')[0]
    case 'year':
      // 最近12个月
      const startYear = new Date(today)
      startYear.setFullYear(today.getFullYear() - 1)
      return startYear.toISOString().split('T')[0]
    default:
      const startDefault = new Date(today)
      startDefault.setDate(today.getDate() - 30)
      return startDefault.toISOString().split('T')[0]
  }
}

// 辅助函数：获取趋势数据的结束日期
const getEndDateForTrend = (): string => {
  const today = new Date()
  return today.toISOString().split('T')[0]
}

// 处理周期切换
const handlePeriodChange = () => {
  // 设置默认日期
  const today = new Date()

  if (period.value === 'day') {
    // 默认今天
    dayDate.value = today.toISOString().split('T')[0]
  } else if (period.value === 'week') {
    // 默认本周（Element Plus week 取周四）
    const thursday = new Date(today)
    thursday.setDate(today.getDate() + (4 - today.getDay()))
    weekDate.value = thursday.toISOString().split('T')[0]
  } else if (period.value === 'month') {
    // 默认本月
    monthDate.value = today.toISOString().substring(0, 7)
  } else if (period.value === 'year') {
    // 默认今年
    yearDate.value = today.getFullYear().toString()
  } else if (period.value === 'custom') {
    // 默认最近7天
    const startDate = new Date(today)
    startDate.setDate(today.getDate() - 6)
    customDateRange.value = [startDate, today]
  }

  // 自动查询数据
  fetchOverviewData()
}

// 日期变化时的处理
const handleDateChange = (value: any) => {
  console.log(`${period.value} 日期变化:`, value)
}

// 获取实时统计数据
const fetchRealtimeData = async () => {
  realtimeLoading.value = true
  try {
    const response = await getRealtime()
    if (response.code === 200) {
      realtimeData.value = response.data
    }
  } catch (error) {
    console.error('获取实时统计失败:', error)
  } finally {
    realtimeLoading.value = false
  }
}

// 初始化趋势图表
const initTrendChart = () => {
  console.log('=== initTrendChart 开始 ===')
  console.log('trendChartRef.value:', trendChartRef.value)
  
  // 检查容器高度
  if (trendChartRef.value) {
    const rect = trendChartRef.value.getBoundingClientRect()
    console.log('图表容器尺寸:', rect.width, 'x', rect.height)
  }
  
  console.log('overviewData.value:', overviewData.value)
  console.log('trendData:', overviewData.value?.trendData)
  
  if (!trendChartRef.value || !overviewData.value) {
    console.log('条件不满足，退出')
    return
  }
  
  // 确保容器有足够的高度
  if (trendChartRef.value.clientHeight === 0) {
    console.log('容器高度为0，设置默认高度')
    trendChartRef.value.style.height = '400px'
  }
  
  // 先销毁旧实例
  if (trendChart) {
    trendChart.dispose()
    console.log('旧图表实例已销毁')
  }
  
  try {
    // 尝试获取已存在的实例
    const existingChart = echarts.getInstanceByDom(trendChartRef.value)
    if (existingChart) {
      trendChart = existingChart
      console.log('使用已存在的图表实例')
    } else {
      // 创建新实例
      trendChart = echarts.init(trendChartRef.value)
      console.log('echarts 实例创建成功:', trendChart)
    }
  } catch (error) {
    console.error('创建echarts实例失败:', error)
    return
  }
  
  // 准备图表数据
  let dates = []
  let dauList = []
  let newUserList = []
  let newPostList = []
  let newCommentList = []
  
  // 检查是否有趋势数据
  if (overviewData.value.trendData) {
    console.log('有趋势数据，使用完整的趋势数据')
    // 周、月、年统计都有完整的趋势数据
    dates = overviewData.value.trendData.dates || []
    dauList = overviewData.value.trendData.dauList || []
    newUserList = overviewData.value.trendData.newUserList || []
    newPostList = overviewData.value.trendData.newPostList || []
    newCommentList = overviewData.value.trendData.newCommentList || []
    
    // 检查是否有newLikeList数据
    if (overviewData.value.trendData.newLikeList) {
      console.log('有newLikeList数据')
    }
  } else {
    console.log('无趋势数据，创建单数据点图表')
    // 单个周期统计（日/周/月/年），创建单数据点图表
    let periodLabel = ''
    if (period.value === 'day') {
      periodLabel = dayDate.value
    } else if (period.value === 'week') {
      periodLabel = overviewData.value.weekRange || weekDate.value
    } else if (period.value === 'month') {
      periodLabel = overviewData.value.month || monthDate.value
    } else if (period.value === 'year') {
      periodLabel = overviewData.value.year || yearDate.value
    }
    
    dates = [periodLabel]
    
    // 从不同的响应结构中提取数据
    dauList = [overviewData.value.dau || overviewData.value.activeUserCount || overviewData.value.avgDailyActiveUsers || 0]
    newUserList = [overviewData.value.newUserCount || overviewData.value.totalNewUsers || 0]
    
    // 计算新增帖子数
    const postCount = (overviewData.value.newPetPostCount || 0) + 
                     (overviewData.value.newActivityCount || 0) + 
                     (overviewData.value.newDailyPostCount || 0) + 
                     (overviewData.value.totalNewPosts || 0)
    newPostList = [postCount]
    
    newCommentList = [overviewData.value.newCommentCount || overviewData.value.totalNewComments || 0]
  }
  
  console.log('图表数据:', { dates, dauList, newUserList, newPostList, newCommentList })
  
  // 即使数据为空，也显示一个空图表
  if (dates.length === 0) {
    dates = ['无数据']
    dauList = [0]
    newUserList = [0]
    newPostList = [0]
    newCommentList = [0]
  }
  
  const option = {
    title: {
      text: '数据趋势',
      left: 'center',
      top: 10
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        label: {
          backgroundColor: '#6a7985'
        }
      }
    },
    legend: {
      data: ['DAU', '新增用户', '新增帖子', '新增评论', '新增点赞'],
      top: 40
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: [
      {
        type: 'category',
        boundaryGap: false,
        data: dates
      }
    ],
    yAxis: [
      {
        type: 'value'
      }
    ],
    series: [
      {
        name: 'DAU',
        type: 'line',
        stack: 'Total',
        areaStyle: {},
        emphasis: {
          focus: 'series'
        },
        data: dauList
      },
      {
        name: '新增用户',
        type: 'line',
        stack: 'Total',
        emphasis: {
          focus: 'series'
        },
        data: newUserList
      },
      {
        name: '新增帖子',
        type: 'line',
        stack: 'Total',
        emphasis: {
          focus: 'series'
        },
        data: newPostList
      },
      {
        name: '新增评论',
        type: 'line',
        stack: 'Total',
        emphasis: {
          focus: 'series'
        },
        data: newCommentList
      },
      {
        name: '新增点赞',
        type: 'line',
        stack: 'Total',
        emphasis: {
          focus: 'series'
        },
        data: overviewData.value.trendData?.newLikeList || []
      }
    ]
  }
  
  console.log('设置图表选项')
  console.log('图表数据:', { dates, dauList, newUserList, newPostList, newCommentList })
  console.log('图表选项:', option)
  
  try {
    // 确保容器有足够的高度
    if (trendChartRef.value.clientHeight === 0) {
      trendChartRef.value.style.height = '400px'
      console.log('设置容器高度为400px')
    }
    
    // 销毁旧实例
    if (trendChart) {
      trendChart.dispose()
      console.log('销毁旧图表实例')
    }
    
    // 创建新实例
    trendChart = echarts.init(trendChartRef.value)
    console.log('创建新图表实例:', trendChart)
    
    // 设置图表选项
    trendChart.setOption(option)
    console.log('图表设置完成')
    
    // 手动触发resize，确保图表正确显示
    setTimeout(() => {
      if (trendChart) {
        trendChart.resize()
        console.log('图表 resize 完成')
      }
    }, 100)
  } catch (error) {
    console.error('设置图表选项失败:', error)
  }
}

// 手动测试图表
let testChartInstance = null
const testChart = () => {
  console.log('=== 手动测试图表 ===')
  console.log('trendChartRef.value:', trendChartRef.value)
  
  if (trendChartRef.value) {
    console.log('容器存在，创建测试图表')
    console.log('容器尺寸:', trendChartRef.value.clientWidth, 'x', trendChartRef.value.clientHeight)
    
    try {
      // 先销毁旧实例
      if (testChartInstance) {
        testChartInstance.dispose()
        console.log('旧实例已销毁')
      }
      
      // 创建新实例
      testChartInstance = echarts.init(trendChartRef.value)
      console.log('echarts实例创建成功:', testChartInstance)
      
      const testOption = {
        title: {
          text: '测试图表',
          left: 'center'
        },
        tooltip: {
          trigger: 'axis'
        },
        xAxis: {
          type: 'category',
          data: ['1月', '2月', '3月', '4月', '5月', '6月']
        },
        yAxis: {
          type: 'value'
        },
        series: [
          {
            data: [120, 200, 150, 80, 70, 110],
            type: 'line'
          }
        ]
      }
      
      testChartInstance.setOption(testOption)
      console.log('测试图表创建成功')
      
      // 手动触发resize
      setTimeout(() => {
        testChartInstance.resize()
        console.log('测试图表resize完成')
      }, 100)
    } catch (error) {
      console.error('创建测试图表失败:', error)
    }
  } else {
    console.log('容器不存在')
  }
}

// 补录统计数据相关
const backfillDialogVisible = ref(false)
const backfillLoading = ref(false)
const backfillFormRef = ref<any>(null)

// 进度对话框
const progressDialogVisible = ref(false)
const backfillProgress = ref(0)
const backfillSuccessCount = ref(0)
const backfillFailCount = ref(0)
const backfillTotalCount = ref(0)
const currentBackfillDate = ref('')
const backfillErrorMessage = ref('')

// 补录表单数据
const backfillForm = reactive({
  backfillType: 'single', // single / range
  singleDate: '',
  startDate: '',
  endDate: ''
})

// 补录表单验证规则
const backfillRules = {
  singleDate: [
    {
      required: true,
      message: '请选择补录日期',
      trigger: 'change',
      validator: (rule: any, value: any, callback: any) => {
        if (backfillForm.backfillType === 'single' && !value) {
          callback(new Error('请选择补录日期'))
        } else {
          callback()
        }
      }
    }
  ],
  startDate: [
    {
      required: true,
      message: '请选择开始日期',
      trigger: 'change',
      validator: (rule: any, value: any, callback: any) => {
        if (backfillForm.backfillType === 'range' && !value) {
          callback(new Error('请选择开始日期'))
        } else {
          callback()
        }
      }
    }
  ],
  endDate: [
    {
      required: true,
      message: '请选择结束日期',
      trigger: 'change',
      validator: (rule: any, value: any, callback: any) => {
        if (backfillForm.backfillType === 'range') {
          if (!value) {
            callback(new Error('请选择结束日期'))
          } else if (backfillForm.startDate && value < backfillForm.startDate) {
            callback(new Error('结束日期不能早于开始日期'))
          } else {
            callback()
          }
        } else {
          callback()
        }
      }
    }
  ]
}

// 计算补录表单是否有效
const isBackfillFormValid = computed(() => {
  if (backfillForm.backfillType === 'single') {
    return !!backfillForm.singleDate
  } else {
    return !!backfillForm.startDate && !!backfillForm.endDate && 
           backfillForm.endDate >= backfillForm.startDate
  }
})

// 禁止选择未来日期
const disabledDate = (date: Date) => {
  return date > new Date()
}

// 打开补录统计数据对话框
const openBackfillDialog = () => {
  resetBackfillForm()
  backfillDialogVisible.value = true
}

// 重置补录表单
const resetBackfillForm = () => {
  backfillForm.backfillType = 'single'
  backfillForm.singleDate = ''
  backfillForm.startDate = ''
  backfillForm.endDate = ''
  backfillErrorMessage.value = ''
  if (backfillFormRef.value) {
    backfillFormRef.value.resetFields()
  }
}

// 处理补录方式切换
const handleBackfillTypeChange = () => {
  if (backfillFormRef.value) {
    backfillFormRef.value.clearValidate()
  }
}

// 单日补录
const submitSingleBackfill = async () => {
  try {
    await regenerateStatistics(backfillForm.singleDate)
    ElMessage.success(`补录成功：${backfillForm.singleDate}`)
    return true
  } catch (error: any) {
    ElMessage.error(error.message || `补录失败：${backfillForm.singleDate}`)
    return false
  }
}

// 区间补录（带进度显示）
const submitRangeBackfill = async () => {
  const startDate = backfillForm.startDate
  const endDate = backfillForm.endDate
  
  // 计算总天数
  const start = new Date(startDate)
  const end = new Date(endDate)
  const totalDays = Math.floor((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1
  
  // 初始化进度状态
  progressDialogVisible.value = true
  backfillProgress.value = 0
  backfillSuccessCount.value = 0
  backfillFailCount.value = 0
  backfillTotalCount.value = totalDays
  backfillErrorMessage.value = ''
  
  // 逐日补录
  let currentDate = new Date(startDate)
  let successCount = 0
  let failCount = 0
  
  for (let i = 0; i < totalDays; i++) {
    const dateStr = currentDate.toISOString().split('T')[0]
    currentBackfillDate.value = dateStr
    
    try {
      await regenerateStatistics(dateStr)
      successCount++
      backfillSuccessCount.value = successCount
    } catch (error: any) {
      failCount++
      backfillFailCount.value = failCount
      console.error(`补录失败 ${dateStr}:`, error)
    }
    
    backfillProgress.value = Math.floor(((i + 1) / totalDays) * 100)
    currentDate.setDate(currentDate.getDate() + 1)
  }
  
  if (failCount > 0) {
    backfillErrorMessage.value = `补录完成，成功 ${successCount} 条，失败 ${failCount} 条`
  } else {
    backfillErrorMessage.value = ''
  }
  
  // 刷新数据
  await fetchOverviewData()
  await fetchRealtimeData()
  
  return failCount === 0
}

// 提交补录
const submitBackfill = async () => {
  if (!backfillFormRef.value) return
  
  await backfillFormRef.value.validate(async (valid: boolean) => {
    if (valid) {
      backfillLoading.value = true
      
      try {
        if (backfillForm.backfillType === 'single') {
          await submitSingleBackfill()
          backfillDialogVisible.value = false
          // 刷新数据
          await fetchOverviewData()
          await fetchRealtimeData()
        } else {
          // 区间补录，关闭主对话框，显示进度对话框
          backfillDialogVisible.value = false
          await submitRangeBackfill()
        }
      } catch (error) {
        console.error('补录失败:', error)
        ElMessage.error('补录失败，请重试')
      } finally {
        backfillLoading.value = false
      }
    }
  })
}

// 关闭进度对话框
const closeProgressDialog = () => {
  progressDialogVisible.value = false
  // 重置进度状态
  backfillProgress.value = 0
  backfillSuccessCount.value = 0
  backfillFailCount.value = 0
  backfillTotalCount.value = 0
  currentBackfillDate.value = ''
  backfillErrorMessage.value = ''
}

</script>

<style scoped>
.audit-container {
  padding: 15px;
}

.audit-card {
  margin-bottom: 20px;
}

.card-header {
  padding: 10px 0;
}

.audit-title {
  font-size: 18px;
  font-weight: bold;
  margin: 0;
  color: #303133;
}

/* 类型切换容器 */
.type-tabs-container {
  margin: 15px 0;
}

.type-tabs {
  width: 100%;
}

:deep(.el-tabs__nav) {
  display: flex;
  width: 100%;
}

:deep(.el-tabs__item) {
  flex: 1;
  text-align: center;
}

/* 优化选项卡高亮效果 */
:deep(.el-tabs__active-bar) {
  background-color: #409eff;
}

:deep(.el-tabs__item.is-active) {
  color: #409eff;
  font-weight: 500;
}

.audit-content {
  padding: 10px 0;
}

/* 操作功能区样式 */
.operation-bar {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px 0;
  margin-bottom: 20px;
  flex-wrap: wrap;
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
}

.search-input {
  width: 280px;
}

.filter-select {
  width: 140px;
}

.date-picker {
  width: 180px !important; /* 减短日期选择器的宽度 */
}

.search-btn {
  flex-shrink: 0;
}

/* 批量操作栏 */
.batch-operations {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 20px;
  margin-bottom: 20px;
  background-color: #ecf5ff;
  border-radius: 8px;
}

.batch-buttons {
  display: flex;
  gap: 10px;
}

/* 审核列表 */
.audit-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(48%, 1fr));
  gap: 20px;
}

.audit-card-item {
  display: flex;
  flex-direction: column;
  gap: 15px;
  padding: 20px;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  height: 100%;
}

.audit-card-item:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.item-checkbox {
  margin-top: 5px;
}

.item-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.item-header {
  margin-bottom: 10px;
}

.item-tags {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}

.type-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
  white-space: nowrap;
}

.type-tag.adopt {
  background-color: #67c23a;
}

.type-tag.help {
  background-color: #e6a23c;
}

.type-tag.activity {
  background-color: #409eff;
}

.type-tag.daily {
  background-color: #909399;
}

.status-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: white;
  white-space: nowrap;
}

.status-tag.status-0 {
  background-color: #909399;
}

.status-tag.status-1 {
  background-color: #67c23a;
}

.status-tag.status-2 {
  background-color: #f56c6c;
}

.item-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 10px 0;
  color: #303133;
}

.item-meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 14px;
  color: #909399;
  flex-wrap: wrap;
  gap: 5px;
}

.item-info {
  margin-bottom: 10px;
  font-size: 14px;
  color: #606266;
  display: flex;
  gap: 15px;
  flex-wrap: wrap;
}

.item-content-text {
  margin-bottom: 15px;
  font-size: 14px;
  line-height: 1.5;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  flex: 1;
}

/* 图片预览 */
.item-images {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
  gap: 10px;
}

.image-preview {
  display: flex;
  gap: 10px;
}

.preview-image {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.preview-image:hover {
  transform: scale(1.05);
}

.image-count {
  font-size: 14px;
  color: #909399;
  margin-left: 5px;
}

.item-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 15px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

/* 历史记录列表 */
.history-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.history-card {
  padding: 15px;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.history-header {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.history-status {
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  color: white;
}

.history-status.status-1 {
  background-color: #67c23a;
}

.history-status.status-2 {
  background-color: #f56c6c;
}

.history-title {
  flex: 1;
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.history-time {
  font-size: 12px;
  color: #909399;
}

.history-meta {
  display: flex;
  gap: 20px;
  margin-bottom: 10px;
  font-size: 14px;
  color: #606266;
}

.history-reason {
  font-size: 14px;
  color: #f56c6c;
  padding: 10px;
  background-color: #fef0f0;
  border-radius: 4px;
  margin-top: 10px;
}

/* 批量拒绝对话框 */
.batch-reject-content {
  padding: 10px 0;
}

.selected-count {
  font-size: 14px;
  margin-bottom: 15px;
  color: #606266;
}

.common-reasons {
  margin-top: 20px;
}

.reasons-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 10px;
  color: #606266;
}

.reasons-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.empty-section {
  padding: 60px 20px;
  text-align: center;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 用户信息样式 */
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-details {
  flex: 1;
  min-width: 0;
}

.nickname {
  font-weight: 500;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.username {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 加载状态 */
.loading-container {
  padding: 40px 0;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .audit-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .audit-container {
    padding: 10px;
  }
  
  .operation-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  
  .search-input {
    width: 100%;
  }
  
  .filter-select {
    width: 100%;
  }
  
  .audit-card-item {
    flex-direction: column;
    gap: 10px;
  }
  
  .item-checkbox {
    align-self: flex-start;
  }
  
  .item-meta {
    flex-direction: column;
    gap: 5px;
    align-items: flex-start;
  }
  
  .item-info {
    flex-direction: column;
    gap: 5px;
  }
  
  .item-actions {
    flex-direction: column;
    align-items: stretch;
  }
  
  .batch-operations {
    flex-direction: column;
    gap: 10px;
    align-items: stretch;
  }
  
  .batch-buttons {
    justify-content: center;
  }
  
  .pagination-container {
    flex-direction: column;
    gap: 10px;
    align-items: stretch;
  }
  
  .realtime-data {
    flex-direction: column;
    gap: 10px;
  }
  
  .statistics-overview {
    grid-template-columns: 1fr;
  }
}

/* 数据统计相关样式 */
.statistics-card,
.realtime-card,
.trend-card {
  margin-top: 20px;
}

.filter-container {
  display: flex;
  align-items: center;
}

.statistics-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.stat-card {
  background-color: #f9f9f9;
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s, box-shadow 0.3s;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 10px;
}

.stat-label {
  font-size: 14px;
  color: #606266;
}

.realtime-data {
  display: flex;
  gap: 40px;
  margin-top: 20px;
}

.realtime-item {
  display: flex;
  align-items: center;
  font-size: 16px;
}

.realtime-label {
  color: #606266;
  margin-right: 10px;
}

.realtime-value {
  font-weight: bold;
  color: #409eff;
  font-size: 18px;
}

.chart-container {
  width: 100%;
  height: 400px;
  margin-top: 20px;
}

.period-tabs {
  margin-bottom: 20px;
}

.period-tabs .el-tabs__header {
  margin-bottom: 15px;
}

.period-tabs .el-tabs__nav {
  display: flex;
  justify-content: center;
}

.period-tabs .el-tabs__item {
  font-size: 14px;
  padding: 0 20px;
}

.tip-text {
  color: #909399;
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
}

/* 周期切换样式 */
.period-switch {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.period-switch .el-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.date-filter-area {
  background-color: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.filter-row {
  display: flex;
  align-items: center;
}

.filter-label {
  font-weight: 500;
  margin-right: 12px;
  color: #606266;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .filter-row {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .filter-label {
    margin-bottom: 5px;
  }
  
  .filter-row .el-button {
    margin-top: 10px;
  }
  
  .period-switch .el-radio-group {
    flex-direction: column;
  }
  
  .period-switch .el-radio-button {
    width: 100%;
  }
}
/* 补录对话框样式 */
.date-range-hint {
  background-color: #ecf5ff;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 12px;
  color: #409eff;
  display: flex;
  align-items: center;
  gap: 8px;
}

.tip-text {
  background-color: #fdf6ec;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 12px;
  color: #e6a23c;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 进度对话框样式 */
.progress-container {
  padding: 20px 0;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  color: #606266;
}

.progress-stats {
  display: flex;
  justify-content: space-around;
  margin-top: 16px;
  font-size: 14px;
  color: #606266;
}

.progress-stats span {
  padding: 4px 12px;
  background-color: #f5f7fa;
  border-radius: 16px;
}

.progress-error {
  margin-top: 16px;
}
</style>
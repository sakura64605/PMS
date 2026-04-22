package com.hongjie.pms.modules.report.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.punishment.scheduler.DelayTaskService;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.comment.entity.Comment;
import com.hongjie.pms.modules.comment.mapper.CommentMapper;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.report.dto.ReportListDto;
import com.hongjie.pms.modules.report.dto.ReportRequest;
import com.hongjie.pms.modules.report.entity.ReportRecord;
import com.hongjie.pms.modules.report.enums.ReportStatus;
import com.hongjie.pms.modules.report.mapper.ReportRecordMapper;
import com.hongjie.pms.modules.report.service.ReportService;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRecordMapper reportRecordMapper;
    private final UserMapper userMapper;
    private final PetPostMapper petPostMapper;
    private final ActivityMapper activityMapper;
    private final CommentMapper commentMapper;
    private final DelayTaskService delayTaskService;

    @Override
    @Transactional
    public void submit(Long userId, ReportRequest request) {
        // 检查是否重复举报
        LambdaQueryWrapper<ReportRecord> wrapper = new LambdaQueryWrapper<ReportRecord>()
                .eq(ReportRecord::getReporterId, userId)
                .eq(ReportRecord::getTargetType, request.getTargetType())
                .eq(ReportRecord::getTargetId, request.getTargetId())
                .eq(ReportRecord::getStatus, ReportStatus.PENDING.getCode());
        Long count = reportRecordMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "您已举报过该内容，请等待处理");
        }

        ReportRecord record = new ReportRecord();
        record.setReporterId(userId);
        record.setTargetType(request.getTargetType());
        record.setTargetId(request.getTargetId());
        record.setReason(request.getReason());
        record.setStatus(ReportStatus.PENDING.getCode());
        reportRecordMapper.insert(record);

        log.info("用户{}举报了{}:{}", userId, request.getTargetType(), request.getTargetId());
    }

    @Override
    public IPage<ReportListDto> getList(Integer status, String targetType, Integer pageNum, Integer pageSize) {
        Page<ReportRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ReportRecord> wrapper = new LambdaQueryWrapper<ReportRecord>();

        if (status != null) {
            wrapper.eq(ReportRecord::getStatus, status);
        }
        if (targetType != null && !targetType.isEmpty()) {
            wrapper.eq(ReportRecord::getTargetType, targetType);
        }
        wrapper.orderByDesc(ReportRecord::getCreateTime);

        IPage<ReportRecord> recordPage = reportRecordMapper.selectPage(page, wrapper);

        List<ReportListDto> records = recordPage.getRecords().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        Page<ReportListDto> resultPage = new Page<>(pageNum, pageSize, recordPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    @Transactional
    public void handle(Long reportId, Long adminId, Integer status, String handleResult) {
        ReportRecord record = reportRecordMapper.selectById(reportId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报记录不存在");
        }
        if (record.getStatus() != ReportStatus.PENDING.getCode()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该举报已处理");
        }

        record.setStatus(status);
        record.setHandlerId(adminId);
        record.setHandleResult(handleResult);
        record.setHandleTime(LocalDateTime.now());
        reportRecordMapper.updateById(record);

        // 如果举报成立，处理目标内容
        if (status == ReportStatus.HANDLED.getCode()) {
            handleTargetContent(record.getTargetType(), record.getTargetId());
        }

        log.info("管理员{}处理举报{}，结果：{}", adminId, reportId, handleResult);
    }

    @Override
    public ReportListDto getDetail(Long reportId) {
        ReportRecord record = reportRecordMapper.selectById(reportId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报记录不存在");
        }
        return convertToDto(record);
    }

    // ==================== 私有方法 ====================

    private void handleTargetContent(String targetType, Long targetId) {
        switch (targetType) {
            case "pet":
                PetPost pet = petPostMapper.selectById(targetId);
                if (pet != null && pet.getStatus() == 1) {
                    pet.setStatus(3); // 下架
                    petPostMapper.updateById(pet);
                }
                break;
            case "activity":
                Activity activity = activityMapper.selectById(targetId);
                if (activity != null && activity.getStatus() == 1) {
                    activity.setStatus(4); // 下架
                    activityMapper.updateById(activity);
                }
                break;
            case "comment":
                Comment comment = commentMapper.selectById(targetId);
                if (comment != null) {
                    comment.setStatus(0); // 删除
                    commentMapper.updateById(comment);
                    Long userId = comment.getUserId();
                    User user = userMapper.selectById(userId);
                    user.setIsMuted(1);
                    LocalDateTime muteEndTime = LocalDateTime.now().plusDays(1);
                    user.setMuteEndTime(muteEndTime);
                    delayTaskService.addTask("MUTED_PUNISHMENT_END", userId, muteEndTime);
                }
                break;
            case "user":
                User user = userMapper.selectById(targetId);
                if (user != null && user.getStatus() == 1) {
                    user.setStatus(0); // 禁用用户
                    userMapper.updateById(user);
                    LocalDateTime banSignupEndTime = LocalDateTime.now().plusDays(1);
                    delayTaskService.addTask("BANNED_PUNISHMENT_END", user.getId(), banSignupEndTime);
                }
                break;
        }
    }

    private ReportListDto convertToDto(ReportRecord record) {
        // 获取举报人信息
        User reporter = userMapper.selectById(record.getReporterId());
        String reporterName = reporter != null ? reporter.getUserName() : "未知";

        // 获取目标内容标题
        String targetTitle = getTargetTitle(record.getTargetType(), record.getTargetId());

        // 获取处理人信息
        String handlerName = "";
        if (record.getHandlerId() != null) {
            User handler = userMapper.selectById(record.getHandlerId());
            handlerName = handler != null ? handler.getUserName() : "";
        }

        return ReportListDto.builder()
                .id(record.getId())
                .targetType(record.getTargetType())
                .targetTypeDesc(getTargetTypeDesc(record.getTargetType()))
                .targetId(record.getTargetId())
                .targetTitle(targetTitle)
                .reporterName(reporterName)
                .reason(record.getReason())
                .status(record.getStatus())
                .statusDesc(getStatusDesc(record.getStatus()))
                .handleResult(record.getHandleResult())
                .handlerName(handlerName)
                .createTime(record.getCreateTime())
                .handleTime(record.getHandleTime())
                .build();
    }

    private String getTargetTitle(String targetType, Long targetId) {
        switch (targetType) {
            case "pet":
                PetPost pet = petPostMapper.selectById(targetId);
                return pet != null ? pet.getTitle() : "已删除";
            case "activity":
                Activity activity = activityMapper.selectById(targetId);
                return activity != null ? activity.getTitle() : "已删除";
            case "comment":
                Comment comment = commentMapper.selectById(targetId);
                return comment != null ? comment.getContent() : "已删除";
            case "user":
                User user = userMapper.selectById(targetId);
                return user != null ? user.getUserName() : "已删除";
            default:
                return "未知";
        }
    }

    private String getTargetTypeDesc(String targetType) {
        switch (targetType) {
            case "pet": return "宠物";
            case "activity": return "活动";
            case "comment": return "评论";
            case "user": return "用户";
            default: return targetType;
        }
    }

    private String getStatusDesc(Integer status) {
        if (status == 0) return "待处理";
        if (status == 1) return "已处理";
        if (status == 2) return "已驳回";
        return "未知";
    }
}
package com.hongjie.pms.modules.report.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.report.dto.ReportListDto;
import com.hongjie.pms.modules.report.dto.ReportRequest;
import com.hongjie.pms.modules.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/pet-system/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 提交举报
     */
    @PostMapping("/submit")
    public CommonResult<String> submit(@Valid @RequestBody ReportRequest request) {
        Long userId = UserContext.getUserId();
        reportService.submit(userId, request);
        return CommonResult.success("举报成功，我们会尽快处理");
    }

    /**
     * 举报列表（管理员）
     */
    @GetMapping("/list")
    public CommonResult<IPage<ReportListDto>> getList(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        checkAdmin();
        return CommonResult.success(reportService.getList(status, targetType, pageNum, pageSize));
    }

    /**
     * 处理举报（管理员）
     */
    @PostMapping("/handle/{id}")
    public CommonResult<String> handle(
            @PathVariable Long id,
            @RequestParam Integer status,
            @RequestParam String handleResult) {
        checkAdmin();
        Long adminId = UserContext.getUserId();
        reportService.handle(id, adminId, status, handleResult);
        return CommonResult.success(status == 1 ? "已处理，内容已下架" : "已驳回");
    }

    /**
     * 举报详情（管理员）
     */
    @GetMapping("/detail/{id}")
    public CommonResult<ReportListDto> getDetail(@PathVariable Long id) {
        checkAdmin();
        return CommonResult.success(reportService.getDetail(id));
    }

    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "无权操作，需要管理员权限");
        }
    }
}
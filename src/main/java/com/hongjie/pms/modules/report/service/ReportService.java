package com.hongjie.pms.modules.report.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.report.dto.ReportListDto;
import com.hongjie.pms.modules.report.dto.ReportRequest;

public interface ReportService {

    void submit(Long userId, ReportRequest request);

    IPage<ReportListDto> getList(Integer status, String targetType, Integer pageNum, Integer pageSize);

    void handle(Long reportId, Long adminId, Integer status, String handleResult);

    ReportListDto getDetail(Long reportId);
}
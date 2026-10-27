package com.hongjie.pms.modules.communication.notice.api.controller;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.platform.pojo.CommonResult;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.entity.Notice;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 管理端编辑详情：允许草稿，不产生阅读记录。 */
@RestController
@RequestMapping("/pet-system/notice/admin")
@RequiredArgsConstructor
public class NoticeAdminDetailController {
    private final NoticeMapper noticeMapper;

    @GetMapping("/{id}")
    public CommonResult<Notice> detail(@PathVariable Long id) {
        if (!UserContext.isAdmin()) throw new BusinessException(ErrorCode.FORBIDDEN);
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return CommonResult.success(notice);
    }
}

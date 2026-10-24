package com.hongjie.pms.refactor.notice.controller;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.notice.entity.Notice;
import com.hongjie.pms.modules.notice.mapper.NoticeMapper;
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

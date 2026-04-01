package com.hongjie.pms.modules.notice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.notice.dto.request.NoticeRequestDto;
import com.hongjie.pms.modules.notice.dto.response.NoticeDetailDto;
import com.hongjie.pms.modules.notice.dto.response.NoticeListDto;
import com.hongjie.pms.modules.notice.service.NoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 公告
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    // ========== 管理员接口 ==========

    /**
     * 创建公告
     * @param request
     * @return
     */
    @PostMapping("/admin/create")
    public CommonResult<NoticeDetailDto> create(@Valid @RequestBody NoticeRequestDto request) {
        log.info("创建公告：{}", request);
        NoticeDetailDto notice = noticeService.create(request);
        return CommonResult.success(notice, "创建成功");
    }

    /**
     * 更新公告
     * @param request
     * @return
     */
    @PutMapping("/admin/update")
    public CommonResult<NoticeDetailDto> update(@Valid @RequestBody NoticeRequestDto request) {
        log.info("更新公告：{}", request);
        NoticeDetailDto notice = noticeService.update(request);
        return CommonResult.success(notice, "更新成功");
    }

    /**
     * 删除公告
     * @param id
     * @return
     */
    @DeleteMapping("/admin/{id}")
    public CommonResult<Void> delete(@PathVariable Long id) {
        log.info("删除公告：id={}", id);
        noticeService.delete(id);
        return CommonResult.success(null, "删除成功");
    }

    /**
     * 发布公告
     * @param id
     * @return
     */
    @PutMapping("/admin/{id}/publish")
    public CommonResult<Void> publish(@PathVariable Long id) {
        log.info("发布公告：id={}", id);
        noticeService.publish(id);
        return CommonResult.success(null, "发布成功");
    }

    /**
     * 撤销发布
     * @param id
     * @return
     */
    @PutMapping("/admin/{id}/unpublish")
    public CommonResult<Void> unpublish(@PathVariable Long id) {
        log.info("撤销发布：id={}", id);
        noticeService.unpublish(id);
        return CommonResult.success(null, "下架成功");
    }

    /**
     * 公告列表（管理员）
     * @param pageNum
     * @param pageSize
     * @param status
     * @param keyword
     * @return
     */
    @GetMapping("/admin/list")
    public CommonResult<IPage<NoticeListDto>> listForAdmin(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        log.info("公告列表（管理员）：pageNum={}, pageSize={}, status={}, keyword={}", pageNum, pageSize, status, keyword);
        IPage<NoticeListDto> page = noticeService.listForAdmin(pageNum, pageSize, status, keyword);
        return CommonResult.success(page);
    }
    
    // ========== 用户端接口 ==========

    /**
     * 公告列表（用户）
     * @param pageNum
     * @param pageSize
     * @return
     */
    @GetMapping("/list")
    public CommonResult<IPage<NoticeListDto>> listForUser(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("公告列表（用户）：pageNum={}, pageSize={}", pageNum, pageSize);
        IPage<NoticeListDto> page = noticeService.listForUser(pageNum, pageSize);
        return CommonResult.success(page);
    }

    /**
     * 公告详情（用户）
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public CommonResult<NoticeDetailDto> getById(@PathVariable Long id) {
        log.info("公告详情（用户）：id={}", id);
        NoticeDetailDto notice = noticeService.getByIdForUser(id);
        return CommonResult.success(notice);
    }

    /**
     * 获取未读公告数量
     * @return
     */
    @GetMapping("/unread-count")
    public CommonResult<Integer> getUnreadCount() {
        log.info("获取未读公告数量");
        int count = noticeService.getUnreadCount();
        return CommonResult.success(count);
    }
}
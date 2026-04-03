package com.hongjie.pms.modules.message.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.message.entity.UserMessage;
import com.hongjie.pms.modules.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 消息控制器
 *
 * @author: hongjie
 * @date: 2020/5/26
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/message")
@RequiredArgsConstructor
public class MessageController {
    
    private final MessageService messageService;
    
    /**
     * 获取未读消息数量
     */
    @GetMapping("/unread-count")
    public CommonResult<Long> getUnreadCount() {
        Long userId = UserContext.getUserId();
        Long count = messageService.getUnreadCount(userId);
        return CommonResult.success(count);
    }
    
    /**
     * 获取消息列表
     */
    @GetMapping("/list")
    public CommonResult<IPage<UserMessage>> getMessageList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String type) {
        Long userId = UserContext.getUserId();
        IPage<UserMessage> page = messageService.getMessageList(userId, pageNum, pageSize, type);
        return CommonResult.success(page);
    }
    
    /**
     * 标记消息为已读
     */
    @PutMapping("/read/{id}")
    public CommonResult<String> markAsRead(@PathVariable Long id) {
        messageService.markAsRead(id);
        return CommonResult.success("已标记为已读");
    }
    
    /**
     * 全部标记为已读
     */
    @PutMapping("/read-all")
    public CommonResult<String> markAllAsRead(@RequestParam(required = false) String type) {
        messageService.markAllAsRead(type);
        return CommonResult.success("已全部标记为已读");
    }
}
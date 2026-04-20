package com.hongjie.pms.modules.privateMessage.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.CommonResult;

import com.hongjie.pms.modules.privateMessage.dto.ConversationDto;
import com.hongjie.pms.modules.privateMessage.dto.MessageDto;
import com.hongjie.pms.modules.privateMessage.dto.SendMessageRequest;
import com.hongjie.pms.modules.privateMessage.service.PrivateMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 私信
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/message/private")
@RequiredArgsConstructor
public class PrivateMessageController {

    private final PrivateMessageService privateMessageService;

    /**
     * 发送私信
     */
    @PostMapping("/send")
    public CommonResult<MessageDto> send(@Valid @RequestBody SendMessageRequest request) {
        Long userId = UserContext.getUserId();
        MessageDto message = privateMessageService.sendMessage(userId, request);
        return CommonResult.success(message);
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public CommonResult<IPage<ConversationDto>> getConversations(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = UserContext.getUserId();
        return CommonResult.success(privateMessageService.getConversationList(userId, pageNum, pageSize));
    }

    /**
     * 获取聊天记录
     */
    @GetMapping("/messages")
    public CommonResult<IPage<MessageDto>> getMessages(
            @RequestParam Long conversationId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = UserContext.getUserId();
        return CommonResult.success(privateMessageService.getMessageList(userId, conversationId, pageNum, pageSize));
    }

    /**
     * 获取未读消息总数
     */
    @GetMapping("/unread-count")
    public CommonResult<Integer> getUnreadCount() {
        Long userId = UserContext.getUserId();
        return CommonResult.success(privateMessageService.getTotalUnreadCount(userId));
    }

    /**
     * 标记会话已读
     */
    @PutMapping("/read/{conversationId}")
    public CommonResult<String> markAsRead(@PathVariable Long conversationId) {
        Long userId = UserContext.getUserId();
        privateMessageService.markConversationRead(userId, conversationId);
        return CommonResult.success("已标记已读");
    }

    /**
     * 创建或获取与某人的会话（点击发消息时调用）
     */
    @GetMapping("/conversation")
    public CommonResult<ConversationDto> getOrCreateConversation(@RequestParam Long otherUserId) {
        Long userId = UserContext.getUserId();
        ConversationDto conversation = privateMessageService.getOrCreateConversation(userId, otherUserId);
        return CommonResult.success(conversation);
    }

    /**
     * 删除会话（删除整个聊天记录）
     */
    @DeleteMapping("/conversation/{conversationId}")
    public CommonResult<String> deleteConversation(@PathVariable Long conversationId) {
        Long userId = UserContext.getUserId();
        privateMessageService.deleteConversation(userId, conversationId);
        return CommonResult.success("删除成功");
    }

    /**
     * 清空聊天记录（保留会话）
     */
    @DeleteMapping("/messages/{conversationId}")
    public CommonResult<String> clearMessages(@PathVariable Long conversationId) {
        Long userId = UserContext.getUserId();
        privateMessageService.clearMessages(userId, conversationId);
        return CommonResult.success("清空成功");
    }
}
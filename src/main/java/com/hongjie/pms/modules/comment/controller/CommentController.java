package com.hongjie.pms.modules.comment.controller;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.annotation.Idempotent;
import com.hongjie.pms.common.annotation.RedisRateLimit;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.comment.dto.request.CommentCreateRequest;
import com.hongjie.pms.modules.comment.dto.response.CommentRespDto;
import com.hongjie.pms.modules.comment.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * 评论
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 获取评论列表
     */
    @GetMapping("/list")
    public CommonResult<IPage<CommentRespDto>> getCommentList(
            @RequestParam String targetType,
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 查询评论列表
        log.info("获取评论列表");
        IPage<CommentRespDto> page = commentService.getCommentList(targetType, targetId, pageNum, pageSize);
        return CommonResult.success(page);
    }

    /**
     * 创建评论
     */
    @RedisRateLimit(key = "createComment", capacity = 5, refillRate = 5, duration = 1, timeUnit = TimeUnit.SECONDS)
    @Idempotent(key = "#request.targetId + '-' + #request.targetType + '-' + #request.content", expire = 300, message = "评论操作正在处理中，请稍后再试")
    @PostMapping("/create")
    public CommonResult<String> createComment(@RequestBody @Valid CommentCreateRequest request) {
        // 创建评论
        log.info("创建评论");
        commentService.createComment(request);
        return CommonResult.success("评论创建成功");
    }

    /**
     * 删除评论
     */
    @Idempotent(key = "#id", expire = 300, message = "删除评论操作正在处理中，请稍后再试")
    @PostMapping("/delete/{id}")
    public CommonResult<String> deleteComment(@PathVariable Long id) {
        // 删除评论
        log.info("删除评论");
        //commentService.deleteComment(id);
        return CommonResult.success("评论删除成功");
    }

}

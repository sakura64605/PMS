package com.hongjie.pms.AI.modules.controller;

import com.hongjie.pms.AI.modules.dto.request.AIUserMemoryRequest;
import com.hongjie.pms.AI.modules.entity.AiUserMemory;
import com.hongjie.pms.AI.modules.service.UserMemoryService;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户自助查看/修改自己的长期记忆。
 *
 * <p>与"对话中自动抽取长期记忆"共用同一张 {@code ai_user_memory} 表，按内容去重，天然不冲突：
 * 手动添加同内容会复用/重启用既有条目；手动删除为软删(status=0)，后续自动抽取不会把它复活。</p>
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/ai/memory")
@RequiredArgsConstructor
public class AIMemoryController {

    private final UserMemoryService userMemoryService;

    /** 查看我的长期记忆 */
    @GetMapping
    public CommonResult<List<AiUserMemory>> list() {
        return CommonResult.success(userMemoryService.listByUser(UserContext.getUserId()));
    }

    /** 手动添加一条长期记忆 */
    @PostMapping
    public CommonResult<AiUserMemory> add(@RequestBody AIUserMemoryRequest request) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new BusinessException("记忆内容不能为空");
        }
        AiUserMemory mem = userMemoryService.addManual(UserContext.getUserId(), request.getContent());
        return CommonResult.success(mem);
    }

    /** 修改我的一条长期记忆（内容/重要度） */
    @PutMapping("/{id}")
    public CommonResult<String> update(@PathVariable Long id, @RequestBody AIUserMemoryRequest request) {
        boolean ok = userMemoryService.updateByUser(
                UserContext.getUserId(), id,
                request != null ? request.getContent() : null,
                request != null ? request.getImportance() : null);
        if (!ok) {
            throw new BusinessException(404, "记忆不存在或无权操作");
        }
        return CommonResult.success("修改成功");
    }

    /** 删除我的一条长期记忆（软删，避免被对话自动抽取复活） */
    @DeleteMapping("/{id}")
    public CommonResult<String> delete(@PathVariable Long id) {
        boolean ok = userMemoryService.deleteByUser(UserContext.getUserId(), id);
        if (!ok) {
            throw new BusinessException(404, "记忆不存在或无权操作");
        }
        return CommonResult.success("删除成功");
    }
}

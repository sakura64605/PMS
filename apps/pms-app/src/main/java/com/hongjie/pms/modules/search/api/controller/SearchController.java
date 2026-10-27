package com.hongjie.pms.modules.search.api.controller;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.CommonResult;
import com.hongjie.pms.modules.search.api.dto.UnifiedSearchResponse;
import com.hongjie.pms.modules.search.api.service.UnifiedSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.hongjie.pms.modules.search.application.index.SearchMaintenance;

import static com.hongjie.pms.platform.utils.SecurityUtils.checkAdmin;

@Slf4j
@RestController
@RequestMapping("/pet-system/search")
@RequiredArgsConstructor
public class SearchController {

    private final UnifiedSearchService unifiedSearchService;
    private final SearchMaintenance maintenance;

    @PostMapping("/rebuild")
    public CommonResult<String> rebuildIndex() {
        checkAdmin();
        log.info("手动触发重建索引");

        maintenance.request();

        return CommonResult.success("重建索引任务已启动，请查看日志");
    }

    @GetMapping("/rebuild/status")
    public CommonResult<SearchMaintenance.Status> rebuildStatus() {
        checkAdmin();
        return CommonResult.success(maintenance.status());
    }

    /**
     * 全局搜索
     * @param keyword 搜索关键词
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param types 类型过滤: daily, activity, pet
     * @param sortBy 排序: relevance(相关度), time(时间), hot(热度)
     */
    @GetMapping("/global")
    public CommonResult<UnifiedSearchResponse> globalSearch(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) List<String> types,
            @RequestParam(defaultValue = "relevance") String sortBy) {

        Long userId = UserContext.getUserId();
        log.info(
                "全局搜索: keyword={}, pageNum={}, pageSize={}, types={}, sortBy={}, userId={}",
                keyword,
                pageNum,
                pageSize,
                types,
                sortBy,
                userId);

        UnifiedSearchResponse response =
                unifiedSearchService.search(keyword, pageNum, pageSize, types, sortBy, userId);

        return CommonResult.success(response);
    }

    /**
     * 搜索建议（自动补全）
     */
    @GetMapping("/suggest")
    public CommonResult<List<String>> suggest(@RequestParam String keyword) {
        // TODO: 实现搜索建议功能
        return CommonResult.success(List.of());
    }
}

package com.hongjie.pms.modules.petpost.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.petpost.dto.PetDetailDto;
import com.hongjie.pms.modules.petpost.dto.request.PetPostRequestDto;
import com.hongjie.pms.modules.petpost.dto.request.PetQueryRequestDto;
import com.hongjie.pms.modules.user.dto.response.AvatarUploadResponse;
import com.hongjie.pms.modules.petpost.dto.response.FavoriteResponseDto;
import com.hongjie.pms.modules.like.dto.response.LikeResponseDto;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import com.hongjie.pms.modules.petpost.service.PetPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


/**
 * 宠物领养救助信息
 *
 * @author: denghongjie
 * @date: 2026/04/01
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/pet")
@RequiredArgsConstructor
public class PetPostController {

    private final PetPostService petPostService;

    /**
     * 宠物救助/领养信息发布
     * @param request
     * @return
     */
    @PostMapping("/post")
    public CommonResult<PetListResponseDto> post(@RequestBody PetPostRequestDto request) {

        PetListResponseDto response = petPostService.post(request);

        return CommonResult.success(response);
    }

    /**
     * 收藏
     */
    @PostMapping("/{id}/collect")
    public CommonResult<FavoriteResponseDto> favorite(@PathVariable Long id) {
        log.info("收藏宠物信息: id={}", id);
        FavoriteResponseDto response = petPostService.favorite(id);
        return CommonResult.success(response);
    }

    /**
     * 宠物救助/领养信息修改
     * @param request
     * @return
     */
    @PostMapping("/update")
    public CommonResult<PetListResponseDto> update(@RequestBody PetPostRequestDto request) {
        PetListResponseDto response = petPostService.update(request);
        return CommonResult.success(response);
    }
    /**
     * 宠物救助/领养信息查询
     * @param request
     * @return
     */
    @GetMapping("/list")
    public CommonResult<IPage<PetListResponseDto>> list(PetQueryRequestDto request) {

        log.info("宠物救助/领养信息查询: request={}", request);
        request.setStatus(1);
        IPage<PetListResponseDto> response = petPostService.list(request);

        return CommonResult.success(response);
    }

    /**
     * 我的发布列表
     */
    @GetMapping("/my-posts")
    public CommonResult<IPage<PetListResponseDto>> myPosts() {
        log.info("我的发布列表");
        PetQueryRequestDto queryDto = new PetQueryRequestDto();
        Long userId = UserContext.getUserId();
        queryDto.setUserId(userId);
        queryDto.setStatus(null);  // 显示所有状态
        queryDto.setOrderBy("creat_time");
        IPage<PetListResponseDto> page = petPostService.list(queryDto);
        return CommonResult.success(page);
    }

    /**
     * 删除宠物信息
     */
    @PostMapping("delete")
    public CommonResult<String> delete(@RequestParam Long id) {
        log.info("删除宠物信息: id={}", id);
        petPostService.delete(id);
        return CommonResult.success();
    }

    /**
     * 恢复宠物信息
     */
    @PostMapping("recover")
    public CommonResult<String> recover(@RequestParam Long id) {
        log.info("恢复宠物信息: id={}", id);
        petPostService.recover(id);
        return CommonResult.success();
    }

    /**
     * 我的收藏列表
     */
    @GetMapping("/favoriteList")
    public CommonResult<IPage<PetListResponseDto>> favoriteList(@RequestParam Integer pageNum, @RequestParam Integer pageSize) {
        log.info("我的收藏列表");
        IPage<PetListResponseDto> page = petPostService.favoriteList(pageNum, pageSize);
        return CommonResult.success(page);
    }

    /**
     * 回收站列表
     */
    @GetMapping("/recycle-bin")
    public CommonResult<IPage<PetListResponseDto>> recycleBin() {
        log.info("回收站列表");
        PetQueryRequestDto queryDto = new PetQueryRequestDto();
        Long userId = UserContext.getUserId();
        queryDto.setUserId(userId);
        queryDto.setStatus(-1);  // 删除状态
        queryDto.setOrderBy("creat_time");
        queryDto.setOrder("desc");
        IPage<PetListResponseDto> page = petPostService.list(queryDto);
        return CommonResult.success(page);
    }

    /**
     * 下架宠物信息
     */
    @PostMapping("offline")
    public CommonResult<String> offline(@RequestParam Long id) {
        log.info("下架宠物信息: id={}", id);
        petPostService.offline(id);
        return CommonResult.success();
    }

    /**
     * 待处理列表
     */
    @GetMapping("/pending-list")
    public CommonResult<IPage<PetListResponseDto>> pendingList() {
        log.info("待处理列表");
        PetQueryRequestDto queryDto = new PetQueryRequestDto();
        // 检查是否是管理员，如果是管理员则不设置userId，这样会返回所有用户的待审核帖子
        if (!UserContext.isAdmin()) {
            Long userId = UserContext.getUserId();
            queryDto.setUserId(userId);
        }
        queryDto.setStatus(0);  // 待审核状态
        queryDto.setOrderBy("creat_time");
        queryDto.setOrder("desc");
        IPage<PetListResponseDto> page = petPostService.list(queryDto);
        return CommonResult.success(page);
    }

    /**
     * 上传图片
     */
    @PostMapping("/upload")
    public CommonResult<AvatarUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        log.info("上传图片");
        AvatarUploadResponse response = petPostService.uploadImage(file);
        return CommonResult.success(response);
    }

    /**
     * 删除图片
     */
    @DeleteMapping("/delete-really")
    public CommonResult<String> deleteReally(@RequestParam Long id) {
        log.info("删除宠物信息: id={}", id);
        petPostService.deleteReally(id);
        return CommonResult.success();
    }

    /**
     * 审核通过/拒绝
     */
    @PostMapping("/pend")
    public CommonResult<PetListResponseDto> pend(
            @RequestParam Long id,
            @RequestParam Integer status) {

        // 1. 获取当前用户
        Long userId = UserContext.getUserId();

        // 2. 检查是否是管理员
        if (!UserContext.isAdmin()) {
            log.warn("非管理员用户尝试审核宠物信息: userId={}, petId={}", userId, id);
            throw new BusinessException(403, "无权操作，需要管理员权限");
        }

        PetListResponseDto response = petPostService.pend(id, status);
        return CommonResult.success(response);
    }

    /**
     * 宠物详情
     */
    @GetMapping("/{id}")
    public CommonResult<PetDetailDto> detail(
            @PathVariable Long id) {
        Long userId = UserContext.getUserId();
        PetDetailDto detail = petPostService.detail(id, userId);
        return CommonResult.success(detail);
    }

}

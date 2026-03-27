package com.hongjie.pms.modules.user.controller;

import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.user.dto.response.AvatarUploadResponse;
import com.hongjie.pms.modules.user.entity.AvatarHistory;
import com.hongjie.pms.modules.user.service.AvatarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/pet-system/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    /**
     * 上传头像
     */
    @PostMapping("/upload")
    public CommonResult<AvatarUploadResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        AvatarUploadResponse response = avatarService.uploadAvatar(file);
        return CommonResult.success(response);
    }

//    @PostMapping("/update")
//    public CommonResult<String> updateAvatar(String avatarUrl) {
//        avatarService.updateAvatar(avatarUrl);
//        return CommonResult.success("更新成功");
//    }

    /**
     * 查看历史头像
     */
    @GetMapping("/historyAvatar")
    public CommonResult<List<AvatarHistory>> historyAvatar() {
        List<AvatarHistory> historyAvatar = avatarService.historyAvatar();
        if(historyAvatar == null || historyAvatar.isEmpty()){
            return CommonResult.error(500, "获取历史头像失败");
        }
        return CommonResult.success(historyAvatar);
    }

    /**
     * 切换到历史头像
     */
    @PutMapping("/switch/{historyId}")
    public CommonResult<String> switchToHistoryAvatar(@PathVariable Long historyId) {
        String newAvatarUrl = avatarService.switchToHistoryAvatar(historyId);
        return CommonResult.success(newAvatarUrl, "切换成功");
    }
}

package com.hongjie.pms.modules.user.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class UserProfileDto {

    private UserSimpleDto user;
    private String signature;     // 个性签名
    private Integer gender;        // 性别，0:未知, 1:男, 2:女

    // ========== 统计信息（始终公开） ==========
    private Integer followerCount; // 粉丝数
    private Integer followingCount;// 关注数
    private Integer likeCount;     // 获赞总数

    // ========== 动态信息（始终公开） ==========
    private LocalDateTime joinTime;     // 注册时间
    private LocalDateTime lastActiveTime; // 最后活跃时间

    // ========== 标签信息（根据隐私设置） ==========
    private List<String> tags;          // 个人标签

    // ========== 联系方式（根据隐私设置） ==========
    private String phone;        // 手机号（需授权）
    private String email;        // 邮箱（需授权）

}

package com.hongjie.pms.modules.user.dto.response;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class UserInfoDto {

    /**
     * 主键ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 密码
     */
    private String password;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 性别
     */
    private Integer gender;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 个性签名
     */
    private String signature;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 隐私设置
     */
    private Map<String, Boolean> privacySettings;

    /**
     * 角色
     */
    private Integer role;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否可搜索
     */
    private Integer searchable;

    private Integer followerCount; // 粉丝数
    private Integer followingCount;// 关注数
    private Integer likeCount;     // 获赞总数

}

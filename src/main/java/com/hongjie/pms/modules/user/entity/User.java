package com.hongjie.pms.modules.user.entity;


import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "user", autoResultMap = true)
public class User {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)  // 自增主键
    private Long id;

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
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /**
     * 隐私设置
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Boolean> privacySettings;

    /**
     * 角色
     */
    private Integer role;

    /**
     * 扩展字段
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extJson;

    /**
     * 拓展字段
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // 新增：是否允许被搜索
    private Integer searchable;  // 0-不允许 1-允许

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastActiveTime;

    private Integer followerCount; // 粉丝数
    private Integer followingCount;// 关注数
    private Integer likeCount;     // 获赞总数

    private Integer totalSignups;
    private Integer totalNoShows;
    private Integer recentNoShows;
    private LocalDateTime punishmentEndTime;
    private LocalDateTime lastActivityDate;
}

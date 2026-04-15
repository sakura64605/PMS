package com.hongjie.pms.modules.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_signup")
public class ActivitySignup {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 备注
     */
    private String remark;

    /**
     * 状态 1：已报名 2：已取消 3：已签到 4：爽约
     */
    private Integer status;

    /**
     * 签到时间
     */
    private LocalDateTime checkInTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 取消时间
     */
    private LocalDateTime cancelTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}

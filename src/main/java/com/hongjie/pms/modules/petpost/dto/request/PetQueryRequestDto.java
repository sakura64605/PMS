package com.hongjie.pms.modules.petpost.dto.request;

import lombok.Data;

@Data
public class PetQueryRequestDto {

    // 分页参数
    private Integer pageNum = 1;
    private Integer pageSize = 10;

    // 筛选条件
    private Integer type;           // 类型：0-领养 1-救助，null表示全部
    private Integer gender;         // 性别：0-未知 1-公 2-母，null表示全部
    private String petType;         // 品种：猫/狗/其他
    private Integer status;  // 状态：0-待审核 1-已发布 2-已完成，null表示已发布+已完成
    private Integer auditStatus;  // 审核状态：0-待审核 1-审核通过 2-审核拒绝，null表示全部
    private String keyword;         // 关键词搜索（标题+内容）
    private String petName;         // 宠物名字搜索
    private Long userId;            // 指定用户ID

    // 排序
    private String orderBy;         // 排序字段：createTime/viewCount
    private String order;           // 排序方向：asc/desc

}

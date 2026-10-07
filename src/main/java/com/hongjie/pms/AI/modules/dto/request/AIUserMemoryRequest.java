package com.hongjie.pms.AI.modules.dto.request;

import lombok.Data;

/** 用户自助添加/修改长期记忆的请求体 */
@Data
public class AIUserMemoryRequest {
    /** 记忆内容 */
    private String content;

    /** 重要度 1-5（可空） */
    private Integer importance;
}

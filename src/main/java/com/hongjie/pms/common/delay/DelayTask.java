package com.hongjie.pms.common.delay;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DelayTask implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 任务类型 */
    private String type;
    
    /** 业务ID */
    private Long businessId;
    
    /** 执行时间戳 */
    private Long executeTime;
    
    /** 额外参数 */
    private String params;
}
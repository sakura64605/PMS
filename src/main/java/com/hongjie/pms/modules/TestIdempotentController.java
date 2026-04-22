package com.hongjie.pms.modules;

import com.hongjie.pms.common.idempotent.annotation.Idempotent;
import com.hongjie.pms.common.idempotent.enums.IdempotentTypeEnum;
import com.hongjie.pms.common.pojo.CommonResult;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
public class TestIdempotentController {

    /**
     * Token 方式幂等测试
     */
    @PostMapping("/token/create")
    @Idempotent(
        type = IdempotentTypeEnum.SPEL,
            key = "#request.orderNo",  // 指定使用订单号作为幂等 key
            uniqueKeyPrefix = "order:",
            message = "请勿重复提交订单",
            perUser = true,
            keyTimeout = 60
    )
    public CommonResult<String> createOrder(@RequestBody OrderRequest request) {
        // 模拟业务处理
        System.out.println("处理订单：" + request.getOrderNo());
        return CommonResult.success("订单创建成功：" + request.getOrderNo());
    }
    
    @Data
    public static class OrderRequest {
        private String orderNo;
        private String productName;
        private Integer amount;
    }
}
package com.hongjie.pms.common.idempotent.handler;

import com.hongjie.pms.common.pojo.CommonResult;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
public class IdempotentTokenController {

    private final IdempotentTokenService idempotentTokenService;

    /**
     * 请求申请Token
     */
    @GetMapping("/token")
    public CommonResult<String> createToken() {
        return CommonResult.success(idempotentTokenService.createToken());
    }
}

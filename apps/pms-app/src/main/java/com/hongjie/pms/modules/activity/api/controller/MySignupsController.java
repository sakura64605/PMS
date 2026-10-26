package com.hongjie.pms.modules.activity.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.platform.pojo.CommonResult;
import com.hongjie.pms.modules.activity.api.dto.request.MySignupsRequest;
import com.hongjie.pms.modules.activity.api.dto.response.MySignupResponse;
import com.hongjie.pms.modules.activity.application.MySignupsQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pet-system/activity/my-signups")
public class MySignupsController {
    private final MySignupsQueries queries;
    @GetMapping
    public CommonResult<IPage<MySignupResponse>> list(MySignupsRequest request) {
        return CommonResult.success(queries.list(request));
    }
    @GetMapping("/summary")
    public CommonResult<Map<String,Object>> summary() {
        return CommonResult.success(queries.summary());
    }
}

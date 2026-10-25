package com.hongjie.pms.modules.content.petpost.api.controller;

import com.hongjie.pms.platform.pojo.CommonResult;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetCategory;
import com.hongjie.pms.modules.content.petpost.application.classification.PetCategories;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/pet-system/admin/pet-category")
@RequiredArgsConstructor
public class PetCategoryAdminController {
    private final PetCategories categories;
    @GetMapping("/list")
    public CommonResult<List<PetCategory>> list() { return CommonResult.success(categories.list()); }
    @PostMapping("/create")
    public CommonResult<PetCategory> create(@RequestBody PetCategory request) {
        return CommonResult.success(categories.create(request));
    }
    @PutMapping("/update")
    public CommonResult<String> update(@RequestBody PetCategory request) {
        return CommonResult.success(categories.update(request));
    }
}

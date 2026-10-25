package com.hongjie.pms.modules.admin.controller;

import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.petpost.entity.PetCategory;
import com.hongjie.pms.refactor.petpost.classification.PetCategories;
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

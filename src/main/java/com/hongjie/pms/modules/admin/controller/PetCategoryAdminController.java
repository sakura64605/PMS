package com.hongjie.pms.modules.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.petpost.entity.PetCategory;
import com.hongjie.pms.modules.petpost.mapper.PetCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 宠物物种分类字典管理（管理员）。
 *
 * <p>id 显式管理：种子 0-7 与 pet_post.pet_category 存量一致，新增取 MAX(id)+1。
 * 不建议硬删除（pet_post 引用），用 status=0 停用即可。
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/admin/pet-category")
@RequiredArgsConstructor
public class PetCategoryAdminController {

    private final PetCategoryMapper petCategoryMapper;

    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "无权操作，需要管理员权限");
        }
    }

    /** 全部分类（含停用，按 sort 排序），供管理后台展示/编辑 */
    @GetMapping("/list")
    public CommonResult<List<PetCategory>> list() {
        checkAdmin();
        LambdaQueryWrapper<PetCategory> wrapper = new LambdaQueryWrapper<PetCategory>()
                .orderByAsc(PetCategory::getSort)
                .orderByAsc(PetCategory::getId);
        return CommonResult.success(petCategoryMapper.selectList(wrapper));
    }

    /** 新增分类 */
    @PostMapping("/create")
    public CommonResult<PetCategory> create(@RequestBody PetCategory request) {
        checkAdmin();
        if (!StringUtils.hasText(request.getName())) {
            throw new BusinessException("分类名不能为空");
        }
        int nextId = nextId();
        PetCategory category = PetCategory.builder()
                .id(nextId)
                .name(request.getName().trim())
                .sort(request.getSort() != null ? request.getSort() : 0)
                .status(request.getStatus() != null ? request.getStatus() : 1)
                .build();
        petCategoryMapper.insert(category);
        log.info("管理员新增物种分类: id={}, name={}", nextId, category.getName());
        return CommonResult.success(category);
    }

    /** 编辑分类（名称/排序/停启用） */
    @PutMapping("/update")
    public CommonResult<String> update(@RequestBody PetCategory request) {
        checkAdmin();
        if (request.getId() == null) {
            throw new BusinessException("分类ID不能为空");
        }
        PetCategory existing = petCategoryMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException("分类不存在: id=" + request.getId());
        }
        if (StringUtils.hasText(request.getName())) {
            existing.setName(request.getName().trim());
        }
        if (request.getSort() != null) {
            existing.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        petCategoryMapper.updateById(existing);
        log.info("管理员更新物种分类: id={}, name={}, status={}", existing.getId(), existing.getName(), existing.getStatus());
        return CommonResult.success("更新成功");
    }

    private int nextId() {
        List<Object> objs = petCategoryMapper.selectObjs(
                new QueryWrapper<PetCategory>().select("COALESCE(MAX(id),-1) + 1"));
        return objs.isEmpty() ? 0 : ((Number) objs.get(0)).intValue();
    }
}

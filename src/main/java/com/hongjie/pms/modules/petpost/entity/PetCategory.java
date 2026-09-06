package com.hongjie.pms.modules.petpost.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 宠物物种分类字典（管理员维护）。
 *
 * <p>id 为显式管理（种子 0-7 与 pet_post.pet_category 存量一致，新增由后端取 MAX(id)+1），
 * 故不使用自增。pet_post.pet_category 引用本表 id。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pet_category")
public class PetCategory {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /** 分类名，如 猫/狗/其他 */
    private String name;

    /** 排序，小的靠前 */
    private Integer sort;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.hongjie.pms.modules.petpost.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PetPostRequestDto {

    private Long id;

    @NotNull(message = "类型不能为空")
    private Integer type;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多100字")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    private String petName;
    private String petType;
    private String petAge;
    private Integer petGender;
    private List<String> images;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String contactPhone;

    private String contactWechat;

    @NotBlank(message = "地址不能为空")
    private String address;

}

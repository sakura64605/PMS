package com.hongjie.pms.modules.ai.api.controller;
import com.hongjie.pms.modules.ai.application.AiPetProfiles;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.AiPetProfile;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.CommonResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/pet-system/ai/pets")
@RequiredArgsConstructor
public class AIPetProfileController {
 private final AiPetProfiles pets;
 @GetMapping public CommonResult<List<AiPetProfile>> list(){return CommonResult.success(pets.list(UserContext.getUserId()));}
 @PostMapping public CommonResult<AiPetProfile> create(@RequestBody AiPetProfile input){return CommonResult.success(pets.save(UserContext.getUserId(),null,input));}
 @PutMapping("/{id}") public CommonResult<AiPetProfile> update(@PathVariable Long id,@RequestBody AiPetProfile input){return CommonResult.success(pets.save(UserContext.getUserId(),id,input));}
 @DeleteMapping("/{id}") public CommonResult<String> delete(@PathVariable Long id){pets.delete(UserContext.getUserId(),id);return CommonResult.success("已删除宠物档案");}
}

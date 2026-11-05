package com.hongjie.pms.modules.ai.api.controller;
import com.hongjie.pms.modules.ai.application.AiPetRecords;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.AiPetRecord;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.CommonResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor
@RequestMapping("/pet-system/ai/pets/{petId}/records")
public class AIPetRecordController {
 private final AiPetRecords records;
 @GetMapping public CommonResult<List<AiPetRecord>> list(@PathVariable Long petId,@RequestParam(required=false) Long beforeId){return CommonResult.success(records.list(UserContext.getUserId(),petId,beforeId));}
 @PostMapping public CommonResult<AiPetRecord> add(@PathVariable Long petId,@RequestBody AiPetRecord input){return CommonResult.success(records.save(UserContext.getUserId(),petId,null,input));}
 @PutMapping("/{id}") public CommonResult<AiPetRecord> edit(@PathVariable Long petId,@PathVariable Long id,@RequestBody AiPetRecord input){return CommonResult.success(records.save(UserContext.getUserId(),petId,id,input));}
 @DeleteMapping("/{id}") public CommonResult<String> delete(@PathVariable Long petId,@PathVariable Long id){records.delete(UserContext.getUserId(),petId,id);return CommonResult.success("已删除档案记录");}
}

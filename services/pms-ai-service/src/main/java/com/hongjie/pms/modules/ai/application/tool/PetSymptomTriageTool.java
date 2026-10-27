package com.hongjie.pms.modules.ai.application.tool;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.ai.contract.BaseTool;
import com.hongjie.pms.modules.ai.api.dto.ToolCall;
import com.hongjie.pms.modules.ai.application.tool.triage.PetSymptomTriagePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class PetSymptomTriageTool implements BaseTool {
    private final PetSymptomTriagePolicy policy;

    @Override
    public String getName() {
        return "triage_pet_symptoms";
    }

    @Override
    public String getDescription() {
        return "宠物症状紧急程度初筛，当前支持猫呕吐。返回RED/YELLOW/GREEN或信息不足UNDETERMINED、依据和追问。仅提交用户明确提供的事实，未知字段省略，不得把未提到当作false。已知危险征象应立即调用，即使其他字段不完整；不是诊断工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        add(properties, "species", "string", "宠物种类：cat、dog、other；当前完整分诊只支持cat");
        add(properties, "symptom", "string", "主要症状：vomiting或other");
        add(properties, "ageMonths", "integer", "年龄（月），0至480；用户仅提供近似年龄时先确认，不猜测");
        add(properties, "vomitingCount24h", "integer", "过去24小时呕吐次数，1至1000");
        add(properties, "durationHours", "integer", "本次症状持续整小时数，0至8760；不足1小时填0");
        add(
                properties,
                "vomitAppearance",
                "string",
                "呕吐物：food、liquid、foam、hairball、bile、blood、coffee_ground、other");
        add(properties, "mentalState", "string", "精神：normal正常、lethargic萎靡、severely_weak明显虚弱");
        add(properties, "appetite", "string", "食欲：normal、reduced、none");
        add(
                properties,
                "waterRetention",
                "string",
                "饮水：normal正常且能留住水、reduced减少、unable_to_keep_down喝水也吐");
        add(properties, "diarrhea", "boolean", "是否同时腹泻；仅明确回答后填写");
        add(properties, "recurrentVomiting", "boolean", "近期是否反复呕吐（不只本次）；未知省略");
        add(properties, "knownMedicalCondition", "boolean", "有无已知疾病或正在接受治疗；未知省略");
        add(properties, "bloodInVomit", "boolean", "呕吐物是否带血");
        add(properties, "suspectedToxin", "boolean", "是否可能误食药物、毒物或有毒植物");
        add(properties, "suspectedForeignBody", "boolean", "是否可能吞入线绳或其他异物");
        add(properties, "breathingDifficulty", "boolean", "是否呼吸困难");
        add(properties, "collapseOrSeizure", "boolean", "是否倒地、意识异常或抽搐");
        add(properties, "severeAbdominalPain", "boolean", "是否明显或剧烈腹痛");
        add(properties, "abdominalSwelling", "boolean", "是否腹部异常膨胀");
        add(properties, "unableToUrinate", "boolean", "是否用力排尿但没有尿液");
        add(properties, "severeOrContinuousVomiting", "boolean", "是否剧烈或持续不断地呕吐；不由一次吐液体或毛球推断");
        return Map.of("type", "object", "properties", properties);
    }

    private void add(Map<String, Object> properties, String name, String type, String description) {
        properties.put(name, Map.of("type", type, "description", description));
    }

    @Override
    public ToolCall execute(Map<String, Object> args, Long userId) {
        Map<String, Object> facts = args == null ? Map.of() : new LinkedHashMap<>(args);
        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name(getName())
                .arguments(JSON.toJSONString(facts))
                .result(JSON.toJSONString(policy.assess(facts)))
                .build();
    }
}

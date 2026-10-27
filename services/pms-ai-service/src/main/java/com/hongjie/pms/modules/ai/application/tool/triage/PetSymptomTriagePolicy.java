package com.hongjie.pms.modules.ai.application.tool.triage;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/** Conservative, deterministic urgency screening; not a diagnostic model. */
@Component
public class PetSymptomTriagePolicy {
    public static final String VERSION = "cat-vomiting-v1";
    public static final List<String> SOURCES =
            List.of(
                    "https://www.vet.cornell.edu/departments-centers-and-institutes/cornell-feline-health-center/health-information/feline-health-topics/vomiting",
                    "https://www.merckvetmanual.com/cat-owners/digestive-disorders-of-cats/vomiting-in-cats",
                    "https://vcahospitals.com/know-your-pet/emergencies-in-cats");

    private static final Map<String, String> RED_FLAGS;

    static {
        Map<String, String> flags = new LinkedHashMap<>();
        flags.put("bloodInVomit", "呕吐物带血");
        flags.put("suspectedToxin", "可能接触毒物或误食药物");
        flags.put("suspectedForeignBody", "可能吞入线绳等异物");
        flags.put("breathingDifficulty", "呼吸困难");
        flags.put("collapseOrSeizure", "倒地、意识异常或抽搐");
        flags.put("severeAbdominalPain", "明显或剧烈腹痛");
        flags.put("abdominalSwelling", "腹部异常膨胀");
        flags.put("unableToUrinate", "用力排尿但没有尿液");
        flags.put("severeOrContinuousVomiting", "剧烈或持续不断地呕吐");
        RED_FLAGS = Collections.unmodifiableMap(flags);
    }

    public Result assess(Map<String, Object> arguments) {
        Input input = new Input(arguments == null ? Map.of() : arguments);
        String species = input.choice("species", "cat", "dog", "other");
        String symptom = input.choice("symptom", "vomiting", "other");
        String mental = input.choice("mentalState", "normal", "lethargic", "severely_weak");
        String water = input.choice("waterRetention", "normal", "reduced", "unable_to_keep_down");
        String appearance =
                input.choice(
                        "vomitAppearance",
                        "food",
                        "liquid",
                        "foam",
                        "hairball",
                        "bile",
                        "blood",
                        "coffee_ground",
                        "other");
        String appetite = input.choice("appetite", "normal", "reduced", "none");
        Integer age = input.integer("ageMonths", 0, 480);
        Integer count = input.integer("vomitingCount24h", 1, 1000);
        Integer duration = input.integer("durationHours", 0, 8760);
        Boolean diarrhea = input.bool("diarrhea");
        Boolean recurrent = input.bool("recurrentVomiting");
        Boolean condition = input.bool("knownMedicalCondition");

        List<String> red = new ArrayList<>();
        for (var flag : RED_FLAGS.entrySet()) {
            if (Boolean.TRUE.equals(input.bool(flag.getKey()))) red.add(flag.getValue());
        }
        if (Set.of("blood", "coffee_ground").contains(Objects.toString(appearance, "")))
            red.add("呕吐物呈血色或咖啡渣样");
        if ("severely_weak".equals(mental)) red.add("精神极差、明显虚弱");
        if ("unable_to_keep_down".equals(water)) red.add("喝水也吐，无法保留水分");

        // Known red flags always win, even when unrelated fields are missing or malformed.
        if (!red.isEmpty())
            return result(
                    input,
                    "RED",
                    "URGENT",
                    red,
                    List.of(),
                    "CONTACT_EMERGENCY_VET",
                    "立即联系并前往可接诊的兽医急诊；不要等待补充信息或知识库结果。出发前可电话确认接诊。",
                    null);

        if ((species != null && !"cat".equals(species))
                || (symptom != null && !"vomiting".equals(symptom)))
            return result(
                    input,
                    "UNDETERMINED",
                    "UNSUPPORTED_SCOPE",
                    List.of("当前规则仅覆盖猫呕吐，不将其他宠物或症状套用为低风险"),
                    List.of(),
                    "CONTACT_VET",
                    "请联系兽医评估；如出现危险征象，应立即就医。",
                    null);

        List<String> yellow = new ArrayList<>();
        if ("lethargic".equals(mental)) yellow.add("精神比平时差");
        if (appetite != null && !"normal".equals(appetite)) yellow.add("食欲下降或不进食");
        if ("reduced".equals(water)) yellow.add("饮水减少");
        if (Boolean.TRUE.equals(diarrhea)) yellow.add("伴随腹泻");
        if (Boolean.TRUE.equals(recurrent)) yellow.add("近期反复呕吐");
        if (Boolean.TRUE.equals(condition)) yellow.add("有已知疾病或正在接受治疗");
        if (age != null && (age < 6 || age >= 120)) yellow.add("幼龄或高龄，需要更谨慎评估");
        if (count != null && count >= 2) yellow.add("24小时内重复呕吐");
        if (duration != null && duration >= 24) yellow.add("症状持续至少24小时");
        if ("other".equals(appearance)) yellow.add("呕吐物性状不明确，需要兽医判断");

        List<String> questions = followUp(input.missing());
        if (!yellow.isEmpty())
            return result(
                    input,
                    "YELLOW",
                    input.missing().isEmpty() ? "ASSESSED" : "NEEDS_INFORMATION",
                    yellow,
                    questions,
                    "CONTACT_VET_TODAY",
                    "尽快联系兽医，建议当天评估；补充信息期间不要延误就医。护理知识仅作等待就诊时的辅助。",
                    "猫呕吐 就医前观察记录 危险征象");
        if (!input.missing().isEmpty())
            return result(
                    input,
                    "UNDETERMINED",
                    "NEEDS_INFORMATION",
                    List.of("信息不足，不能排除需要就医的情况"),
                    questions,
                    "ASK_FOLLOW_UP",
                    "优先确认危险征象，再分轮询问其余信息；用户无法确认时建议联系兽医。",
                    null);

        return result(
                input,
                "GREEN",
                "ASSESSED",
                List.of("目前提供的信息未触发红色或黄色规则；这不代表排除疾病"),
                List.of(),
                "OBSERVE_AND_CONSULT",
                "可在联系兽医咨询的同时密切观察；再次呕吐、精神或食欲变差、腹泻或出现危险征象时及时升级就医。",
                "猫偶发呕吐 安全观察 何时就医");
    }

    private Result result(
            Input input,
            String level,
            String status,
            List<String> reasons,
            List<String> questions,
            String action,
            String advice,
            String knowledgeQuery) {
        return new Result(
                VERSION,
                level,
                status,
                input.missing().isEmpty(),
                List.copyOf(reasons),
                List.copyOf(input.missing()),
                List.copyOf(input.invalid),
                questions,
                action,
                advice,
                knowledgeQuery,
                "仅为基于用户描述的紧急程度初筛，不是诊断；不提供药物剂量、催吐或自行禁食方案。",
                SOURCES);
    }

    private List<String> followUp(Set<String> missing) {
        List<String> questions = new ArrayList<>();
        if (RED_FLAGS.keySet().stream().anyMatch(missing::contains)
                || missing.contains("waterRetention")
                || missing.contains("mentalState"))
            questions.add(
                    "有没有吐血、持续剧烈呕吐、喝水也吐、呼吸困难、倒地或抽搐、明显腹痛腹胀、尿不出来，或可能误食药物/毒物/线绳？精神是否正常？如有以上危险情况请先就医。");
        if (missing.contains("species")
                || missing.contains("symptom")
                || missing.contains("ageMonths")) questions.add("是哪种宠物、几个月或几岁，主要症状是否为呕吐？");
        if (missing.contains("vomitingCount24h")
                || missing.contains("durationHours")
                || missing.contains("vomitAppearance"))
            questions.add("过去24小时吐了几次、从什么时候开始，呕吐物像食物、液体、泡沫、毛球还是带血/咖啡渣样？");
        if (missing.contains("appetite")
                || missing.contains("diarrhea")
                || missing.contains("recurrentVomiting")
                || missing.contains("knownMedicalCondition"))
            questions.add("食欲和排便怎样，近期是否反复呕吐，有没有已知疾病或正在用药？");
        return List.copyOf(questions.stream().limit(3).toList());
    }

    public record Result(
            String ruleVersion,
            String level,
            String status,
            boolean informationComplete,
            List<String> reasons,
            List<String> missingFields,
            List<String> invalidFields,
            List<String> followUpQuestions,
            String recommendedAction,
            String advice,
            String knowledgeQuery,
            String limitation,
            List<String> sources) {}

    private static final class Input {
        private final Map<String, Object> values;
        private final Set<String> missing = new LinkedHashSet<>();
        private final Set<String> invalid = new LinkedHashSet<>();

        Input(Map<String, Object> values) {
            this.values = values;
        }

        Set<String> missing() {
            return missing;
        }

        String choice(String key, String... choices) {
            Object value = values.get(key);
            if (value == null || "unknown".equals(value)) {
                missing.add(key);
                return null;
            }
            if (value instanceof String text) {
                String normalized = text.trim().toLowerCase(Locale.ROOT);
                if (Arrays.asList(choices).contains(normalized)) return normalized;
            }
            invalid.add(key);
            missing.add(key);
            return null;
        }

        Boolean bool(String key) {
            Object value = values.get(key);
            if (value instanceof Boolean flag) return flag;
            if (value != null) invalid.add(key);
            missing.add(key);
            return null;
        }

        Integer integer(String key, int min, int max) {
            Object value = values.get(key);
            if (value instanceof Number number) {
                try {
                    int parsed = new BigDecimal(number.toString()).intValueExact();
                    if (parsed >= min && parsed <= max) return parsed;
                } catch (NumberFormatException | ArithmeticException ignored) {
                    // Fractional, non-finite and overflowing values are unknown, never defaults.
                }
            }
            if (value != null) invalid.add(key);
            missing.add(key);
            return null;
        }
    }
}

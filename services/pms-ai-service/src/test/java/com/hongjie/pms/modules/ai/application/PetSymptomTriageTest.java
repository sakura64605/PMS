package com.hongjie.pms.modules.ai.application;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.modules.ai.application.tool.PetSymptomTriageTool;
import com.hongjie.pms.modules.ai.application.tool.ToolRegistry;
import com.hongjie.pms.modules.ai.application.tool.triage.PetSymptomTriagePolicy;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class PetSymptomTriageTest {
    private final PetSymptomTriagePolicy policy = new PetSymptomTriagePolicy();
    private final PetSymptomTriageTool tool = new PetSymptomTriageTool(policy);

    private Map<String, Object> lowRiskFacts() {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("species", "cat");
        facts.put("symptom", "vomiting");
        facts.put("ageMonths", 36);
        facts.put("vomitingCount24h", 1);
        facts.put("durationHours", 1);
        facts.put("vomitAppearance", "food");
        facts.put("mentalState", "normal");
        facts.put("appetite", "normal");
        facts.put("waterRetention", "normal");
        for (String field :
                List.of(
                        "diarrhea",
                        "recurrentVomiting",
                        "knownMedicalCondition",
                        "bloodInVomit",
                        "suspectedToxin",
                        "suspectedForeignBody",
                        "breathingDifficulty",
                        "collapseOrSeizure",
                        "severeAbdominalPain",
                        "abdominalSwelling",
                        "unableToUrinate",
                        "severeOrContinuousVomiting")) facts.put(field, false);
        return facts;
    }

    @Test
    void noFactsAndPartialFactsNeverDefaultToGreen() {
        for (var input :
                List.of(
                        Map.<String, Object>of(),
                        Map.<String, Object>of("species", "cat", "symptom", "vomiting"))) {
            var result = policy.assess(input);
            assertThat(result.level()).isEqualTo("UNDETERMINED");
            assertThat(result.informationComplete()).isFalse();
            assertThat(result.followUpQuestions()).hasSize(3);
            assertThat(result.followUpQuestions().get(0)).contains("危险");
            assertThat(result.knowledgeQuery()).isNull();
        }
        assertThat(policy.assess(null).level()).isEqualTo("UNDETERMINED");
    }

    @Test
    void everyRequiredFactMustBeKnownBeforeGreen() {
        var facts = lowRiskFacts();
        assertThat(policy.assess(facts).level()).isEqualTo("GREEN");
        for (String key : facts.keySet()) {
            var incomplete = new HashMap<>(facts);
            incomplete.remove(key);
            var result = policy.assess(incomplete);
            assertThat(result.level()).as("missing %s", key).isEqualTo("UNDETERMINED");
            assertThat(result.missingFields()).contains(key);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "bloodInVomit",
                "suspectedToxin",
                "suspectedForeignBody",
                "breathingDifficulty",
                "collapseOrSeizure",
                "severeAbdominalPain",
                "abdominalSwelling",
                "unableToUrinate",
                "severeOrContinuousVomiting"
            })
    void redFlagsDoNotWaitForMissingOrMalformedFields(String flag) {
        var result = policy.assess(Map.of(flag, true, "ageMonths", "not-a-number"));
        assertThat(result.level()).isEqualTo("RED");
        assertThat(result.recommendedAction()).isEqualTo("CONTACT_EMERGENCY_VET");
        assertThat(result.status()).isEqualTo("URGENT");
        assertThat(result.invalidFields()).contains("ageMonths");
        assertThat(result.followUpQuestions()).isEmpty();
        assertThat(result.knowledgeQuery()).isNull();
    }

    @Test
    void redFlagWinsOverContradictoryAppearanceAndOtherSpecies() {
        assertThat(policy.assess(Map.of("species", "dog", "bloodInVomit", true)).level())
                .isEqualTo("RED");
        assertThat(
                        policy.assess(
                                        Map.of(
                                                "bloodInVomit",
                                                false,
                                                "vomitAppearance",
                                                "coffee_ground"))
                                .level())
                .isEqualTo("RED");
        assertThat(policy.assess(Map.of("waterRetention", "unable_to_keep_down")).level())
                .isEqualTo("RED");
        assertThat(policy.assess(Map.of("mentalState", "severely_weak")).level()).isEqualTo("RED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"diarrhea", "recurrentVomiting", "knownMedicalCondition"})
    void knownYellowFlagsCannotBeOverriddenByMissingInformation(String flag) {
        var result = policy.assess(Map.of("species", "cat", "symptom", "vomiting", flag, true));
        assertThat(result.level()).isEqualTo("YELLOW");
        assertThat(result.recommendedAction()).isEqualTo("CONTACT_VET_TODAY");
        assertThat(result.informationComplete()).isFalse();
        assertThat(result.followUpQuestions()).isNotEmpty();
        assertThat(result.knowledgeQuery()).isNotBlank();
    }

    @Test
    void vulnerableAgeRepeatedVomitingAndDurationEscalate() {
        for (var entry :
                Map.of("ageMonths", 5, "vomitingCount24h", 2, "durationHours", 24).entrySet()) {
            var facts = lowRiskFacts();
            facts.put(entry.getKey(), entry.getValue());
            assertThat(policy.assess(facts).level()).isEqualTo("YELLOW");
        }
        var facts = lowRiskFacts();
        facts.put("ageMonths", 120);
        assertThat(policy.assess(facts).level()).isEqualTo("YELLOW");
        facts.put("ageMonths", 6);
        assertThat(policy.assess(facts).level()).isEqualTo("GREEN");
        facts.put("ageMonths", 119);
        facts.put("durationHours", 23);
        assertThat(policy.assess(facts).level()).isEqualTo("GREEN");
    }

    @Test
    void nonSupportedSpeciesAndSymptomsNeverGetGreen() {
        var facts = lowRiskFacts();
        facts.put("species", "dog");
        assertThat(policy.assess(facts).status()).isEqualTo("UNSUPPORTED_SCOPE");
        facts.put("species", "cat");
        facts.put("symptom", "other");
        assertThat(policy.assess(facts).level()).isEqualTo("UNDETERMINED");
    }

    @Test
    void invalidTypesRangesAndUnknownsAreNotCoercedIntoReassurance() {
        for (Object value :
                List.of(
                        -1,
                        0,
                        1.5,
                        Double.NaN,
                        Double.POSITIVE_INFINITY,
                        Long.MAX_VALUE,
                        "1",
                        true)) {
            var facts = lowRiskFacts();
            facts.put("vomitingCount24h", value);
            var result = policy.assess(facts);
            assertThat(result.level()).isEqualTo("UNDETERMINED");
            assertThat(result.invalidFields()).contains("vomitingCount24h");
        }
        var facts = lowRiskFacts();
        facts.put("bloodInVomit", "false");
        facts.put("mentalState", "unknown");
        assertThat(policy.assess(facts).level()).isEqualTo("UNDETERMINED");
        assertThat(policy.assess(facts).invalidFields()).containsExactly("bloodInVomit");
    }

    @Test
    void registryExposesTypedSchemaAndRealExecutorReturnsStructuredObservation() {
        var registry = new ToolRegistry(List.of(tool));
        var spec = registry.getToolSpecifications().get(0);
        assertThat(spec.name()).isEqualTo("triage_pet_symptoms");
        assertThat(spec.parameters().properties().keySet()).containsAll(lowRiskFacts().keySet());
        var executor = registry.getToolExecutors(7L).values().iterator().next();
        String observation =
                executor.execute(
                        ToolExecutionRequest.builder()
                                .name(tool.getName())
                                .arguments("{\"bloodInVomit\":true}")
                                .build(),
                        "session");
        assertThat(JSON.parseObject(observation).getString("level")).isEqualTo("RED");
        var call = registry.execute(tool.getName(), JSON.toJSONString(lowRiskFacts()), 7L);
        assertThat(JSON.parseObject(call.getResult()).getString("level")).isEqualTo("GREEN");
        assertThat(call.getId()).isNotBlank();
        assertThat(JSON.parseObject(call.getResult()).getJSONArray("sources")).hasSize(3);
        assertThat(lowRiskFacts()).hasSize(21);
    }
}

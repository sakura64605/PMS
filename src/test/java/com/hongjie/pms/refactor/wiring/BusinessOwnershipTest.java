package com.hongjie.pms.refactor.wiring;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class BusinessOwnershipTest {
    @Test void migratedFacadesDoNotInheritLegacyImplementations() throws Exception {
        for (String name : List.of("com.hongjie.pms.refactor.ai.PmsChatMemoryService",
"com.hongjie.pms.refactor.ai.PmsChatSessionService",
"com.hongjie.pms.refactor.ai.PmsHumanTransferService",
"com.hongjie.pms.refactor.daily.PmsDailyPostService",
"com.hongjie.pms.refactor.daily.PmsDailyRecommendService",
"com.hongjie.pms.refactor.daily.PmsDailyUserInterestService",
"com.hongjie.pms.refactor.daily.PmsTopicService",
"com.hongjie.pms.refactor.message.PmsMessageService",
"com.hongjie.pms.refactor.search.PmsUnifiedSearchService",
"com.hongjie.pms.refactor.search.index.PmsFullDataSyncService",
"com.hongjie.pms.refactor.search.index.PmsSearchDataSyncService",
"com.hongjie.pms.refactor.statistics.PmsStatisticsService")) {
            assertThat(Class.forName(name).getSuperclass()).as(name).isEqualTo(Object.class);
        }
    }
    @Test void legacyConcreteServicesAreExplicitlyReferenceOnly() throws Exception {
        for (String name : List.of("com.hongjie.pms.AI.modules.service.ChatMemoryService",
"com.hongjie.pms.AI.modules.service.ChatSessionService",
"com.hongjie.pms.AI.modules.service.HumanTransferService",
"com.hongjie.pms.modules.daily.service.DailyPostService",
"com.hongjie.pms.modules.daily.service.DailyRecommendService",
"com.hongjie.pms.modules.daily.service.DailyUserInterestService",
"com.hongjie.pms.modules.daily.service.TopicService",
"com.hongjie.pms.modules.message.service.MessageService",
"com.hongjie.pms.modules.search.service.UnifiedSearchService",
"com.hongjie.pms.modules.search.service.FullDataSyncService",
"com.hongjie.pms.modules.search.service.SearchDataSyncService",
"com.hongjie.pms.modules.statistics.service.StatisticsService")) {
            var profile=Class.forName(name).getAnnotation(Profile.class);
            assertThat(profile).as(name).isNotNull();
            assertThat(profile.value()).containsExactly("legacy-reference");
        }
    }
}

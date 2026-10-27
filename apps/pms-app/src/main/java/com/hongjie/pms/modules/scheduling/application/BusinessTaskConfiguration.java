package com.hongjie.pms.modules.scheduling.application;

import com.hongjie.pms.modules.scheduling.api.DelayTaskExecutor;
import org.springframework.context.annotation.*;
import java.util.function.Consumer;

@Configuration(proxyBeanMethods = false)
public class BusinessTaskConfiguration {
    @Bean
    DelayTaskExecutor activityStartTask(ScheduledBusinessTasks tasks) {
        return handler("ACTIVITY_START", tasks::start);
    }

    @Bean
    DelayTaskExecutor activityReminderTask(ScheduledBusinessTasks tasks) {
        return handler("ACTIVITY_REMIND", tasks::remind);
    }

    @Bean
    DelayTaskExecutor activityStatisticsTask(ScheduledBusinessTasks tasks) {
        return handler("ACTIVITY_STATISTICS", tasks::statistics);
    }

    @Bean
    DelayTaskExecutor signupExpiryTask(ScheduledBusinessTasks tasks) {
        return handler("SIGNUP_PUNISHMENT_END", tasks::signupExpiry);
    }

    @Bean
    DelayTaskExecutor muteExpiryTask(ScheduledBusinessTasks tasks) {
        return handler("MUTED_PUNISHMENT_END", tasks::muteExpiry);
    }

    @Bean
    DelayTaskExecutor banExpiryTask(ScheduledBusinessTasks tasks) {
        return handler("BANNED_PUNISHMENT_END", tasks::banExpiry);
    }

    private DelayTaskExecutor handler(String type, Consumer<Long> action) {
        return new DelayTaskExecutor() {
            public String taskType() {
                return type;
            }

            public void execute(Long id) {
                action.accept(id);
            }
        };
    }
}

package com.hongjie.pms.modules.scheduling.application;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SchedulingProperties.class)
@EnableScheduling
public class SchedulingConfiguration {
    @Bean
    @ConditionalOnProperty(name = "pms.scheduling.mode", havingValue = "xxl")
    public XxlJobSpringExecutor xxlJobExecutor(SchedulingProperties properties) {
        var settings = properties.getXxl();
        Assert.hasText(settings.getAccessToken(), "XXL-JOB access token must be configured");
        Assert.hasText(settings.getAdminAddresses(), "XXL-JOB admin address must be configured");
        Assert.hasText(settings.getAppname(), "XXL-JOB appname must be configured");
        Assert.isTrue(settings.getPort() > 0 && settings.getPort() < 65536, "Invalid XXL-JOB executor port");
        var executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(settings.getAdminAddresses());
        executor.setAccessToken(settings.getAccessToken());
        executor.setAppname(settings.getAppname());
        executor.setIp(settings.getIp());
        executor.setPort(settings.getPort());
        executor.setLogPath(settings.getLogpath());
        executor.setLogRetentionDays(settings.getLogretentiondays());
        executor.setTimeout(3);
        return executor;
    }
}

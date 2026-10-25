package com.hongjie.pms.refactor.scheduling;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("pms.scheduling")
public class SchedulingProperties {
    public enum Mode { LOCAL, XXL, DISABLED }
    private Mode mode = Mode.LOCAL;
    private int batchSize = 100;
    private int maxBatches = 10;
    private final Xxl xxl = new Xxl();
    @Data public static class Xxl {
        private String adminAddresses = "http://127.0.0.1:9090/xxl-job-admin";
        private String accessToken;
        private String appname = "pms-executor";
        private String ip = "127.0.0.1";
        private int port = 9999;
        private String logpath = "logs/xxl-job";
        private int logretentiondays = 14;
    }
}

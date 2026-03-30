package com.hongjie.pms.common.punishment.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 惩罚机制配置
 * 可在 application.yml 中动态配置
 */
@Component
@ConfigurationProperties(prefix = "punishment")
@Data
public class PunishmentConfig {

    /** 爽约率阈值，默认30% */
    private double noShowRateThreshold = 0.3;

    /** 最少报名次数才计算，默认5次 */
    private int minSignups = 5;

    /** 惩罚规则列表 */
    private List<Rule> rules = new ArrayList<>();

    @Data
    public static class Rule {
        private double minRate;   // 最低爽约率
        private double maxRate;   // 最高爽约率
        private int days;         // 惩罚天数
        private String message;   // 提示信息
    }

    @PostConstruct
    public void initDefaultRules() {
        if (rules.isEmpty()) {
            Rule rule1 = new Rule();
            rule1.setMinRate(0.3);
            rule1.setMaxRate(0.4);
            rule1.setDays(7);
            rule1.setMessage("爽约率较高，禁止报名7天");
            rules.add(rule1);

            Rule rule2 = new Rule();
            rule2.setMinRate(0.4);
            rule2.setMaxRate(0.5);
            rule2.setDays(14);
            rule2.setMessage("爽约率过高，禁止报名14天");
            rules.add(rule2);

            Rule rule3 = new Rule();
            rule3.setMinRate(0.5);
            rule3.setMaxRate(1.0);
            rule3.setDays(30);
            rule3.setMessage("爽约率严重超标，禁止报名30天");
            rules.add(rule3);
        }
    }
}
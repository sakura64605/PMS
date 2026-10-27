package com.hongjie.pms.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.mybatis.spring.annotation.MapperScan(
        "com.hongjie.pms.modules.ai.infrastructure.persistence.mapper")
@SpringBootApplication(
        scanBasePackages = {
            "com.hongjie.pms.aiservice",
            "com.hongjie.pms.modules.ai",
            "com.hongjie.pms.platform"
        })
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}

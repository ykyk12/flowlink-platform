package com.flowlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FlowLink —— 企业级实时风控决策平台入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FlowLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowLinkApplication.class, args);
    }
}

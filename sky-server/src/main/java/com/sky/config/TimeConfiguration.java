package com.sky.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 集中提供业务时钟，便于精确测试售后时间边界。 */
@Configuration
public class TimeConfiguration {
    @Bean
    public Clock businessClock() {
        return Clock.systemDefaultZone();
    }
}

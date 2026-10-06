package com.fitbet.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 메서드를 실제로 동작시키는 스위치. 이게 없으면 @Scheduled는 그냥 무시된다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

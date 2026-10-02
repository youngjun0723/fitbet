package com.fitbet.common.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * "지금"을 Clock 빈 하나로 통일한다.
 * - 서비스는 LocalDate.now(clock) 처럼 Clock을 주입받아 사용 → 테스트에서 Clock.fixed()로 날짜 고정 가능
 * - JPA Auditing(@CreatedDate)도 같은 Clock을 쓰도록 DateTimeProvider를 연결
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "clockDateTimeProvider")
public class TimeConfig {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SEOUL);
    }

    @Bean
    public DateTimeProvider clockDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}

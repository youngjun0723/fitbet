package com.fitbet.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

/** Spring 없이 도는 순수 단위 테스트: "언제 돌고, 무슨 날짜를 마감하는가" */
class MidnightSchedulerTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void 자정_직후에_실행되면_어제를_마감한다() {
        DailyClosingService service = mock(DailyClosingService.class);
        Clock at0005 = Clock.fixed(LocalDateTime.of(2026, 10, 7, 0, 0, 5).atZone(SEOUL).toInstant(), SEOUL);

        new MidnightScheduler(service, at0005).closeYesterday();

        then(service).should().closeDay(LocalDate.of(2026, 10, 6));
    }

    @Test
    void cron은_매일_서울_기준_00시00분05초() throws NoSuchMethodException {
        Scheduled scheduled = MidnightScheduler.class.getMethod("closeYesterday").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("5 0 0 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");

        // 10/6 23:59:59 다음 실행 시각 = 10/7 00:00:05 (마감 직후, 5초 여유)
        LocalDateTime next = CronExpression.parse(scheduled.cron())
                .next(LocalDateTime.of(2026, 10, 6, 23, 59, 59));
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 10, 7, 0, 0, 5));
    }
}

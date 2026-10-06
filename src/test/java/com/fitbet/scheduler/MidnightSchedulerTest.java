package com.fitbet.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

/** Spring 없이 도는 순수 단위 테스트: "언제 돌고, 어느 날짜들을 마감하는가" */
class MidnightSchedulerTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final Clock AT_0005 = Clock.fixed(LocalDateTime.of(2026, 10, 7, 0, 0, 5).atZone(SEOUL).toInstant(), SEOUL);

    @Test
    void 자정_직후에_실행되면_오늘_기준_최근_7일을_마감한다() {
        DailyClosingService service = mock(DailyClosingService.class);

        new MidnightScheduler(service, AT_0005, 7).closeRecentDays();

        then(service).should().closeRecentDays(LocalDate.of(2026, 10, 7), 7); // → 9/30 ~ 10/6
    }

    @Test
    void 서버가_켜질_때도_따라잡기를_한_번_실행한다() throws NoSuchMethodException {
        DailyClosingService service = mock(DailyClosingService.class);

        new MidnightScheduler(service, AT_0005, 3).catchUpOnStartup();

        then(service).should().closeRecentDays(LocalDate.of(2026, 10, 7), 3);
        EventListener listener = MidnightScheduler.class.getMethod("catchUpOnStartup").getAnnotation(EventListener.class);
        assertThat(listener.value()).containsExactly(ApplicationReadyEvent.class);
    }

    @Test
    void cron은_매일_서울_기준_00시00분05초() throws NoSuchMethodException {
        Scheduled scheduled = MidnightScheduler.class.getMethod("closeRecentDays").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("5 0 0 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");

        // 10/6 23:59:59 다음 실행 시각 = 10/7 00:00:05 (마감 직후, 5초 여유)
        LocalDateTime next = CronExpression.parse(scheduled.cron())
                .next(LocalDateTime.of(2026, 10, 6, 23, 59, 59));
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 10, 7, 0, 0, 5));
    }
}

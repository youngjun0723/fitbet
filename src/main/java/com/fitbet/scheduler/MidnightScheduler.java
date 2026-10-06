package com.fitbet.scheduler;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * PRD 5.2 마감 스케줄러. 다음 날 00:00:05에 "어제"를 마감한다.
 * 23:59에 돌리면 23:59:00~23:59:59 사이 업로드가 인증과 벌금을 동시에 받는 모순이 생겨서 이렇게 정했다.
 * cron 필드: 초 분 시 일 월 요일
 */
@Component
@RequiredArgsConstructor
public class MidnightScheduler {

    static final String CRON = "5 0 0 * * *";
    static final String ZONE = "Asia/Seoul";

    private final DailyClosingService dailyClosingService;
    private final Clock clock;

    @Scheduled(cron = CRON, zone = ZONE)
    public void closeYesterday() {
        dailyClosingService.closeDay(LocalDate.now(clock).minusDays(1));
    }
}

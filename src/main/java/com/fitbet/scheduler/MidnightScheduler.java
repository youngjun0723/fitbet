package com.fitbet.scheduler;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PRD 5.2 마감 스케줄러.
 * - 매일 00:00:05에 실행 (23:59에 돌리면 23:59:00~59 업로드가 인증과 벌금을 동시에 받는 모순)
 * - "어제 하루"가 아니라 "최근 N일"을 마감한다 → 서버가 자정에 꺼져 있었어도 다음 실행 때 빠진 날이 채워진다
 * - 서버가 켜질 때도 한 번 실행 → 재배포·재시작 직후 바로 복구
 * cron 필드: 초 분 시 일 월 요일
 */
@Component
public class MidnightScheduler {

    static final String CRON = "5 0 0 * * *";
    static final String ZONE = "Asia/Seoul";

    private final DailyClosingService dailyClosingService;
    private final Clock clock;
    private final int catchUpDays;

    public MidnightScheduler(DailyClosingService dailyClosingService, Clock clock,
                             @Value("${fitbet.closing.catch-up-days:7}") int catchUpDays) {
        this.dailyClosingService = dailyClosingService;
        this.clock = clock;
        this.catchUpDays = catchUpDays;
    }

    @Scheduled(cron = CRON, zone = ZONE)
    public void closeRecentDays() {
        dailyClosingService.closeRecentDays(LocalDate.now(clock), catchUpDays);
    }

    /** 서버 기동이 끝난 직후 1회 (ApplicationReadyEvent = 모든 빈 준비 + 웹 서버 시작 완료) */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        closeRecentDays();
    }
}

package com.fitbet.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 테스트에서 "시간을 흘려보낼 수 있는" Clock. 하루 지나기, 날짜 점프 같은 시나리오용. */
public class MutableClock extends Clock {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private Instant instant;

    public MutableClock(LocalDateTime start) {
        setTo(start);
    }

    public void setTo(LocalDateTime dateTime) {
        this.instant = dateTime.atZone(SEOUL).toInstant();
    }

    public void plusDays(long days) {
        this.instant = instant.plus(Duration.ofDays(days));
    }

    @Override
    public ZoneId getZone() {
        return SEOUL;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}

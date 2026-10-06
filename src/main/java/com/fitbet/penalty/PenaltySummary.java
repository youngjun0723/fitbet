package com.fitbet.penalty;

import java.time.LocalDate;

/** 멤버별 미정산 벌금 집계 (GROUP BY 결과를 바로 받는 DTO projection) */
public record PenaltySummary(Long userId, String username, Long total, Long missedDays,
                             LocalDate firstDate, LocalDate lastDate) {
}

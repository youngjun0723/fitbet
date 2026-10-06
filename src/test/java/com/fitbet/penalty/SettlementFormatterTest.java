package com.fitbet.penalty;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fitbet.penalty.SettlementFormatter.RankedPenalty;

/** 순수 단위 테스트: 순위 계산과 송금 가이드 문구 */
class SettlementFormatterTest {

    static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
    static final LocalDate OCT6 = LocalDate.of(2026, 10, 6);
    static final Long HOST_ID = 1L;

    static PenaltySummary summary(long userId, String name, long total, long days) {
        return new PenaltySummary(userId, name, total, days, OCT1, OCT6);
    }

    @Test
    void PRD_예시와_같은_송금_가이드를_만든다() {
        List<RankedPenalty> ranked = SettlementFormatter.rank(List.of(
                summary(2, "민수", 4000, 4),
                summary(3, "지훈", 2000, 2),
                summary(4, "서연", 1000, 1)), HOST_ID);

        String text = SettlementFormatter.guideText("헬창들의 모임", "영진", OCT1, OCT6, ranked);

        assertThat(text).isEqualTo("""
                📢 [헬창들의 모임] 정산 리포트 (10/01 ~ 10/06)
                총 벌금 풀: 7,000원

                1위 🐢 민수 — 4,000원 (4일 미인증)
                2위 😅 지훈 — 2,000원 (2일 미인증)
                3위 🙂 서연 — 1,000원 (1일 미인증)

                💸 송금 안내
                - 민수 → 영진(방장): 4,000원
                - 지훈 → 영진(방장): 2,000원
                - 서연 → 영진(방장): 1,000원""");
    }

    @Test
    void 금액이_같으면_공동_순위이고_다음_순위는_건너뛴다() {
        List<RankedPenalty> ranked = SettlementFormatter.rank(List.of(
                summary(2, "민수", 3000, 3),
                summary(3, "지훈", 3000, 3),
                summary(4, "서연", 1000, 1)), HOST_ID);

        assertThat(ranked).extracting(RankedPenalty::rank).containsExactly(1, 1, 3);
    }

    @Test
    void 방장_본인_벌금은_송금_대신_풀에_포함으로_안내한다() {
        List<RankedPenalty> ranked = SettlementFormatter.rank(List.of(
                summary(2, "민수", 2000, 2),
                summary(HOST_ID, "영진", 1000, 1)), HOST_ID);

        String text = SettlementFormatter.guideText("헬창들의 모임", "영진", OCT1, OCT6, ranked);

        assertThat(ranked.get(1).host()).isTrue();
        assertThat(text)
                .contains("총 벌금 풀: 3,000원")
                .contains("- 민수 → 영진(방장): 2,000원")
                .contains("- 영진(방장) 본인 벌금 1,000원은 송금 없이 풀에 포함")
                .doesNotContain("영진 → 영진");
    }

    @Test
    void 하루치만_있으면_기간을_날짜_하나로_표시() {
        List<RankedPenalty> ranked = SettlementFormatter.rank(List.of(summary(2, "민수", 1000, 1)), HOST_ID);

        String text = SettlementFormatter.guideText("방", "영진", OCT6, OCT6, ranked);

        assertThat(text).startsWith("📢 [방] 정산 리포트 (10/06)\n");
    }

    @Test
    void 미정산_벌금이_없으면_축하_문구() {
        String text = SettlementFormatter.guideText("방", "영진", null, null, List.of());

        assertThat(text).contains("미정산 벌금이 없어요");
    }
}

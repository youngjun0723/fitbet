package com.fitbet.penalty;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 정산 리포트의 "계산"과 "문구"만 담당하는 순수 클래스 (DB·Spring 의존 없음 → 단위 테스트가 쉽다).
 * 벌금 풀 분배는 PRD 2.6 MVP 제안 ①: 전부 방장에게 송금.
 */
public final class SettlementFormatter {

    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM/dd");

    private SettlementFormatter() {
    }

    public record RankedPenalty(int rank, Long userId, String username, long total, long missedDays, boolean host) {
    }

    /**
     * 공동 순위 처리(1, 1, 3위 방식). summaries는 벌금 많은 순으로 정렬되어 있어야 한다.
     */
    public static List<RankedPenalty> rank(List<PenaltySummary> summaries, Long hostUserId) {
        List<RankedPenalty> ranked = new ArrayList<>();
        int rank = 0;
        Long previousTotal = null;
        for (int i = 0; i < summaries.size(); i++) {
            PenaltySummary s = summaries.get(i);
            if (!Objects.equals(s.total(), previousTotal)) {
                rank = i + 1; // 금액이 바뀌면 "앞에 있는 사람 수 + 1"위
                previousTotal = s.total();
            }
            ranked.add(new RankedPenalty(rank, s.userId(), s.username(), s.total(), s.missedDays(),
                    s.userId().equals(hostUserId)));
        }
        return ranked;
    }

    /** PRD 5.3 송금 가이드 텍스트 (카톡에 그대로 붙여넣는 용도) */
    public static String guideText(String roomTitle, String hostName,
                                   LocalDate from, LocalDate to, List<RankedPenalty> ranked) {
        if (ranked.isEmpty()) {
            return "📢 [" + roomTitle + "] 정산 리포트\n\n🎉 미정산 벌금이 없어요. 모두 성실했네요!";
        }
        long pool = ranked.stream().mapToLong(RankedPenalty::total).sum();

        StringBuilder sb = new StringBuilder();
        sb.append("📢 [").append(roomTitle).append("] 정산 리포트 (").append(period(from, to)).append(")\n");
        sb.append("총 벌금 풀: ").append(won(pool)).append("\n\n");

        for (RankedPenalty r : ranked) {
            sb.append(r.rank()).append("위 ").append(emoji(r.rank())).append(' ').append(r.username())
                    .append(" — ").append(won(r.total()))
                    .append(" (").append(r.missedDays()).append("일 미인증)\n");
        }

        sb.append("\n💸 송금 안내\n");
        for (RankedPenalty r : ranked) {
            if (r.host()) {
                sb.append("- ").append(hostName).append("(방장) 본인 벌금 ").append(won(r.total()))
                        .append("은 송금 없이 풀에 포함\n");
            } else {
                sb.append("- ").append(r.username()).append(" → ").append(hostName).append("(방장): ")
                        .append(won(r.total())).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    static String won(long amount) {
        return String.format("%,d원", amount);
    }

    private static String period(LocalDate from, LocalDate to) {
        return from.equals(to) ? from.format(MM_DD) : from.format(MM_DD) + " ~ " + to.format(MM_DD);
    }

    private static String emoji(int rank) {
        return switch (rank) {
            case 1 -> "🐢";
            case 2 -> "😅";
            default -> "🙂";
        };
    }
}

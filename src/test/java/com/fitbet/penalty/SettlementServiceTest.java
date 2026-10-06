package com.fitbet.penalty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.common.exception.ErrorCode;
import com.fitbet.penalty.SettlementFormatter.RankedPenalty;
import com.fitbet.penalty.SettlementResponse.StreakRank;
import com.fitbet.penalty.SettlementService.SettleResult;
import com.fitbet.room.Room;
import com.fitbet.room.RoomService;
import com.fitbet.streak.StreakRepository;
import com.fitbet.user.User;
import com.fitbet.user.UserRepository;

@SpringBootTest
@Transactional
class SettlementServiceTest {

    static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);

    @Autowired SettlementService settlementService;
    @Autowired PenaltyLogRepository penaltyLogRepository;
    @Autowired StreakRepository streakRepository;
    @Autowired RoomService roomService;
    @Autowired UserRepository userRepository;

    User host, minsu, jihun;
    Room room;

    @BeforeEach
    void setUp() {
        host = userRepository.save(User.create("영진"));
        minsu = userRepository.save(User.create("민수"));
        jihun = userRepository.save(User.create("지훈"));
        room = roomService.createRoom(host.getId(), "헬창들의 모임", null);
        roomService.join(minsu.getId(), room.getInviteCode());
        roomService.join(jihun.getId(), room.getInviteCode());

        penalize(minsu, 0, 1, 2); // 민수 10/1~10/3 → 3,000원
        penalize(jihun, 4);       // 지훈 10/5     → 1,000원
    }

    @Test
    void 미정산_벌금을_멤버별로_합산해_순위를_매긴다() {
        PenaltyLog alreadySettled = PenaltyLog.of(jihun, room, OCT1.minusDays(10));
        alreadySettled.settle();
        penaltyLogRepository.save(alreadySettled); // 지난 정산분은 제외돼야 함

        SettlementResponse report = settlementService.getReport(minsu.getId(), room.getId());

        assertThat(report.totalPool()).isEqualTo(4000);
        assertThat(report.penaltyRanking())
                .extracting(RankedPenalty::rank, RankedPenalty::username, RankedPenalty::total, RankedPenalty::missedDays)
                .containsExactly(tuple(1, "민수", 3000L, 3L), tuple(2, "지훈", 1000L, 1L));
        assertThat(report.periodFrom()).isEqualTo(OCT1);
        assertThat(report.periodTo()).isEqualTo(OCT1.plusDays(4));
        assertThat(report.hostName()).isEqualTo("영진");
        assertThat(report.meHost()).isFalse();
        assertThat(report.guideText()).contains("- 민수 → 영진(방장): 3,000원");
    }

    @Test
    void 다른_방의_벌금은_섞이지_않는다() {
        Room other = roomService.createRoom(minsu.getId(), "러닝 크루", 5000);
        penaltyLogRepository.save(PenaltyLog.of(minsu, other, OCT1));

        SettlementResponse report = settlementService.getReport(host.getId(), room.getId());

        assertThat(report.totalPool()).isEqualTo(4000);
    }

    @Test
    void Streak_랭킹은_현재_연속일수_높은_순() {
        streakRepository.findByUserIdAndRoomId(jihun.getId(), room.getId()).orElseThrow()
                .recordVerification(OCT1);

        SettlementResponse report = settlementService.getReport(host.getId(), room.getId());

        assertThat(report.streakRanking()).extracting(StreakRank::username).first().isEqualTo("지훈");
        assertThat(report.streakRanking()).hasSize(3);
    }

    @Test
    void 방장이_정산_완료하면_until_이전_벌금만_정산되고_리포트에서_빠진다() {
        penalize(minsu, 6); // 방장이 리포트를 본 뒤(10/5까지) 스케줄러가 10/7 벌금을 추가한 상황

        SettleResult result = settlementService.settle(host.getId(), room.getId(), OCT1.plusDays(4));

        assertThat(result.settledCount()).isEqualTo(4);   // 10/1~10/5의 4건
        assertThat(result.remainingPool()).isEqualTo(1000); // 10/7 건은 다음 정산으로
        // 벌크 UPDATE 후 같은 트랜잭션에서 다시 읽어도 최신 상태 (clearAutomatically 덕분)
        SettlementResponse after = settlementService.getReport(host.getId(), room.getId());
        assertThat(after.penaltyRanking()).extracting(RankedPenalty::username).containsExactly("민수");
        assertThat(after.periodFrom()).isEqualTo(OCT1.plusDays(6));
    }

    @Test
    void 방장이_아니면_정산_완료_불가() {
        assertThatThrownBy(() -> settlementService.settle(minsu.getId(), room.getId(), OCT1.plusDays(4)))
                .extracting("errorCode").isEqualTo(ErrorCode.HOST_ONLY);
        assertThat(penaltyLogRepository.sumUnsettledAmount(room.getId())).isEqualTo(4000);
    }

    @Test
    void 방_멤버가_아니면_리포트도_못_본다() {
        User outsider = userRepository.save(User.create("외부인"));

        assertThatThrownBy(() -> settlementService.getReport(outsider.getId(), room.getId()))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    private void penalize(User user, int... dayOffsets) {
        for (int offset : dayOffsets) {
            penaltyLogRepository.save(PenaltyLog.of(user, room, OCT1.plusDays(offset)));
        }
    }
}

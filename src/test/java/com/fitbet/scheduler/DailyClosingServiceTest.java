package com.fitbet.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.challenge.ChallengeService;
import com.fitbet.penalty.PenaltyLog;
import com.fitbet.penalty.PenaltyLogRepository;
import com.fitbet.room.Room;
import com.fitbet.room.RoomService;
import com.fitbet.scheduler.DailyClosingService.ClosingResult;
import com.fitbet.streak.Streak;
import com.fitbet.streak.StreakRepository;
import com.fitbet.support.MutableClock;
import com.fitbet.user.User;
import com.fitbet.user.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * 시나리오 (방 "헬창들의 모임", 벌금 2,000원)
 *   10/5 영진(방장)·민수·지훈 참여, 지훈만 10/5 인증
 *   10/6 서연 참여, 민수만 10/6 인증
 *   10/7 00:00:05 → 10/6 마감
 */
@SpringBootTest
@Transactional
class DailyClosingServiceTest {

    static final LocalDate OCT5 = LocalDate.of(2026, 10, 5);
    static final LocalDate OCT6 = LocalDate.of(2026, 10, 6);
    static final LocalDate OCT7 = LocalDate.of(2026, 10, 7);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(OCT5.atTime(10, 0));
        }
    }

    @Autowired DailyClosingService dailyClosingService;
    @Autowired RoomService roomService;
    @Autowired ChallengeService challengeService;
    @Autowired UserRepository userRepository;
    @Autowired PenaltyLogRepository penaltyLogRepository;
    @Autowired StreakRepository streakRepository;
    @Autowired MutableClock clock;
    @Autowired EntityManager em;

    User host, minsu, jihun, seoyeon;
    Room room;

    @BeforeEach
    void setUp() {
        clock.setTo(OCT5.atTime(10, 0));
        host = userRepository.save(User.create("영진"));
        minsu = userRepository.save(User.create("민수"));
        jihun = userRepository.save(User.create("지훈"));
        seoyeon = userRepository.save(User.create("서연"));

        room = roomService.createRoom(host.getId(), "헬창들의 모임", 2000);
        roomService.join(minsu.getId(), room.getInviteCode());
        roomService.join(jihun.getId(), room.getInviteCode());
        challengeService.upload(jihun.getId(), room.getId(), photo(), null); // 지훈 10/5 인증 → streak 1

        clock.setTo(OCT6.atTime(9, 0));
        roomService.join(seoyeon.getId(), room.getInviteCode());
        challengeService.upload(minsu.getId(), room.getId(), photo(), null); // 민수 10/6 인증

        clock.setTo(OCT7.atTime(0, 0, 5));
    }

    @Test
    void 미인증_멤버에게만_방_벌금액_스냅샷으로_벌금을_부과한다() {
        ClosingResult result = dailyClosingService.closeDay(OCT6);

        assertThat(result.penalizedCount()).isEqualTo(2);
        assertThat(penaltyLogRepository.findAll())
                .extracting(p -> p.getUser().getUsername(), PenaltyLog::getTargetDate, PenaltyLog::getAmount)
                .containsExactlyInAnyOrder(
                        tuple("영진", OCT6, 2000),
                        tuple("지훈", OCT6, 2000));
    }

    @Test
    void 참여_당일은_면제되고_다음_날부터_벌금이_붙는다() {
        dailyClosingService.closeDay(OCT6);
        assertThat(penaltiesOf(seoyeon)).isZero(); // 10/6 참여 → 10/6은 면제

        dailyClosingService.closeDay(OCT7);
        assertThat(penaltiesOf(seoyeon)).isEqualTo(1); // 10/7은 부과
    }

    @Test
    void 미인증자의_Streak은_0이_되지만_최고기록은_남는다() {
        dailyClosingService.closeDay(OCT6);
        em.flush();
        em.clear();

        Streak jihunStreak = streakOf(jihun);
        assertThat(jihunStreak.getCurrentStreak()).isZero();
        assertThat(jihunStreak.getMaxStreak()).isEqualTo(1);

        Streak minsuStreak = streakOf(minsu); // 인증한 사람은 그대로
        assertThat(minsuStreak.getCurrentStreak()).isEqualTo(1);
    }

    @Test
    void 스케줄러가_두_번_돌아도_벌금은_한_번만_멱등성() {
        dailyClosingService.closeDay(OCT6);
        ClosingResult second = dailyClosingService.closeDay(OCT6);

        assertThat(second.penalizedCount()).isZero();
        assertThat(second.failedRoomIds()).isEmpty();
        assertThat(penaltyLogRepository.count()).isEqualTo(2);
    }

    @Test
    void 방마다_따로_판정한다_다른_방_인증은_인정되지_않는다() {
        Room otherRoom = createRoomAt(OCT5, minsu, "러닝 크루"); // 민수 방장, 벌금 1,000원

        dailyClosingService.closeDay(OCT6);

        // 민수는 헬창방엔 인증했지만 러닝 크루엔 안 함 → 러닝 크루에서만 1,000원
        assertThat(penaltyLogRepository.findAll())
                .filteredOn(p -> p.getUser().getId().equals(minsu.getId()))
                .extracting(p -> p.getRoom().getId(), PenaltyLog::getAmount)
                .containsExactly(tuple(otherRoom.getId(), 1000));
    }

    @Test
    void 인증하면_다음_날_Streak은_1부터_다시_시작() {
        dailyClosingService.closeDay(OCT6); // 지훈 streak 0

        clock.setTo(OCT7.atTime(8, 0));
        var result = challengeService.upload(jihun.getId(), room.getId(), photo(), null);

        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(result.maxStreak()).isEqualTo(1);
    }

    @Test
    void 서버가_꺼져서_빠진_날은_따라잡기로_채우고_그_뒤_인증한_사람의_Streak은_지킨다() {
        // 10/6, 10/7 자정에 서버가 꺼져 있어서 마감이 한 번도 안 돌았다
        clock.setTo(OCT7.plusDays(1).atTime(10, 0));                          // 10/8
        challengeService.upload(jihun.getId(), room.getId(), photo(), null); // 지훈 10/8 인증 → streak 1

        // 10/9 00:00:05 서버 복구 → 최근 7일(10/2~10/8) 따라잡기
        List<ClosingResult> results = dailyClosingService.closeRecentDays(OCT7.plusDays(2), 7);

        assertThat(results).extracting(ClosingResult::targetDate)
                .containsExactly(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4),
                        OCT5, OCT6, OCT7, OCT7.plusDays(1));
        assertThat(results).extracting(ClosingResult::penalizedCount)
                .containsExactly(0, 0, 0, 0, 2, 4, 3); // 10/5는 전원 참여 당일, 10/6 영진·지훈, 10/7 전원, 10/8 지훈 제외

        em.flush();
        em.clear();
        assertThat(streakOf(jihun).getCurrentStreak()).isEqualTo(1); // 10/8 인증 이후라 10/6·7 늦은 마감에도 유지
        assertThat(streakOf(minsu).getCurrentStreak()).isZero();     // 10/6 인증 후 10/7 놓침
        assertThat(streakOf(minsu).getMaxStreak()).isEqualTo(1);

        // 한 번 더 돌려도 추가 벌금 없음 (매일 7일치를 다시 훑어도 안전한 이유)
        assertThat(dailyClosingService.closeRecentDays(OCT7.plusDays(2), 7))
                .extracting(ClosingResult::penalizedCount).containsOnly(0);
    }

    private Room createRoomAt(LocalDate date, User host, String title) {
        clock.setTo(date.atTime(10, 0));
        Room created = roomService.createRoom(host.getId(), title, null);
        clock.setTo(OCT7.atTime(0, 0, 5));
        return created;
    }

    private long penaltiesOf(User user) {
        return penaltyLogRepository.findAll().stream()
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .count();
    }

    private Streak streakOf(User user) {
        return streakRepository.findByUserIdAndRoomId(user.getId(), room.getId()).orElseThrow();
    }

    private MockMultipartFile photo() {
        return new MockMultipartFile("photo", "workout.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}

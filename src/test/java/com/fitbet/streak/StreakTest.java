package com.fitbet.streak;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fitbet.room.Room;
import com.fitbet.user.User;

/** Spring도 DB도 없이 도는 순수 단위 테스트 — 규칙이 엔티티 안에 있어서 가능하다. */
class StreakTest {

    static final LocalDate D1 = LocalDate.of(2026, 10, 1);

    Streak newStreak() {
        return Streak.start(User.create("민수"), Room.create("방", null, "ABC234"));
    }

    @Test
    void 첫_인증이면_1일() {
        Streak streak = newStreak();

        streak.recordVerification(D1);

        assertThat(streak.getCurrentStreak()).isEqualTo(1);
        assertThat(streak.getMaxStreak()).isEqualTo(1);
        assertThat(streak.getLastVerifiedDate()).isEqualTo(D1);
    }

    @Test
    void 어제_인증했으면_이어서_증가() {
        Streak streak = newStreak();

        streak.recordVerification(D1);
        streak.recordVerification(D1.plusDays(1));
        streak.recordVerification(D1.plusDays(2));

        assertThat(streak.getCurrentStreak()).isEqualTo(3);
        assertThat(streak.getMaxStreak()).isEqualTo(3);
    }

    @Test
    void 하루라도_비면_1부터_다시_시작하고_max는_유지() {
        Streak streak = newStreak();
        streak.recordVerification(D1);
        streak.recordVerification(D1.plusDays(1)); // 2일 연속

        streak.recordVerification(D1.plusDays(3)); // 하루(D+2) 건너뜀

        assertThat(streak.getCurrentStreak()).isEqualTo(1);
        assertThat(streak.getMaxStreak()).isEqualTo(2);
    }

    @Test
    void 같은_날_두_번_반영해도_한_번만_센다() {
        Streak streak = newStreak();

        streak.recordVerification(D1);
        streak.recordVerification(D1);

        assertThat(streak.getCurrentStreak()).isEqualTo(1);
    }

    @Test
    void 리셋_후_다음_인증은_1부터_max는_그대로() {
        Streak streak = newStreak();
        streak.recordVerification(D1);
        streak.recordVerification(D1.plusDays(1));
        streak.recordVerification(D1.plusDays(2)); // 3일 연속

        streak.reset(); // D+3 미인증으로 마감
        assertThat(streak.getCurrentStreak()).isZero();
        assertThat(streak.getMaxStreak()).isEqualTo(3);

        streak.recordVerification(D1.plusDays(4));
        assertThat(streak.getCurrentStreak()).isEqualTo(1);
        assertThat(streak.getMaxStreak()).isEqualTo(3);
    }

    @Test
    void 기록을_다시_넘어서면_max도_갱신() {
        Streak streak = newStreak();
        streak.recordVerification(D1);              // 1
        streak.recordVerification(D1.plusDays(2)); // 끊김 → 1
        streak.recordVerification(D1.plusDays(3)); // 2

        assertThat(streak.getCurrentStreak()).isEqualTo(2);
        assertThat(streak.getMaxStreak()).isEqualTo(2);
    }
}

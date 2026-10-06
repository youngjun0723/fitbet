package com.fitbet.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fitbet.room.RoomRepository;
import com.fitbet.scheduler.DailyClosingService.ClosingResult;

/** 한 방이 실패해도 나머지 방은 마감된다 — 방마다 트랜잭션을 나눈 이유를 검증 */
class DailyClosingServiceUnitTest {

    static final LocalDate TARGET = LocalDate.of(2026, 10, 6);

    @Test
    void 한_방에서_예외가_나도_다른_방은_계속_처리하고_실패한_방을_기록한다() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        RoomDayCloser closer = mock(RoomDayCloser.class);
        given(roomRepository.findAllIds()).willReturn(List.of(1L, 2L, 3L));
        given(closer.close(1L, TARGET)).willReturn(2);
        given(closer.close(2L, TARGET)).willThrow(new IllegalStateException("DB 오류"));
        given(closer.close(3L, TARGET)).willReturn(1);

        ClosingResult result = new DailyClosingService(roomRepository, closer).closeDay(TARGET);

        then(closer).should().close(3L, TARGET); // 2번 방 실패 후에도 3번 방까지 진행
        assertThat(result.roomCount()).isEqualTo(3);
        assertThat(result.penalizedCount()).isEqualTo(3);
        assertThat(result.failedRoomIds()).containsExactly(2L);
    }
}

package com.fitbet.scheduler;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fitbet.room.RoomRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모든 방의 하루 마감. 여기엔 일부러 @Transactional을 붙이지 않았다.
 * 전체를 트랜잭션 하나로 묶으면 방 하나에서 예외가 날 때 모든 방의 벌금이 롤백된다.
 * → 방마다 RoomDayCloser.close()가 각자 커밋/롤백하고, 실패한 방만 기록해 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyClosingService {

    private final RoomRepository roomRepository;
    private final RoomDayCloser roomDayCloser;

    public ClosingResult closeDay(LocalDate targetDate) {
        List<Long> roomIds = roomRepository.findAllIds();
        int penalized = 0;
        List<Long> failedRoomIds = new ArrayList<>();

        for (Long roomId : roomIds) {
            try {
                penalized += roomDayCloser.close(roomId, targetDate);
            } catch (Exception e) {
                failedRoomIds.add(roomId);
                log.error("[마감] {} roomId={} 처리 실패", targetDate, roomId, e);
            }
        }

        ClosingResult result = new ClosingResult(targetDate, roomIds.size(), penalized, failedRoomIds);
        log.info("[마감] {}", result);
        return result;
    }

    public record ClosingResult(LocalDate targetDate, int roomCount, int penalizedCount, List<Long> failedRoomIds) {
    }
}

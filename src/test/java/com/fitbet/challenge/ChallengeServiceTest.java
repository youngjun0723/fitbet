package com.fitbet.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.willReturn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.FileSystemUtils;

import com.fitbet.challenge.ChallengeService.UploadResult;
import com.fitbet.common.exception.ErrorCode;
import com.fitbet.common.storage.StorageService;
import com.fitbet.room.Room;
import com.fitbet.room.RoomMemberRepository;
import com.fitbet.room.RoomRepository;
import com.fitbet.room.RoomService;
import com.fitbet.streak.StreakRepository;
import com.fitbet.support.MutableClock;
import com.fitbet.user.User;
import com.fitbet.user.UserRepository;

/**
 * 업로드 전체 흐름 통합 테스트.
 * 클래스에 @Transactional을 붙이지 않았다: 서비스의 "실제 커밋/롤백"과 그에 따른 파일 보상 삭제를 확인해야 하기 때문.
 * 대신 @AfterEach에서 데이터를 직접 지운다.
 */
@SpringBootTest
class ChallengeServiceTest {

    static final LocalDateTime DAY1_MORNING = LocalDateTime.of(2026, 10, 2, 9, 0);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(DAY1_MORNING);
        }
    }

    @Autowired ChallengeService challengeService;
    @Autowired RoomService roomService;
    @Autowired UserRepository userRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired RoomMemberRepository roomMemberRepository;
    @Autowired StreakRepository streakRepository;
    @MockitoSpyBean ChallengeLogRepository challengeLogRepository;
    @MockitoSpyBean StorageService storageService;
    @Autowired MutableClock clock;
    @Value("${fitbet.storage.local-dir}") String uploadDir;

    User minsu;
    User outsider;
    Room room;

    @BeforeEach
    void setUp() throws IOException {
        clock.setTo(DAY1_MORNING);
        FileSystemUtils.deleteRecursively(Path.of(uploadDir));
        Files.createDirectories(Path.of(uploadDir));

        minsu = userRepository.save(User.create("민수"));
        outsider = userRepository.save(User.create("외부인"));
        room = roomService.createRoom(minsu.getId(), "헬창들의 모임", null);
    }

    @AfterEach
    void tearDown() {
        // FK 순서: 자식 테이블부터
        challengeLogRepository.deleteAllInBatch();
        streakRepository.deleteAllInBatch();
        roomMemberRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 첫_인증이면_로그가_저장되고_Streak은_1() {
        UploadResult result = challengeService.upload(minsu.getId(), room.getId(), photo(), "하체 데이");

        assertThat(result.logDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(result.maxStreak()).isEqualTo(1);
        assertThat(result.photoUrl()).startsWith("/uploads/").endsWith(".jpg");
        assertThat(uploadedFileCount()).isEqualTo(1);
    }

    @Test
    void 다음_날_또_인증하면_Streak_2() {
        challengeService.upload(minsu.getId(), room.getId(), photo(), null);
        clock.plusDays(1);

        UploadResult result = challengeService.upload(minsu.getId(), room.getId(), photo(), null);

        assertThat(result.currentStreak()).isEqualTo(2);
        assertThat(result.maxStreak()).isEqualTo(2);
    }

    @Test
    void 하루_건너뛰면_Streak은_1로_다시_시작하고_max는_유지() {
        challengeService.upload(minsu.getId(), room.getId(), photo(), null);
        clock.plusDays(1);
        challengeService.upload(minsu.getId(), room.getId(), photo(), null);
        clock.plusDays(2); // 하루 건너뜀

        UploadResult result = challengeService.upload(minsu.getId(), room.getId(), photo(), null);

        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(result.maxStreak()).isEqualTo(2);
    }

    @Test
    void 같은_날_두_번째_업로드는_409이고_파일도_남지_않는다() {
        challengeService.upload(minsu.getId(), room.getId(), photo(), null);

        assertThatThrownBy(() -> challengeService.upload(minsu.getId(), room.getId(), photo(), null))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_VERIFIED_TODAY);
        assertThat(uploadedFileCount()).isEqualTo(1);
        then(storageService).should(never()).delete(anyString()); // 1차 체크에서 막혀 파일을 쓰지도 않았다
    }

    @Test
    void 동시_업로드로_앱_체크를_통과해도_DB가_막고_저장된_파일은_보상_삭제된다() {
        challengeService.upload(minsu.getId(), room.getId(), photo(), null);

        // 두 요청이 동시에 들어와 둘 다 "아직 인증 안 함"으로 판단한 상황을 재현
        willReturn(false).given(challengeLogRepository)
                .existsByUserIdAndRoomIdAndLogDate(any(), any(), any());

        assertThatThrownBy(() -> challengeService.upload(minsu.getId(), room.getId(), photo(), null))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_VERIFIED_TODAY);
        // 두 번째 요청은 파일을 디스크에 쓴 뒤(DB 단계에서) 실패 → 롤백과 함께 보상 삭제
        then(storageService).should().delete(anyString());
        assertThat(uploadedFileCount()).isEqualTo(1);
        assertThat(challengeLogRepository.count()).isEqualTo(1);
    }

    @Test
    void 방_멤버가_아니면_403_파일도_저장하지_않는다() {
        assertThatThrownBy(() -> challengeService.upload(outsider.getId(), room.getId(), photo(), null))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
        assertThat(uploadedFileCount()).isZero();
    }

    @Test
    void 이미지가_아니면_400_파일도_저장하지_않는다() {
        var gif = new MockMultipartFile("photo", "a.gif", "image/gif", new byte[]{1});

        assertThatThrownBy(() -> challengeService.upload(minsu.getId(), room.getId(), gif, null))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_IMAGE);
        assertThat(uploadedFileCount()).isZero();
    }

    private MockMultipartFile photo() {
        return new MockMultipartFile("photo", "workout.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private long uploadedFileCount() {
        try (Stream<Path> files = Files.list(Path.of(uploadDir))) {
            return files.count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

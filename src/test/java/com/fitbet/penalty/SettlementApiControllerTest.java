package com.fitbet.penalty;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.common.auth.LoginUserIdArgumentResolver;
import com.fitbet.room.Room;
import com.fitbet.room.RoomService;
import com.fitbet.user.User;
import com.fitbet.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoomService roomService;
    @Autowired PenaltyLogRepository penaltyLogRepository;

    User host, minsu;
    Room room;

    @BeforeEach
    void setUp() {
        host = userRepository.save(User.create("영진"));
        minsu = userRepository.save(User.create("민수"));
        room = roomService.createRoom(host.getId(), "헬창들의 모임", null);
        roomService.join(minsu.getId(), room.getInviteCode());
        penaltyLogRepository.save(PenaltyLog.of(minsu, room, LocalDate.of(2026, 10, 5)));
    }

    @Test
    void 정산_리포트_조회() throws Exception {
        mockMvc.perform(get("/api/rooms/{id}/settlement", room.getId()).session(sessionOf(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPool").value(1000))
                .andExpect(jsonPath("$.meHost").value(true))
                .andExpect(jsonPath("$.periodTo").value("2026-10-05"))
                .andExpect(jsonPath("$.penaltyRanking[0].username").value("민수"));
    }

    @Test
    void 방장은_정산_완료_가능() throws Exception {
        mockMvc.perform(patch("/api/rooms/{id}/settlement", room.getId()).session(sessionOf(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"until\":\"2026-10-05\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settledCount").value(1))
                .andExpect(jsonPath("$.remainingPool").value(0));
    }

    @Test
    void 멤버가_정산_완료를_시도하면_403() throws Exception {
        mockMvc.perform(patch("/api/rooms/{id}/settlement", room.getId()).session(sessionOf(minsu))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"until\":\"2026-10-05\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HOST_ONLY"));
    }

    @Test
    void until이_없으면_400() throws Exception {
        mockMvc.perform(patch("/api/rooms/{id}/settlement", room.getId()).session(sessionOf(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private MockHttpSession sessionOf(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginUserIdArgumentResolver.SESSION_KEY, user.getId());
        return session;
    }
}

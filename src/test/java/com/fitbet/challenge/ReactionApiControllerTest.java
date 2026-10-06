package com.fitbet.challenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
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
class ReactionApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoomService roomService;
    @Autowired ChallengeService challengeService;

    User host;
    Long logId;

    @BeforeEach
    void setUp() {
        host = userRepository.save(User.create("영진"));
        User minsu = userRepository.save(User.create("민수"));
        Room room = roomService.createRoom(host.getId(), "헬창들의 모임", null);
        roomService.join(minsu.getId(), room.getInviteCode());
        logId = challengeService.upload(minsu.getId(), room.getId(),
                new MockMultipartFile("photo", "a.jpg", "image/jpeg", new byte[]{1}), null).logId();
    }

    @Test
    void 리액션을_남기면_카운트와_내_리액션을_돌려준다() throws Exception {
        mockMvc.perform(post("/api/challenges/{logId}/reactions", logId).session(sessionOf(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approveCount").value(1))
                .andExpect(jsonPath("$.doubtCount").value(0))
                .andExpect(jsonPath("$.myReaction").value("APPROVE"));
    }

    @Test
    void 없는_리액션_종류면_400() throws Exception {
        mockMvc.perform(post("/api/challenges/{logId}/reactions", logId).session(sessionOf(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LIKE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 종류를_빠뜨리면_400() throws Exception {
        mockMvc.perform(post("/api/challenges/{logId}/reactions", logId).session(sessionOf(host))
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

package com.fitbet.room;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** HTTP 요청 → 컨트롤러 → 서비스 → DB 전체 흐름 검증 (로그인 세션, 검증 에러, 예외 응답 포함) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void 로그인_방생성_참여_시나리오() throws Exception {
        MockHttpSession hostSession = login("영진");
        String body = mockMvc.perform(post("/api/rooms").session(hostSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"헬창들의 모임\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.penaltyAmount").value(1000))
                .andExpect(jsonPath("$.myRole").value("HOST"))
                .andReturn().getResponse().getContentAsString();
        String inviteCode = objectMapper.readTree(body).get("inviteCode").asText();

        MockHttpSession friendSession = login("민수");
        mockMvc.perform(post("/api/rooms/join").session(friendSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"));

        // 같은 코드로 한 번 더 → 409
        mockMvc.perform(post("/api/rooms/join").session(friendSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));

        // 참여한 친구는 대시보드 조회 가능, 처음 보는 사람은 403
        String roomId = objectMapper.readTree(body).get("roomId").asText();
        mockMvc.perform(get("/api/rooms/{roomId}/dashboard", roomId).session(friendSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.title").value("헬창들의 모임"))
                .andExpect(jsonPath("$.room.memberCount").value(2))
                .andExpect(jsonPath("$.me.role").value("MEMBER"));
        mockMvc.perform(get("/api/rooms/{roomId}/dashboard", roomId).session(login("외부인")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"방\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    @Test
    void 제목이_비어있으면_400() throws Exception {
        mockMvc.perform(post("/api/rooms").session(login("영진"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 초대_코드_형식이_틀리면_400() throws Exception {
        mockMvc.perform(post("/api/rooms/join").session(login("민수"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"12\"}"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession login(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String body = mockMvc.perform(post("/api/users/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        assert node.get("userId") != null;
        return session;
    }
}

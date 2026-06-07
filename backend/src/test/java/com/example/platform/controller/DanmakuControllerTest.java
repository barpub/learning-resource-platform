package com.example.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.platform.entity.User;
import com.example.platform.mapper.UserMapper;
import com.example.platform.security.JwtUtil;
import com.example.platform.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class DanmakuControllerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Test
    void sharedNoteModeShowsOnlySharerResourceAuthorAndCurrentUserDanmakus() throws Exception {
        String ownerToken = token("danmaku_owner", "danmaku-owner@example.com");
        String sharerToken = token("danmaku_sharer", "danmaku-sharer@example.com");
        String viewerToken = token("danmaku_viewer", "danmaku-viewer@example.com");
        String otherToken = token("danmaku_other", "danmaku-other@example.com");

        long resourceId = uploadFile(ownerToken);
        sendDanmaku(ownerToken, resourceId, "owner-visible");
        sendDanmaku(sharerToken, resourceId, "sharer-visible");
        sendDanmaku(viewerToken, resourceId, "viewer-visible");
        sendDanmaku(otherToken, resourceId, "other-hidden");

        long noteId = createSharedNote(sharerToken, resourceId);
        String shareToken = createShare(sharerToken, noteId);

        List<String> allContents = contents(mockMvc.perform(get("/api/resources/{id}/danmakus", resourceId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(allContents).contains("owner-visible", "sharer-visible", "viewer-visible", "other-hidden");

        List<String> filteredContents = contents(mockMvc.perform(get("/api/resources/{id}/danmakus", resourceId)
                        .param("mode", "SHARE_NOTE")
                        .param("shareToken", shareToken)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(filteredContents).contains("owner-visible", "sharer-visible", "viewer-visible");
        assertThat(filteredContents).doesNotContain("other-hidden");

        List<String> invalidShareContents = contents(mockMvc.perform(get("/api/resources/{id}/danmakus", resourceId)
                        .param("mode", "SHARE_NOTE")
                        .param("shareToken", "missing-token")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(invalidShareContents).contains("owner-visible", "viewer-visible");
        assertThat(invalidShareContents).doesNotContain("sharer-visible", "other-hidden");

        List<String> anonymousFilteredContents = contents(mockMvc.perform(get("/api/resources/{id}/danmakus", resourceId)
                        .param("mode", "SHARE_NOTE")
                        .param("shareToken", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(anonymousFilteredContents).contains("owner-visible", "sharer-visible");
        assertThat(anonymousFilteredContents).doesNotContain("viewer-visible", "other-hidden");
    }

    private String token(String username, String email) {
        userService.ensureSeedUser(username, "user123456", email, "USER");
        User user = userMapper.findByUsername(username);
        return jwtUtil.generateToken(user);
    }

    private long uploadFile(String token) throws Exception {
        String response = mockMvc.perform(multipart("/api/resources")
                        .file(new MockMultipartFile("file", "danmaku-video.mp4", "video/mp4", "video".getBytes()))
                        .param("title", "Danmaku Video")
                        .param("description", "Danmaku Video")
                        .param("categoryId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return OBJECT_MAPPER.readTree(response).path("data").path("id").asLong();
    }

    private void sendDanmaku(String token, long resourceId, String content) throws Exception {
        mockMvc.perform(post("/api/resources/{id}/danmakus", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "content", content,
                                "timeSeconds", 1.0,
                                "type", "scroll",
                                "color", "#ffffff"
                        )))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private long createSharedNote(String token, long resourceId) throws Exception {
        String response = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Shared note danmaku",
                                "content", "Share this video note",
                                "resourceId", resourceId
                        )))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return OBJECT_MAPPER.readTree(response).path("data").path("id").asLong();
    }

    private String createShare(String token, long noteId) throws Exception {
        String response = mockMvc.perform(post("/api/notes/{id}/share", noteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return OBJECT_MAPPER.readTree(response).path("data").path("token").asText();
    }

    private List<String> contents(String response) throws Exception {
        JsonNode data = OBJECT_MAPPER.readTree(response).path("data");
        List<String> result = new ArrayList<>();
        for (JsonNode item : data) {
            result.add(item.path("content").asText());
        }
        return result;
    }

    private String json(Object value) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(value);
    }
}

package com.example.platform.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.platform.entity.User;
import com.example.platform.mapper.UserMapper;
import com.example.platform.security.JwtUtil;
import com.example.platform.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class UserDashboardControllerTest {
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
    void mineDashboardRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/users/me/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void mineDashboardSummarizesCurrentUserUploadsAndEngagement() throws Exception {
        User owner = user("mine_owner", "mine-owner@example.com", "USER");
        User viewer = user("mine_viewer", "mine-viewer@example.com", "USER");
        String ownerToken = jwtUtil.generateToken(owner);
        String viewerToken = jwtUtil.generateToken(viewer);

        long docId = uploadFile(ownerToken, "mine-doc.pdf", "Mine Doc", "application/pdf");
        uploadFile(ownerToken, "mine-note.txt", "Mine Note", "text/plain");
        uploadFile(viewerToken, "other.pdf", "Other Owner", "application/pdf");

        mockMvc.perform(get("/api/resources/{id}", docId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/resources/{id}/download", docId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me/dashboard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.summary.uploadCount").value(2))
                .andExpect(jsonPath("$.data.summary.totalViews").value(1))
                .andExpect(jsonPath("$.data.summary.totalDownloads").value(1))
                .andExpect(jsonPath("$.data.uploads.length()").value(2))
                .andExpect(jsonPath("$.data.uploads[0].username").value("mine_owner"))
                .andExpect(jsonPath("$.data.categoryStats[0].categoryId").value(1))
                .andExpect(jsonPath("$.data.categoryStats[0].count").value(2))
                .andExpect(jsonPath("$.data.downloadTrend.length()").value(7));
    }

    private User user(String username, String email, String role) {
        userService.ensureSeedUser(username, "user123456", email, role);
        return userMapper.findByUsername(username);
    }

    private long uploadFile(String token, String fileName, String title, String contentType) throws Exception {
        String response = mockMvc.perform(multipart("/api/resources")
                        .file(new MockMultipartFile("file", fileName, contentType, "content".getBytes()))
                        .param("title", title)
                        .param("description", title)
                        .param("categoryId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return OBJECT_MAPPER.readTree(response).path("data").path("id").asLong();
    }
}

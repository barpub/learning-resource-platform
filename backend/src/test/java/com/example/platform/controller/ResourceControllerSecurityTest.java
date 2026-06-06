package com.example.platform.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.platform.entity.User;
import com.example.platform.mapper.UserMapper;
import com.example.platform.security.JwtUtil;
import com.example.platform.service.UserService;
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
class ResourceControllerSecurityTest {
    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
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
    void uploadRequiresAValidLoginToken() throws Exception {
        mockMvc.perform(multipart("/api/resources")
                        .file(file())
                        .param("title", "security upload test")
                        .param("description", "multipart upload")
                        .param("categoryId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void uploadAcceptsValidLoginToken() throws Exception {
        userService.ensureSeedUser("upload_test_user", "user123456", "upload-test@example.com", "USER");
        User user = userMapper.findByUsername("upload_test_user");

        mockMvc.perform(multipart("/api/resources")
                        .file(file())
                        .param("title", "security upload test")
                        .param("description", "multipart upload")
                        .param("categoryId", "1")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(content().string(containsString(DOCX_MIME_TYPE)));
    }

    @Test
    void deleteRequiresOwnerOrAdmin() throws Exception {
        User owner = user("delete_owner", "delete-owner@example.com", "USER");
        User other = user("delete_other", "delete-other@example.com", "USER");
        User admin = user("delete_admin", "delete-admin@example.com", "ADMIN");

        long resourceId = uploadFile(owner, "owned-resource.pdf", "application/pdf", "pdf content");

        mockMvc.perform(delete("/api/resources/{id}", resourceId)
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(delete("/api/resources/{id}", resourceId)
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/resources/{id}", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void deletingFolderAlsoHidesChildFiles() throws Exception {
        User owner = user("folder_delete_owner", "folder-delete-owner@example.com", "USER");

        String response = mockMvc.perform(multipart("/api/resources/batch")
                        .file(new MockMultipartFile("files", "lesson.mp4", "video/mp4", "video".getBytes()))
                        .file(new MockMultipartFile("files", "slides.pdf", "application/pdf", "pdf".getBytes()))
                        .param("title", "Folder Delete Course")
                        .param("description", "course package")
                        .param("categoryId", "1")
                        .param("relativePaths", "Chapter 1/lesson.mp4")
                        .param("relativePaths", "Chapter 1/slides.pdf")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long folderId = OBJECT_MAPPER.readTree(response).path("data").path("id").asLong();
        String childrenResponse = mockMvc.perform(get("/api/resources/{id}/children", folderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long childId = OBJECT_MAPPER.readTree(childrenResponse).path("data").get(0).path("id").asLong();

        mockMvc.perform(delete("/api/resources/{id}", folderId)
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/resources/{id}", childId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    private MockMultipartFile file() {
        return new MockMultipartFile(
                "file",
                "security-test.docx",
                DOCX_MIME_TYPE,
                "docx content".getBytes()
        );
    }

    private User user(String username, String email, String role) {
        userService.ensureSeedUser(username, "user123456", email, role);
        return userMapper.findByUsername(username);
    }

    private long uploadFile(User user, String filename, String contentType, String content) throws Exception {
        String response = mockMvc.perform(multipart("/api/resources")
                        .file(new MockMultipartFile("file", filename, contentType, content.getBytes()))
                        .param("title", filename)
                        .param("description", "delete test")
                        .param("categoryId", "1")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = OBJECT_MAPPER.readTree(response).path("data");
        return data.path("id").asLong();
    }
}

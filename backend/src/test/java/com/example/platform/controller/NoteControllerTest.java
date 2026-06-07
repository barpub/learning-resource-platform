package com.example.platform.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.platform.entity.User;
import com.example.platform.mapper.UserMapper;
import com.example.platform.security.JwtUtil;
import com.example.platform.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
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
class NoteControllerTest {
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
    void notesRequireLogin() throws Exception {
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void noteLifecycleKeepsResourceAssociationAndTagsUsable() throws Exception {
        String token = token("note_lifecycle_user", "note-lifecycle@example.com");
        long resourceId = uploadFile(token, "note-resource.pdf", "Note Resource");

        String createResponse = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "  Java 笔记  ",
                                "content", "  封装 继承 多态  ",
                                "category", " 后端 ",
                                "resourceId", resourceId,
                                "isFavorite", 1,
                                "tagNames", List.of(" Java ", "Java", "", "Spring Boot", "Java")
                        )))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Java 笔记"))
                .andExpect(jsonPath("$.data.content").value("封装 继承 多态"))
                .andExpect(jsonPath("$.data.category").value("后端"))
                .andExpect(jsonPath("$.data.resourceId").value((int) resourceId))
                .andExpect(jsonPath("$.data.resourceTitle").value("Note Resource"))
                .andExpect(jsonPath("$.data.isFavorite").value(1))
                .andExpect(jsonPath("$.data.tags.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long noteId = OBJECT_MAPPER.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/api/notes")
                        .param("keyword", "Java 笔记")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value((int) noteId))
                .andExpect(jsonPath("$.data[0].tags.length()").value(2));

        String updateResponse = mockMvc.perform(put("/api/notes/{id}", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Java 笔记更新",
                                "content", "更新后的正文",
                                "tagNames", List.of("Spring Boot", "MyBatis", "Spring Boot")
                        )))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Java 笔记更新"))
                .andExpect(jsonPath("$.data.category").value("后端"))
                .andExpect(jsonPath("$.data.resourceId").value((int) resourceId))
                .andExpect(jsonPath("$.data.isFavorite").value(1))
                .andExpect(jsonPath("$.data.tags.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode updated = OBJECT_MAPPER.readTree(updateResponse).path("data");
        long tagId = updated.path("tags").get(0).path("id").asLong();

        mockMvc.perform(get("/api/notes/by-resource/{resourceId}", resourceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value((int) noteId));

        long secondNoteId = createNote(token, "数据库笔记", "索引和事务", "后端");
        mockMvc.perform(post("/api/notes/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "noteIds", List.of(noteId, secondNoteId),
                                "title", "整合笔记"
                        )))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("整合笔记"))
                .andExpect(jsonPath("$.data.content", containsString("## Java 笔记更新")));

        mockMvc.perform(get("/api/notes/export")
                        .param("tagId", String.valueOf(tagId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", containsString("Java 笔记更新")));

        mockMvc.perform(delete("/api/notes/{id}", noteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/notes/{id}", noteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void createRejectsBlankTitleAndContent() throws Exception {
        String token = token("note_validation_user", "note-validation@example.com");

        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "   ", "content", "内容")))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "标题", "content", "   ")))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void sharedNoteCanBePreviewedPubliclyAndImportedByAnotherUser() throws Exception {
        String ownerToken = token("note_share_owner", "note-share-owner@example.com");
        String importerToken = token("note_share_importer", "note-share-importer@example.com");
        long noteId = createNote(ownerToken, "Shared Algorithms", "Dynamic programming notes", "CS",
                List.of("dp", "interview"), Map.of(
                        "anchorType", "VIDEO",
                        "anchorText", "Video frame near the state transition formula",
                        "anchorImage", "data:image/jpeg;base64,test-snapshot",
                        "anchorSeconds", 12.345
                ));

        String shareResponse = mockMvc.perform(post("/api/notes/{id}/share", noteId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String shareToken = OBJECT_MAPPER.readTree(shareResponse).path("data").path("token").asText();

        mockMvc.perform(get("/api/notes/share/{token}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Shared Algorithms"))
                .andExpect(jsonPath("$.data.content").value("Dynamic programming notes"))
                .andExpect(jsonPath("$.data.category").value("CS"))
                .andExpect(jsonPath("$.data.anchorType").value("VIDEO"))
                .andExpect(jsonPath("$.data.anchorText").value("Video frame near the state transition formula"))
                .andExpect(jsonPath("$.data.anchorImage").value("data:image/jpeg;base64,test-snapshot"))
                .andExpect(jsonPath("$.data.anchorSeconds").value(12.345))
                .andExpect(jsonPath("$.data.tagNames.length()").value(2));

        mockMvc.perform(post("/api/notes/share/{token}/import", shareToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        String importResponse = mockMvc.perform(post("/api/notes/share/{token}/import", shareToken)
                        .header("Authorization", "Bearer " + importerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Shared Algorithms"))
                .andExpect(jsonPath("$.data.content").value("Dynamic programming notes"))
                .andExpect(jsonPath("$.data.category").value("CS"))
                .andExpect(jsonPath("$.data.anchorType").value("VIDEO"))
                .andExpect(jsonPath("$.data.anchorText").value("Video frame near the state transition formula"))
                .andExpect(jsonPath("$.data.anchorSeconds").value(12.345))
                .andExpect(jsonPath("$.data.isFavorite").value(0))
                .andExpect(jsonPath("$.data.tags.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long importedId = OBJECT_MAPPER.readTree(importResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/api/notes/{id}", importedId)
                        .header("Authorization", "Bearer " + importerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Shared Algorithms"));

        mockMvc.perform(get("/api/notes/{id}", importedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(get("/api/notes/share/{token}", "missing-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    private String token(String username, String email) {
        userService.ensureSeedUser(username, "user123456", email, "USER");
        User user = userMapper.findByUsername(username);
        return jwtUtil.generateToken(user);
    }

    private long uploadFile(String token, String fileName, String title) throws Exception {
        String response = mockMvc.perform(multipart("/api/resources")
                        .file(new MockMultipartFile("file", fileName, "application/pdf", "content".getBytes()))
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

    private long createNote(String token, String title, String content, String category) throws Exception {
        return createNote(token, title, content, category, null);
    }

    private long createNote(String token, String title, String content, String category, List<String> tagNames) throws Exception {
        return createNote(token, title, content, category, tagNames, null);
    }

    private long createNote(String token, String title, String content, String category, List<String> tagNames,
                            Map<String, Object> extraFields) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("category", category);
        if (tagNames != null) {
            body.put("tagNames", tagNames);
        }
        if (extraFields != null) {
            body.putAll(extraFields);
        }

        String response = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return OBJECT_MAPPER.readTree(response).path("data").path("id").asLong();
    }

    private String json(Object value) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(value);
    }
}

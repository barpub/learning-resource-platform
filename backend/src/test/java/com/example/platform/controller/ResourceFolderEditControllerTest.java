package com.example.platform.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.platform.entity.User;
import com.example.platform.mapper.UserMapper;
import com.example.platform.security.JwtUtil;
import com.example.platform.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
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
class ResourceFolderEditControllerTest {
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
    void ownerCanAppendFilesToExistingFolder() throws Exception {
        User owner = user("folder_append_owner", "folder-append-owner@example.com", "USER");
        long folderId = uploadFolder(owner);

        mockMvc.perform(multipart("/api/resources/{id}/children", folderId)
                        .file(new MockMultipartFile("files", "exercise.pdf", "application/pdf", "exercise".getBytes()))
                        .param("relativePaths", "Chapter 2/exercise.pdf")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].relativePath").value("Chapter 2/exercise.pdf"));

        mockMvc.perform(get("/api/resources/{id}/children", folderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(get("/api/resources/{id}", folderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileCount").value(3));
    }

    @Test
    void ownerCanAppendUploadOnlyFileTypesToExistingFolder() throws Exception {
        User owner = user("folder_append_zip_owner", "folder-append-zip-owner@example.com", "USER");
        long folderId = uploadFolder(owner);

        String response = mockMvc.perform(multipart("/api/resources/{id}/children", folderId)
                        .file(new MockMultipartFile("files", "archive.zip", "application/zip", "zip".getBytes()))
                        .param("relativePaths", "Archive/archive.zip")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].fileName").value("archive.zip"))
                .andExpect(jsonPath("$.data[0].relativePath").value("Archive/archive.zip"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long childId = OBJECT_MAPPER.readTree(response).path("data").get(0).path("id").asLong();
        mockMvc.perform(get("/api/resources/{id}/preview", childId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(415));
    }

    @Test
    void nonOwnerCannotAppendFilesToExistingFolder() throws Exception {
        User owner = user("folder_append_owner_block", "folder-append-owner-block@example.com", "USER");
        User other = user("folder_append_other_block", "folder-append-other-block@example.com", "USER");
        long folderId = uploadFolder(owner);

        mockMvc.perform(multipart("/api/resources/{id}/children", folderId)
                        .file(new MockMultipartFile("files", "exercise.pdf", "application/pdf", "exercise".getBytes()))
                        .param("relativePaths", "Chapter 2/exercise.pdf")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void ownerCanRenameAndMoveChildFile() throws Exception {
        User owner = user("folder_edit_owner", "folder-edit-owner@example.com", "USER");
        long folderId = uploadFolder(owner);
        long childId = firstChildId(folderId);

        mockMvc.perform(put("/api/resources/{id}/file-path", childId)
                        .param("fileName", "intro-renamed.mp4")
                        .param("relativePath", "Chapter 2/intro-renamed.mp4")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileName").value("intro-renamed.mp4"))
                .andExpect(jsonPath("$.data.relativePath").value("Chapter 2/intro-renamed.mp4"));
    }

    @Test
    void ownerCanMoveLogicalFolderByRewritingChildrenPaths() throws Exception {
        User owner = user("folder_move_owner", "folder-move-owner@example.com", "USER");
        long folderId = uploadFolder(owner);

        mockMvc.perform(put("/api/resources/{id}/folder-path", folderId)
                        .param("sourcePath", "Chapter 1")
                        .param("targetPath", "Archive/Chapter 1")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].relativePath").value("Archive/Chapter 1/intro.mp4"));
    }

    private User user(String username, String email, String role) {
        userService.ensureSeedUser(username, "user123456", email, role);
        return userMapper.findByUsername(username);
    }

    private long uploadFolder(User owner) throws Exception {
        String response = mockMvc.perform(multipart("/api/resources/batch")
                        .file(new MockMultipartFile("files", "intro.mp4", "video/mp4", "video".getBytes()))
                        .file(new MockMultipartFile("files", "slides.pdf", "application/pdf", "pdf".getBytes()))
                        .param("title", "Editable Course")
                        .param("description", "course package")
                        .param("categoryId", "1")
                        .param("relativePaths", "Chapter 1/intro.mp4")
                        .param("relativePaths", "Chapter 1/slides.pdf")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return OBJECT_MAPPER.readTree(response).path("data").path("id").asLong();
    }

    private long firstChildId(long folderId) throws Exception {
        String response = mockMvc.perform(get("/api/resources/{id}/children", folderId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode children = OBJECT_MAPPER.readTree(response).path("data");
        return children.get(0).path("id").asLong();
    }
}

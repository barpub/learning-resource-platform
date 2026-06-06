package com.example.platform.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ResourceBatchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Test
    void batchUploadCreatesFolderResourceWithChildren() throws Exception {
        String token = token();

        mockMvc.perform(multipart("/api/resources/batch")
                        .file(file("files", "lesson-video.mp4", "video/mp4", "video"))
                        .file(file("files", "lesson-slides.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"))
                        .param("title", "Java 第1讲")
                        .param("description", "视频和课件配套资料")
                        .param("categoryId", "1")
                        .param("relativePaths", "第1讲/lesson-video.mp4")
                        .param("relativePaths", "第1讲/lesson-slides.pptx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.resourceType").value("FOLDER"))
                .andExpect(jsonPath("$.data.fileCount").value(2))
                .andExpect(jsonPath("$.data.fileSize").value(9));
    }

    @Test
    void pageOnlyReturnsTopLevelResourcesAndChildrenEndpointReturnsFiles() throws Exception {
        String token = token();

        String response = mockMvc.perform(multipart("/api/resources/batch")
                        .file(file("files", "chapter.mp4", "video/mp4", "video"))
                        .file(file("files", "chapter.pdf", "application/pdf", "pdf"))
                        .param("title", "批量课程资料")
                        .param("description", "课程视频与讲义")
                        .param("categoryId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = response.replaceAll(".*\"id\":(\\d+).*", "$1");

        mockMvc.perform(get("/api/resources")
                        .param("keyword", "chapter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/resources/{id}/children", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].resourceType").value("FILE"))
                .andExpect(jsonPath("$.data[0].parentId").value(Integer.parseInt(id)));
    }

    @Test
    void pageSearchMatchesUploaderUsername() throws Exception {
        String token = authorToken();

        mockMvc.perform(multipart("/api/resources")
                        .file(file("file", "author-note.pdf", "application/pdf", "pdf"))
                        .param("title", "匿名课程资料")
                        .param("description", "没有作者关键字的资料描述")
                        .param("categoryId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/resources")
                        .param("keyword", "author_search_user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].fileName").value("author-note.pdf"))
                .andExpect(jsonPath("$.data.records[0].username").value("author_search_user"));
    }

    private String token() {
        userService.ensureSeedUser("batch_upload_user", "user123456", "batch-upload@example.com", "USER");
        User user = userMapper.findByUsername("batch_upload_user");
        return jwtUtil.generateToken(user);
    }

    private String authorToken() {
        userService.ensureSeedUser("author_search_user", "user123456", "author-search@example.com", "USER");
        User user = userMapper.findByUsername("author_search_user");
        return jwtUtil.generateToken(user);
    }

    private MockMultipartFile file(String partName, String fileName, String type, String content) {
        return new MockMultipartFile(partName, fileName, type, content.getBytes());
    }
}

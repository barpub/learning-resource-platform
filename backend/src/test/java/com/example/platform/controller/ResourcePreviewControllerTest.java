package com.example.platform.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.platform.entity.Resource;
import com.example.platform.mapper.ResourceMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ResourcePreviewControllerTest {
    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResourceMapper resourceMapper;

    @Test
    void previewStreamsPdfInline() throws Exception {
        Path file = Files.createTempFile("preview-test", ".pdf");
        Files.writeString(file, "%PDF-1.4 preview");
        Resource resource = insertResource("preview.pdf", file, "application/pdf");

        mockMvc.perform(get("/api/resources/{id}/preview", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''preview.pdf"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    void previewAllowsHeadRequestsForBrowserProbing() throws Exception {
        Path file = Files.createTempFile("preview-head-test", ".txt");
        Files.writeString(file, "preview");
        Resource resource = insertResource("preview.txt", file, "text/plain");

        mockMvc.perform(head("/api/resources/{id}/preview", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/plain"));
    }

    @Test
    void previewStreamsDocxForClientSideRendering() throws Exception {
        Path file = Files.createTempFile("preview-office-test", ".docx");
        Files.writeString(file, "docx content");
        Resource resource = insertResource(
                "preview.docx",
                file,
                DOCX_MIME_TYPE
        );

        mockMvc.perform(get("/api/resources/{id}/preview", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, DOCX_MIME_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''preview.docx"));
    }

    @Test
    void previewStreamsPptxForClientSideRendering() throws Exception {
        Path file = Files.createTempFile("preview-office-test", ".pptx");
        Files.writeString(file, "pptx content");
        String pptxMimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        Resource resource = insertResource("preview.pptx", file, pptxMimeType);

        mockMvc.perform(get("/api/resources/{id}/preview", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, pptxMimeType))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''preview.pptx"));
    }

    @Test
    void previewRejectsUnsupportedFiles() throws Exception {
        Path file = Files.createTempFile("preview-unsupported-test", ".bin");
        Files.writeString(file, "binary content");
        Resource resource = insertResource("preview.bin", file, "application/octet-stream");

        mockMvc.perform(get("/api/resources/{id}/preview", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(415));
    }

    private Resource insertResource(String fileName, Path file, String fileType) {
        Resource resource = new Resource();
        resource.setTitle("preview test");
        resource.setDescription("preview stream");
        resource.setFileName(fileName);
        resource.setFilePath(file.toString());
        resource.setFileSize(file.toFile().length());
        resource.setFileType(fileType);
        resource.setCategoryId(1L);
        resource.setUserId(1L);
        resource.setStatus(1);
        resourceMapper.insert(resource);
        return resource;
    }
}

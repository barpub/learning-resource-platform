package com.example.platform.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.platform.entity.Resource;
import com.example.platform.mapper.ResourceMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ResourceAgentControllerTest {
    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PPTX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResourceMapper resourceMapper;

    @Test
    void summarizesDocxTextIntoKnowledgePoints() throws Exception {
        Path file = Files.createTempFile("agent-word", ".docx");
        writeZip(file, "word/document.xml",
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                        + "<w:body><w:p><w:r><w:t>Java inheritance encapsulation polymorphism course notes.</w:t></w:r></w:p></w:body>"
                        + "</w:document>");
        Resource resource = insertResource("agent-word.docx", file, DOCX_MIME_TYPE);

        mockMvc.perform(get("/api/agent/resources/{id}/summary", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mediaKind").value("WORD"))
                .andExpect(jsonPath("$.data.contentSource").value("docx-text"))
                .andExpect(jsonPath("$.data.summary", containsString("Java inheritance")))
                .andExpect(jsonPath("$.data.knowledgePoints[0]", containsString("java")));
    }

    @Test
    void summarizesPptxSlidesInSlideOrder() throws Exception {
        Path file = Files.createTempFile("agent-slides", ".pptx");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            writeEntry(zip, "ppt/slides/slide2.xml",
                    "<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
                            + "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                            + "<a:t>Second slide explains vector search ranking.</a:t></p:sld>");
            writeEntry(zip, "ppt/slides/slide1.xml",
                    "<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
                            + "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                            + "<a:t>First slide introduces resource agent workflow.</a:t></p:sld>");
        }
        Resource resource = insertResource("agent-slides.pptx", file, PPTX_MIME_TYPE);

        mockMvc.perform(get("/api/agent/resources/{id}/summary", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaKind").value("PPT"))
                .andExpect(jsonPath("$.data.contentSource").value("pptx-slides"))
                .andExpect(jsonPath("$.data.outline[0]", containsString("First slide")))
                .andExpect(jsonPath("$.data.outline[1]", containsString("Second slide")));
    }

    @Test
    void audioSummaryFallsBackToMetadataWithExplicitLimitation() throws Exception {
        Path file = Files.createTempFile("agent-audio", ".mp3");
        Files.writeString(file, "ID3 fake audio bytes");
        Resource resource = insertResource("agent-audio.mp3", file, "audio/mpeg");

        mockMvc.perform(get("/api/agent/resources/{id}/summary", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaKind").value("AUDIO"))
                .andExpect(jsonPath("$.data.contentSource").value("media-metadata"))
                .andExpect(jsonPath("$.data.limitations", hasItem(containsString("语音识别"))));
    }

    private Resource insertResource(String fileName, Path file, String fileType) {
        Resource resource = new Resource();
        resource.setTitle(fileName);
        resource.setDescription("agent summary test resource");
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

    private void writeZip(Path file, String entryName, String content) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            writeEntry(zip, entryName, content);
        }
    }

    private void writeEntry(ZipOutputStream zip, String entryName, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

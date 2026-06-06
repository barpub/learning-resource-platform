package com.example.platform.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.entity.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("h2")
class ResourceMapperTest {
    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private ResourceMapper resourceMapper;

    @Test
    void insertsResourceWithDocxMimeType() {
        Resource resource = new Resource();
        resource.setTitle("docx test");
        resource.setDescription("stores standard docx MIME type");
        resource.setFileName("test.docx");
        resource.setFilePath("uploads/test.docx");
        resource.setFileSize(128L);
        resource.setFileType(DOCX_MIME_TYPE);
        resource.setCategoryId(1L);
        resource.setUserId(1L);
        resource.setStatus(1);

        int inserted = resourceMapper.insert(resource);

        assertThat(inserted).isEqualTo(1);
        assertThat(resource.getId()).isNotNull();
        assertThat(resourceMapper.findById(resource.getId()).getFileType()).isEqualTo(DOCX_MIME_TYPE);
    }
}

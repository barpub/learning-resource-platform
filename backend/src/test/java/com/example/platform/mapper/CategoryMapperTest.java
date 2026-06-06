package com.example.platform.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.entity.Category;
import com.example.platform.entity.Resource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("h2")
class CategoryMapperTest {
    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private ResourceMapper resourceMapper;

    @Test
    void categoryCountsOnlyTopLevelResources() {
        Resource folder = resource("count folder", "count-folder", "FOLDER", null);
        folder.setFileType("inode/directory");
        folder.setFilePath("");
        folder.setFileCount(1);
        resourceMapper.insert(folder);

        Resource child = resource("count child", "count-child.pdf", "FILE", folder.getId());
        child.setFileType("application/pdf");
        child.setFilePath("uploads/count-child.pdf");
        child.setRelativePath("count-child.pdf");
        resourceMapper.insert(child);

        List<Category> categories = categoryMapper.findAllWithCount();

        Category category = categories.stream()
                .filter(item -> Long.valueOf(1L).equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(category.getResourceCount()).isEqualTo(1);
    }

    private Resource resource(String title, String fileName, String resourceType, Long parentId) {
        Resource resource = new Resource();
        resource.setTitle(title);
        resource.setDescription(title);
        resource.setFileName(fileName);
        resource.setFileSize(1L);
        resource.setResourceType(resourceType);
        resource.setParentId(parentId);
        resource.setCategoryId(1L);
        resource.setUserId(1L);
        resource.setStatus(1);
        return resource;
    }
}

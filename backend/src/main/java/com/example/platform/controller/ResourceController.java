package com.example.platform.controller;

import com.example.platform.common.BusinessException;
import com.example.platform.common.PageResult;
import com.example.platform.common.Result;
import com.example.platform.dto.ResourceDTO;
import com.example.platform.entity.Resource;
import com.example.platform.security.CurrentUser;
import com.example.platform.service.ResourceService;
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    public Result<PageResult<Resource>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(defaultValue = "createTime") String sort,
                                             @RequestParam(defaultValue = "desc") String order) {
        return Result.success(resourceService.page(page, size, keyword, categoryId, sort, order));
    }

    @GetMapping("/search")
    public Result<PageResult<Resource>> search(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Long categoryId) {
        return Result.success(resourceService.page(page, size, keyword, categoryId, "createTime", "desc"));
    }

    @GetMapping("/{id}")
    public Result<Resource> get(@PathVariable Long id) {
        return Result.success(resourceService.get(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Resource> upload(@Valid @ModelAttribute ResourceDTO dto,
                                   @RequestPart("file") MultipartFile file) {
        Long userId = requireUser();
        return Result.success(resourceService.upload(dto, file, userId));
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Resource> uploadBatch(@Valid @ModelAttribute ResourceDTO dto,
                                        @RequestPart("files") List<MultipartFile> files,
                                        @RequestParam(required = false) List<String> relativePaths) {
        Long userId = requireUser();
        return Result.success(resourceService.uploadBatch(dto, files, relativePaths, userId));
    }

    @PostMapping(value = "/{id}/children", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<Resource>> appendChildren(@PathVariable Long id,
                                                 @RequestPart("files") List<MultipartFile> files,
                                                 @RequestParam(required = false) List<String> relativePaths) {
        return Result.success(resourceService.appendChildren(id, files, relativePaths));
    }

    @GetMapping("/{id}/children")
    public Result<List<Resource>> children(@PathVariable Long id) {
        return Result.success(resourceService.children(id));
    }

    @PutMapping("/{id}")
    public Result<Resource> update(@PathVariable Long id, @Valid @ModelAttribute ResourceDTO dto) {
        return Result.success(resourceService.update(id, dto));
    }

    @PutMapping("/{id}/file-path")
    public Result<Resource> updateFilePath(@PathVariable Long id,
                                           @RequestParam String fileName,
                                           @RequestParam String relativePath) {
        return Result.success(resourceService.updateFilePath(id, fileName, relativePath));
    }

    @PutMapping("/{id}/folder-path")
    public Result<List<Resource>> updateFolderPath(@PathVariable Long id,
                                                   @RequestParam String sourcePath,
                                                   @RequestParam String targetPath) {
        return Result.success(resourceService.updateFolderPath(id, sourcePath, targetPath));
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        resourceService.delete(id);
        return Result.success("删除成功");
    }

    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long userId = requireUser();
        resourceService.download(id, userId, request.getRemoteAddr(), response);
    }

    @GetMapping("/{id}/preview")
    public void preview(@PathVariable Long id, HttpServletResponse response) throws IOException {
        resourceService.preview(id, response);
    }

    private Long requireUser() {
        Long userId = CurrentUser.id();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
}

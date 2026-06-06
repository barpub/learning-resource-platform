package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.FtpConnectionDTO;
import com.example.platform.entity.FtpConnection;
import com.example.platform.service.FtpService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for browsing remote FTP servers. Regular users see enabled connections
 * and can browse/preview/download files. Administrators additionally manage connection entries.
 */
@RestController
@RequestMapping("/api/ftp")
public class FtpController {
    private final FtpService ftpService;

    public FtpController(FtpService ftpService) {
        this.ftpService = ftpService;
    }

    @GetMapping("/connections")
    public Result<List<FtpConnection>> listConnections() {
        return Result.success(ftpService.listEnabled());
    }

    @GetMapping("/admin/connections")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<FtpConnection>> adminList() {
        return Result.success(ftpService.listAll());
    }

    @PostMapping("/admin/connections")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<FtpConnection> create(@Valid @RequestBody FtpConnectionDTO dto) {
        return Result.success(ftpService.create(dto));
    }

    @PutMapping("/admin/connections/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<FtpConnection> update(@PathVariable Long id, @Valid @RequestBody FtpConnectionDTO dto) {
        return Result.success(ftpService.update(id, dto));
    }

    @DeleteMapping("/admin/connections/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> delete(@PathVariable Long id) {
        ftpService.delete(id);
        return Result.success("删除成功");
    }

    @PostMapping("/admin/connections/test")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> testNew(@RequestBody FtpConnectionDTO dto) {
        return Result.success(ftpService.test(null, dto));
    }

    @PostMapping("/admin/connections/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> testExisting(@PathVariable Long id,
                                                    @RequestBody(required = false) FtpConnectionDTO overrides) {
        return Result.success(ftpService.test(id, overrides));
    }

    @GetMapping("/connections/{id}/list")
    public Result<Map<String, Object>> listDirectory(@PathVariable Long id,
                                                     @RequestParam(required = false) String path) {
        return Result.success(ftpService.listDirectory(id, path));
    }

    @GetMapping("/connections/{id}/search")
    public Result<Map<String, Object>> searchDirectory(@PathVariable Long id,
                                                       @RequestParam(required = false) String path,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(defaultValue = "80") Integer limit,
                                                       @RequestParam(defaultValue = "8") Integer maxDepth) {
        return Result.success(ftpService.searchDirectory(id, path, keyword, limit, maxDepth));
    }

    @GetMapping("/connections/{id}/preview")
    public void preview(@PathVariable Long id,
                        @RequestParam String path,
                        HttpServletResponse response) throws IOException {
        ftpService.preview(id, path, response);
    }

    @GetMapping("/connections/{id}/download")
    public void download(@PathVariable Long id,
                         @RequestParam String path,
                         HttpServletResponse response) throws IOException {
        ftpService.download(id, path, response);
    }
}

package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.DanmakuConfigDTO;
import com.example.platform.dto.DanmakuDTO;
import com.example.platform.entity.Danmaku;
import com.example.platform.entity.Resource;
import com.example.platform.service.DanmakuService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DanmakuController {
    private final DanmakuService danmakuService;

    public DanmakuController(DanmakuService danmakuService) {
        this.danmakuService = danmakuService;
    }

    @GetMapping("/api/resources/{id}/danmakus")
    public Result<List<Danmaku>> list(@PathVariable Long id) {
        return Result.success(danmakuService.list(id));
    }

    @PostMapping("/api/resources/{id}/danmakus")
    public Result<Danmaku> send(@PathVariable Long id, @Valid @RequestBody DanmakuDTO dto) {
        return Result.success(danmakuService.send(id, dto));
    }

    @PutMapping("/api/resources/{id}/danmaku-config")
    public Result<Resource> updateConfig(@PathVariable Long id, @RequestBody DanmakuConfigDTO config) {
        return Result.success(danmakuService.updateConfig(id, config));
    }

    @DeleteMapping("/api/danmakus/{id}")
    public Result<String> delete(@PathVariable Long id) {
        danmakuService.delete(id);
        return Result.success("删除成功");
    }
}

package com.example.platform.controller;

import com.example.platform.common.BusinessException;
import com.example.platform.common.Result;
import com.example.platform.dto.FavoriteDTO;
import com.example.platform.entity.Favorite;
import com.example.platform.entity.Resource;
import com.example.platform.security.CurrentUser;
import com.example.platform.service.FavoriteService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public Result<List<Resource>> list() {
        return Result.success(favoriteService.list(requireUser()));
    }

    @PostMapping
    public Result<Favorite> create(@Valid @RequestBody FavoriteDTO dto) {
        return Result.success(favoriteService.create(requireUser(), dto));
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        favoriteService.delete(requireUser(), id);
        return Result.success("取消收藏成功");
    }

    @DeleteMapping
    public Result<String> deleteByResource(@RequestParam Long resourceId) {
        favoriteService.deleteByResource(requireUser(), resourceId);
        return Result.success("取消收藏成功");
    }

    private Long requireUser() {
        Long userId = CurrentUser.id();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
}

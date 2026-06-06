package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.CategoryDTO;
import com.example.platform.entity.Category;
import com.example.platform.service.CategoryService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<Category>> list() {
        return Result.success(categoryService.list());
    }

    @GetMapping("/{id}")
    public Result<Category> get(@PathVariable Long id) {
        return Result.success(categoryService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Category> create(@Valid @RequestBody CategoryDTO dto) {
        return Result.success(categoryService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Category> update(@PathVariable Long id, @Valid @RequestBody CategoryDTO dto) {
        return Result.success(categoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success("删除成功");
    }
}

package com.example.platform.service;

import com.example.platform.common.BusinessException;
import com.example.platform.dto.CategoryDTO;
import com.example.platform.entity.Category;
import com.example.platform.mapper.CategoryMapper;
import com.example.platform.utils.RedisUtil;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {
    private final CategoryMapper categoryMapper;
    private final RedisUtil redisUtil;

    public CategoryService(CategoryMapper categoryMapper, RedisUtil redisUtil) {
        this.categoryMapper = categoryMapper;
        this.redisUtil = redisUtil;
    }

    @SuppressWarnings("unchecked")
    public List<Category> list() {
        Object cached = redisUtil.get("category:tree");
        if (cached instanceof List) {
            return (List<Category>) cached;
        }
        List<Category> categories = categoryMapper.findAllWithCount();
        redisUtil.set("category:tree", categories, 21600);
        return categories;
    }

    public Category get(Long id) {
        Category category = categoryMapper.findById(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }
        return category;
    }

    public Category create(CategoryDTO dto) {
        Category category = new Category();
        fill(category, dto);
        categoryMapper.insert(category);
        redisUtil.delete("category:tree");
        return get(category.getId());
    }

    public Category update(Long id, CategoryDTO dto) {
        Category category = get(id);
        fill(category, dto);
        categoryMapper.update(category);
        redisUtil.delete("category:tree");
        return get(id);
    }

    public void delete(Long id) {
        get(id);
        if (categoryMapper.countChildren(id) > 0) {
            throw new BusinessException(400, "存在子分类，不能删除");
        }
        if (categoryMapper.countResources(id) > 0) {
            throw new BusinessException(400, "分类下存在资源，不能删除");
        }
        categoryMapper.delete(id);
        redisUtil.delete("category:tree");
    }

    private void fill(Category category, CategoryDTO dto) {
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        category.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
    }
}

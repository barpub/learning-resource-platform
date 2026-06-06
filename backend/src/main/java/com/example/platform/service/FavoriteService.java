package com.example.platform.service;

import com.example.platform.common.BusinessException;
import com.example.platform.dto.FavoriteDTO;
import com.example.platform.entity.Favorite;
import com.example.platform.entity.Resource;
import com.example.platform.mapper.FavoriteMapper;
import com.example.platform.mapper.ResourceMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FavoriteService {
    private final FavoriteMapper favoriteMapper;
    private final ResourceMapper resourceMapper;

    public FavoriteService(FavoriteMapper favoriteMapper, ResourceMapper resourceMapper) {
        this.favoriteMapper = favoriteMapper;
        this.resourceMapper = resourceMapper;
    }

    public List<Resource> list(Long userId) {
        return resourceMapper.findFavorites(userId);
    }

    public Favorite create(Long userId, FavoriteDTO dto) {
        if (resourceMapper.findById(dto.getResourceId()) == null) {
            throw new BusinessException(404, "资源不存在");
        }
        Favorite existing = favoriteMapper.findByUserAndResource(userId, dto.getResourceId());
        if (existing != null) {
            return existing;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setResourceId(dto.getResourceId());
        favoriteMapper.insert(favorite);
        return favoriteMapper.findById(favorite.getId());
    }

    public void delete(Long userId, Long id) {
        favoriteMapper.deleteByIdAndUser(id, userId);
    }

    public void deleteByResource(Long userId, Long resourceId) {
        favoriteMapper.deleteByUserAndResource(userId, resourceId);
    }
}

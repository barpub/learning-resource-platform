package com.example.platform.mapper;

import com.example.platform.entity.Favorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FavoriteMapper {
    @Select("SELECT * FROM favorite WHERE id=#{id}")
    Favorite findById(Long id);

    @Select("SELECT * FROM favorite WHERE user_id=#{userId} AND resource_id=#{resourceId}")
    Favorite findByUserAndResource(Long userId, Long resourceId);

    @Select("SELECT COUNT(*) FROM favorite WHERE user_id=#{userId}")
    long countByUser(Long userId);

    @Select("SELECT COUNT(*) FROM favorite f JOIN resource r ON f.resource_id = r.id WHERE r.user_id=#{userId} AND r.status=1")
    long countReceivedByUploader(Long userId);

    @Insert("INSERT INTO favorite (user_id, resource_id) VALUES (#{userId}, #{resourceId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Favorite favorite);

    @Delete("DELETE FROM favorite WHERE id=#{id} AND user_id=#{userId}")
    int deleteByIdAndUser(Long id, Long userId);

    @Delete("DELETE FROM favorite WHERE user_id=#{userId} AND resource_id=#{resourceId}")
    int deleteByUserAndResource(Long userId, Long resourceId);
}

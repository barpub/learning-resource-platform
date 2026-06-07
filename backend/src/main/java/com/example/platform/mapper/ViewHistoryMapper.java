package com.example.platform.mapper;

import com.example.platform.entity.ViewHistory;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ViewHistoryMapper {

    @Insert("INSERT INTO view_history (user_id, resource_id, view_duration) VALUES (#{userId}, #{resourceId}, #{viewDuration})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ViewHistory viewHistory);

    @Select("SELECT resource_id, COUNT(*) as view_count FROM view_history " +
            "WHERE user_id = #{userId} GROUP BY resource_id ORDER BY view_count DESC LIMIT #{limit}")
    List<Long> findTopViewedResourceIds(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT r.category_id FROM view_history vh " +
            "INNER JOIN resource r ON vh.resource_id = r.id " +
            "WHERE vh.user_id = #{userId} AND r.category_id IS NOT NULL " +
            "GROUP BY r.category_id ORDER BY MAX(vh.create_time) DESC LIMIT 20")
    List<Long> findRecentCategoryIds(@Param("userId") Long userId);
}

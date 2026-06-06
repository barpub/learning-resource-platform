package com.example.platform.mapper;

import com.example.platform.entity.Category;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CategoryMapper {
    @Select("SELECT c.*, (SELECT COUNT(*) FROM resource r WHERE r.category_id = c.id AND r.status = 1 AND r.parent_id IS NULL) AS resource_count FROM category c ORDER BY c.sort_order ASC, c.id ASC")
    List<Category> findAllWithCount();

    @Select("SELECT * FROM category WHERE id=#{id}")
    Category findById(Long id);

    @Insert("INSERT INTO category (name, description, parent_id, sort_order) VALUES (#{name}, #{description}, #{parentId}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);

    @Update("UPDATE category SET name=#{name}, description=#{description}, parent_id=#{parentId}, sort_order=#{sortOrder}, update_time=NOW() WHERE id=#{id}")
    int update(Category category);

    @Delete("DELETE FROM category WHERE id=#{id}")
    int delete(Long id);

    @Select("SELECT COUNT(*) FROM resource WHERE category_id=#{id}")
    int countResources(Long id);

    @Select("SELECT COUNT(*) FROM category WHERE parent_id=#{id}")
    int countChildren(Long id);
}

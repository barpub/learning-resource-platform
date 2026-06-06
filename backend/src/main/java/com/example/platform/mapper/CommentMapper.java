package com.example.platform.mapper;

import com.example.platform.entity.Comment;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommentMapper {
    @Select("SELECT c.*, u.username FROM comment c LEFT JOIN `user` u ON c.user_id = u.id WHERE c.resource_id=#{resourceId} AND c.status=1 ORDER BY c.create_time DESC")
    List<Comment> findByResourceId(Long resourceId);

    @Select("SELECT c.*, u.username FROM comment c LEFT JOIN `user` u ON c.user_id = u.id WHERE c.status=1 ORDER BY c.create_time DESC")
    List<Comment> findAll();

    @Select("SELECT * FROM comment WHERE id=#{id}")
    Comment findById(Long id);

    @Insert("INSERT INTO comment (resource_id, user_id, content, rating, parent_id, status) VALUES (#{resourceId}, #{userId}, #{content}, #{rating}, #{parentId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Comment comment);

    @Update("UPDATE comment SET status=0, update_time=NOW() WHERE id=#{id}")
    int softDelete(Long id);

    @Delete("DELETE FROM comment WHERE id=#{id}")
    int delete(Long id);
}

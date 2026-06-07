package com.example.platform.mapper;

import com.example.platform.entity.Note;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NoteMapper {

    @Insert("INSERT INTO note (title, content, category, resource_id, anchor_type, anchor_text, anchor_image, anchor_seconds, user_id, is_favorite, status) " +
            "VALUES (#{title}, #{content}, #{category}, #{resourceId}, #{anchorType}, #{anchorText}, #{anchorImage}, #{anchorSeconds}, #{userId}, #{isFavorite}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Note note);

    @Update("UPDATE note SET title = #{title}, content = #{content}, category = #{category}, " +
            "resource_id = #{resourceId}, anchor_type = #{anchorType}, anchor_text = #{anchorText}, " +
            "anchor_image = #{anchorImage}, anchor_seconds = #{anchorSeconds}, is_favorite = #{isFavorite}, status = #{status} " +
            "WHERE id = #{id} AND user_id = #{userId} AND status = 1")
    int update(Note note);

    @Delete("UPDATE note SET status = 0 WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT n.*, r.title AS resource_title FROM note n " +
            "LEFT JOIN resource r ON n.resource_id = r.id WHERE n.id = #{id} AND n.status = 1")
    Note findById(@Param("id") Long id);

    @Select({
        "<script>",
        "SELECT n.*, r.title AS resource_title FROM note n ",
        "LEFT JOIN resource r ON n.resource_id = r.id ",
        "WHERE n.user_id = #{userId} AND n.status = 1 ",
        "<if test='category != null'>AND n.category = #{category}</if> ",
        "<if test='keyword != null'>AND (n.title LIKE CONCAT('%', #{keyword}, '%') OR n.content LIKE CONCAT('%', #{keyword}, '%'))</if> ",
        "ORDER BY n.create_time DESC",
        "</script>"
    })
    List<Note> findByUserId(@Param("userId") Long userId, @Param("category") String category, @Param("keyword") String keyword);

    @Select("SELECT n.*, r.title AS resource_title FROM note n " +
            "LEFT JOIN resource r ON n.resource_id = r.id " +
            "WHERE n.resource_id = #{resourceId} AND n.user_id = #{userId} AND n.status = 1 " +
            "ORDER BY n.create_time DESC")
    List<Note> findByResourceId(@Param("resourceId") Long resourceId, @Param("userId") Long userId);
}

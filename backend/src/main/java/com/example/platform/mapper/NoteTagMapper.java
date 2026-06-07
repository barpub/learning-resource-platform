package com.example.platform.mapper;

import com.example.platform.entity.NoteTag;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NoteTagMapper {

    @Insert("INSERT INTO note_tag (name, user_id) VALUES (#{name}, #{userId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NoteTag tag);

    @Delete("DELETE FROM note_tag WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM note_tag WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<NoteTag> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM note_tag WHERE id = #{id}")
    NoteTag findById(@Param("id") Long id);

    @Select("SELECT * FROM note_tag WHERE name = #{name} AND user_id = #{userId}")
    NoteTag findByNameAndUserId(@Param("name") String name, @Param("userId") Long userId);

    @Select("SELECT t.* FROM note_tag t " +
            "INNER JOIN note_tag_relation r ON t.id = r.tag_id WHERE r.note_id = #{noteId}")
    List<NoteTag> findByNoteId(@Param("noteId") Long noteId);

    @Insert("INSERT INTO note_tag_relation (note_id, tag_id) VALUES (#{noteId}, #{tagId})")
    int addNoteTag(@Param("noteId") Long noteId, @Param("tagId") Long tagId);

    @Delete("DELETE FROM note_tag_relation WHERE note_id = #{noteId}")
    int deleteNoteTagsByNoteId(@Param("noteId") Long noteId);

    @Delete("DELETE FROM note_tag_relation WHERE note_id = #{noteId} AND tag_id = #{tagId}")
    int deleteNoteTag(@Param("noteId") Long noteId, @Param("tagId") Long tagId);
}

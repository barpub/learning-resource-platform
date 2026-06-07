package com.example.platform.mapper;

import com.example.platform.entity.NoteShare;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NoteShareMapper {
    @Insert("INSERT INTO note_share (token, note_id, owner_id, import_count, status) " +
            "VALUES (#{token}, #{noteId}, #{ownerId}, #{importCount}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NoteShare share);

    @Select("SELECT * FROM note_share WHERE note_id = #{noteId} AND owner_id = #{ownerId} AND status = 1 " +
            "ORDER BY create_time DESC LIMIT 1")
    NoteShare findActiveByNoteAndOwner(@Param("noteId") Long noteId, @Param("ownerId") Long ownerId);

    @Select("SELECT * FROM note_share WHERE token = #{token} AND status = 1")
    NoteShare findActiveByToken(@Param("token") String token);

    @Select("SELECT * FROM note_share WHERE token = #{token}")
    NoteShare findByToken(@Param("token") String token);

    @Update("UPDATE note_share SET import_count = import_count + 1 WHERE id = #{id}")
    int incrementImportCount(@Param("id") Long id);
}

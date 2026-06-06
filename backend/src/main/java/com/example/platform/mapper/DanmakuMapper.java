package com.example.platform.mapper;

import com.example.platform.entity.Danmaku;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DanmakuMapper {

    @Select("SELECT d.*, u.username FROM danmaku d LEFT JOIN `user` u ON u.id = d.user_id "
            + "WHERE d.resource_id = #{resourceId} AND d.status = 1 "
            + "ORDER BY d.time_seconds ASC, d.id ASC")
    List<Danmaku> findByResource(Long resourceId);

    @Select("SELECT d.*, u.username FROM danmaku d LEFT JOIN `user` u ON u.id = d.user_id WHERE d.id = #{id}")
    Danmaku findById(Long id);

    @Insert("INSERT INTO danmaku (resource_id, user_id, content, time_seconds, type, color, status) "
            + "VALUES (#{resourceId}, #{userId}, #{content}, #{timeSeconds}, #{type}, #{color}, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Danmaku danmaku);

    @Update("UPDATE danmaku SET status = 0 WHERE id = #{id}")
    int softDelete(Long id);

    @Delete("DELETE FROM danmaku WHERE resource_id = #{resourceId}")
    int deleteByResource(Long resourceId);
}

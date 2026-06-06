package com.example.platform.mapper;

import com.example.platform.entity.ForumComment;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ForumCommentMapper {
    @Select("SELECT fc.*, u.username, u.nickname, u.avatar FROM forum_comment fc LEFT JOIN `user` u ON fc.user_id = u.id WHERE fc.post_id=#{postId} AND fc.status=1 ORDER BY fc.create_time ASC")
    List<ForumComment> findByPostId(Long postId);

    @Select("SELECT * FROM forum_comment WHERE id=#{id}")
    ForumComment findById(Long id);

    @Insert("INSERT INTO forum_comment (post_id, user_id, content, image_urls, parent_id, status) VALUES (#{postId}, #{userId}, #{content}, #{imageUrls}, #{parentId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ForumComment comment);

    @Update("UPDATE forum_comment SET status=0, update_time=NOW() WHERE id=#{id}")
    int softDelete(Long id);
}

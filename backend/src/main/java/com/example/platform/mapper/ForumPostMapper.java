package com.example.platform.mapper;

import com.example.platform.entity.ForumPost;
import com.example.platform.entity.ForumPostResource;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ForumPostMapper {
    @Select({
            "<script>",
            "SELECT p.*, u.username, u.nickname, u.avatar,",
            "(SELECT COUNT(*) FROM forum_post_resource pr WHERE pr.post_id = p.id) AS resource_count,",
            "(SELECT COUNT(*) FROM forum_comment fc WHERE fc.post_id = p.id AND fc.status = 1) AS comment_count",
            "FROM forum_post p",
            "LEFT JOIN `user` u ON p.user_id = u.id",
            "WHERE p.status = 1",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (p.title LIKE CONCAT('%', #{keyword}, '%') OR p.content LIKE CONCAT('%', #{keyword}, '%') OR u.username LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "ORDER BY p.create_time DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<ForumPost> findPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM forum_post p LEFT JOIN `user` u ON p.user_id = u.id WHERE p.status = 1",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (p.title LIKE CONCAT('%', #{keyword}, '%') OR p.content LIKE CONCAT('%', #{keyword}, '%') OR u.username LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</script>"
    })
    int countPage(@Param("keyword") String keyword);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar, (SELECT COUNT(*) FROM forum_post_resource pr WHERE pr.post_id = p.id) AS resource_count, (SELECT COUNT(*) FROM forum_comment fc WHERE fc.post_id = p.id AND fc.status = 1) AS comment_count FROM forum_post p LEFT JOIN `user` u ON p.user_id = u.id WHERE p.id=#{id} AND p.status=1")
    ForumPost findById(Long id);

    @Insert("INSERT INTO forum_post (user_id, title, content, image_urls, folder_path, status) VALUES (#{userId}, #{title}, #{content}, #{imageUrls}, #{folderPath}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ForumPost post);

    @Update("UPDATE forum_post SET status=0, update_time=NOW() WHERE id=#{id}")
    int softDelete(Long id);

    @Insert("INSERT INTO forum_post_resource (post_id, resource_id, folder_path) VALUES (#{postId}, #{resourceId}, #{folderPath})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertResource(ForumPostResource resource);

    @Select("SELECT pr.*, r.title, r.file_name, r.resource_type, r.file_type, r.file_size, r.relative_path, c.name AS category_name FROM forum_post_resource pr JOIN resource r ON pr.resource_id = r.id LEFT JOIN category c ON r.category_id = c.id WHERE pr.post_id=#{postId} AND r.status=1 ORDER BY pr.folder_path ASC, pr.id ASC")
    List<ForumPostResource> findResources(Long postId);

    @Delete("DELETE FROM forum_post_resource WHERE post_id=#{postId}")
    int deleteResources(Long postId);
}

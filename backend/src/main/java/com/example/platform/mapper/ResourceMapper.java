package com.example.platform.mapper;

import com.example.platform.entity.Resource;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResourceMapper {
    @Select({
            "<script>",
            "SELECT r.*, c.name AS category_name, u.username",
            "FROM resource r",
            "LEFT JOIN category c ON r.category_id = c.id",
            "LEFT JOIN `user` u ON r.user_id = u.id",
            "WHERE r.status = 1 AND r.parent_id IS NULL",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (r.title LIKE CONCAT('%', #{keyword}, '%')",
            "OR r.description LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.username LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.nickname LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test='categoryId != null'>AND r.category_id = #{categoryId}</if>",
            "ORDER BY",
            "<choose>",
            "<when test='sort == \"downloadCount\"'>r.download_count</when>",
            "<when test='sort == \"rating\"'>r.rating</when>",
            "<otherwise>r.create_time</otherwise>",
            "</choose>",
            "<choose><when test='order == \"asc\"'>ASC</when><otherwise>DESC</otherwise></choose>",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<Resource> findPage(@Param("keyword") String keyword, @Param("categoryId") Long categoryId,
                            @Param("sort") String sort, @Param("order") String order,
                            @Param("offset") int offset, @Param("limit") int limit);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM resource r",
            "LEFT JOIN `user` u ON r.user_id = u.id",
            "WHERE r.status = 1 AND r.parent_id IS NULL",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (r.title LIKE CONCAT('%', #{keyword}, '%')",
            "OR r.description LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.username LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.nickname LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test='categoryId != null'>AND r.category_id = #{categoryId}</if>",
            "</script>"
    })
    int countPage(@Param("keyword") String keyword, @Param("categoryId") Long categoryId);

    @Select("SELECT r.*, c.name AS category_name, u.username FROM resource r LEFT JOIN category c ON r.category_id = c.id LEFT JOIN `user` u ON r.user_id = u.id WHERE r.id=#{id}")
    Resource findById(Long id);

    @Select("SELECT r.*, c.name AS category_name, u.username FROM resource r LEFT JOIN category c ON r.category_id = c.id LEFT JOIN `user` u ON r.user_id = u.id WHERE r.parent_id=#{parentId} AND r.status=1 ORDER BY r.sort_order ASC, r.create_time ASC")
    List<Resource> findChildren(Long parentId);

    @Select("SELECT r.*, c.name AS category_name, u.username FROM favorite f JOIN resource r ON f.resource_id = r.id LEFT JOIN category c ON r.category_id = c.id LEFT JOIN `user` u ON r.user_id = u.id WHERE f.user_id=#{userId} ORDER BY f.create_time DESC")
    List<Resource> findFavorites(Long userId);

    @Select("SELECT r.*, c.name AS category_name, u.username FROM resource r LEFT JOIN category c ON r.category_id = c.id LEFT JOIN `user` u ON r.user_id = u.id WHERE r.user_id=#{userId} AND r.status=1 AND r.parent_id IS NULL ORDER BY r.create_time DESC LIMIT #{limit}")
    List<Resource> findTopLevelByUser(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT r.*, c.name AS category_name, u.username FROM resource r LEFT JOIN category c ON r.category_id = c.id LEFT JOIN `user` u ON r.user_id = u.id WHERE r.status=1 ORDER BY r.create_time DESC LIMIT #{limit}")
    List<Resource> findRecommendationPool(@Param("limit") int limit);

    @Select({
            "<script>",
            "SELECT r.*, c.name AS category_name, u.username",
            "FROM resource r",
            "LEFT JOIN category c ON r.category_id = c.id",
            "LEFT JOIN `user` u ON r.user_id = u.id",
            "WHERE r.status = 1",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (r.title LIKE CONCAT('%', #{keyword}, '%')",
            "OR r.description LIKE CONCAT('%', #{keyword}, '%')",
            "OR r.file_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR r.relative_path LIKE CONCAT('%', #{keyword}, '%')",
            "OR c.name LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.username LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.nickname LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "ORDER BY r.create_time DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<Resource> searchGlobal(@Param("keyword") String keyword, @Param("limit") int limit);

    @Insert("INSERT INTO resource (title, description, file_name, file_path, file_size, file_type, resource_type, parent_id, sort_order, relative_path, file_count, category_id, user_id, status) VALUES (#{title}, #{description}, #{fileName}, #{filePath}, #{fileSize}, #{fileType}, COALESCE(#{resourceType}, 'FILE'), #{parentId}, COALESCE(#{sortOrder}, 0), #{relativePath}, COALESCE(#{fileCount}, 0), #{categoryId}, #{userId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Resource resource);

    @Update("UPDATE resource SET title=#{title}, description=#{description}, category_id=#{categoryId}, update_time=NOW() WHERE id=#{id}")
    int update(Resource resource);

    @Update("UPDATE resource SET title=#{fileName}, file_name=#{fileName}, relative_path=#{relativePath}, update_time=NOW() WHERE id=#{id}")
    int updateFileLocation(@Param("id") Long id, @Param("fileName") String fileName, @Param("relativePath") String relativePath);

    @Update("UPDATE resource SET status=0, update_time=NOW() WHERE id=#{id}")
    int softDelete(Long id);

    @Update("UPDATE resource SET status=0, update_time=NOW() WHERE parent_id=#{parentId}")
    int softDeleteChildren(Long parentId);

    @Update("UPDATE resource SET file_count=(SELECT COUNT(*) FROM (SELECT id FROM resource WHERE parent_id=#{parentId} AND status=1) active_children), file_size=(SELECT COALESCE(SUM(file_size), 0) FROM (SELECT file_size FROM resource WHERE parent_id=#{parentId} AND status=1) active_sizes), update_time=NOW() WHERE id=#{parentId}")
    int refreshFolderStats(Long parentId);

    @Delete("DELETE FROM resource WHERE id=#{id}")
    int delete(Long id);

    @Update("UPDATE resource SET view_count = view_count + 1 WHERE id=#{id}")
    int incrementViewCount(Long id);

    @Update("UPDATE resource SET download_count = download_count + 1 WHERE id=#{id}")
    int incrementDownloadCount(Long id);

    @Update("UPDATE resource SET rating=(SELECT IFNULL(AVG(rating), 0) FROM comment WHERE resource_id=#{id} AND rating IS NOT NULL AND status=1), rating_count=(SELECT COUNT(*) FROM comment WHERE resource_id=#{id} AND rating IS NOT NULL AND status=1) WHERE id=#{id}")
    int refreshRating(Long id);

    @Update("UPDATE resource SET danmaku_enabled=#{enabled}, danmaku_permission=#{permission}, update_time=NOW() WHERE id=#{id}")
    int updateDanmakuConfig(@Param("id") Long id, @Param("enabled") Integer enabled, @Param("permission") String permission);
}

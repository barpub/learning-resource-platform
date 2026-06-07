package com.example.platform.mapper;

import com.example.platform.entity.MediaEnhancementJob;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MediaEnhancementJobMapper {
    @Insert("""
            INSERT INTO media_enhancement_job
              (source_resource_id, output_resource_id, user_id, media_type, target_resolution, target_fps,
               video_preset, audio_preset, ai_upscale, frame_interpolation, status, progress, message, output_file_path)
            VALUES
              (#{sourceResourceId}, #{outputResourceId}, #{userId}, #{mediaType}, #{targetResolution}, #{targetFps},
               #{videoPreset}, #{audioPreset}, COALESCE(#{aiUpscale}, 0), COALESCE(#{frameInterpolation}, 0),
               #{status}, COALESCE(#{progress}, 0), #{message}, #{outputFilePath})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MediaEnhancementJob job);

    @Select("SELECT * FROM media_enhancement_job WHERE id=#{id}")
    MediaEnhancementJob findById(Long id);

    @Select({
            "<script>",
            "SELECT * FROM media_enhancement_job",
            "WHERE source_resource_id=#{sourceResourceId}",
            "<if test='admin == false'>AND user_id=#{userId}</if>",
            "ORDER BY create_time DESC",
            "</script>"
    })
    List<MediaEnhancementJob> findBySourceResourceId(@Param("sourceResourceId") Long sourceResourceId,
                                                     @Param("userId") Long userId,
                                                     @Param("admin") boolean admin);

    @Select("""
            SELECT * FROM media_enhancement_job
            WHERE source_resource_id=#{sourceResourceId}
              AND user_id=#{userId}
              AND status IN ('PENDING', 'RUNNING')
            ORDER BY create_time DESC
            LIMIT 1
            """)
    MediaEnhancementJob findActiveBySourceAndUser(@Param("sourceResourceId") Long sourceResourceId,
                                                  @Param("userId") Long userId);

    @Update("""
            UPDATE media_enhancement_job
            SET status=#{status}, progress=#{progress}, message=#{message}, update_time=NOW()
            WHERE id=#{id}
            """)
    int updateState(@Param("id") Long id, @Param("status") String status,
                    @Param("progress") int progress, @Param("message") String message);

    @Update("""
            UPDATE media_enhancement_job
            SET progress=#{progress}, message=#{message}, update_time=NOW()
            WHERE id=#{id} AND status='RUNNING'
            """)
    int updateRunning(@Param("id") Long id, @Param("progress") int progress, @Param("message") String message);

    @Update("""
            UPDATE media_enhancement_job
            SET status='SUCCESS', progress=100, output_resource_id=#{outputResourceId},
                output_file_path=#{outputFilePath}, message=#{message}, update_time=NOW()
            WHERE id=#{id}
            """)
    int markSuccess(@Param("id") Long id, @Param("outputResourceId") Long outputResourceId,
                    @Param("outputFilePath") String outputFilePath, @Param("message") String message);

    @Update("""
            UPDATE media_enhancement_job
            SET status='FAILED', message=#{message}, update_time=NOW()
            WHERE id=#{id}
            """)
    int markFailed(@Param("id") Long id, @Param("message") String message);

    @Update("""
            UPDATE media_enhancement_job
            SET status='CANCELLED', message=#{message}, update_time=NOW()
            WHERE id=#{id} AND status IN ('PENDING', 'RUNNING')
            """)
    int cancel(@Param("id") Long id, @Param("message") String message);
}

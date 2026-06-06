package com.example.platform.mapper;

import com.example.platform.dto.UserDashboardDTO;
import com.example.platform.entity.DownloadRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DownloadRecordMapper {
    @Insert("INSERT INTO download_record (user_id, resource_id, ip_address) VALUES (#{userId}, #{resourceId}, #{ipAddress})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DownloadRecord record);

    @Select("SELECT CAST(download_time AS DATE) AS date, COUNT(*) AS count FROM download_record d JOIN resource r ON d.resource_id = r.id WHERE r.user_id=#{userId} AND d.download_time >= #{startDate} GROUP BY CAST(download_time AS DATE) ORDER BY CAST(download_time AS DATE)")
    List<UserDashboardDTO.TrendPoint> countDailyDownloadsForUploader(@Param("userId") Long userId, @Param("startDate") java.time.LocalDate startDate);
}

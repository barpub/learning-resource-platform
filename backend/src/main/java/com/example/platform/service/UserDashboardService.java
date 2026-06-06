package com.example.platform.service;

import com.example.platform.dto.UserDashboardDTO;
import com.example.platform.entity.Resource;
import com.example.platform.mapper.DownloadRecordMapper;
import com.example.platform.mapper.FavoriteMapper;
import com.example.platform.mapper.ResourceMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserDashboardService {
    private final ResourceMapper resourceMapper;
    private final FavoriteMapper favoriteMapper;
    private final DownloadRecordMapper downloadRecordMapper;

    public UserDashboardService(ResourceMapper resourceMapper, FavoriteMapper favoriteMapper,
                                DownloadRecordMapper downloadRecordMapper) {
        this.resourceMapper = resourceMapper;
        this.favoriteMapper = favoriteMapper;
        this.downloadRecordMapper = downloadRecordMapper;
    }

    public UserDashboardDTO dashboard(Long userId) {
        List<Resource> uploads = resourceMapper.findTopLevelByUser(userId, 50);
        UserDashboardDTO dto = new UserDashboardDTO();
        dto.setUploads(uploads);
        dto.setSummary(summary(userId, uploads));
        dto.setCategoryStats(categoryStats(uploads));
        dto.setDownloadTrend(downloadTrend(userId));
        return dto;
    }

    private UserDashboardDTO.Summary summary(Long userId, List<Resource> uploads) {
        UserDashboardDTO.Summary summary = new UserDashboardDTO.Summary();
        summary.setUploadCount(uploads.size());
        summary.setTotalViews(uploads.stream().mapToLong(resource -> value(resource.getViewCount())).sum());
        summary.setTotalDownloads(uploads.stream().mapToLong(resource -> value(resource.getDownloadCount())).sum());
        summary.setMyFavoriteCount(favoriteMapper.countByUser(userId));
        summary.setReceivedFavoriteCount(favoriteMapper.countReceivedByUploader(userId));
        return summary;
    }

    private List<UserDashboardDTO.CategoryStat> categoryStats(List<Resource> uploads) {
        Map<Long, UserDashboardDTO.CategoryStat> stats = new LinkedHashMap<>();
        for (Resource resource : uploads) {
            Long categoryId = resource.getCategoryId() == null ? 0L : resource.getCategoryId();
            UserDashboardDTO.CategoryStat stat = stats.computeIfAbsent(categoryId, id -> {
                UserDashboardDTO.CategoryStat next = new UserDashboardDTO.CategoryStat();
                next.setCategoryId(id);
                next.setName(resource.getCategoryName() == null ? "未分类" : resource.getCategoryName());
                return next;
            });
            stat.setCount(stat.getCount() + 1);
        }
        return new ArrayList<>(stats.values());
    }

    private List<UserDashboardDTO.TrendPoint> downloadTrend(Long userId) {
        LocalDate startDate = LocalDate.now().minusDays(6);
        List<UserDashboardDTO.TrendPoint> stored =
                downloadRecordMapper.countDailyDownloadsForUploader(userId, startDate);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (UserDashboardDTO.TrendPoint point : stored) {
            counts.put(point.getDate(), point.getCount());
        }

        List<UserDashboardDTO.TrendPoint> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            UserDashboardDTO.TrendPoint point = new UserDashboardDTO.TrendPoint();
            point.setDate(date.toString());
            point.setCount(counts.getOrDefault(date.toString(), 0L));
            result.add(point);
        }
        return result;
    }

    private long value(Integer number) {
        return number == null ? 0 : number;
    }
}

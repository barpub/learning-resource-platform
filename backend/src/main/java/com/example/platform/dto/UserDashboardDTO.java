package com.example.platform.dto;

import com.example.platform.entity.Resource;
import java.util.List;

public class UserDashboardDTO {
    private Summary summary;
    private List<Resource> uploads;
    private List<CategoryStat> categoryStats;
    private List<TrendPoint> downloadTrend;

    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    public List<Resource> getUploads() {
        return uploads;
    }

    public void setUploads(List<Resource> uploads) {
        this.uploads = uploads;
    }

    public List<CategoryStat> getCategoryStats() {
        return categoryStats;
    }

    public void setCategoryStats(List<CategoryStat> categoryStats) {
        this.categoryStats = categoryStats;
    }

    public List<TrendPoint> getDownloadTrend() {
        return downloadTrend;
    }

    public void setDownloadTrend(List<TrendPoint> downloadTrend) {
        this.downloadTrend = downloadTrend;
    }

    public static class Summary {
        private long uploadCount;
        private long totalViews;
        private long totalDownloads;
        private long myFavoriteCount;
        private long receivedFavoriteCount;

        public long getUploadCount() {
            return uploadCount;
        }

        public void setUploadCount(long uploadCount) {
            this.uploadCount = uploadCount;
        }

        public long getTotalViews() {
            return totalViews;
        }

        public void setTotalViews(long totalViews) {
            this.totalViews = totalViews;
        }

        public long getTotalDownloads() {
            return totalDownloads;
        }

        public void setTotalDownloads(long totalDownloads) {
            this.totalDownloads = totalDownloads;
        }

        public long getMyFavoriteCount() {
            return myFavoriteCount;
        }

        public void setMyFavoriteCount(long myFavoriteCount) {
            this.myFavoriteCount = myFavoriteCount;
        }

        public long getReceivedFavoriteCount() {
            return receivedFavoriteCount;
        }

        public void setReceivedFavoriteCount(long receivedFavoriteCount) {
            this.receivedFavoriteCount = receivedFavoriteCount;
        }
    }

    public static class CategoryStat {
        private Long categoryId;
        private String name;
        private long count;

        public Long getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(Long categoryId) {
            this.categoryId = categoryId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }

    public static class TrendPoint {
        private String date;
        private long count;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }
}

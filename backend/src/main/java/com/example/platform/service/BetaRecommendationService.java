package com.example.platform.service;

import com.example.platform.dto.BetaRecommendationItem;
import com.example.platform.dto.BetaRecommendationRequest;
import com.example.platform.dto.BetaRecommendationResponse;
import com.example.platform.entity.FtpConnection;
import com.example.platform.entity.Resource;
import com.example.platform.mapper.FtpConnectionMapper;
import com.example.platform.mapper.ResourceMapper;
import com.example.platform.mapper.ViewHistoryMapper;
import com.example.platform.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class BetaRecommendationService {
    private static final int LOCAL_POOL_LIMIT = 120;
    private static final int REMOTE_CONNECTION_LIMIT = 4;
    private static final int REMOTE_ENTRY_LIMIT = 30;

    private final ResourceMapper resourceMapper;
    private final FtpConnectionMapper ftpConnectionMapper;
    private final FtpService ftpService;
    private final ViewHistoryMapper viewHistoryMapper;

    public BetaRecommendationService(ResourceMapper resourceMapper,
                                     FtpConnectionMapper ftpConnectionMapper,
                                     FtpService ftpService,
                                     ViewHistoryMapper viewHistoryMapper) {
        this.resourceMapper = resourceMapper;
        this.ftpConnectionMapper = ftpConnectionMapper;
        this.ftpService = ftpService;
        this.viewHistoryMapper = viewHistoryMapper;
    }

    public BetaRecommendationResponse recommend(BetaRecommendationRequest request) {
        BetaRecommendationRequest safeRequest = request == null ? new BetaRecommendationRequest() : request;
        int limit = Math.max(1, Math.min(safeRequest.getLimit() == null ? 18 : safeRequest.getLimit(), 40));

        Long userId = CurrentUser.id();
        List<Long> preferredCategoryIds = new ArrayList<>();
        List<Long> viewedResourceIds = new ArrayList<>();

        if (userId != null) {
            preferredCategoryIds = viewHistoryMapper.findRecentCategoryIds(userId);
            viewedResourceIds = viewHistoryMapper.findTopViewedResourceIds(userId, 50);
        }

        List<String> userTags = normalizeTags(safeRequest);
        List<BetaRecommendationItem> pool = new ArrayList<>();
        int remoteSkipped = 0;

        if (!Boolean.FALSE.equals(safeRequest.getIncludeLocal())) {
            List<Resource> resources;
            if (!preferredCategoryIds.isEmpty()) {
                resources = resourceMapper.findByCategoryIds(preferredCategoryIds, LOCAL_POOL_LIMIT);
            } else {
                resources = resourceMapper.findRecommendationPool(LOCAL_POOL_LIMIT);
            }
            for (Resource resource : resources) {
                if (!viewedResourceIds.contains(resource.getId())) {
                    pool.add(fromLocal(resource));
                }
            }
        }

        if (!Boolean.FALSE.equals(safeRequest.getIncludeRemote())) {
            List<FtpConnection> connections = ftpConnectionMapper.findEnabled();
            int connectionCount = Math.min(connections.size(), REMOTE_CONNECTION_LIMIT);
            for (int i = 0; i < connectionCount; i++) {
                FtpConnection connection = connections.get(i);
                try {
                    List<Map<String, Object>> entries = ftpService.listTopEntriesForRecommendation(connection.getId(), REMOTE_ENTRY_LIMIT);
                    for (Map<String, Object> entry : entries) {
                        pool.add(fromRemote(connection, entry));
                    }
                } catch (RuntimeException ex) {
                    remoteSkipped++;
                }
            }
        }

        String keyword = lower(safeRequest.getKeyword());
        List<BetaRecommendationItem> ranked = new ArrayList<>();
        for (BetaRecommendationItem item : pool) {
            if (!keyword.isBlank() && !haystack(item).contains(keyword)) {
                continue;
            }
            score(item, userTags);
            ranked.add(item);
        }

        Collections.shuffle(ranked);
        ranked.sort(Comparator.comparing(BetaRecommendationItem::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        if (ranked.size() > limit) {
            ranked = new ArrayList<>(ranked.subList(0, limit));
        }

        BetaRecommendationResponse response = new BetaRecommendationResponse();
        response.setUserVectorTags(userTags);
        response.setRecords(ranked);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("candidateCount", pool.size());
        summary.put("returnedCount", ranked.size());
        summary.put("localEnabled", !Boolean.FALSE.equals(safeRequest.getIncludeLocal()));
        summary.put("remoteEnabled", !Boolean.FALSE.equals(safeRequest.getIncludeRemote()));
        summary.put("remoteConnectionErrors", remoteSkipped);
        summary.put("betaIsolation", "read-only beta namespace; no production resource writes");
        response.setSummary(summary);
        return response;
    }

    private BetaRecommendationItem fromLocal(Resource resource) {
        BetaRecommendationItem item = new BetaRecommendationItem();
        item.setBetaId("local-" + resource.getId());
        item.setSource("LOCAL");
        item.setTitle(resource.getTitle());
        item.setDescription(resource.getDescription());
        item.setFileName(resource.getFileName());
        item.setFileType(resource.getFileType());
        item.setResourceType(resource.getResourceType());
        item.setFileSize(resource.getFileSize());
        item.setLocalResourceId(resource.getId());
        item.setOpenUrl("/resources/" + resource.getId());
        item.setPreviewUrl("/api/resources/" + resource.getId() + "/preview");
        item.setDownloadUrl("/api/resources/" + resource.getId() + "/download");
        item.setCategoryName(resource.getCategoryName());
        item.setUsername(resource.getUsername());
        item.setViewCount(resource.getViewCount());
        item.setDownloadCount(resource.getDownloadCount());
        item.setRating(resource.getRating());
        item.setObjectTags(objectTags(item, resource.getCategoryName(), resource.getUsername()));
        return item;
    }

    private BetaRecommendationItem fromRemote(FtpConnection connection, Map<String, Object> entry) {
        String name = value(entry.get("name"));
        String path = value(entry.get("path"));
        boolean directory = Boolean.TRUE.equals(entry.get("directory"));
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
        BetaRecommendationItem item = new BetaRecommendationItem();
        item.setBetaId("remote-" + connection.getId() + "-" + path);
        item.setSource("REMOTE");
        item.setTitle(name);
        item.setDescription((directory ? "远程目录" : "远程文件") + " / " + connection.getName());
        item.setFileName(name);
        item.setFileType(value(entry.get("type")));
        item.setResourceType(directory ? "FOLDER" : "FILE");
        item.setFileSize(longValue(entry.get("size")));
        item.setRemoteConnectionId(connection.getId());
        item.setRemoteConnectionName(connection.getName());
        item.setRemotePath(path);
        item.setOpenUrl("/resources?source=ftp");
        if (!directory) {
            item.setPreviewUrl("/api/ftp/connections/" + connection.getId() + "/preview?path=" + encodedPath);
            item.setDownloadUrl("/api/ftp/connections/" + connection.getId() + "/download?path=" + encodedPath);
        }
        item.setCategoryName("远程资源");
        item.setUsername(connection.getName());
        item.setObjectTags(objectTags(item, connection.getName(), connection.getDescription()));
        return item;
    }

    private List<String> normalizeTags(BetaRecommendationRequest request) {
        Set<String> tags = new LinkedHashSet<>();
        addTags(tags, request.getPreferenceTags());
        addTags(tags, request.getObjectTags());
        addTokens(tags, splitWords(request.getKeyword()));
        if (tags.isEmpty()) {
            tags.addAll(Arrays.asList("学习", "文档", "视频", "实践"));
        }
        return new ArrayList<>(tags);
    }

    private void addTags(Set<String> tags, List<String> values) {
        if (values == null) return;
        for (String value : values) {
            for (String token : splitWords(value)) {
                tags.add(token);
            }
        }
    }

    private List<String> objectTags(BetaRecommendationItem item, String... extras) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(item.getSource().equals("LOCAL") ? "本地资源" : "远程资源");
        tags.add("FOLDER".equals(item.getResourceType()) ? "目录" : "文件");
        String ext = extension(item.getFileName());
        if (!ext.isBlank()) {
            tags.add(ext);
            tags.add(typeTag(ext));
        }
        addTokens(tags, splitWords(item.getTitle()));
        addTokens(tags, splitWords(item.getDescription()));
        for (String extra : extras) {
            addTokens(tags, splitWords(extra));
        }
        return new ArrayList<>(tags);
    }

    private void addTokens(Set<String> tags, List<String> values) {
        if (values == null) return;
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                tags.add(value);
            }
        }
    }

    private void score(BetaRecommendationItem item, List<String> userTags) {
        Set<String> user = new LinkedHashSet<>(userTags);
        Set<String> target = new LinkedHashSet<>(item.getObjectTags());
        List<String> reasons = new ArrayList<>();
        int hits = 0;
        for (String tag : user) {
            if (target.contains(tag) || haystack(item).contains(tag)) {
                hits++;
                reasons.add("匹配标签：" + tag);
            }
        }
        double denominator = Math.sqrt(Math.max(user.size(), 1) * Math.max(target.size(), 1));
        double vector = denominator == 0 ? 0 : hits / denominator;
        double quality = qualityScore(item);
        double random = Math.random() * 0.12;
        double score = vector * 0.68 + quality * 0.2 + random;
        if (reasons.isEmpty()) {
            reasons.add("随机探索：补充未标记但可能有价值的资源");
        }
        if (item.getSource().equals("REMOTE")) {
            reasons.add("远程资源纳入 beta 推荐池");
        }
        item.setScore(round(score * 100));
        item.setVectorDistance(round(1 - vector));
        item.setMatchReasons(reasons);
    }

    private double qualityScore(BetaRecommendationItem item) {
        double rating = item.getRating() == null ? 0 : item.getRating().doubleValue() / 5.0;
        double views = Math.log10((item.getViewCount() == null ? 0 : item.getViewCount()) + 1) / 3.0;
        double downloads = Math.log10((item.getDownloadCount() == null ? 0 : item.getDownloadCount()) + 1) / 3.0;
        return Math.min(1, Math.max(rating, 0) * 0.55 + Math.min(views, 1) * 0.25 + Math.min(downloads, 1) * 0.2);
    }

    private List<String> splitWords(String text) {
        String source = lower(text);
        if (source.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = source.split("[\\s,，;；/\\\\|._\\-:：()（）\\[\\]{}]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String token = part.trim();
            if (!token.isBlank()) {
                result.add(token);
            }
        }
        return result;
    }

    private String typeTag(String ext) {
        switch (ext) {
            case "pdf":
            case "doc":
            case "docx":
            case "ppt":
            case "pptx":
            case "xls":
            case "xlsx":
            case "txt":
            case "md":
                return "文档";
            case "mp4":
            case "mov":
            case "webm":
                return "视频";
            case "mp3":
            case "wav":
            case "ogg":
                return "音频";
            case "zip":
            case "rar":
            case "7z":
                return "压缩包";
            default:
                return "资料";
        }
    }

    private String extension(String name) {
        String value = lower(name);
        int index = value.lastIndexOf('.');
        return index < 0 ? "" : value.substring(index + 1);
    }

    private String haystack(BetaRecommendationItem item) {
        return lower(String.join(" ",
                value(item.getTitle()),
                value(item.getDescription()),
                value(item.getFileName()),
                value(item.getCategoryName()),
                value(item.getRemoteConnectionName()),
                String.join(" ", item.getObjectTags())));
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}

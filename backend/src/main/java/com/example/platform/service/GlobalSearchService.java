package com.example.platform.service;

import com.example.platform.dto.GlobalSearchItem;
import com.example.platform.dto.GlobalSearchResponse;
import com.example.platform.entity.FtpConnection;
import com.example.platform.entity.Resource;
import com.example.platform.mapper.FtpConnectionMapper;
import com.example.platform.mapper.ResourceMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class GlobalSearchService {
    private static final int REMOTE_CONNECTION_LIMIT = 6;
    private static final int REMOTE_DEPTH_LIMIT = 8;

    private final ResourceMapper resourceMapper;
    private final FtpConnectionMapper ftpConnectionMapper;
    private final FtpService ftpService;

    public GlobalSearchService(ResourceMapper resourceMapper,
                               FtpConnectionMapper ftpConnectionMapper,
                               FtpService ftpService) {
        this.resourceMapper = resourceMapper;
        this.ftpConnectionMapper = ftpConnectionMapper;
        this.ftpService = ftpService;
    }

    public GlobalSearchResponse search(String keyword, String source, Integer limit) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        String safeSource = source == null ? "all" : source.trim().toLowerCase(Locale.ROOT);
        int safeLimit = Math.max(1, Math.min(limit == null ? 30 : limit, 80));
        boolean includeLocal = !"remote".equals(safeSource);
        boolean includeRemote = !"local".equals(safeSource);
        List<GlobalSearchItem> records = new ArrayList<>();
        int remoteErrors = 0;

        if (includeLocal) {
            List<Resource> resources = resourceMapper.searchGlobal(safeKeyword, safeLimit);
            for (Resource resource : resources) {
                GlobalSearchItem item = fromLocal(resource);
                score(item, safeKeyword);
                records.add(item);
            }
        }

        if (includeRemote) {
            List<FtpConnection> connections = ftpConnectionMapper.findEnabled();
            int connectionCount = Math.min(connections.size(), REMOTE_CONNECTION_LIMIT);
            int perConnectionLimit = Math.max(5, safeLimit);
            for (int i = 0; i < connectionCount; i++) {
                FtpConnection connection = connections.get(i);
                try {
                    List<Map<String, Object>> entries = ftpService.searchEntriesForGlobalSearch(
                            connection.getId(), safeKeyword, perConnectionLimit, REMOTE_DEPTH_LIMIT);
                    for (Map<String, Object> entry : entries) {
                        GlobalSearchItem item = fromRemote(connection, entry);
                        score(item, safeKeyword);
                        records.add(item);
                    }
                } catch (RuntimeException ex) {
                    remoteErrors++;
                }
            }
        }

        Collections.shuffle(records);
        records.sort(Comparator.comparing(GlobalSearchItem::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        if (records.size() > safeLimit) {
            records = new ArrayList<>(records.subList(0, safeLimit));
        }

        GlobalSearchResponse response = new GlobalSearchResponse();
        response.setKeyword(safeKeyword);
        response.setRecords(records);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("returnedCount", records.size());
        summary.put("source", safeSource);
        summary.put("localEnabled", includeLocal);
        summary.put("remoteEnabled", includeRemote);
        summary.put("remoteConnectionErrors", remoteErrors);
        summary.put("remoteDepthLimit", REMOTE_DEPTH_LIMIT);
        response.setSummary(summary);
        return response;
    }

    private GlobalSearchItem fromLocal(Resource resource) {
        GlobalSearchItem item = new GlobalSearchItem();
        item.setId("local-" + resource.getId());
        item.setSource("LOCAL");
        item.setTitle(resource.getTitle());
        item.setDescription(resource.getDescription());
        item.setFileName(resource.getFileName());
        item.setFileType(resource.getFileType());
        item.setResourceType(resource.getResourceType());
        item.setFileSize(resource.getFileSize());
        item.setLocalResourceId(resource.getId());
        item.setOpenUrl("/resources/" + resource.getId());
        if (!"FOLDER".equals(resource.getResourceType())) {
            item.setPreviewUrl("/api/resources/" + resource.getId() + "/preview");
            item.setDownloadUrl("/api/resources/" + resource.getId() + "/download");
        }
        item.setCategoryName(resource.getCategoryName());
        item.setTags(resource.getTags());
        item.setOwnerName(resource.getUsername());
        item.setViewCount(resource.getViewCount());
        item.setDownloadCount(resource.getDownloadCount());
        item.setRating(resource.getRating());
        return item;
    }

    private GlobalSearchItem fromRemote(FtpConnection connection, Map<String, Object> entry) {
        String name = value(entry.get("name"));
        String path = value(entry.get("path"));
        boolean directory = Boolean.TRUE.equals(entry.get("directory"));
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
        GlobalSearchItem item = new GlobalSearchItem();
        item.setId("remote-" + connection.getId() + "-" + path);
        item.setSource("REMOTE");
        item.setTitle(name);
        item.setDescription((directory ? "远程目录" : "远程文件") + " / " + connection.getName() + " / " + path);
        item.setFileName(name);
        item.setFileType(value(entry.get("type")));
        item.setResourceType(directory ? "FOLDER" : "FILE");
        item.setFileSize(longValue(entry.get("size")));
        item.setRemoteConnectionId(connection.getId());
        item.setRemoteConnectionName(connection.getName());
        item.setRemotePath(path);
        item.setOpenUrl("/resources?source=ftp&connectionId=" + connection.getId() + "&path=" + encodedPath);
        if (!directory) {
            item.setPreviewUrl("/api/ftp/connections/" + connection.getId() + "/preview?path=" + encodedPath);
            item.setDownloadUrl("/api/ftp/connections/" + connection.getId() + "/download?path=" + encodedPath);
        }
        item.setCategoryName("远程资源");
        item.setOwnerName(connection.getName());
        return item;
    }

    private void score(GlobalSearchItem item, String keyword) {
        SearchTerms searchTerms = new SearchTerms(keyword);
        double score = 0;
        List<String> highlights = new ArrayList<>();
        if (searchTerms.blank) {
            score = 30;
            highlights.add("默认展示最近和可用资源");
        } else {
            score += fieldScore(item.getTitle(), searchTerms, 42, "标题匹配", highlights);
            score += fieldScore(item.getFileName(), searchTerms, 26, "文件名匹配", highlights);
            score += fieldScore(item.getDescription(), searchTerms, 16, "描述匹配", highlights);
            score += fieldScore(item.getTags(), searchTerms, 18, "标签匹配", highlights);
            score += fieldScore(item.getCategoryName(), searchTerms, 6, "分类匹配", highlights);
            score += fieldScore(item.getOwnerName(), searchTerms, 8, "来源/作者匹配", highlights);
            score += fieldScore(item.getRemotePath(), searchTerms, 20, "远程路径匹配", highlights);
        }
        if ("LOCAL".equals(item.getSource())) {
            score += qualityScore(item);
        } else {
            score += 4;
        }
        if (highlights.isEmpty()) {
            highlights.add("相关资源");
        }
        item.setScore(round(score));
        item.setHighlights(highlights);
    }

    private double fieldScore(String text, SearchTerms searchTerms, double weight, String reason, List<String> highlights) {
        String source = normalizeSearchText(text);
        if (source.isBlank() || searchTerms.blank) {
            return 0;
        }
        if (!searchTerms.normalizedKeyword.isBlank() && source.equals(searchTerms.normalizedKeyword)) {
            highlights.add(reason + "：完全命中");
            return weight + 10;
        }
        if (!searchTerms.normalizedKeyword.isBlank() && source.contains(searchTerms.normalizedKeyword)) {
            highlights.add(reason);
            return weight;
        }
        int matched = searchTerms.matchedCount(source);
        if (matched > 0) {
            highlights.add(reason);
            double ratio = Math.min(1.0, matched / (double) Math.max(2, searchTerms.terms.size()));
            return weight * (0.35 + ratio * 0.55);
        }
        return 0;
    }

    private static String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        String lower = value.toLowerCase(Locale.ROOT);
        lower.codePoints().forEach(codePoint -> {
            if (Character.isLetterOrDigit(codePoint)) {
                builder.appendCodePoint(codePoint);
            }
        });
        return builder.toString();
    }

    private static class SearchTerms {
        private static final Pattern LATIN_TERM = Pattern.compile("[A-Za-z][A-Za-z0-9+#.]*");
        private static final Pattern CJK_TERM = Pattern.compile("[\\u4e00-\\u9fff]{2,}");
        private static final Pattern LECTURE_TERM = Pattern.compile("第\\s*0*(\\d{1,3})\\s*讲");

        private final String normalizedKeyword;
        private final List<String> terms;
        private final boolean blank;

        private SearchTerms(String keyword) {
            String value = keyword == null ? "" : keyword.trim();
            this.normalizedKeyword = normalizeSearchText(value);
            this.terms = buildTerms(value);
            this.blank = normalizedKeyword.isBlank() && terms.isEmpty();
        }

        private int matchedCount(String source) {
            int count = 0;
            for (String term : terms) {
                if (source.contains(term)) {
                    count++;
                }
            }
            return count;
        }

        private static List<String> buildTerms(String keyword) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            String source = keyword == null ? "" : keyword;
            String lower = source.toLowerCase(Locale.ROOT);

            Matcher lectureMatcher = LECTURE_TERM.matcher(lower);
            while (lectureMatcher.find()) {
                int lectureNo = Integer.parseInt(lectureMatcher.group(1));
                result.add("第" + lectureNo + "讲");
                if (lectureNo < 10) {
                    result.add("第0" + lectureNo + "讲");
                }
            }

            Matcher latinMatcher = LATIN_TERM.matcher(source);
            while (latinMatcher.find()) {
                String term = normalizeSearchText(latinMatcher.group());
                if (term.length() >= 2) {
                    result.add(term);
                }
            }

            Matcher cjkMatcher = CJK_TERM.matcher(source);
            while (cjkMatcher.find()) {
                String term = normalizeSearchText(cjkMatcher.group());
                if (term.length() >= 2) {
                    result.add(term);
                }
            }

            return new ArrayList<>(result);
        }
    }

    private double qualityScore(GlobalSearchItem item) {
        double rating = item.getRating() == null ? 0 : item.getRating().doubleValue() / 5.0;
        double views = Math.log10((item.getViewCount() == null ? 0 : item.getViewCount()) + 1) / 3.0;
        double downloads = Math.log10((item.getDownloadCount() == null ? 0 : item.getDownloadCount()) + 1) / 3.0;
        return Math.min(18, rating * 9 + Math.min(views, 1) * 5 + Math.min(downloads, 1) * 4);
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

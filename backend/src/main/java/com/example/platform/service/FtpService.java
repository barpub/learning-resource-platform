package com.example.platform.service;

import com.example.platform.common.BusinessException;
import com.example.platform.dto.FtpConnectionDTO;
import com.example.platform.entity.FtpConnection;
import com.example.platform.mapper.FtpConnectionMapper;
import com.example.platform.utils.CryptoUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.stereotype.Service;

/**
 * High level service that exposes FTP connections stored in the database as browsable resources.
 *
 * Each request opens a short-lived {@link FTPClient}, performs the required operation and closes
 * the connection. This keeps the implementation simple and avoids complex connection pooling,
 * which is adequate for the demo-sized workload this platform targets.
 */
@Service
public class FtpService {
    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int DATA_TIMEOUT = 30_000;
    private static final int DEFAULT_SEARCH_DEPTH = 8;
    private static final int DEFAULT_SEARCH_TIMEOUT_MS = 45_000;
    private static final int DEFAULT_SEARCH_DIRECTORY_LIMIT = 2_500;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FtpConnectionMapper connectionMapper;
    private final CryptoUtil cryptoUtil;

    public FtpService(FtpConnectionMapper connectionMapper, CryptoUtil cryptoUtil) {
        this.connectionMapper = connectionMapper;
        this.cryptoUtil = cryptoUtil;
    }

    public List<FtpConnection> listAll() {
        List<FtpConnection> records = connectionMapper.findAll();
        records.forEach(item -> item.setPasswordCipher(item.getPasswordCipher() == null ? null : "******"));
        return records;
    }

    public List<FtpConnection> listEnabled() {
        List<FtpConnection> records = connectionMapper.findEnabled();
        records.forEach(item -> item.setPasswordCipher(null));
        return records;
    }

    public FtpConnection create(FtpConnectionDTO dto) {
        FtpConnection entity = new FtpConnection();
        applyDto(entity, dto, null);
        connectionMapper.insert(entity);
        FtpConnection saved = connectionMapper.findById(entity.getId());
        saved.setPasswordCipher(saved.getPasswordCipher() == null ? null : "******");
        return saved;
    }

    public FtpConnection update(Long id, FtpConnectionDTO dto) {
        FtpConnection existing = connectionMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(404, "FTP 连接不存在");
        }
        applyDto(existing, dto, existing.getPasswordCipher());
        connectionMapper.update(existing);
        FtpConnection saved = connectionMapper.findById(id);
        saved.setPasswordCipher(saved.getPasswordCipher() == null ? null : "******");
        return saved;
    }

    public void delete(Long id) {
        if (connectionMapper.findById(id) == null) {
            throw new BusinessException(404, "FTP 连接不存在");
        }
        connectionMapper.delete(id);
    }

    /**
     * Tests the connection using the stored credentials (or the ones provided in the DTO when updating).
     */
    public Map<String, Object> test(Long id, FtpConnectionDTO overrides) {
        FtpConnection connection = prepareForTest(id, overrides);
        long start = System.currentTimeMillis();
        FTPClient client = null;
        try {
            client = openClient(connection);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("success", true);
            info.put("elapsedMs", System.currentTimeMillis() - start);
            info.put("systemType", safeSystemType(client));
            info.put("workingDir", safeWorkingDir(client));
            return info;
        } catch (Exception ex) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("success", false);
            info.put("elapsedMs", System.currentTimeMillis() - start);
            info.put("message", ex.getMessage());
            return info;
        } finally {
            quietlyClose(client);
        }
    }

    public Map<String, Object> listDirectory(Long id, String path) {
        FtpConnection connection = requireEnabled(id);
        String normalized = normalizeRemotePath(path, connection.getHomePath());
        FTPClient client = null;
        try {
            client = openClient(connection);
            List<Map<String, Object>> entries = listDirectoryEntries(client, normalized);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connectionId", connection.getId());
            result.put("connectionName", connection.getName());
            result.put("path", normalized);
            result.put("parent", parentRemotePath(normalized));
            result.put("entries", entries);
            result.put("breadcrumbs", buildBreadcrumbs(normalized));
            return result;
        } catch (IOException ex) {
            throw new BusinessException(500, "读取远程目录失败：" + ex.getMessage());
        } finally {
            quietlyClose(client);
        }
    }

    public void preview(Long id, String path, HttpServletResponse response) throws IOException {
        FtpConnection connection = requireEnabled(id);
        String normalized = requireFilePath(path, connection.getHomePath());
        FTPClient client = null;
        try {
            client = openClient(connection);
            FTPFile[] files = client.listFiles(normalized);
            if (files == null || files.length == 0) {
                throw new BusinessException(404, "远程文件不存在");
            }
            FTPFile file = files[0];
            if (file.isDirectory()) {
                throw new BusinessException(400, "目标是目录，不能预览");
            }
            String contentType = guessContentType(baseName(normalized));
            String fileName = baseName(normalized);
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "inline; filename*=UTF-8''" + encoded);
            response.setHeader("Accept-Ranges", "none");
            if (file.getSize() > 0) {
                response.setContentLengthLong(file.getSize());
            }
            streamFile(client, normalized, response.getOutputStream());
        } catch (IOException ex) {
            throw new BusinessException(500, "预览远程文件失败：" + ex.getMessage());
        } finally {
            quietlyClose(client);
        }
    }

    public void download(Long id, String path, HttpServletResponse response) throws IOException {
        FtpConnection connection = requireEnabled(id);
        String normalized = requireFilePath(path, connection.getHomePath());
        FTPClient client = null;
        try {
            client = openClient(connection);
            FTPFile[] files = client.listFiles(normalized);
            if (files == null || files.length == 0) {
                throw new BusinessException(404, "远程文件不存在");
            }
            FTPFile file = files[0];
            if (file.isDirectory()) {
                throw new BusinessException(400, "目标是目录，不能下载");
            }
            String fileName = baseName(normalized);
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            if (file.getSize() > 0) {
                response.setContentLengthLong(file.getSize());
            }
            streamFile(client, normalized, response.getOutputStream());
        } catch (IOException ex) {
            throw new BusinessException(500, "下载远程文件失败：" + ex.getMessage());
        } finally {
            quietlyClose(client);
        }
    }

    public List<Map<String, Object>> listTopEntriesForRecommendation(Long id, int limit) {
        FtpConnection connection = requireEnabled(id);
        String normalized = normalizeRemotePath(connection.getHomePath(), "/");
        FTPClient client = null;
        try {
            client = openClient(connection);
            List<Map<String, Object>> entries = listDirectoryEntries(client, normalized);
            int safeLimit = Math.max(1, Math.min(limit, 60));
            return entries.size() <= safeLimit ? entries : new ArrayList<>(entries.subList(0, safeLimit));
        } catch (IOException ex) {
            throw new BusinessException(500, "读取远程推荐资源失败：" + ex.getMessage());
        } finally {
            quietlyClose(client);
        }
    }

    public List<Map<String, Object>> searchEntriesForGlobalSearch(Long id, String keyword, int limit, int maxDepth) {
        FtpConnection connection = requireEnabled(id);
        return searchEntries(connection, connection.getHomePath(), keyword, limit, maxDepth).entries;
    }

    public Map<String, Object> searchDirectory(Long id, String path, String keyword, Integer limit, Integer maxDepth) {
        FtpConnection connection = requireEnabled(id);
        String normalized = normalizeRemotePath(path, connection.getHomePath());
        int safeLimit = Math.max(1, Math.min(limit == null ? 80 : limit, 120));
        int safeDepth = Math.max(0, Math.min(maxDepth == null ? DEFAULT_SEARCH_DEPTH : maxDepth, DEFAULT_SEARCH_DEPTH));
        SearchResult searchResult = searchEntries(connection, normalized, keyword, safeLimit, safeDepth);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connectionId", connection.getId());
        result.put("connectionName", connection.getName());
        result.put("path", normalized);
        result.put("parent", parentRemotePath(normalized));
        result.put("keyword", keyword == null ? "" : keyword.trim());
        result.put("limit", safeLimit);
        result.put("maxDepth", safeDepth);
        result.put("entries", searchResult.entries);
        result.put("partial", searchResult.partial);
        result.put("visitedDirectories", searchResult.visitedDirectories);
        result.put("breadcrumbs", buildBreadcrumbs(normalized));
        return result;
    }

    private SearchResult searchEntries(FtpConnection connection, String path, String keyword, int limit, int maxDepth) {
        String normalized = normalizeRemotePath(path, connection.getHomePath());
        SearchMatcher matcher = new SearchMatcher(keyword);
        int safeLimit = Math.max(1, Math.min(limit, 120));
        int safeDepth = Math.max(0, Math.min(maxDepth, DEFAULT_SEARCH_DEPTH));
        FTPClient client = null;
        try {
            client = openClient(connection);
            List<SearchCandidate> candidates = new ArrayList<>();
            SearchState state = new SearchState(
                    System.nanoTime() + DEFAULT_SEARCH_TIMEOUT_MS * 1_000_000L,
                    DEFAULT_SEARCH_DIRECTORY_LIMIT);
            searchDirectories(client, normalized, matcher, safeLimit, safeDepth, candidates, new HashSet<>(), state);
            return new SearchResult(rankedEntries(candidates, safeLimit), state.partial, state.visitedDirectories);
        } catch (IOException ex) {
            throw new BusinessException(500, "搜索远程资源失败：" + ex.getMessage());
        } finally {
            quietlyClose(client);
        }
    }

    private void searchDirectories(FTPClient client,
                                   String startPath,
                                   SearchMatcher matcher,
                                   int limit,
                                   int maxDepth,
                                   List<SearchCandidate> candidates,
                                   Set<String> visited,
                                   SearchState state) throws IOException {
        PriorityQueue<SearchNode> queue = new PriorityQueue<>((a, b) -> {
            if (a.priority != b.priority) {
                return b.priority - a.priority;
            }
            if (a.depth != b.depth) {
                return a.depth - b.depth;
            }
            return a.path.compareToIgnoreCase(b.path);
        });
        queue.add(new SearchNode(normalizeRemotePath(startPath, "/"), 0, Integer.MAX_VALUE));

        while (!queue.isEmpty()) {
            if (!state.canContinue()) {
                state.partial = true;
                return;
            }
            SearchNode node = queue.poll();
            String normalizedPath = normalizeRemotePath(node.path, "/");
            if (!visited.add(normalizedPath)) {
                continue;
            }
            state.visitedDirectories++;
            List<Map<String, Object>> entries;
            try {
                entries = listDirectoryEntries(client, normalizedPath);
            } catch (BusinessException ex) {
                if (node.depth == 0) {
                    throw ex;
                }
                continue;
            }

            List<Map<String, Object>> directories = new ArrayList<>();
            for (Map<String, Object> entry : entries) {
                if (!state.canContinue()) {
                    state.partial = true;
                    return;
                }
                int score = matcher.score(entry);
                if (score > 0) {
                    candidates.add(new SearchCandidate(entry, score));
                    if (matcher.blank && candidates.size() >= limit) {
                        return;
                    }
                }
                if (Boolean.TRUE.equals(entry.get("directory")) && node.depth < maxDepth) {
                    directories.add(entry);
                }
            }
            directories.sort((a, b) -> compareSearchDirectory(a, b, matcher));
            for (Map<String, Object> directory : directories) {
                queue.add(new SearchNode(
                        String.valueOf(directory.getOrDefault("path", "")),
                        node.depth + 1,
                        matcher.directoryPriority(directory)));
            }
        }
        if (!queue.isEmpty() && candidates.size() < limit) {
            state.partial = true;
        }
    }

    private int compareSearchDirectory(Map<String, Object> a, Map<String, Object> b, SearchMatcher matcher) {
        int priorityA = matcher.directoryPriority(a);
        int priorityB = matcher.directoryPriority(b);
        if (priorityA != priorityB) {
            return priorityB - priorityA;
        }
        int name = stringValue(a.get("name")).compareToIgnoreCase(stringValue(b.get("name")));
        if (name != 0) {
            return name;
        }
        return stringValue(b.get("modified")).compareTo(stringValue(a.get("modified")));
    }

    private List<Map<String, Object>> rankedEntries(List<SearchCandidate> candidates, int limit) {
        candidates.sort((a, b) -> {
            if (a.score != b.score) {
                return b.score - a.score;
            }
            boolean directoryA = Boolean.TRUE.equals(a.entry.get("directory"));
            boolean directoryB = Boolean.TRUE.equals(b.entry.get("directory"));
            if (directoryA != directoryB) {
                return directoryA ? 1 : -1;
            }
            int modified = stringValue(b.entry.get("modified")).compareTo(stringValue(a.entry.get("modified")));
            if (modified != 0) {
                return modified;
            }
            return stringValue(a.entry.get("name")).compareToIgnoreCase(stringValue(b.entry.get("name")));
        });
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SearchCandidate candidate : candidates) {
            String path = stringValue(candidate.entry.get("path"));
            if (!seen.add(path)) {
                continue;
            }
            result.add(candidate.entry);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private static class SearchMatcher {
        private static final Pattern LATIN_TERM = Pattern.compile("[A-Za-z][A-Za-z0-9+#.]*");
        private static final Pattern CJK_TERM = Pattern.compile("[\\u4e00-\\u9fff]{2,}");
        private static final Pattern LECTURE_TERM = Pattern.compile("第\\s*0*(\\d{1,3})\\s*讲");
        private static final List<String> TECHNICAL_TERMS = Arrays.asList(
                "docker", "minio", "java", "spring", "boot", "linux", "mysql", "redis",
                "vue", "node", "web", "html", "css", "javascript", "python", "数据库",
                "网络", "操作系统", "软件", "程序", "编程", "算法", "部署", "服务器");
        private static final List<String> TECHNICAL_PATH_HINTS = Arrays.asList(
                "计算机", "信息", "网络", "数据库", "操作系统", "教学资源",
                "课程", "课件", "项目", "实训", "web", "云");

        private final String normalizedKeyword;
        private final List<String> terms;
        private final List<String> strongTerms;
        private final boolean hasTechnicalTerms;
        private final boolean blank;

        private SearchMatcher(String keyword) {
            String value = keyword == null ? "" : keyword.trim();
            this.normalizedKeyword = normalizeSearchText(value);
            this.terms = buildTerms(value);
            this.strongTerms = terms.stream().filter(SearchMatcher::isAsciiTerm).toList();
            this.hasTechnicalTerms = terms.stream().anyMatch(TECHNICAL_TERMS::contains);
            this.blank = normalizedKeyword.isBlank() && terms.isEmpty();
        }

        private boolean matches(Map<String, Object> entry) {
            return score(entry) > 0;
        }

        private int score(Map<String, Object> entry) {
            if (blank) {
                return 1;
            }
            String name = normalizeSearchText(stringValue(entry.get("name")));
            String path = normalizeSearchText(stringValue(entry.get("path")));
            String type = normalizeSearchText(stringValue(entry.get("type")));
            if (!strongTerms.isEmpty()
                    && strongMatchedCount(name) == 0
                    && strongMatchedCount(path) == 0
                    && strongMatchedCount(type) == 0) {
                return 0;
            }
            int score = textScore(name, 100, 30, 10)
                    + textScore(path, 70, 18, 6)
                    + textScore(type, 12, 8, 4);
            if (score > 0 && !Boolean.TRUE.equals(entry.get("directory"))) {
                score += 5;
            }
            return score;
        }

        private int directoryPriority(Map<String, Object> entry) {
            int priority = score(entry);
            String rawPath = stringValue(entry.get("path"));
            if (hasTechnicalTerms && pathDepth(rawPath) <= 5) {
                String text = normalizeSearchText(stringValue(entry.get("name")));
                for (String hint : TECHNICAL_PATH_HINTS) {
                    if (text.contains(hint)) {
                        priority += 24;
                    }
                }
            }
            return priority;
        }

        private int textScore(String source, int fullWeight, int importantWeight, int lectureWeight) {
            if (source.isBlank()) {
                return 0;
            }
            int score = 0;
            if (!normalizedKeyword.isBlank() && source.contains(normalizedKeyword)) {
                score += fullWeight;
            }
            int matched = 0;
            int importantMatched = 0;
            for (String term : terms) {
                if (source.contains(term)) {
                    matched++;
                    if (isLectureTerm(term)) {
                        score += lectureWeight;
                    } else {
                        importantMatched++;
                        score += importantWeight;
                    }
                }
            }
            if (matched > 1) {
                score += matched * 6;
            }
            if (importantMatched > 1) {
                score += importantMatched * 8;
            }
            return score;
        }

        private static List<String> buildTerms(String keyword) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            String lower = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);

            Matcher lectureMatcher = LECTURE_TERM.matcher(lower);
            while (lectureMatcher.find()) {
                int lectureNo = Integer.parseInt(lectureMatcher.group(1));
                result.add("第" + lectureNo + "讲");
                if (lectureNo < 10) {
                    result.add("第0" + lectureNo + "讲");
                }
            }

            Matcher latinMatcher = LATIN_TERM.matcher(keyword == null ? "" : keyword);
            while (latinMatcher.find()) {
                String term = normalizeSearchText(latinMatcher.group());
                if (term.length() >= 2) {
                    result.add(term);
                }
            }

            Matcher cjkMatcher = CJK_TERM.matcher(keyword == null ? "" : keyword);
            while (cjkMatcher.find()) {
                String term = normalizeSearchText(cjkMatcher.group());
                if (term.length() >= 2) {
                    result.add(term);
                }
            }

            return new ArrayList<>(result);
        }

        private static boolean isLectureTerm(String term) {
            return term.startsWith("第") && term.endsWith("讲");
        }

        private static boolean isAsciiTerm(String term) {
            return term.codePoints().anyMatch(codePoint -> codePoint >= 'a' && codePoint <= 'z');
        }

        private int strongMatchedCount(String source) {
            int count = 0;
            for (String term : strongTerms) {
                if (source.contains(term)) {
                    count++;
                }
            }
            return count;
        }

        private static int pathDepth(String path) {
            if (path == null || path.isBlank() || "/".equals(path)) {
                return 0;
            }
            int depth = 0;
            for (String part : path.split("/")) {
                if (!part.isBlank()) {
                    depth++;
                }
            }
            return depth;
        }
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

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static class SearchNode {
        private final String path;
        private final int depth;
        private final int priority;

        private SearchNode(String path, int depth, int priority) {
            this.path = path;
            this.depth = depth;
            this.priority = priority;
        }
    }

    private static class SearchCandidate {
        private final Map<String, Object> entry;
        private final int score;

        private SearchCandidate(Map<String, Object> entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

    private static class SearchState {
        private final long deadlineNanos;
        private final int directoryLimit;
        private int visitedDirectories = 0;
        private boolean partial = false;

        private SearchState(long deadlineNanos, int directoryLimit) {
            this.deadlineNanos = deadlineNanos;
            this.directoryLimit = directoryLimit;
        }

        private boolean canContinue() {
            return visitedDirectories < directoryLimit && System.nanoTime() <= deadlineNanos;
        }
    }

    private static class SearchResult {
        private final List<Map<String, Object>> entries;
        private final boolean partial;
        private final int visitedDirectories;

        private SearchResult(List<Map<String, Object>> entries, boolean partial, int visitedDirectories) {
            this.entries = entries;
            this.partial = partial;
            this.visitedDirectories = visitedDirectories;
        }
    }

    private List<Map<String, Object>> listDirectoryEntries(FTPClient client, String normalized) throws IOException {
        if (!client.changeWorkingDirectory(normalized)) {
            throw new BusinessException(404, "远程目录不存在：" + normalized);
        }
        FTPFile[] files = client.listFiles();
        List<Map<String, Object>> entries = new ArrayList<>();
        if (files == null) {
            return entries;
        }
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (FTPFile file : files) {
            String name = file.getName();
            if (name == null || name.equals(".") || name.equals("..")) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("path", joinRemotePath(normalized, name));
            item.put("directory", file.isDirectory());
            item.put("size", file.getSize());
            if (file.getTimestamp() != null) {
                item.put("modified", TIME_FORMATTER.format(file.getTimestamp().toInstant().atZone(ZoneId.systemDefault())));
            }
            item.put("type", guessContentType(name));
            entries.add(item);
        }
        return entries;
    }

    private void applyDto(FtpConnection entity, FtpConnectionDTO dto, String existingCipher) {
        entity.setName(dto.getName().trim());
        entity.setHost(dto.getHost().trim());
        entity.setPort(dto.getPort() == null ? 21 : dto.getPort());
        String username = dto.getUsername() == null ? "anonymous" : dto.getUsername().trim();
        if (username.isEmpty()) {
            username = "anonymous";
        }
        entity.setUsername(username);
        entity.setPassiveMode(Boolean.FALSE.equals(dto.getPassiveMode()) ? 0 : 1);
        entity.setEncoding(dto.getEncoding() == null || dto.getEncoding().isBlank() ? "UTF-8" : dto.getEncoding().trim());
        String homePath = dto.getHomePath() == null || dto.getHomePath().isBlank() ? "/" : normalizeRemotePath(dto.getHomePath(), "/");
        entity.setHomePath(homePath);
        entity.setDescription(dto.getDescription());
        entity.setStatus(Integer.valueOf(0).equals(dto.getStatus()) ? 0 : 1);

        if (Boolean.TRUE.equals(dto.getClearPassword())) {
            entity.setPasswordCipher(null);
        } else if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            entity.setPasswordCipher(cryptoUtil.encrypt(dto.getPassword()));
        } else {
            entity.setPasswordCipher(existingCipher);
        }
    }

    private FtpConnection prepareForTest(Long id, FtpConnectionDTO overrides) {
        FtpConnection connection;
        if (id != null) {
            connection = connectionMapper.findById(id);
            if (connection == null) {
                throw new BusinessException(404, "FTP 连接不存在");
            }
        } else {
            if (overrides == null) {
                throw new BusinessException(400, "缺少连接参数");
            }
            connection = new FtpConnection();
        }
        if (overrides != null) {
            if (overrides.getHost() != null) connection.setHost(overrides.getHost().trim());
            if (overrides.getPort() != null) connection.setPort(overrides.getPort());
            if (overrides.getUsername() != null) connection.setUsername(overrides.getUsername().trim());
            if (overrides.getPassiveMode() != null) connection.setPassiveMode(overrides.getPassiveMode() ? 1 : 0);
            if (overrides.getEncoding() != null && !overrides.getEncoding().isBlank()) connection.setEncoding(overrides.getEncoding());
            if (overrides.getHomePath() != null && !overrides.getHomePath().isBlank()) connection.setHomePath(overrides.getHomePath());
            if (overrides.getPassword() != null && !overrides.getPassword().isEmpty()) {
                connection.setPasswordCipher(cryptoUtil.encrypt(overrides.getPassword()));
            }
        }
        if (connection.getHost() == null || connection.getHost().isBlank()) {
            throw new BusinessException(400, "主机地址不能为空");
        }
        if (connection.getPort() == null) connection.setPort(21);
        if (connection.getUsername() == null || connection.getUsername().isBlank()) connection.setUsername("anonymous");
        if (connection.getPassiveMode() == null) connection.setPassiveMode(1);
        if (connection.getEncoding() == null || connection.getEncoding().isBlank()) connection.setEncoding("UTF-8");
        if (connection.getHomePath() == null || connection.getHomePath().isBlank()) connection.setHomePath("/");
        return connection;
    }

    private FtpConnection requireEnabled(Long id) {
        FtpConnection connection = connectionMapper.findById(id);
        if (connection == null) {
            throw new BusinessException(404, "FTP 连接不存在");
        }
        if (!Integer.valueOf(1).equals(connection.getStatus())) {
            throw new BusinessException(403, "该 FTP 连接已禁用");
        }
        return connection;
    }

    private FTPClient openClient(FtpConnection connection) throws IOException {
        FTPClient client = new FTPClient();
        client.setConnectTimeout(CONNECT_TIMEOUT);
        client.setDefaultTimeout(CONNECT_TIMEOUT);
        // Trust the user-configured control encoding. Chinese Windows FTP typically uses GBK
        // while Linux servers use UTF-8. Auto-detection is disabled because many servers
        // advertise UTF-8 but actually respond with GBK bytes.
        client.setAutodetectUTF8(false);
        client.setControlEncoding(connection.getEncoding() == null ? "UTF-8" : connection.getEncoding());
        try {
            client.connect(connection.getHost(), connection.getPort() == null ? 21 : connection.getPort());
        } catch (IOException ex) {
            throw new BusinessException(502, "无法连接到 " + connection.getHost() + ":" + connection.getPort() + " (" + ex.getMessage() + ")");
        }
        int reply = client.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            quietlyClose(client);
            throw new BusinessException(502, "FTP 服务器拒绝连接，响应：" + reply);
        }

        String password = cryptoUtil.decrypt(connection.getPasswordCipher());
        if (password == null) password = "";
        if (!client.login(connection.getUsername(), password)) {
            String message = client.getReplyString();
            quietlyClose(client);
            throw new BusinessException(401, "FTP 登录失败：" + (message == null ? "用户名或密码错误" : message.trim()));
        }

        client.setSoTimeout(DATA_TIMEOUT);
        client.setDataTimeout(DATA_TIMEOUT);
        client.setFileType(FTP.BINARY_FILE_TYPE);
        if (Integer.valueOf(0).equals(connection.getPassiveMode())) {
            client.enterLocalActiveMode();
        } else {
            client.enterLocalPassiveMode();
        }
        return client;
    }

    private void streamFile(FTPClient client, String remotePath, OutputStream out) throws IOException {
        try (InputStream in = client.retrieveFileStream(remotePath)) {
            if (in == null) {
                throw new BusinessException(500, "远程流打开失败：" + client.getReplyString());
            }
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            out.flush();
        }
        if (!client.completePendingCommand()) {
            throw new BusinessException(500, "远程传输未正常结束");
        }
    }

    private void quietlyClose(FTPClient client) {
        if (client == null) return;
        try {
            if (client.isConnected()) {
                client.logout();
            }
        } catch (IOException ignored) {
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (IOException ignored) {
        }
    }

    private String safeSystemType(FTPClient client) {
        try {
            return client.getSystemType();
        } catch (IOException ex) {
            return null;
        }
    }

    private String safeWorkingDir(FTPClient client) {
        try {
            return client.printWorkingDirectory();
        } catch (IOException ex) {
            return null;
        }
    }

    private String normalizeRemotePath(String path, String fallback) {
        String base = path == null || path.isBlank() ? (fallback == null || fallback.isBlank() ? "/" : fallback) : path;
        String normalized = base.replace("\\", "/").trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        List<String> parts = new ArrayList<>();
        for (String part : normalized.split("/")) {
            if (part.isBlank() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
                continue;
            }
            parts.add(part);
        }
        return "/" + String.join("/", parts);
    }

    private String requireFilePath(String path, String fallback) {
        String normalized = normalizeRemotePath(path, fallback);
        if ("/".equals(normalized)) {
            throw new BusinessException(400, "文件路径不能为空");
        }
        return normalized;
    }

    private String parentRemotePath(String path) {
        if (path == null || path.equals("/")) return null;
        int index = path.lastIndexOf('/');
        if (index <= 0) return "/";
        return path.substring(0, index);
    }

    private String joinRemotePath(String base, String name) {
        if (base == null || base.isBlank() || "/".equals(base)) {
            return "/" + name;
        }
        return base + "/" + name;
    }

    private String baseName(String path) {
        if (path == null) return "";
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private List<Map<String, String>> buildBreadcrumbs(String path) {
        List<Map<String, String>> crumbs = new ArrayList<>();
        Map<String, String> root = new LinkedHashMap<>();
        root.put("label", "/");
        root.put("path", "/");
        crumbs.add(root);
        if (path == null || path.isBlank() || "/".equals(path)) {
            return crumbs;
        }
        StringBuilder current = new StringBuilder();
        for (String part : path.split("/")) {
            if (part.isBlank()) continue;
            current.append('/').append(part);
            Map<String, String> crumb = new LinkedHashMap<>();
            crumb.put("label", part);
            crumb.put("path", current.toString());
            crumbs.add(crumb);
        }
        return crumbs;
    }

    /**
     * Guess a response content type from the file extension. Returning a generic
     * {@code application/octet-stream} is fine for unsupported types because the front-end
     * falls back to download-only for them.
     */
    public static String guessContentType(String name) {
        if (name == null) return "application/octet-stream";
        String lower = name.toLowerCase(Locale.ROOT);
        int idx = lower.lastIndexOf('.');
        String ext = idx >= 0 ? lower.substring(idx + 1) : "";
        switch (ext) {
            case "pdf": return "application/pdf";
            case "txt": return "text/plain;charset=UTF-8";
            case "log": return "text/plain;charset=UTF-8";
            case "md": return "text/markdown;charset=UTF-8";
            case "csv": return "text/csv;charset=UTF-8";
            case "json": return "application/json;charset=UTF-8";
            case "xml": return "application/xml;charset=UTF-8";
            case "html": case "htm": return "text/html;charset=UTF-8";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "bmp": return "image/bmp";
            case "mp3": return "audio/mpeg";
            case "wav": return "audio/wav";
            case "ogg": return "audio/ogg";
            case "mp4": return "video/mp4";
            case "webm": return "video/webm";
            case "mov": return "video/quicktime";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "zip": return "application/zip";
            case "rar": return "application/vnd.rar";
            case "7z": return "application/x-7z-compressed";
            default: return "application/octet-stream";
        }
    }

    public static List<String> previewableExtensions() {
        return Collections.unmodifiableList(Arrays.asList(
                "pdf", "txt", "log", "md", "csv", "json", "xml", "html", "htm",
                "png", "jpg", "jpeg", "gif", "webp", "bmp",
                "mp3", "wav", "ogg", "mp4", "webm", "mov",
                "docx", "pptx"));
    }
}

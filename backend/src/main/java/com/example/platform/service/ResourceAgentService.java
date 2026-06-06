package com.example.platform.service;

import com.example.platform.common.BusinessException;
import com.example.platform.dto.GlobalSearchItem;
import com.example.platform.dto.GlobalSearchResponse;
import com.example.platform.dto.ResourceAgentRemoteSummaryRequest;
import com.example.platform.dto.ResourceAgentSearchResponse;
import com.example.platform.dto.ResourceContentSummary;
import com.example.platform.entity.Resource;
import com.example.platform.mapper.ResourceMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;

@Service
public class ResourceAgentService {
    private static final int TEXT_LIMIT = 32_000;
    private static final int SUMMARY_SENTENCE_LIMIT = 3;
    private static final int FOLDER_ANALYSIS_FILE_LIMIT = 30;
    private static final int FOLDER_ANALYSIS_TEXT_PER_FILE_LIMIT = 1_800;
    private static final Pattern XML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern PPT_SLIDE = Pattern.compile("ppt/slides/slide(\\d+)\\.xml");

    private final GlobalSearchService globalSearchService;
    private final ResourceMapper resourceMapper;
    private final FileStorageService fileStorageService;
    private final ResourceAgentLmService resourceAgentLmService;

    public ResourceAgentService(GlobalSearchService globalSearchService,
                                ResourceMapper resourceMapper,
                                FileStorageService fileStorageService,
                                ResourceAgentLmService resourceAgentLmService) {
        this.globalSearchService = globalSearchService;
        this.resourceMapper = resourceMapper;
        this.fileStorageService = fileStorageService;
        this.resourceAgentLmService = resourceAgentLmService;
    }

    public ResourceAgentSearchResponse search(String keyword, String source, Integer limit) {
        GlobalSearchResponse base = globalSearchService.search(keyword, source, limit);
        ResourceAgentSearchResponse response = new ResourceAgentSearchResponse();
        response.setKeyword(base.getKeyword());
        response.setRecords(base.getRecords());
        response.setSummary(base.getSummary());
        response.setAgentSteps(List.of(
                "复用全局搜索索引检索本地资源和远程 FTP 资源",
                "按标题、文件名、描述、分类、来源和质量分进行排序",
                "为可总结资源标记可执行的内容分析入口"
        ));
        response.setSuggestedQueries(suggestedQueries(base.getRecords(), base.getKeyword()));
        response.setAnswer(buildSearchAnswer(base));
        return response;
    }

    public ResourceContentSummary summarizeLocal(Long id) {
        Resource resource = resourceMapper.findById(id);
        if (resource == null || Integer.valueOf(0).equals(resource.getStatus())) {
            throw new BusinessException(404, "资源不存在");
        }

        ResourceContentSummary result = baseSummary(resource);
        if ("FOLDER".equals(resource.getResourceType())) {
            summarizeFolder(resource, result);
            return result;
        }

        String kind = mediaKind(resource.getFileName(), resource.getFileType());
        result.setMediaKind(kind);
        Path path = fileStorageService.resolve(resource.getFilePath());
        ExtractedContent extracted = extractLocalContent(resource, path, kind);
        fillSummary(result, resource, extracted);
        return result;
    }

    public ResourceContentSummary summarizeVirtualFolder(Long id, String path) {
        Resource folder = requireUploadedVirtualFolder(id);
        ResourceContentSummary result = baseSummary(folder);
        summarizeFolderPath(folder, normalizeVirtualPath(path), result);
        return result;
    }

    public ResourceContentSummary summarizeRemote(ResourceAgentRemoteSummaryRequest request) {
        Resource virtual = new Resource();
        virtual.setTitle(firstNonBlank(request.getTitle(), request.getFileName(), request.getRemotePath(), "远程资源"));
        virtual.setDescription(request.getDescription());
        virtual.setFileName(firstNonBlank(request.getFileName(), baseName(request.getRemotePath()), virtual.getTitle()));
        virtual.setFileType(request.getFileType());
        virtual.setResourceType(request.getResourceType());
        virtual.setFileSize(request.getFileSize());

        ResourceContentSummary result = baseSummary(virtual);
        result.setSource("REMOTE");
        result.setMediaKind(mediaKind(virtual.getFileName(), virtual.getFileType()));
        result.setContentSource("remote-metadata");
        result.setConfidence(0.34);
        result.setMetadata(new LinkedHashMap<>());
        result.getMetadata().put("remoteConnectionId", request.getRemoteConnectionId());
        result.getMetadata().put("remoteConnectionName", request.getRemoteConnectionName());
        result.getMetadata().put("remotePath", request.getRemotePath());
        result.setSummary(metadataSummary(virtual, "远程文件暂未下载正文，当前基于文件名、路径、格式和描述生成初步判断。"));
        result.setKnowledgePoints(keywordPoints(metadataText(virtual, request.getRemotePath()), 8));
        result.setOutline(List.of(
                "来源：" + firstNonBlank(request.getRemoteConnectionName(), "远程 FTP"),
                "路径：" + firstNonBlank(request.getRemotePath(), "-"),
                "格式：" + result.getMediaKind()
        ));
        result.setEvidenceSnippets(evidenceFromMetadata(virtual, request.getRemotePath()));
        result.setLimitations(List.of("远程资源未下载到本地，无法直接解析正文或音视频内容。", "可先预览或下载后进入本地资源库，再执行深度总结。"));
        result.setNextActions(nextActions(result.getMediaKind(), false));
        enhanceWithLlm(result, virtual, metadataText(virtual, request.getRemotePath()), false);
        return result;
    }

    private ResourceContentSummary baseSummary(Resource resource) {
        ResourceContentSummary result = new ResourceContentSummary();
        result.setResourceId(resource.getId());
        result.setSource("LOCAL");
        result.setTitle(resource.getTitle());
        result.setFileName(resource.getFileName());
        result.setMediaKind(mediaKind(resource.getFileName(), resource.getFileType()));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fileType", resource.getFileType());
        metadata.put("resourceType", resource.getResourceType());
        metadata.put("fileSize", resource.getFileSize());
        metadata.put("categoryName", resource.getCategoryName());
        metadata.put("ownerName", resource.getUsername());
        metadata.put("relativePath", resource.getRelativePath());
        result.setMetadata(metadata);
        return result;
    }

    private Resource requireUploadedVirtualFolder(Long id) {
        Resource folder = resourceMapper.findById(id);
        if (folder == null || Integer.valueOf(0).equals(folder.getStatus())) {
            throw new BusinessException(404, "资源不存在");
        }
        if (!"FOLDER".equals(folder.getResourceType()) || folder.getParentId() != null) {
            throw new BusinessException(400, "分析 Agent 只支持用户上传的本地虚拟文件夹");
        }
        return folder;
    }

    private void summarizeFolder(Resource folder, ResourceContentSummary result) {
        summarizeFolderPath(folder, "", result);
    }

    private void summarizeFolderPath(Resource folder, String folderPath, ResourceContentSummary result) {
        String targetPath = normalizeVirtualPath(folderPath);
        List<Resource> children = resourceMapper.findChildren(folder.getId());
        List<Resource> files = children.stream()
                .filter(child -> !"FOLDER".equals(child.getResourceType()))
                .filter(child -> isInVirtualFolder(child, targetPath))
                .sorted(Comparator.comparing(child -> normalizeVirtualPath(firstNonBlank(child.getRelativePath(), child.getFileName(), child.getTitle()))))
                .toList();

        if (files.isEmpty()) {
            throw new BusinessException(404, targetPath.isBlank()
                    ? "该上传文件夹下没有可分析的文件"
                    : "当前虚拟文件夹下没有可分析的文件");
        }

        long totalSize = files.stream()
                .map(Resource::getFileSize)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        int directFileCount = directFileCount(files, targetPath);
        int childFolderCount = childFolderCount(files, targetPath);

        StringBuilder text = new StringBuilder();
        text.append("上传文件夹：").append(firstNonBlank(folder.getTitle(), folder.getFileName(), "未命名文件夹")).append('\n');
        text.append("当前虚拟路径：").append(targetPath.isBlank() ? "/" : targetPath).append('\n');
        text.append("扫描文件数：").append(files.size()).append('\n');
        text.append("直属文件数：").append(directFileCount).append('\n');
        text.append("子目录数：").append(childFolderCount).append('\n');
        for (Resource child : files) {
            text.append('\n')
                    .append(firstNonBlank(child.getRelativePath(), child.getFileName(), child.getTitle(), "文件"))
                    .append(" / ")
                    .append(mediaKind(child.getFileName(), child.getFileType()))
                    .append(" / ")
                    .append(firstNonBlank(child.getDescription(), ""));
        }

        int inspectedFiles = 0;
        int extractedFileCount = 0;
        int metadataOnlyFileCount = 0;
        int readFailureCount = 0;
        StringBuilder analysisText = new StringBuilder(text);
        for (Resource file : files) {
            if (inspectedFiles >= FOLDER_ANALYSIS_FILE_LIMIT || analysisText.length() >= TEXT_LIMIT) {
                break;
            }
            inspectedFiles++;
            String kind = mediaKind(file.getFileName(), file.getFileType());
            try {
                ExtractedContent extracted = extractLocalContent(file, fileStorageService.resolve(file.getFilePath()), kind);
                String content = compact(extracted.text());
                boolean hasContent = content.length() > 24 && !extracted.source().contains("metadata");
                if (hasContent) {
                    extractedFileCount++;
                    analysisText.append("\n\n正文摘录：")
                            .append(firstNonBlank(file.getRelativePath(), file.getFileName(), file.getTitle(), "文件"))
                            .append('\n')
                            .append(cut(content, FOLDER_ANALYSIS_TEXT_PER_FILE_LIMIT));
                } else {
                    metadataOnlyFileCount++;
                }
            } catch (RuntimeException ex) {
                readFailureCount++;
            }
        }

        Resource virtualFolder = virtualFolderResource(folder, targetPath, files.size(), totalSize);
        boolean hasExtractedContent = extractedFileCount > 0;
        Map<String, Object> metadata = result.getMetadata() == null ? new LinkedHashMap<>() : result.getMetadata();
        metadata.put("virtualFolderPath", targetPath.isBlank() ? "/" : targetPath);
        metadata.put("fileCount", files.size());
        metadata.put("directFileCount", directFileCount);
        metadata.put("childFolderCount", childFolderCount);
        metadata.put("totalSize", totalSize);
        metadata.put("inspectedFileCount", inspectedFiles);
        metadata.put("extractedFileCount", extractedFileCount);
        metadata.put("metadataOnlyFileCount", metadataOnlyFileCount);
        metadata.put("readFailureCount", readFailureCount);
        result.setMetadata(metadata);
        result.setTitle(virtualFolder.getTitle());
        result.setFileName(virtualFolder.getFileName());
        result.setMediaKind("FOLDER");
        result.setContentSource(hasExtractedContent ? "folder-files+content" : "folder-files");
        result.setConfidence(hasExtractedContent ? 0.72 : 0.54);
        result.setSummary("分析 Agent 已扫描当前虚拟文件夹“" + (targetPath.isBlank() ? "/" : targetPath)
                + "”下的 " + files.size() + " 个文件，其中 " + extractedFileCount
                + " 个文件读取到正文内容，其余文件使用文件名、路径、格式和描述参与分析。");
        String sourceText = compact(analysisText.toString());
        result.setKnowledgePoints(keywordPoints(sourceText, 12));
        result.setOutline(folderOutline(files, targetPath));
        result.setEvidenceSnippets(snippets(sourceText, 6));
        List<String> limitations = new ArrayList<>();
        if (inspectedFiles < files.size()) {
            limitations.add("为控制分析时间，本次只尝试读取前 " + inspectedFiles + " 个文件的正文；所有文件仍已参与目录和路径分析。");
        }
        if (metadataOnlyFileCount > 0) {
            limitations.add(metadataOnlyFileCount + " 个文件暂时只能按元数据分析，未直接解析正文。");
        }
        if (readFailureCount > 0) {
            limitations.add(readFailureCount + " 个文件读取失败，已跳过正文提取。");
        }
        if (limitations.isEmpty()) {
            limitations.add("整夹分析适合先看主题结构；单个文件的精确内容仍建议进入文件预览核对。");
        }
        result.setLimitations(limitations);
        result.setNextActions(List.of("按分析结果挑选重点文件进入预览核对。", "把当前文件夹拆成课程章节、实验材料或复习资料清单。", "继续在资源库中切换虚拟子目录后重新分析。"));
        enhanceWithLlm(result, virtualFolder, sourceText, hasExtractedContent);
    }

    private Resource virtualFolderResource(Resource folder, String targetPath, int fileCount, long totalSize) {
        Resource virtualFolder = new Resource();
        String rootTitle = firstNonBlank(folder.getTitle(), folder.getFileName(), "上传文件夹");
        String label = targetPath.isBlank() ? rootTitle : targetPath.substring(targetPath.lastIndexOf('/') + 1);
        virtualFolder.setId(folder.getId());
        virtualFolder.setTitle(targetPath.isBlank() ? rootTitle : rootTitle + " / " + targetPath);
        virtualFolder.setDescription("用户上传虚拟文件夹：" + (targetPath.isBlank() ? "/" : targetPath));
        virtualFolder.setFileName(label);
        virtualFolder.setFileType("inode/directory");
        virtualFolder.setResourceType("FOLDER");
        virtualFolder.setFileSize(totalSize);
        virtualFolder.setFileCount(fileCount);
        virtualFolder.setCategoryName(folder.getCategoryName());
        virtualFolder.setUsername(folder.getUsername());
        virtualFolder.setRelativePath(targetPath);
        return virtualFolder;
    }

    private List<String> folderOutline(List<Resource> files, String targetPath) {
        List<String> outline = files.stream()
                .limit(12)
                .map(item -> relativePathInside(firstNonBlank(item.getRelativePath(), item.getFileName(), item.getTitle()), targetPath)
                        + " - " + mediaKind(item.getFileName(), item.getFileType()))
                .toList();
        if (files.size() <= 12) {
            return outline;
        }
        List<String> expanded = new ArrayList<>(outline);
        expanded.add("其余 " + (files.size() - 12) + " 个文件已参与统计和关键词分析");
        return expanded;
    }

    private int directFileCount(List<Resource> files, String targetPath) {
        int count = 0;
        for (Resource file : files) {
            String remaining = relativePathInside(firstNonBlank(file.getRelativePath(), file.getFileName(), file.getTitle()), targetPath);
            if (!remaining.isBlank() && !remaining.contains("/")) {
                count++;
            }
        }
        return count;
    }

    private int childFolderCount(List<Resource> files, String targetPath) {
        Set<String> folders = new LinkedHashSet<>();
        for (Resource file : files) {
            String remaining = relativePathInside(firstNonBlank(file.getRelativePath(), file.getFileName(), file.getTitle()), targetPath);
            int slash = remaining.indexOf('/');
            if (slash > 0) {
                folders.add(remaining.substring(0, slash));
            }
        }
        return folders.size();
    }

    private boolean isInVirtualFolder(Resource resource, String targetPath) {
        String relativePath = normalizeVirtualPath(firstNonBlank(resource.getRelativePath(), resource.getFileName(), resource.getTitle()));
        if (relativePath.isBlank()) {
            return false;
        }
        if (targetPath.isBlank()) {
            return true;
        }
        return relativePath.startsWith(targetPath + "/");
    }

    private String relativePathInside(String relativePath, String targetPath) {
        String normalized = normalizeVirtualPath(relativePath);
        String target = normalizeVirtualPath(targetPath);
        if (target.isBlank() || !normalized.startsWith(target + "/")) {
            return normalized;
        }
        return normalized.substring(target.length() + 1);
    }

    private String normalizeVirtualPath(String path) {
        if (path == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String part : path.replace('\\', '/').split("/")) {
            String value = part.trim();
            if (!value.isBlank() && !".".equals(value) && !"..".equals(value)) {
                parts.add(value);
            }
        }
        return String.join("/", parts);
    }

    private ExtractedContent extractLocalContent(Resource resource, Path path, String kind) {
        try {
            if ("WORD".equals(kind) && endsWith(resource.getFileName(), ".docx")) {
                return new ExtractedContent("docx-text", extractDocx(path), 0.88, List.of());
            }
            if ("PPT".equals(kind) && endsWith(resource.getFileName(), ".pptx")) {
                return new ExtractedContent("pptx-slides", extractPptx(path), 0.86, List.of());
            }
            if ("TEXT".equals(kind)) {
                return new ExtractedContent("plain-text", readText(path), 0.82, List.of());
            }
            if ("VIDEO".equals(kind) || "AUDIO".equals(kind)) {
                ExtractedContent sidecar = findSidecarTranscript(resource);
                if (!sidecar.text().isBlank()) {
                    return sidecar;
                }
                return new ExtractedContent("media-metadata", metadataText(resource, null), 0.38,
                        List.of("当前版本未接入语音识别，无法直接听取音频或视频中的人声内容。"));
            }
            return new ExtractedContent("metadata", metadataText(resource, null), 0.45,
                    List.of("该格式暂未接入正文解析器，当前使用元数据进行初步总结。"));
        } catch (IOException ex) {
            return new ExtractedContent("metadata-fallback", metadataText(resource, null), 0.32,
                    List.of("正文解析失败，已回退到资源元数据总结：" + ex.getMessage()));
        }
    }

    private void fillSummary(ResourceContentSummary result, Resource resource, ExtractedContent extracted) {
        String text = compact(extracted.text());
        boolean hasContent = text.length() > 24 && !extracted.source().contains("metadata");
        result.setContentSource(extracted.source());
        result.setConfidence(extracted.confidence());
        result.setSummary(hasContent
                ? textSummary(text, resource)
                : metadataSummary(resource, "当前基于资源标题、描述、文件名和分类生成初步总结。"));
        result.setKnowledgePoints(keywordPoints(hasContent ? text : metadataText(resource, null), 10));
        result.setOutline(hasContent ? outline(text, result.getMediaKind()) : metadataOutline(resource, result.getMediaKind()));
        result.setEvidenceSnippets(snippets(hasContent ? text : metadataText(resource, null), 5));
        result.setLimitations(extracted.limitations());
        result.setNextActions(nextActions(result.getMediaKind(), hasContent));
        enhanceWithLlm(result, resource, hasContent ? text : metadataText(resource, null), hasContent);
    }

    private void enhanceWithLlm(ResourceContentSummary result,
                                Resource resource,
                                String inputText,
                                boolean hasExtractedContent) {
        resourceAgentLmService.summarize(resource, result, inputText, hasExtractedContent)
                .ifPresent(summary -> {
                    result.setSummary(summary.summary());
                    if (!summary.knowledgePoints().isEmpty()) {
                        result.setKnowledgePoints(summary.knowledgePoints());
                    }
                    if (!summary.outline().isEmpty()) {
                        result.setOutline(summary.outline());
                    }
                    if (!summary.nextActions().isEmpty()) {
                        result.setNextActions(summary.nextActions());
                    }
                    result.setContentSource(firstNonBlank(result.getContentSource(), "metadata") + "+llm");
                    if (result.getMetadata() == null) {
                        result.setMetadata(new LinkedHashMap<>());
                    }
                    result.getMetadata().put("llmStatus", "enhanced");
                    result.getMetadata().put("llmModel", resourceAgentLmService.modelName());
                    double base = result.getConfidence() == null ? 0.0 : result.getConfidence();
                    double boost = hasExtractedContent ? 0.08 : 0.03;
                    result.setConfidence(Math.min(0.95, base + boost));
                });
    }

    private ExtractedContent findSidecarTranscript(Resource resource) {
        if (resource.getParentId() == null) {
            return new ExtractedContent("", "", 0.0, List.of());
        }
        String stem = stem(resource.getFileName());
        List<Resource> siblings = resourceMapper.findChildren(resource.getParentId());
        for (Resource sibling : siblings) {
            if (Objects.equals(sibling.getId(), resource.getId())) {
                continue;
            }
            String name = sibling.getFileName();
            if ((endsWith(name, ".srt") || endsWith(name, ".vtt") || endsWith(name, ".txt"))
                    && stem(name).equalsIgnoreCase(stem)) {
                try {
                    Path path = fileStorageService.resolve(sibling.getFilePath());
                    return new ExtractedContent("sidecar-transcript:" + name, readText(path), 0.76, List.of());
                } catch (RuntimeException | IOException ignored) {
                    return new ExtractedContent("", "", 0.0, List.of());
                }
            }
        }
        return new ExtractedContent("", "", 0.0, List.of());
    }

    private String extractDocx(Path path) throws IOException {
        StringBuilder text = new StringBuilder();
        readZipXml(path, name -> name.startsWith("word/") && name.endsWith(".xml"), (name, xml) -> {
            String cleaned = extractXmlText(xml, "w:t");
            if (!cleaned.isBlank()) {
                text.append(cleaned).append('\n');
            }
        });
        return text.toString();
    }

    private String extractPptx(Path path) throws IOException {
        List<SlideText> slides = new ArrayList<>();
        readZipXml(path, name -> PPT_SLIDE.matcher(name).matches(), (name, xml) -> {
            Matcher matcher = PPT_SLIDE.matcher(name);
            int index = matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
            String cleaned = extractXmlText(xml, "a:t");
            if (!cleaned.isBlank()) {
                slides.add(new SlideText(index, cleaned));
            }
        });
        slides.sort(Comparator.comparingInt(SlideText::index));
        StringBuilder text = new StringBuilder();
        for (SlideText slide : slides) {
            text.append("第 ").append(slide.index()).append(" 页：")
                    .append(slide.text()).append('\n');
        }
        return text.toString();
    }

    private void readZipXml(Path path, EntryMatcher matcher, EntryConsumer consumer) throws IOException {
        try (InputStream input = Files.newInputStream(path);
             ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !matcher.matches(entry.getName())) {
                    continue;
                }
                String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                consumer.accept(entry.getName(), xml);
            }
        }
    }

    private String extractXmlText(String xml, String textTag) {
        String normalized = xml
                .replaceAll("<w:tab\\s*/>", " ")
                .replaceAll("<w:br\\s*/>", "\n")
                .replaceAll("</a:p>|</w:p>", "\n")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
        Pattern tag = Pattern.compile("<" + Pattern.quote(textTag) + "(?:\\s[^>]*)?>(.*?)</" + Pattern.quote(textTag) + ">", Pattern.DOTALL);
        Matcher matcher = tag.matcher(normalized);
        StringBuilder text = new StringBuilder();
        while (matcher.find()) {
            String part = XML_TAG.matcher(matcher.group(1)).replaceAll("");
            if (!part.isBlank()) {
                text.append(part.trim()).append(' ');
            }
        }
        return compact(text.toString());
    }

    private String readText(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('\uFFFD') >= 0) {
            text = new String(bytes, Charset.forName("GBK"));
        }
        return text;
    }

    private String textSummary(String text, Resource resource) {
        List<String> sentences = sentences(text);
        if (sentences.isEmpty()) {
            return metadataSummary(resource, "正文文本较短，Agent 使用资源信息补充总结。");
        }
        String joined = String.join("；", sentences.stream().limit(SUMMARY_SENTENCE_LIMIT).toList());
        return "该资源主要围绕“" + firstNonBlank(resource.getTitle(), resource.getFileName(), "学习资源")
                + "”展开。核心内容可概括为：" + joined + "。";
    }

    private String metadataSummary(Resource resource, String prefix) {
        return prefix + " 资源名称为“" + firstNonBlank(resource.getTitle(), resource.getFileName(), "未命名资源")
                + "”，文件为“" + firstNonBlank(resource.getFileName(), "-")
                + "”，描述为“" + firstNonBlank(resource.getDescription(), "暂无描述") + "”。";
    }

    private List<String> keywordPoints(String text, int limit) {
        String source = compact(text).toLowerCase(Locale.ROOT);
        if (source.isBlank()) {
            return List.of("暂无可提取知识点");
        }
        List<String> candidates = new ArrayList<>();
        Matcher chinese = Pattern.compile("[\\p{IsHan}A-Za-z0-9][\\p{IsHan}A-Za-z0-9\\-_/]{1,24}").matcher(source);
        while (chinese.find()) {
            String word = chinese.group().trim();
            if (word.length() >= 2 && !isStopWord(word)) {
                candidates.add(word);
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String item : candidates) {
            counts.put(item, counts.getOrDefault(item, 0) + 1);
        }
        List<String> points = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> "掌握“" + entry.getKey() + "”相关概念或内容线索")
                .toList();
        return points.isEmpty() ? List.of("围绕资源标题和描述建立初步知识框架") : points;
    }

    private List<String> outline(String text, String mediaKind) {
        List<String> lines = new ArrayList<>();
        for (String line : compact(text).split("[\\n。；;]")) {
            String item = line.trim();
            if (item.length() >= 6) {
                lines.add(cut(item, 92));
            }
            if (lines.size() >= 8) {
                break;
            }
        }
        if (!lines.isEmpty()) {
            return lines;
        }
        return List.of(mediaKind + " 内容结构待补充");
    }

    private List<String> metadataOutline(Resource resource, String mediaKind) {
        List<String> lines = new ArrayList<>();
        lines.add("类型：" + mediaKind);
        lines.add("标题：" + firstNonBlank(resource.getTitle(), "-"));
        lines.add("文件：" + firstNonBlank(resource.getFileName(), "-"));
        if (!blank(resource.getDescription())) {
            lines.add("描述：" + cut(resource.getDescription(), 120));
        }
        if (!blank(resource.getCategoryName())) {
            lines.add("分类：" + resource.getCategoryName());
        }
        return lines;
    }

    private List<String> evidenceFromMetadata(Resource resource, String path) {
        return snippets(metadataText(resource, path), 4);
    }

    private List<String> snippets(String text, int limit) {
        List<String> sentences = sentences(text);
        if (sentences.isEmpty() && !blank(text)) {
            sentences = List.of(compact(text));
        }
        return sentences.stream().limit(limit).map(item -> cut(item, 130)).toList();
    }

    private List<String> sentences(String text) {
        String source = compact(text);
        if (source.isBlank()) {
            return List.of();
        }
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.CHINA);
        iterator.setText(source);
        List<String> result = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = source.substring(start, end).trim();
            if (sentence.length() >= 8) {
                result.add(cut(sentence, 140));
            }
            if (result.size() >= 12) {
                break;
            }
        }
        if (result.isEmpty()) {
            for (String part : source.split("[。！？!?；;\\n]")) {
                String sentence = part.trim();
                if (sentence.length() >= 8) {
                    result.add(cut(sentence, 140));
                }
                if (result.size() >= 12) {
                    break;
                }
            }
        }
        return result;
    }

    private List<String> nextActions(String mediaKind, boolean hasContent) {
        if ("VIDEO".equals(mediaKind)) {
            return hasContent
                    ? List.of("按字幕/文本线索提炼课程章节。", "进入视频播放器核对关键时间段。")
                    : List.of("上传同名 .srt/.vtt/.txt 字幕后重新总结。", "后续可接入 ASR 服务生成逐字稿。");
        }
        if ("AUDIO".equals(mediaKind)) {
            return hasContent
                    ? List.of("按逐字稿提取重点术语。", "结合音频播放器复听重点片段。")
                    : List.of("上传同名歌词、字幕或听写文本后重新总结。", "后续可接入语音识别生成内容摘要。");
        }
        if ("PPT".equals(mediaKind)) {
            return List.of("按页码检查讲义结构。", "把知识点整理成复习提纲或题库。");
        }
        if ("WORD".equals(mediaKind)) {
            return List.of("继续生成详细学习笔记。", "把知识点转为问答卡片。");
        }
        return List.of("打开预览核对内容。", "补充资源描述以提升推荐和总结质量。");
    }

    private String buildSearchAnswer(GlobalSearchResponse base) {
        int count = base.getRecords() == null ? 0 : base.getRecords().size();
        if (count == 0) {
            return "没有找到匹配资源。可以换一个关键词，或扩大到全部来源继续搜索。";
        }
        long local = base.getRecords().stream().filter(item -> "LOCAL".equals(item.getSource())).count();
        long remote = count - local;
        String top = base.getRecords().get(0).getTitle();
        return "已找到 " + count + " 个候选资源，其中本地 " + local + " 个、远程 " + remote
                + " 个。最相关资源是“" + top + "”，可以直接进入内容总结。";
    }

    private List<String> suggestedQueries(List<GlobalSearchItem> records, String keyword) {
        Set<String> queries = new LinkedHashSet<>();
        String safe = keyword == null ? "" : keyword.trim();
        if (!safe.isBlank()) {
            queries.add(safe + " PPT");
            queries.add(safe + " 视频");
            queries.add(safe + " 知识点");
        }
        if (records != null) {
            for (GlobalSearchItem item : records) {
                if (!blank(item.getCategoryName())) {
                    queries.add(item.getCategoryName());
                }
                if (!blank(item.getFileName())) {
                    queries.add(stem(item.getFileName()));
                }
                if (queries.size() >= 6) {
                    break;
                }
            }
        }
        if (queries.isEmpty()) {
            queries.addAll(List.of("课程 PPT", "教学视频", "复习资料"));
        }
        return new ArrayList<>(queries).stream().limit(6).toList();
    }

    private String metadataText(Resource resource, String path) {
        return String.join("\n",
                firstNonBlank(resource.getTitle(), ""),
                firstNonBlank(resource.getDescription(), ""),
                firstNonBlank(resource.getFileName(), ""),
                firstNonBlank(resource.getCategoryName(), ""),
                firstNonBlank(resource.getUsername(), ""),
                firstNonBlank(resource.getRelativePath(), ""),
                firstNonBlank(path, ""));
    }

    private String mediaKind(String fileName, String fileType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String type = fileType == null ? "" : fileType.toLowerCase(Locale.ROOT);
        if (type.startsWith("video/") || name.matches(".*\\.(mp4|mov|webm|mkv|avi)$")) return "VIDEO";
        if (type.startsWith("audio/") || name.matches(".*\\.(mp3|wav|ogg|flac|m4a)$")) return "AUDIO";
        if (name.endsWith(".ppt") || name.endsWith(".pptx")) return "PPT";
        if (name.endsWith(".doc") || name.endsWith(".docx")) return "WORD";
        if (type.startsWith("text/") || name.matches(".*\\.(txt|md|csv|json|xml|srt|vtt)$")) return "TEXT";
        if (name.endsWith(".pdf")) return "PDF";
        return "OTHER";
    }

    private String compact(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return normalized.length() <= TEXT_LIMIT ? normalized : normalized.substring(0, TEXT_LIMIT);
    }

    private String cut(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(0, limit - 1)).trim() + "…";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean endsWith(String name, String suffix) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(suffix);
    }

    private String stem(String name) {
        if (name == null) {
            return "";
        }
        String base = baseName(name);
        int index = base.lastIndexOf('.');
        return index < 0 ? base : base.substring(0, index);
    }

    private String baseName(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace("\\", "/");
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    private boolean isStopWord(String word) {
        return Set.of("the", "and", "for", "with", "this", "that", "资源", "文件", "学习", "内容", "暂无", "描述", "视频", "音频")
                .contains(word);
    }

    private record ExtractedContent(String source, String text, double confidence, List<String> limitations) {}

    private record SlideText(int index, String text) {}

    private interface EntryMatcher {
        boolean matches(String name);
    }

    private interface EntryConsumer {
        void accept(String name, String xml);
    }
}

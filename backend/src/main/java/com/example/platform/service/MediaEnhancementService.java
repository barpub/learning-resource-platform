package com.example.platform.service;

import com.example.platform.common.BusinessException;
import com.example.platform.dto.MediaEnhancementCapability;
import com.example.platform.dto.MediaEnhancementRequest;
import com.example.platform.entity.MediaEnhancementJob;
import com.example.platform.entity.Resource;
import com.example.platform.entity.User;
import com.example.platform.mapper.MediaEnhancementJobMapper;
import com.example.platform.mapper.ResourceMapper;
import com.example.platform.security.CurrentUser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MediaEnhancementService {
    private static final Pattern FFMPEG_DURATION = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
    private static final DateTimeFormatter OUTPUT_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_MESSAGE_LENGTH = 480;

    private final ResourceMapper resourceMapper;
    private final MediaEnhancementJobMapper jobMapper;
    private final FileStorageService fileStorageService;
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();

    @Value("${media.ffmpeg-path:}")
    private String configuredFfmpegPath;

    @Value("${media.ffprobe-path:}")
    private String configuredFfprobePath;

    @Value("${media.worker-threads:1}")
    private int workerThreads;

    private ExecutorService executor;

    public MediaEnhancementService(ResourceMapper resourceMapper, MediaEnhancementJobMapper jobMapper,
                                   FileStorageService fileStorageService) {
        this.resourceMapper = resourceMapper;
        this.jobMapper = jobMapper;
        this.fileStorageService = fileStorageService;
    }

    @PostConstruct
    public void startExecutor() {
        int threads = Math.max(1, Math.min(workerThreads, 2));
        executor = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            private int index = 1;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "media-enhancement-" + index++);
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @PreDestroy
    public void shutdownExecutor() {
        runningProcesses.values().forEach(process -> {
            try {
                process.destroyForcibly();
            } catch (RuntimeException ignored) {
            }
        });
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public MediaEnhancementCapability capability() {
        String ffmpeg = ffmpegPath();
        String ffprobe = ffprobePath();
        boolean ffmpegAvailable = isToolAvailable(ffmpeg);
        boolean ffprobeAvailable = isToolAvailable(ffprobe);

        MediaEnhancementCapability capability = new MediaEnhancementCapability();
        capability.setFfmpegPath(ffmpeg);
        capability.setFfmpegAvailable(ffmpegAvailable);
        capability.setFfprobeAvailable(ffprobeAvailable);
        capability.setAiUpscaleAvailable(false);
        capability.setFrameInterpolationAvailable(ffmpegAvailable);
        capability.setAvailableFeatures(ffmpegAvailable
                ? List.of("4K/2K/1080P FFmpeg scaling", "60/120 FPS FFmpeg motion interpolation",
                        "audio loudness normalization", "peak smoothing limiter", "lossless FLAC audio output")
                : List.of("browser playback EQ/limiter"));
        capability.setUnavailableFeatures(List.of("Real-ESRGAN AI super-resolution",
                "RIFE neural frame interpolation", "Demucs vocal/drum source separation"));
        capability.setMessage(ffmpegAvailable
                ? "FFmpeg is available. Local enhancement jobs can be submitted."
                : "FFmpeg is not installed or not configured. Set FFMPEG_PATH or put ffmpeg under backend/tools/ffmpeg/bin.");
        return capability;
    }

    public MediaEnhancementJob submit(Long sourceResourceId, MediaEnhancementRequest request) {
        User user = requireUser();
        Resource source = requireOwnedFile(sourceResourceId, user);
        String mediaType = mediaType(source);
        if (mediaType == null) {
            throw new BusinessException(400, "当前资源不是可增强的视频或音频文件");
        }
        if (!isToolAvailable(ffmpegPath())) {
            throw new BusinessException(503, "FFmpeg 不可用，无法执行本地媒体增强");
        }

        MediaEnhancementJob active = jobMapper.findActiveBySourceAndUser(sourceResourceId, user.getId());
        if (active != null) {
            return active;
        }

        MediaEnhancementJob job = new MediaEnhancementJob();
        job.setSourceResourceId(sourceResourceId);
        job.setUserId(user.getId());
        job.setMediaType(mediaType);
        job.setTargetResolution(normalizeResolution(request == null ? null : request.getTargetResolution(), mediaType));
        job.setTargetFps(normalizeFps(request == null ? null : request.getTargetFps(), mediaType));
        job.setVideoPreset(normalizeVideoPreset(request == null ? null : request.getVideoPreset()));
        job.setAudioPreset(normalizeAudioPreset(request == null ? null : request.getAudioPreset()));
        job.setAiUpscale(Boolean.TRUE.equals(request == null ? null : request.getAiUpscale()) ? 1 : 0);
        job.setFrameInterpolation(Boolean.TRUE.equals(request == null ? null : request.getFrameInterpolation()) ? 1 : 0);
        job.setStatus("PENDING");
        job.setProgress(0);
        job.setMessage("任务已提交，等待本地增强队列执行");
        jobMapper.insert(job);
        executor.submit(() -> runJob(job.getId()));
        return jobMapper.findById(job.getId());
    }

    public MediaEnhancementJob get(Long jobId) {
        MediaEnhancementJob job = jobMapper.findById(jobId);
        if (job == null) {
            throw new BusinessException(404, "增强任务不存在");
        }
        assertJobVisible(job);
        return job;
    }

    public List<MediaEnhancementJob> listByResource(Long sourceResourceId) {
        User user = requireUser();
        Resource source = resourceMapper.findById(sourceResourceId);
        if (source == null || Integer.valueOf(0).equals(source.getStatus())) {
            throw new BusinessException(404, "资源不存在");
        }
        boolean admin = "ADMIN".equals(user.getRole());
        if (!admin && !source.getUserId().equals(user.getId())) {
            throw new BusinessException(403, "无权限查看该资源的增强任务");
        }
        return jobMapper.findBySourceResourceId(sourceResourceId, user.getId(), admin);
    }

    public MediaEnhancementJob cancel(Long jobId) {
        MediaEnhancementJob job = get(jobId);
        if ("SUCCESS".equals(job.getStatus()) || "FAILED".equals(job.getStatus()) || "CANCELLED".equals(job.getStatus())) {
            return job;
        }
        jobMapper.cancel(jobId, "任务已取消");
        Process process = runningProcesses.remove(jobId);
        if (process != null) {
            process.destroyForcibly();
        }
        return jobMapper.findById(jobId);
    }

    private void runJob(Long jobId) {
        Path outputPath = null;
        try {
            MediaEnhancementJob job = jobMapper.findById(jobId);
            if (job == null || "CANCELLED".equals(job.getStatus())) {
                return;
            }
            Resource source = resourceMapper.findById(job.getSourceResourceId());
            if (source == null || Integer.valueOf(0).equals(source.getStatus())) {
                throw new BusinessException(404, "源资源不存在");
            }

            Path inputPath = fileStorageService.resolve(source.getFilePath());
            String outputExtension = "VIDEO".equals(job.getMediaType()) ? "mp4" : "flac";
            String outputContentType = "VIDEO".equals(job.getMediaType()) ? "video/mp4" : "audio/flac";
            String outputName = outputFileName(source, job, outputExtension);
            outputPath = fileStorageService.prepareGeneratedPath(outputExtension);
            double durationSeconds = readDurationSeconds(inputPath);
            List<String> command = buildCommand(job, inputPath, outputPath);

            jobMapper.updateState(jobId, "RUNNING", 5, "FFmpeg 正在处理媒体文件");
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            runningProcesses.put(jobId, process);
            StringBuilder tail = new StringBuilder();
            double[] durationRef = new double[] { durationSeconds };
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendTail(tail, line);
                    updateProgressFromLine(jobId, line, durationRef);
                }
            }
            int exitCode = process.waitFor();
            runningProcesses.remove(jobId);
            if (isCancelled(jobId)) {
                deleteQuietly(outputPath);
                return;
            }
            if (exitCode != 0 || !Files.isRegularFile(outputPath) || Files.size(outputPath) == 0) {
                throw new BusinessException(500, "FFmpeg 处理失败：" + trimMessage(tail.toString()));
            }

            Resource outputResource = createOutputResource(source, job, outputName, outputPath, outputContentType);
            jobMapper.markSuccess(jobId, outputResource.getId(), outputPath.toString(),
                    "增强完成，已生成资源 #" + outputResource.getId());
        } catch (Exception ex) {
            runningProcesses.remove(jobId);
            deleteQuietly(outputPath);
            if (!isCancelled(jobId)) {
                jobMapper.markFailed(jobId, trimMessage(ex.getMessage() == null ? "媒体增强失败" : ex.getMessage()));
            }
        }
    }

    private List<String> buildCommand(MediaEnhancementJob job, Path inputPath, Path outputPath) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath());
        command.add("-y");
        command.add("-hide_banner");
        command.add("-progress");
        command.add("pipe:1");
        command.add("-i");
        command.add(inputPath.toString());
        if ("VIDEO".equals(job.getMediaType())) {
            List<String> filters = videoFilters(job);
            if (!filters.isEmpty()) {
                command.add("-vf");
                command.add(String.join(",", filters));
            }
            command.add("-c:v");
            command.add("libx264");
            command.add("-preset");
            command.add(videoEncoderPreset(job.getVideoPreset()));
            command.add("-crf");
            command.add(videoCrf(job.getVideoPreset()));
            command.add("-pix_fmt");
            command.add("yuv420p");
            if (hasAudioStream(inputPath)) {
                command.add("-af");
                command.add(audioFilter(job.getAudioPreset()));
                command.add("-c:a");
                command.add("aac");
                command.add("-b:a");
                command.add("320k");
            }
            command.add("-movflags");
            command.add("+faststart");
        } else {
            command.add("-vn");
            command.add("-af");
            command.add(audioFilter(job.getAudioPreset()));
            command.add("-ar");
            command.add("48000");
            command.add("-c:a");
            command.add("flac");
        }
        command.add(outputPath.toString());
        return command;
    }

    private List<String> videoFilters(MediaEnhancementJob job) {
        List<String> filters = new ArrayList<>();
        int[] size = resolutionSize(job.getTargetResolution());
        if (size != null) {
            filters.add("scale=" + size[0] + ":" + size[1]
                    + ":force_original_aspect_ratio=decrease:flags=lanczos");
            filters.add("pad=" + size[0] + ":" + size[1] + ":(ow-iw)/2:(oh-ih)/2");
        }
        Integer fps = job.getTargetFps();
        if (fps != null && fps > 0) {
            if (Integer.valueOf(1).equals(job.getFrameInterpolation())) {
                filters.add("minterpolate=fps=" + fps + ":mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1");
            } else {
                filters.add("fps=" + fps);
            }
        }
        return filters;
    }

    private String audioFilter(String preset) {
        String mode = preset == null ? "HIFI" : preset.toUpperCase(Locale.ROOT);
        if ("VOCAL".equals(mode)) {
            return "highpass=f=70,lowpass=f=18000,loudnorm=I=-16:TP=-1.5:LRA=9,"
                    + "acompressor=threshold=-22dB:ratio=2.8:attack=5:release=140,alimiter=limit=0.95";
        }
        if ("BEAT".equals(mode)) {
            return "highpass=f=35,lowpass=f=19000,loudnorm=I=-14:TP=-1.2:LRA=10,"
                    + "acompressor=threshold=-18dB:ratio=2.2:attack=8:release=180,alimiter=limit=0.95";
        }
        return "highpass=f=35,lowpass=f=19000,loudnorm=I=-16:TP=-1.5:LRA=11,"
                + "acompressor=threshold=-20dB:ratio=2.2:attack=6:release=160,alimiter=limit=0.95";
    }

    private Resource createOutputResource(Resource source, MediaEnhancementJob job, String outputName,
                                          Path outputPath, String contentType) {
        FileStorageService.StoredFile stored = fileStorageService.describeGeneratedFile(outputName, outputPath, contentType);
        Resource output = new Resource();
        output.setTitle(outputTitle(source, job));
        output.setDescription(outputDescription(source, job));
        output.setFileName(stored.getOriginalName());
        output.setFilePath(stored.getPath());
        output.setFileSize(stored.getSize());
        output.setFileType(stored.getType());
        output.setResourceType("FILE");
        output.setParentId(source.getParentId());
        output.setSortOrder(nextSortOrder(source.getParentId()));
        output.setRelativePath(source.getParentId() == null ? null : outputRelativePath(source, outputName));
        output.setFileCount(0);
        output.setCategoryId(source.getCategoryId());
        output.setTags(source.getTags());
        output.setUserId(source.getUserId());
        output.setStatus(1);
        resourceMapper.insert(output);
        if (source.getParentId() != null) {
            resourceMapper.refreshFolderStats(source.getParentId());
        }
        return resourceMapper.findById(output.getId());
    }

    private String outputDescription(Resource source, MediaEnhancementJob job) {
        String base = source.getDescription() == null ? "" : source.getDescription().trim();
        String generated = "Generated from resource #" + source.getId() + " by local media enhancement. "
                + "Video=" + job.getTargetResolution() + ", fps=" + job.getTargetFps()
                + ", audio=" + job.getAudioPreset() + ".";
        return base.isBlank() ? generated : base + "\n\n" + generated;
    }

    private String outputTitle(Resource source, MediaEnhancementJob job) {
        String title = firstNonBlank(source.getTitle(), stripExtension(source.getFileName()), "enhanced media");
        if ("VIDEO".equals(job.getMediaType())) {
            return title + " Enhanced " + job.getTargetResolution() + " " + job.getTargetFps() + "fps";
        }
        return title + " Enhanced " + job.getAudioPreset();
    }

    private String outputFileName(Resource source, MediaEnhancementJob job, String extension) {
        String base = stripExtension(firstNonBlank(source.getFileName(), source.getTitle(), "media"));
        String suffix = "VIDEO".equals(job.getMediaType())
                ? "enhanced-" + job.getTargetResolution().toLowerCase(Locale.ROOT) + "-" + job.getTargetFps() + "fps"
                : "enhanced-" + job.getAudioPreset().toLowerCase(Locale.ROOT);
        return sanitizeFileName(base + "-" + suffix + "-" + LocalDateTime.now().format(OUTPUT_TIME) + "." + extension);
    }

    private String outputRelativePath(Resource source, String outputName) {
        String relativePath = source.getRelativePath();
        if (relativePath == null || relativePath.isBlank() || !relativePath.contains("/")) {
            return outputName;
        }
        return relativePath.substring(0, relativePath.lastIndexOf('/') + 1) + outputName;
    }

    private int nextSortOrder(Long parentId) {
        if (parentId == null) {
            return 0;
        }
        return resourceMapper.findChildren(parentId).size();
    }

    private void updateProgressFromLine(Long jobId, String line, double[] durationRef) {
        if (line == null) {
            return;
        }
        Matcher matcher = FFMPEG_DURATION.matcher(line);
        if (matcher.find()) {
            durationRef[0] = parseDuration(matcher);
            return;
        }
        if (line.startsWith("out_time_ms=")) {
            double duration = durationRef[0];
            if (duration <= 0) {
                return;
            }
            long micros = parseLong(line.substring("out_time_ms=".length()), 0L);
            double seconds = micros / 1_000_000.0;
            int progress = Math.max(6, Math.min(96, 6 + (int) Math.round((seconds / duration) * 90)));
            jobMapper.updateRunning(jobId, progress, "FFmpeg 正在处理，进度约 " + progress + "%");
        } else if ("progress=end".equals(line.trim())) {
            jobMapper.updateRunning(jobId, 98, "媒体文件正在登记为资源");
        }
    }

    private double parseDuration(Matcher matcher) {
        int hours = (int) parseLong(matcher.group(1), 0);
        int minutes = (int) parseLong(matcher.group(2), 0);
        double seconds = parseDouble(matcher.group(3), 0);
        return hours * 3600.0 + minutes * 60.0 + seconds;
    }

    private double readDurationSeconds(Path inputPath) {
        if (!isToolAvailable(ffprobePath())) {
            return -1;
        }
        try {
            Process process = new ProcessBuilder(ffprobePath(), "-v", "error",
                    "-show_entries", "format=duration", "-of", "default=nk=1:nw=1",
                    inputPath.toString()).redirectErrorStream(true).start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.readLine();
            }
            if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                return parseDouble(output, -1);
            }
        } catch (IOException ex) {
            return -1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return -1;
    }

    private boolean hasAudioStream(Path inputPath) {
        if (!isToolAvailable(ffprobePath())) {
            return false;
        }
        try {
            Process process = new ProcessBuilder(ffprobePath(), "-v", "error",
                    "-select_streams", "a:0", "-show_entries", "stream=codec_type",
                    "-of", "csv=p=0", inputPath.toString()).redirectErrorStream(true).start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.readLine();
            }
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
                    && output != null && output.trim().equalsIgnoreCase("audio");
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isToolAvailable(String tool) {
        try {
            Process process = new ProcessBuilder(tool, "-version").redirectErrorStream(true).start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private String ffmpegPath() {
        return resolveToolPath(configuredFfmpegPath, "ffmpeg");
    }

    private String ffprobePath() {
        return resolveToolPath(configuredFfprobePath, "ffprobe");
    }

    private String resolveToolPath(String configuredPath, String executableName) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return configuredPath.trim();
        }
        String executable = isWindows() ? executableName + ".exe" : executableName;
        List<Path> candidates = List.of(
                Paths.get("tools", "ffmpeg", "bin", executable),
                Paths.get("..", "tools", "ffmpeg", "bin", executable),
                Paths.get("backend", "tools", "ffmpeg", "bin", executable)
        );
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized.toString();
            }
        }
        return executableName;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private Resource requireOwnedFile(Long resourceId, User user) {
        Resource source = resourceMapper.findById(resourceId);
        if (source == null || Integer.valueOf(0).equals(source.getStatus())) {
            throw new BusinessException(404, "资源不存在");
        }
        if (!"FILE".equals(source.getResourceType())) {
            throw new BusinessException(400, "文件夹不能直接增强，请选择具体视频或音频文件");
        }
        if (!"ADMIN".equals(user.getRole()) && !source.getUserId().equals(user.getId())) {
            throw new BusinessException(403, "无权限增强该资源");
        }
        return source;
    }

    private User requireUser() {
        User user = CurrentUser.get();
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        return user;
    }

    private void assertJobVisible(MediaEnhancementJob job) {
        User user = requireUser();
        if (!"ADMIN".equals(user.getRole()) && !job.getUserId().equals(user.getId())) {
            throw new BusinessException(403, "无权限查看该增强任务");
        }
    }

    private boolean isCancelled(Long jobId) {
        MediaEnhancementJob job = jobMapper.findById(jobId);
        return job != null && "CANCELLED".equals(job.getStatus());
    }

    private String mediaType(Resource source) {
        String type = source.getFileType() == null ? "" : source.getFileType().toLowerCase(Locale.ROOT);
        String name = source.getFileName() == null ? "" : source.getFileName().toLowerCase(Locale.ROOT);
        if (type.startsWith("video/") || name.matches(".*\\.(mp4|mov|webm|mkv|avi)$")) {
            return "VIDEO";
        }
        if (type.startsWith("audio/") || name.matches(".*\\.(mp3|wav|ogg|flac|m4a|aac)$")) {
            return "AUDIO";
        }
        return null;
    }

    private String normalizeResolution(String value, String mediaType) {
        if (!"VIDEO".equals(mediaType)) {
            return "ORIGINAL";
        }
        String normalized = value == null ? "4K" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "720P", "1080P", "2K", "4K", "ORIGINAL" -> normalized;
            default -> "4K";
        };
    }

    private Integer normalizeFps(Integer value, String mediaType) {
        if (!"VIDEO".equals(mediaType)) {
            return null;
        }
        if (value == null) {
            return 60;
        }
        if (value <= 0) {
            return null;
        }
        return Math.min(120, Math.max(24, value));
    }

    private String normalizeVideoPreset(String value) {
        String normalized = value == null ? "BALANCED" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FAST", "BALANCED", "QUALITY" -> normalized;
            default -> "BALANCED";
        };
    }

    private String normalizeAudioPreset(String value) {
        String normalized = value == null ? "HIFI" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIFI", "VOCAL", "BEAT" -> normalized;
            default -> "HIFI";
        };
    }

    private int[] resolutionSize(String resolution) {
        if (resolution == null) {
            return null;
        }
        return switch (resolution.toUpperCase(Locale.ROOT)) {
            case "720P" -> new int[] { 1280, 720 };
            case "1080P" -> new int[] { 1920, 1080 };
            case "2K" -> new int[] { 2560, 1440 };
            case "4K" -> new int[] { 3840, 2160 };
            default -> null;
        };
    }

    private String videoEncoderPreset(String preset) {
        return switch (preset == null ? "BALANCED" : preset) {
            case "FAST" -> "veryfast";
            case "QUALITY" -> "slow";
            default -> "medium";
        };
    }

    private String videoCrf(String preset) {
        return switch (preset == null ? "BALANCED" : preset) {
            case "FAST" -> "23";
            case "QUALITY" -> "18";
            default -> "20";
        };
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String stripExtension(String value) {
        String name = firstNonBlank(value, "media");
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String sanitizeFileName(String value) {
        String sanitized = value == null ? "media" : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isBlank() ? "media" : sanitized;
    }

    private void appendTail(StringBuilder tail, String line) {
        if (tail.length() > 3000) {
            tail.delete(0, tail.length() - 2400);
        }
        tail.append(line).append('\n');
    }

    private String trimMessage(String message) {
        String normalized = message == null ? "" : message.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "媒体增强失败";
        }
        return normalized.length() <= MAX_MESSAGE_LENGTH ? normalized : normalized.substring(0, MAX_MESSAGE_LENGTH);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}

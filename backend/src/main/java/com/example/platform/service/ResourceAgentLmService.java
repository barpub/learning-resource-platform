package com.example.platform.service;

import com.example.platform.config.ResourceAgentLmProperties;
import com.example.platform.dto.ResourceContentSummary;
import com.example.platform.entity.Resource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ResourceAgentLmService {
    private final ResourceAgentLmProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ResourceAgentLmService(ResourceAgentLmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMs())))
                .build();
    }

    public Optional<LlmSummary> summarize(Resource resource,
                                          ResourceContentSummary current,
                                          String inputText,
                                          boolean hasExtractedContent) {
        if (!available()) {
            return Optional.empty();
        }

        try {
            String responseBody = callChatCompletions(resource, current, inputText, hasExtractedContent);
            String content = assistantContent(responseBody);
            if (blank(content)) {
                return Optional.empty();
            }
            LlmSummaryPayload payload = objectMapper.readValue(stripJsonFences(content), LlmSummaryPayload.class);
            String summary = clean(payload.summary);
            if (blank(summary)) {
                return Optional.empty();
            }
            return Optional.of(new LlmSummary(
                    summary,
                    cleanList(payload.knowledgePoints),
                    cleanList(payload.outline),
                    cleanList(payload.nextActions)
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public String modelName() {
        return clean(properties.getModel());
    }

    private boolean available() {
        return properties.isEnabled()
                && !blank(properties.getApiKey())
                && !blank(properties.getBaseUrl())
                && !blank(properties.getModel());
    }

    private String callChatCompletions(Resource resource,
                                       ResourceContentSummary current,
                                       String inputText,
                                       boolean hasExtractedContent) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", userPrompt(resource, current, inputText, hasExtractedContent))
        ));
        body.put("temperature", 0.2);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl()))
                .timeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMs())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return "";
        }
        return response.body();
    }

    private String systemPrompt() {
        return String.join("\n",
                "You are the summary layer for a learning-resource search agent.",
                "Return ONLY strict JSON. Do not wrap it in markdown.",
                "The JSON schema is:",
                "{\"summary\":\"string\",\"knowledgePoints\":[\"string\"],\"outline\":[\"string\"],\"nextActions\":[\"string\"]}",
                "Write concise Simplified Chinese unless the source text is clearly another language.",
                "Never invent watched/heard/read content. If only metadata is available, say the result is metadata-based.");
    }

    private String userPrompt(Resource resource,
                              ResourceContentSummary current,
                              String inputText,
                              boolean hasExtractedContent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Resource metadata:\n");
        prompt.append("title: ").append(clean(firstNonBlank(resource.getTitle(), current.getTitle()))).append('\n');
        prompt.append("fileName: ").append(clean(firstNonBlank(resource.getFileName(), current.getFileName()))).append('\n');
        prompt.append("mediaKind: ").append(clean(current.getMediaKind())).append('\n');
        prompt.append("contentSource: ").append(clean(current.getContentSource())).append('\n');
        prompt.append("hasExtractedContent: ").append(hasExtractedContent).append('\n');
        prompt.append("description: ").append(clean(resource.getDescription())).append('\n');
        prompt.append("category: ").append(clean(resource.getCategoryName())).append('\n');
        prompt.append("currentRuleSummary: ").append(clean(current.getSummary())).append('\n');
        if (current.getLimitations() != null && !current.getLimitations().isEmpty()) {
            prompt.append("knownLimitations: ").append(String.join(" | ", current.getLimitations())).append('\n');
        }
        prompt.append("\nSource text for summarization:\n");
        prompt.append(limit(clean(inputText), Math.max(500, properties.getMaxInputChars())));
        return prompt.toString();
    }

    private String chatCompletionsUrl() {
        String base = properties.getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    private String assistantContent(String responseBody) throws Exception {
        if (blank(responseBody)) {
            return "";
        }
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private String stripJsonFences(String content) {
        String value = clean(content);
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                value = value.substring(firstLine + 1, lastFence).trim();
            }
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String cleaned = clean(value);
            if (!cleaned.isBlank()) {
                result.add(limit(cleaned, 180));
            }
            if (result.size() >= 12) {
                break;
            }
        }
        return result;
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

    private String limit(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, limit - 1)).trim();
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record LlmSummary(String summary,
                             List<String> knowledgePoints,
                             List<String> outline,
                             List<String> nextActions) {
    }

    public static class LlmSummaryPayload {
        public String summary;
        public List<String> knowledgePoints;
        public List<String> outline;
        public List<String> nextActions;
    }
}

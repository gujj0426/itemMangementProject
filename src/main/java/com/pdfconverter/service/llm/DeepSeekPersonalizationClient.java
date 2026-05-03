package com.pdfconverter.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfconverter.config.PersonalizationLlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek Chat Completions（OpenAI 兼容）调用。
 */
@Component
public class DeepSeekPersonalizationClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekPersonalizationClient.class);

    @Resource
    private PersonalizationLlmProperties properties;

    @Resource
    @Qualifier("personalizationRestTemplate")
    private RestTemplate restTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * @return message.content 原文；失败或空返回 null
     */
    public String chatCompletionJson(String systemPrompt, String userPrompt) {
        return chatCompletionJson(systemPrompt, userPrompt, properties.getMaxTokens(), null);
    }

    /**
     * @param maxTokens        本次请求的 max_tokens
     * @param modelOverride    非空则覆盖默认 {@link PersonalizationLlmProperties#getModel()}（用于路由等小调用）
     */
    public String chatCompletionJson(String systemPrompt, String userPrompt, int maxTokens, String modelOverride) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("未配置 app.llm.personalization.api-key / DEEPSEEK_API_KEY，跳过 DeepSeek 调用");
            return null;
        }
        String url = trimTrailingSlash(properties.getBaseUrl()) + properties.getChatPath();
        String model = (modelOverride != null && !modelOverride.isBlank())
                ? modelOverride.trim()
                : properties.getModel();

        Map<String, Object> body = buildBody(systemPrompt, userPrompt, true, maxTokens, model);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey().trim());

        log.info("DeepSeek API 调用开始 model={} max_tokens={} POST {}", model, maxTokens, url);
        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("DeepSeek HTTP 非预期: {}", response.getStatusCode());
                return postWithoutThinking(url, headers, systemPrompt, userPrompt, maxTokens, model);
            }
            String content = extractContent(response.getBody());
            if (content == null) {
                log.info("DeepSeek API 响应无可用 content，将尝试不带 thinking 重试");
                return postWithoutThinking(url, headers, systemPrompt, userPrompt, maxTokens, model);
            }
            log.info("DeepSeek API 调用成功，返回 JSON 长度 {} 字符", content.length());
            return content;
        } catch (RestClientException e) {
            log.warn("DeepSeek 调用失败: {}", e.getMessage());
            return postWithoutThinking(url, headers, systemPrompt, userPrompt, maxTokens, model);
        }
    }

    private String postWithoutThinking(String url, HttpHeaders headers, String systemPrompt, String userPrompt,
                                       int maxTokens, String model) {
        log.info("DeepSeek API 重试（未带 thinking）POST {}", url);
        try {
            Map<String, Object> body = buildBody(systemPrompt, userPrompt, false, maxTokens, model);
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String content = extractContent(response.getBody());
                if (content != null) {
                    log.info("DeepSeek API 重试成功，返回 JSON 长度 {} 字符", content.length());
                }
                return content;
            }
        } catch (RestClientException ignored) {
            // ignore
        }
        return null;
    }

    private Map<String, Object> buildBody(String systemPrompt, String userPrompt, boolean thinkingDisabled,
                                         int maxTokens, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("temperature", 0.2);
        body.put("max_tokens", Math.max(maxTokens, 64));
        body.put("response_format", Map.of("type", "json_object"));
        if (thinkingDisabled) {
            body.put("thinking", Map.of("type", "disabled"));
        }
        return body;
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isMissingNode() || !content.isTextual()) {
                return null;
            }
            String text = content.asText();
            return text != null && !text.isBlank() ? text.trim() : null;
        } catch (Exception e) {
            log.warn("解析 DeepSeek 响应 JSON 失败", e);
            return null;
        }
    }

    private static String trimTrailingSlash(String base) {
        if (base == null) {
            return "";
        }
        String s = base.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}

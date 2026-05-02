package com.pdfconverter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek（OpenAI 兼容）接口：从 Personalization 抽取设计风格、字体、刻录内容。
 */
@Component
@ConfigurationProperties(prefix = "app.llm.personalization")
public class PersonalizationLlmProperties {

    /**
     * 关闭时不发起 HTTP 请求，Excel 行为与旧版一致。
     */
    private boolean enabled = false;

    /**
     * API Key，建议使用环境变量 DEEPSEEK_API_KEY。
     */
    private String apiKey = "";

    private String baseUrl = "https://api.deepseek.com";

    /**
     * OpenAI 兼容路径，一般无需修改。
     */
    private String chatPath = "/chat/completions";

    private String model = "deepseek-v4-flash";

    private int timeoutMs = 60000;

    private int maxTokens = 1024;

    /**
     * 可选 JSON：非空则仅允许列表内取值；空数组或未配置则从字体/风格映射表取值域。
     */
    private String canonicalListsFile = "personalization-canonical-lists.json";

    /**
     * fill-empty-only：规则引擎已有值时不覆盖；overlay：LLM 非空时优先写 Excel。
     */
    private MergePolicy mergePolicy = MergePolicy.FILL_EMPTY_ONLY;

    public enum MergePolicy {
        FILL_EMPTY_ONLY,
        OVERLAY
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatPath() {
        return chatPath;
    }

    public void setChatPath(String chatPath) {
        this.chatPath = chatPath;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getCanonicalListsFile() {
        return canonicalListsFile;
    }

    public void setCanonicalListsFile(String canonicalListsFile) {
        this.canonicalListsFile = canonicalListsFile;
    }

    public MergePolicy getMergePolicy() {
        return mergePolicy;
    }

    public void setMergePolicy(MergePolicy mergePolicy) {
        this.mergePolicy = mergePolicy;
    }
}

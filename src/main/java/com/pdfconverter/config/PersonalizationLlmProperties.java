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

    /** 抽取阶段输出 JSON；过小易导致刻录长句被模型截断，默认放宽 */
    private int maxTokens = 2048;

    /**
     * 可选 JSON：非空则仅允许列表内取值；空数组或未配置则从字体/风格映射表取值域。
     */
    private String canonicalListsFile = "personalization-canonical-lists.json";

    /**
     * fill-empty-only：规则引擎已有值时不覆盖；overlay：LLM 非空时优先写 Excel。
     */
    private MergePolicy mergePolicy = MergePolicy.FILL_EMPTY_ONLY;

    /**
     * 开启后先做「路由」调用：从杂乱 Personalization 中筛出仅对应本导出行的买家表述，再调用原有四维抽取。
     * 每条 eligible 行会增加一次 API 调用（成本约 doubling）。
     */
    private boolean routingEnabled = false;

    /**
     * 路由阶段使用的模型；留空则与 {@link #model} 相同。
     */
    private String routingModel = "";

    /**
     * 路由 JSON 通常很短，单独限制 token 以节省费用。
     */
    /** 路由阶段若仍需粘贴较长原文片段，需足够 completion budget */
    private int routingMaxTokens = 1024;

    /**
     * Personalization 全文短于此字符数时不发起路由（直接走单阶段抽取）。
     */
    private int routingMinPersonalizationChars = 40;

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

    public boolean isRoutingEnabled() {
        return routingEnabled;
    }

    public void setRoutingEnabled(boolean routingEnabled) {
        this.routingEnabled = routingEnabled;
    }

    public String getRoutingModel() {
        return routingModel;
    }

    public void setRoutingModel(String routingModel) {
        this.routingModel = routingModel;
    }

    public int getRoutingMaxTokens() {
        return routingMaxTokens;
    }

    public void setRoutingMaxTokens(int routingMaxTokens) {
        this.routingMaxTokens = routingMaxTokens;
    }

    public int getRoutingMinPersonalizationChars() {
        return routingMinPersonalizationChars;
    }

    public void setRoutingMinPersonalizationChars(int routingMinPersonalizationChars) {
        this.routingMinPersonalizationChars = routingMinPersonalizationChars;
    }
}

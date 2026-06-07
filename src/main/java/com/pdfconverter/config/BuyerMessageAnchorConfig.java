package com.pdfconverter.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 买家刻录留言锚点标签：决定 PDF 中哪些「行首 Label:」视为 Personalization 正文起点，
 * 从而触发刻录拆行与 DeepSeek 意图识别。
 */
@Component
public class BuyerMessageAnchorConfig {

    private static final Logger log = LoggerFactory.getLogger(BuyerMessageAnchorConfig.class);

    public static List<String> defaultLabels() {
        return DEFAULT_LABELS;
    }

    public static List<String> defaultExcludePrefixes() {
        return DEFAULT_EXCLUDE_PREFIXES;
    }

    private static final List<String> DEFAULT_LABELS = List.of(
            "Personalization",
            "TieClip Engraving",
            "Tie Clip Engraving",
            "Cufflink Engraving",
            "Cufflinks Engraving",
            "Engraving"
    );

    private static final List<String> DEFAULT_EXCLUDE_PREFIXES = List.of(
            "Engraving Sides",
            "Engraving Fee",
            "Engraving Options",
            "Additional Engraving"
    );

    @Value("${app.llm.personalization.buyer-message-labels-file:buyer-message-anchor-labels.json}")
    private String configFile;

    private List<String> buyerMessageLabels = new ArrayList<>(DEFAULT_LABELS);
    private List<String> excludePrefixes = new ArrayList<>(DEFAULT_EXCLUDE_PREFIXES);
    private List<Pattern> anchorPatterns = List.of();

    @PostConstruct
    public void init() {
        load();
    }

    public void load() {
        LabelsFileDto dto = readDto();
        if (dto != null && dto.buyerMessageLabels != null && !dto.buyerMessageLabels.isEmpty()) {
            buyerMessageLabels = normalizeLabels(dto.buyerMessageLabels);
            excludePrefixes = dto.excludePrefixes != null
                    ? normalizeLabels(dto.excludePrefixes)
                    : new ArrayList<>(DEFAULT_EXCLUDE_PREFIXES);
        } else {
            buyerMessageLabels = new ArrayList<>(DEFAULT_LABELS);
            excludePrefixes = new ArrayList<>(DEFAULT_EXCLUDE_PREFIXES);
            log.warn("买家留言锚点配置为空或读取失败，使用内置默认标签 {}", buyerMessageLabels);
        }
        anchorPatterns = compilePatterns(buyerMessageLabels, excludePrefixes);
        log.info("买家刻录留言锚点标签已加载 {} 项（配置文件 {}）: {}",
                buyerMessageLabels.size(), configFile, buyerMessageLabels);
    }

    public List<String> getBuyerMessageLabels() {
        return Collections.unmodifiableList(buyerMessageLabels);
    }

    public List<String> getExcludePrefixes() {
        return Collections.unmodifiableList(excludePrefixes);
    }

    public List<Pattern> getAnchorPatterns() {
        return anchorPatterns;
    }

    public static List<Pattern> compilePatterns(List<String> labels, List<String> excludePrefixes) {
        return BuyerMessageAnchorPatternBuilder.compile(labels, excludePrefixes);
    }

    /** 行首是否命中任一已配置锚点标签（用于截断 personalization 尾段）。 */
    public boolean lineStartsWithAnchorLabel(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String trimmed = line.trim();
        for (String label : buyerMessageLabels) {
            if (lineStartsWithLabel(trimmed, label)) {
                return true;
            }
        }
        return false;
    }

    private LabelsFileDto readDto() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream in = openConfigStream()) {
            if (in == null) {
                return null;
            }
            return mapper.readValue(in, LabelsFileDto.class);
        } catch (Exception e) {
            log.error("读取买家留言锚点配置失败 {}: {}", configFile, e.getMessage(), e);
            return null;
        }
    }

    private InputStream openConfigStream() throws java.io.IOException {
        if (configFile != null && !configFile.isBlank()) {
            File external = new File(configFile.trim());
            if (external.isFile()) {
                log.info("从外部文件加载买家留言锚点: {}", external.getAbsolutePath());
                return new java.io.FileInputStream(external);
            }
            ClassPathResource resource = new ClassPathResource(configFile.trim());
            if (resource.exists()) {
                log.info("从 classpath 加载买家留言锚点: {}", configFile.trim());
                return resource.getInputStream();
            }
        }
        ClassPathResource fallback = new ClassPathResource("buyer-message-anchor-labels.json");
        if (fallback.exists()) {
            log.info("从 classpath 加载买家留言锚点默认文件: buyer-message-anchor-labels.json");
            return fallback.getInputStream();
        }
        return null;
    }

    private static List<String> normalizeLabels(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    public static boolean lineStartsWithLabel(String trimmedLine, String label) {
        if (label == null || label.isBlank()) {
            return false;
        }
        String escaped = Pattern.quote(label.trim());
        return trimmedLine.matches("(?i)" + escaped + "\\s*:.*");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LabelsFileDto {
        public String comment;
        public List<String> buyerMessageLabels;
        public List<String> excludePrefixes;
    }
}

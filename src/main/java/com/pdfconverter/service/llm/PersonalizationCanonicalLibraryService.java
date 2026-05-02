package com.pdfconverter.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfconverter.config.PersonalizationLlmProperties;
import com.pdfconverter.service.mapper.FontNameMappingService;
import com.pdfconverter.service.mapper.StyleNameMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.InputStream;
import java.util.*;

/**
 * 提供给模型的「允许取值」列表：可从 personalization-canonical-lists.json 配置，
 * 若列表为空则使用 style/font 映射表中的标准名称（去重排序）。
 * Icon 默认白名单为 icon #1 … icon #90（可在 JSON 中覆盖）。
 */
@Service
@DependsOn({"styleNameMappingService", "fontNameMappingService"})
public class PersonalizationCanonicalLibraryService {

    private static final Logger log = LoggerFactory.getLogger(PersonalizationCanonicalLibraryService.class);

    @Resource
    private PersonalizationLlmProperties llmProperties;

    @Resource
    private StyleNameMappingService styleNameMappingService;

    @Resource
    private FontNameMappingService fontNameMappingService;

    @Resource
    private ObjectMapper objectMapper;

    private List<String> designStyles = List.of();
    private List<String> fonts = List.of();
    private List<String> icons = List.of();

    @PostConstruct
    public void load() {
        List<String> fromFileStyles = null;
        List<String> fromFileFonts = null;
        List<String> fromFileIcons = null;
        String file = llmProperties.getCanonicalListsFile();
        if (file != null && !file.isBlank()) {
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                org.springframework.core.io.Resource resource =
                        resolver.getResource("classpath:" + file.trim());
                if (resource.exists()) {
                    try (InputStream in = resource.getInputStream()) {
                        JsonNode root = objectMapper.readTree(in);
                        fromFileStyles = readStringArray(root, "designStyles");
                        fromFileFonts = readStringArray(root, "fonts");
                        fromFileIcons = readStringArray(root, "icons");
                    }
                }
            } catch (Exception e) {
                log.warn("读取 canonical 列表失败，回退到映射表取值域: {}", file, e);
            }
        }

        if (fromFileStyles != null && !fromFileStyles.isEmpty()) {
            designStyles = normalizeList(fromFileStyles);
            log.info("设计风格允许列表来自配置文件，共 {} 项", designStyles.size());
        } else {
            designStyles = uniqueSortedValues(styleNameMappingService.getAllMappings().values());
            log.info("设计风格允许列表来自 style-name-mapping，共 {} 项", designStyles.size());
        }

        if (fromFileFonts != null && !fromFileFonts.isEmpty()) {
            fonts = normalizeList(fromFileFonts);
            log.info("字体允许列表来自配置文件，共 {} 项", fonts.size());
        } else {
            fonts = uniqueSortedValues(fontNameMappingService.getAllMappings().values());
            log.info("字体允许列表来自 font-name-mapping，共 {} 项", fonts.size());
        }

        if (fromFileIcons != null && !fromFileIcons.isEmpty()) {
            icons = normalizeList(fromFileIcons);
            log.info("Icon 允许列表来自配置文件，共 {} 项", icons.size());
        } else {
            icons = defaultIconCanonicalList();
            log.info("Icon 允许列表使用默认 icon #1 … icon #90，共 {} 项", icons.size());
        }
    }

    /** 与 Excel 约定一致：icon #1 … icon #90（小写 icon，# 前有空格） */
    private static List<String> defaultIconCanonicalList() {
        List<String> list = new ArrayList<>(90);
        for (int i = 1; i <= 90; i++) {
            list.add("icon #" + i);
        }
        return list;
    }

    private static List<String> readStringArray(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (JsonNode n : node) {
            if (n.isTextual()) {
                String s = n.asText().trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static List<String> normalizeList(List<String> raw) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : raw) {
            if (s != null && !s.trim().isEmpty()) {
                set.add(s.trim());
            }
        }
        return new ArrayList<>(set);
    }

    private static List<String> uniqueSortedValues(Collection<String> values) {
        TreeSet<String> sorted = new TreeSet<>(Comparator.naturalOrder());
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                sorted.add(v.trim());
            }
        }
        return new ArrayList<>(sorted);
    }

    public List<String> getDesignStyles() {
        return designStyles;
    }

    public List<String> getFonts() {
        return fonts;
    }

    public List<String> getIcons() {
        return icons;
    }
}

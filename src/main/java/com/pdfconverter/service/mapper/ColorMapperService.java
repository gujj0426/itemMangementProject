package com.pdfconverter.service.mapper;

import com.pdfconverter.constant.ProductColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 颜色名称映射服务
 * 用于将从订单中提取的原始颜色名称映射为标准的中文颜色名称
 * 映射关系从配置文件中读取，便于后期维护
 * 支持部分匹配，会从长到短依次匹配，优先匹配更长的字符串（如 "rose gold" 优先于 "gold"）
 */
@Service
public class ColorMapperService {

    private static final Logger log = LoggerFactory.getLogger(ColorMapperService.class);

    /**
     * 映射配置文件路径（默认值）
     */
    @Value("${app.mapping.color-name-file:color-name-mapping.properties}")
    private String mappingFilePath;

    /**
     * 颜色名称映射表
     * Key: 从订单中提取的原始名称
     * Value: 对应的ProductColor枚举名称
     */
    private Map<String, String> colorNameMap;

    /**
     * 按长度降序排序的key列表（用于部分匹配）
     */
    private List<String> sortedKeys;

    @PostConstruct
    public void init() {
        colorNameMap = new HashMap<>();
        sortedKeys = new ArrayList<>();
        loadMappingFromFile();
    }

    /**
     * 从配置文件加载映射关系
     */
    private void loadMappingFromFile() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            Resource resource = resolver.getResource("classpath:" + mappingFilePath);

            if (!resource.exists()) {
                log.warn("颜色名称映射配置文件不存在: {}", mappingFilePath);
                // 使用默认映射
                loadDefaultMappings();
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;

                    // 跳过空行和注释行
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    // 解析 key=value 格式
                    int equalIndex = line.indexOf("=");
                    if (equalIndex > 0) {
                        String key = line.substring(0, equalIndex).trim();
                        String value = line.substring(equalIndex + 1).trim();

                        if (!key.isEmpty()) {
                            colorNameMap.put(key, value);
                            log.debug("加载映射: {} -> {}", key, value);
                        }
                    } else {
                        log.warn("配置文件第 {} 行格式错误，跳过: {}", lineNumber, line);
                    }
                }
            }

            // 按长度降序排序，优先匹配更长的字符串
            sortedKeys = colorNameMap.keySet().stream()
                    .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                    .collect(Collectors.toList());

            log.info("成功加载颜色名称映射，共 {} 条映射关系", colorNameMap.size());

        } catch (IOException e) {
            log.error("加载颜色名称映射配置文件失败: {}", mappingFilePath, e);
            // 加载默认映射
            loadDefaultMappings();
        }
    }

    /**
     * 加载默认映射关系
     */
    private void loadDefaultMappings() {
        // 添加一些常见的默认映射
        colorNameMap.put("gold", "GOLD");
        colorNameMap.put("rose gold", "ROSE_GOLD");
        colorNameMap.put("silver", "SILVER");
        colorNameMap.put("black", "BLACK");
        colorNameMap.put("white", "WHITE");
        colorNameMap.put("blue", "BLUE");
        colorNameMap.put("red", "RED");
        colorNameMap.put("green", "GREEN");
        colorNameMap.put("pink", "PINK");

        sortedKeys = colorNameMap.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .collect(Collectors.toList());

        log.info("使用默认颜色名称映射，共 {} 条映射关系", colorNameMap.size());
    }

    /**
     * 根据原始名称查询对应的标准颜色枚举
     *
     * @param originalName 从订单中提取的原始名称
     * @return 对应的ProductColor枚举，如果未找到映射则返回UNKNOWN
     */
    public ProductColor mapColor(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return ProductColor.UNKNOWN;
        }

        String trimmedValue = originalName.trim().toLowerCase();

        // 按长度降序排序，优先匹配更长的字符串
        for (String key : sortedKeys) {
            if (trimmedValue.contains(key.trim().toLowerCase())) {
                String enumName = colorNameMap.get(key);
                try {
                    return ProductColor.valueOf(enumName);
                } catch (IllegalArgumentException e) {
                    log.warn("无法找到ProductColor枚举: {}", enumName);
                }
            }
        }

        // 尝试直接转换为枚举
        try {
            return ProductColor.valueOf(trimmedValue.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            log.debug("无法将 '{}' 转换为ProductColor枚举", originalName);
        }

        return ProductColor.UNKNOWN;
    }

    /**
     * 根据原始名称查询对应的标准颜色名称（支持部分匹配，返回字符串，兼容旧方法）
     *
     * @param originalName 从订单中提取的原始名称
     * @return 对应的标准颜色名称，如果未找到映射则返回 null
     */
    public String getStandardName(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return null;
        }

        // 使用mapColor方法
        ProductColor productColor = mapColor(originalName);
        if (productColor != ProductColor.UNKNOWN) {
            return productColor.getDisplayName();
        }

        return null;
    }

    /**
     * 根据原始名称查询对应的中文颜色（兼容旧方法名）
     * 支持部分匹配，会从长到短依次匹配
     *
     * @param value 从订单中提取的原始名称
     * @return 对应的中文颜色名称，如果未找到映射则返回 null
     */
    public String getColorFromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return getStandardName(value);
    }

    /**
     * 根据精确匹配返回对应的中文颜色
     *
     * @param originalName 从订单中提取的原始名称
     * @return 对应的中文颜色名称，如果未找到映射则返回 null
     */
    public String getStandardNameExactMatch(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return null;
        }

        String trimmedName = originalName.trim();

        // 精确匹配
        String enumName = colorNameMap.get(trimmedName);
        if (enumName != null) {
            try {
                return ProductColor.valueOf(enumName).getDisplayName();
            } catch (IllegalArgumentException e) {
                log.warn("无法找到ProductColor枚举: {}", enumName);
            }
        }

        // 不区分大小写匹配
        for (Map.Entry<String, String> entry : colorNameMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmedName)) {
                try {
                    return ProductColor.valueOf(entry.getValue()).getDisplayName();
                } catch (IllegalArgumentException e) {
                    log.warn("无法找到ProductColor枚举: {}", entry.getValue());
                }
            }
        }

        return null;
    }

    /**
     * 重新加载配置文件（用于动态更新映射关系）
     */
    public void reloadMapping() {
        colorNameMap.clear();
        sortedKeys.clear();
        loadMappingFromFile();
        log.info("颜色名称映射已重新加载");
    }

    /**
     * 检查是否存在指定的映射
     *
     * @param originalName 原始名称
     * @return 如果存在映射返回 true，否则返回 false
     */
    public boolean hasMapping(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return false;
        }

        String trimmedName = originalName.trim();
        return colorNameMap.containsKey(trimmedName) ||
               colorNameMap.entrySet().stream()
                       .anyMatch(entry -> entry.getKey().equalsIgnoreCase(trimmedName));
    }

    /**
     * 获取所有映射关系（用于调试或展示）
     *
     * @return 所有映射关系的副本
     */
    public Map<String, String> getAllMappings() {
        return new HashMap<>(colorNameMap);
    }
}

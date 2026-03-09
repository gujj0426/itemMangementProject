package com.pdfconverter.service;

import com.pdfconverter.constant.ProductSize;
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
 * 产品型号映射服务
 * 用于将从订单中提取的原始型号映射为标准的型号枚举
 * 映射关系从配置文件中读取，便于后期维护
 */
@Service
public class ProductSizeMapperService {

    private static final Logger log = LoggerFactory.getLogger(ProductSizeMapperService.class);

    /**
     * 映射配置文件路径（默认值）
     */
    @Value("${app.mapping.product-size-file:product-size-mapping.properties}")
    private String mappingFilePath;

    /**
     * 型号名称映射表
     * Key: 从订单中提取的原始名称
     * Value: 对应的ProductSize枚举名称
     */
    private Map<String, String> sizeMap;

    /**
     * 按长度降序排序的key列表（用于部分匹配）
     */
    private List<String> sortedKeys;

    @PostConstruct
    public void init() {
        sizeMap = new HashMap<>();
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
                log.warn("产品型号映射配置文件不存在: {}", mappingFilePath);
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
                            sizeMap.put(key, value);
                            log.debug("加载映射: {} -> {}", key, value);
                        }
                    } else {
                        log.warn("配置文件第 {} 行格式错误，跳过: {}", lineNumber, line);
                    }
                }
            }

            // 按长度降序排序，优先匹配更长的字符串
            sortedKeys = sizeMap.keySet().stream()
                    .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                    .collect(Collectors.toList());

            log.info("成功加载产品型号映射，共 {} 条映射关系", sizeMap.size());

        } catch (IOException e) {
            log.error("加载产品型号映射配置文件失败: {}", mappingFilePath, e);
            // 加载默认映射
            loadDefaultMappings();
        }
    }

    /**
     * 加载默认映射关系
     */
    private void loadDefaultMappings() {
        // 添加一些常见的默认映射
        sizeMap.put("small", "SMALL");
        sizeMap.put("medium", "MEDIUM");
        sizeMap.put("large", "LARGE");
        sizeMap.put("xlarge", "XLARGE");
        sizeMap.put("xxl", "XXL");

        sortedKeys = sizeMap.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .collect(Collectors.toList());

        log.info("使用默认产品型号映射，共 {} 条映射关系", sizeMap.size());
    }

    /**
     * 根据原始名称查询对应的标准型号枚举
     *
     * @param originalName 从订单中提取的原始名称
     * @return 对应的ProductSize枚举，如果未找到映射则返回UNKNOWN
     */
    public ProductSize mapSize(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return ProductSize.UNKNOWN;
        }

        String trimmedValue = originalName.trim().toLowerCase();

        // 按长度降序排序，优先匹配更长的字符串
        for (String key : sortedKeys) {
            if (trimmedValue.contains(key.trim().toLowerCase())) {
                String enumName = sizeMap.get(key);
                try {
                    return ProductSize.valueOf(enumName);
                } catch (IllegalArgumentException e) {
                    log.warn("无法找到ProductSize枚举: {}", enumName);
                }
            }
        }

        // 尝试直接转换为枚举
        try {
            return ProductSize.valueOf(trimmedValue.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            log.debug("无法将 '{}' 转换为ProductSize枚举", originalName);
        }

        return ProductSize.UNKNOWN;
    }

    /**
     * 根据精确匹配返回对应的型号枚举
     *
     * @param originalName 从订单中提取的原始名称
     * @return 对应的ProductSize枚举，如果未找到映射则返回UNKNOWN
     */
    public ProductSize mapSizeExact(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return ProductSize.UNKNOWN;
        }

        String trimmedName = originalName.trim();

        // 精确匹配
        String enumName = sizeMap.get(trimmedName);
        if (enumName != null) {
            try {
                return ProductSize.valueOf(enumName);
            } catch (IllegalArgumentException e) {
                log.warn("无法找到ProductSize枚举: {}", enumName);
            }
        }

        // 不区分大小写匹配
        for (Map.Entry<String, String> entry : sizeMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmedName)) {
                try {
                    return ProductSize.valueOf(entry.getValue());
                } catch (IllegalArgumentException e) {
                    log.warn("无法找到ProductSize枚举: {}", entry.getValue());
                }
            }
        }

        // 尝试直接转换为枚举
        try {
            return ProductSize.valueOf(trimmedName.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            log.debug("无法将 '{}' 转换为ProductSize枚举", originalName);
        }

        return ProductSize.UNKNOWN;
    }

    /**
     * 重新加载配置文件（用于动态更新映射关系）
     */
    public void reloadMapping() {
        sizeMap.clear();
        sortedKeys.clear();
        loadMappingFromFile();
        log.info("产品型号映射已重新加载");
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
        return sizeMap.containsKey(trimmedName) ||
               sizeMap.entrySet().stream()
                       .anyMatch(entry -> entry.getKey().equalsIgnoreCase(trimmedName));
    }

    /**
     * 获取所有映射关系（用于调试或展示）
     *
     * @return 所有映射关系的副本
     */
    public Map<String, String> getAllMappings() {
        return new HashMap<>(sizeMap);
    }
}

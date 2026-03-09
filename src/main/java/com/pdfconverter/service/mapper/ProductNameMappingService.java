package com.pdfconverter.service.mapper;

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
import java.util.HashMap;
import java.util.Map;

/**
 * 产品名称映射服务
 * 用于将从订单中提取的原始产品名称映射为标准的产品名称
 * 映射关系从配置文件中读取，便于后期维护
 */
@Service
public class ProductNameMappingService {

    private static final Logger log = LoggerFactory.getLogger(ProductNameMappingService.class);

    /**
     * 映射配置文件路径（默认值）
     */
    @Value("${app.mapping.product-name-file:product-name-mapping.properties}")
    private String mappingFilePath;

    /**
     * 产品名称映射表
     * Key: 从订单中提取的原始名称
     * Value: 对应的标准产品名称
     */
    private Map<String, String> productNameMap;

    @PostConstruct
    public void init() {
        productNameMap = new HashMap<>();
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
                log.warn("产品名称映射配置文件不存在: {}", mappingFilePath);
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
                            productNameMap.put(key, value);
                            log.debug("加载映射: {} -> {}", key, value);
                        }
                    } else {
                        log.warn("配置文件第 {} 行格式错误，跳过: {}", lineNumber, line);
                    }
                }
            }

            log.info("成功加载产品名称映射，共 {} 条映射关系", productNameMap.size());

        } catch (IOException e) {
            log.error("加载产品名称映射配置文件失败: {}", mappingFilePath, e);
        }
    }

    /**
     * 根据原始名称查询对应的标准产品名称
     *
     * @param originalName 从订单中提取的原始名称
     * @return 对应的标准产品名称，如果未找到映射则返回原始名称
     */
    public String getStandardName(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return originalName;
        }

        // 精确匹配
        String standardName = productNameMap.get(originalName.trim());
        if (standardName != null) {
            return standardName;
        }

        // 不区分大小写匹配
        for (Map.Entry<String, String> entry : productNameMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(originalName.trim())) {
                return entry.getValue();
            }
        }

        // 如果没有找到映射，返回原始名称
        return originalName;
    }

    /**
     * 重新加载配置文件（用于动态更新映射关系）
     */
    public void reloadMapping() {
        productNameMap.clear();
        loadMappingFromFile();
        log.info("产品名称映射已重新加载");
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
        return productNameMap.containsKey(trimmedName) ||
               productNameMap.entrySet().stream()
                       .anyMatch(entry -> entry.getKey().equalsIgnoreCase(trimmedName));
    }

    /**
     * 获取所有映射关系（用于调试或展示）
     *
     * @return 所有映射关系的副本
     */
    public Map<String, String> getAllMappings() {
        return new HashMap<>(productNameMap);
    }
}

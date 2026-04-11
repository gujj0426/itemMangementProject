package com.pdfconverter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 属性标签语义配置
 * 从 attribute-label-semantics.json 加载配置
 * 定义每个属性标签的语义和提取规则
 */
@Component
public class AttributeSemanticsConfig {
    
    private static final Logger log = LoggerFactory.getLogger(AttributeSemanticsConfig.class);
    
    private static final String CONFIG_FILE = "attribute-label-semantics.json";
    
    // 标签名称 -> 语义配置
    private Map<String, LabelSemantic> labelSemanticMap;
    
    @PostConstruct
    public void init() {
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    public void loadConfig() {
        long startTime = System.currentTimeMillis();
        log.info("开始加载属性标签语义配置：{}", CONFIG_FILE);
        
        try {
            // 初始化Map
            labelSemanticMap = new ConcurrentHashMap<>();
            
            // 读取JSON文件
            ClassPathResource resource = new ClassPathResource(CONFIG_FILE);
            if (!resource.exists()) {
                log.error("配置文件不存在：{}", CONFIG_FILE);
                return;
            }
            
            ObjectMapper mapper = new ObjectMapper();
            try (InputStream is = resource.getInputStream()) {
                JsonConfig config = mapper.readValue(is, JsonConfig.class);
                
                if (config.labelSemantics == null || config.labelSemantics.isEmpty()) {
                    log.warn("配置文件中未找到标签语义定义");
                    return;
                }
                
                // 解析并构建映射
                for (LabelSemantic semantic : config.labelSemantics) {
                    String labelName = semantic.labelName;
                    
                    if (labelName == null) {
                        log.warn("无效的标签语义配置：labelName为空");
                        continue;
                    }
                    
                    // 添加到映射
                    labelSemanticMap.put(labelName, semantic);
                    log.debug("加载标签 [{}] 的语义配置，类型: {}", labelName, semantic.attributeType);
                }
                
                long endTime = System.currentTimeMillis();
                log.info("属性标签语义配置加载完成，共 {} 个标签，耗时 {}ms",
                         labelSemanticMap.size(), endTime - startTime);
            }
            
        } catch (Exception e) {
            log.error("加载配置文件失败：" + CONFIG_FILE, e);
            throw new RuntimeException("Failed to load attribute semantics config", e);
        }
    }
    
    /**
     * 获取标签的语义配置
     * 
     * @param labelName 标签名称
     * @return 语义配置，如果未找到返回null
     */
    public LabelSemantic getSemantic(String labelName) {
        return labelSemanticMap.get(labelName);
    }
    
    /**
     * 获取标签的属性类型
     * 
     * @param labelName 标签名称
     * @return 属性类型，如果未找到返回null
     */
    public String getAttributeType(String labelName) {
        LabelSemantic semantic = labelSemanticMap.get(labelName);
        return semantic != null ? semantic.attributeType : null;
    }
    
    /**
     * 检查标签是否存在
     * 
     * @param labelName 标签名称
     * @return true如果存在
     */
    public boolean containsLabel(String labelName) {
        return labelSemanticMap.containsKey(labelName);
    }
    
    /**
     * 获取映射文件路径
     * 
     * @param labelName 标签名称
     * @return 映射文件路径，如果未找到返回null
     */
    public String getMappingFile(String labelName) {
        LabelSemantic semantic = labelSemanticMap.get(labelName);
        return semantic != null ? semantic.mappingFile : null;
    }
    
    /**
     * 获取提取模式（正则表达式）
     * 
     * @param labelName 标签名称
     * @return 提取模式，如果未找到返回null
     */
    public String getExtractionPattern(String labelName) {
        LabelSemantic semantic = labelSemanticMap.get(labelName);
        return semantic != null ? semantic.extractionPattern : null;
    }
    
    /**
     * 获取复合属性的值模式
     * 
     * @param labelName 标签名称
     * @return 值模式，如果未找到返回null
     */
    public String getValuePattern(String labelName) {
        LabelSemantic semantic = labelSemanticMap.get(labelName);
        return semantic != null ? semantic.valuePattern : null;
    }
    
    /**
     * 获取复合属性的分组映射
     * 
     * @param labelName 标签名称
     * @return 分组映射，如果未找到返回null
     */
    public Map<String, String> getGroupMapping(String labelName) {
        LabelSemantic semantic = labelSemanticMap.get(labelName);
        return semantic != null ? semantic.groupMapping : null;
    }
    
    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        log.info("重新加载属性标签语义配置...");
        loadConfig();
    }
    
    /**
     * 获取已加载的标签数量
     */
    public int getLoadedLabelCount() {
        return labelSemanticMap != null ? labelSemanticMap.size() : 0;
    }
    
    /**
     * JSON配置结构
     */
    private static class JsonConfig {
        public List<LabelSemantic> labelSemantics;
    }
    
    /**
     * 标签语义类
     */
    public static class LabelSemantic {
        public String labelName;
        public String attributeType;
        public String description;
        public String mappingFile;
        public String extractionPattern;
        public String valuePattern;
        public Map<String, String> groupMapping;
        
        @Override
        public String toString() {
            return "LabelSemantic{" +
                   "labelName='" + labelName + '\'' +
                   ", attributeType='" + attributeType + '\'' +
                   ", description='" + description + '\'' +
                   '}';
        }
    }
}

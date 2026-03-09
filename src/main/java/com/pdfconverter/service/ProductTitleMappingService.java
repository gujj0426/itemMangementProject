package com.pdfconverter.service;

import com.pdfconverter.config.ProductMappingConfig;
import com.pdfconverter.model.ProductAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 商品映射服务
 * 提供商品标题到主商品类型的映射
 */
@Service
public class ProductTitleMappingService {

    private static final Logger log = LoggerFactory.getLogger(ProductTitleMappingService.class);

    @Resource
    private ProductMappingConfig productMappingConfig;

    /**
     * 根据商品标题获取映射信息
     *
     * @param title 商品标题
     * @return ProductAttribute 包含映射后的信息，如果未匹配返回null
     */
    public ProductMappingConfig.ProductTitleMappingRule getMappingByTitle(String title) {
        ProductMappingConfig.ProductTitleMappingRule rule = productMappingConfig.findMatchingRule(title);
        if (rule == null) {
            log.debug("未找到匹配的商品标题映射规则: {}", title);
            return null;
        }
        return rule;
    }

    /**
     * 根据商品标题解析出所有可能的映射信息
     *
     * @param title 商品标题
     * @return ProductAttribute 包含所有映射信息
     */
    public ProductAttribute parseProductFromTitle(String title) {
        ProductMappingConfig.ProductTitleMappingRule rule = getMappingByTitle(title);
        ProductAttribute attribute = new ProductAttribute();
        if (rule != null) {
            // 仅保留主商品信息
            attribute.setProductType(rule.getMainProductType());
        }
        return attribute;
    }

    /**
     * 重新加载配置文件
     */
    public void reloadConfig() {
        log.info("重新加载商品映射配置...");
        productMappingConfig.loadConfig();
    }

    /**
     * 获取配置统计信息
     */
    public String getConfigStats() {
        return String.format("商品标题映射规则: %d 条",
            productMappingConfig.getTitleMappingRules().size());
    }

    /**
     * 获取所有标题映射规则
     */
    public List<ProductMappingConfig.ProductTitleMappingRule> getAllTitleMappingRules() {
        return productMappingConfig.getTitleMappingRules();
    }
}

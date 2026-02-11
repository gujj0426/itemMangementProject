package com.pdfconverter.service;

import com.pdfconverter.config.ProductMappingConfig;
import com.pdfconverter.model.ProductAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品映射服务
 * 提供商品标题到主商品类型的映射，以及主商品到附属商品的映射
 */
@Service
public class ProductMappingService {

    private static final Logger log = LoggerFactory.getLogger(ProductMappingService.class);

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
     * 根据主商品类型获取附属商品列表
     *
     * @param mainProductType 主商品类型
     * @param title 标题（用于判断条件）
     * @return 附属商品中文名称列表
     */
    public List<String> getAccessoryProducts(String mainProductType, String title) {
        List<String> accessories = new ArrayList<>();

        // 获取匹配的附属商品规则
        List<ProductMappingConfig.AccessoryMappingRule> rules =
            productMappingConfig.findAccessoryRules(mainProductType);

        for (ProductMappingConfig.AccessoryMappingRule rule : rules) {
            // 检查条件是否满足
            if (checkCondition(rule.getCondition(), title)) {
                String accessoryName = rule.getAccessoryChineseName();
                if (accessoryName != null && !accessoryName.isEmpty()) {
                    // 处理可能包含"则"的情况，提取商品名称
                    String[] parts = accessoryName.split("则");
                    if (parts.length > 1) {
                        accessories.add(parts[1].trim());
                    } else {
                        accessories.add(accessoryName.trim());
                    }
                }
            }
        }

        if (!accessories.isEmpty()) {
            log.info("主商品 {} 匹配到附属商品: {}", mainProductType, accessories);
        }

        return accessories;
    }

    /**
     * 检查条件是否满足
     *
     * @param condition 条件描述
     * @param title 商品标题
     * @return true表示条件满足，false表示不满足
     */
    private boolean checkCondition(String condition, String title) {
        if (condition == null || condition.trim().isEmpty()) {
            // 无条件限制，默认满足
            return true;
        }

        String lowerTitle = title != null ? title.toLowerCase() : "";

        // 检查"没有BOX"条件
        if (condition.toLowerCase().contains("没有box")) {
            // 检查标题中是否包含box
            return !lowerTitle.contains("box");
        }

        // 检查"无附加"条件
        if (condition.toLowerCase().contains("无附加")) {
            return false;
        }

        // 其他条件默认满足
        return true;
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
            String accessoryRule = rule.getAccessoryRule();
            if (accessoryRule != null && !accessoryRule.isEmpty() && !accessoryRule.contains("无附加")) {
                // 解析附属商品规则并添加
                parseAndAddAccessory(attribute, accessoryRule);
            }
        }
        return attribute;
    }

    /**
     * 解析并添加附属商品
     */
    private void parseAndAddAccessory(ProductAttribute attribute, String accessoryRule) {
        // 解析类似 "商品中没有BOX，则附加一个长方形礼盒" 的规则
        if (accessoryRule.contains("则附加")) {
            String[] parts = accessoryRule.split("则附加");
            if (parts.length > 1) {
                String accessoryName = parts[1].trim();
                // 去掉"一个"等量词
                accessoryName = accessoryName.replaceFirst("一个", "");
                attribute.addAdditionalProductName(accessoryName);
            }
        }
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
        return String.format("商品标题映射规则: %d 条，附属商品规则: %d 条",
            productMappingConfig.getTitleMappingRules().size(),
            productMappingConfig.getAccessoryRules().size());
    }

    /**
     * 获取所有标题映射规则
     */
    public List<ProductMappingConfig.ProductTitleMappingRule> getAllTitleMappingRules() {
        return productMappingConfig.getTitleMappingRules();
    }

    /**
     * 获取所有附属商品规则
     */
    public List<ProductMappingConfig.AccessoryMappingRule> getAllAccessoryRules() {
        return productMappingConfig.getAccessoryRules();
    }
}

package com.pdfconverter.service;

import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 商品标题识别服务
 * 用于通过商品标题识别商品类别
 * 配置从 properties 文件读取，支持动态更新
 */
@Service
public class ProductTitleRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(ProductTitleRecognitionService.class);

    @Resource
    private ProductTitleConfigManager configManager;

    /**
     * 通过商品标题识别商品类型
     *
     * @param title 商品标题
     * @return 商品类型，如果无法识别则返回"未知商品"
     */
    public ItemDetail identifyProductType(String title) {
        ItemDetail itemDetail = new ItemDetail();
        if (title == null || title.trim().isEmpty()) {
            log.warn("商品标题为空，无法识别商品类型");
            return itemDetail;
        }

        String titleLower = title.toLowerCase();
        // 遍历所有映射规则
        Map<String, List<String>> rules = configManager.getMappingRules();

        for (Map.Entry<String, List<String>> entry : rules.entrySet()) {
            String ruleKey = entry.getKey();
            List<String> keywords = entry.getValue();

            // 检查标题中是否包含该规则的所有关键词
            boolean allKeywordsMatched = true;
            for (String keyword : keywords) {
                if (!titleLower.contains(keyword)) {
                    allKeywordsMatched = false;
                    break;
                }
            }

            if (allKeywordsMatched) {
                itemDetail.setMainProductFlg("Y");
                itemDetail.setOrderType(configManager.getProductType(ruleKey));
                return itemDetail;
            }
        }

        log.warn("无法识别商品类型, 标题: {}", title);
        return itemDetail;
    }

    /**
     * 检查标题是否包含指定商品类型的关键词
     *
     * @param title 商品标题
     * @param productType 商品类型
     * @return true表示匹配，false表示不包含
     */
    public boolean isProductType(String title, String productType) {
        String identifiedType = identifyProductType(title);
        return identifiedType.equals(productType);
    }

    /**
     * 获取商品标题的详细匹配信息
     *
     * @param title 商品标题
     * @return 匹配信息字符串
     */
    public String getMatchInfo(String title) {
        String productType = identifyProductType(title);
        if (productType.equals(ProductTitleMapping.PRODUCT_UNKNOWN)) {
            return String.format("标题: [%s] 未匹配到已知商品类型", title);
        }
        return String.format("标题: [%s] 匹配到商品类型: [%s]", title, productType);
    }

    /**
     * 根据规则键获取对应的内部常量
     *
     * @param ruleKey 规则键
     * @return 内部常量名称
     */
    public String getInternalTypeByRule(String ruleKey) {
        return configManager.getInternalType(ruleKey);
    }

    /**
     * 重新加载配置文件
     * 当配置文件修改后，可以调用此方法重新加载
     */
    public void reloadConfig() {
        log.info("重新加载商品标题配置...");
        configManager.reloadConfig();
    }

    /**
     * 获取当前配置统计信息
     */
    public String getConfigStats() {
        return configManager.getConfigStats();
    }

    /**
     * 获取所有配置的规则
     */
    public Map<String, List<String>> getAllRules() {
        return configManager.getMappingRules();
    }
}

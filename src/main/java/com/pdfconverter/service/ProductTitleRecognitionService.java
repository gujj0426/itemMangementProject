package com.pdfconverter.service;

import com.pdfconverter.config.ProductTitleRecognitionConfig;
import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductName;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 商品标题识别服务
 * 用于通过商品标题识别商品类别和商品名称
 * 配置从 product-title-recognition-rules.properties 文件读取，支持动态更新
 */
@Service
public class ProductTitleRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(ProductTitleRecognitionService.class);

    @Resource
    private ProductTitleRecognitionConfig productTitleRecognitionConfig;

    /**
     * 通过商品标题识别商品类型和商品名称
     * 使用 product-title-recognition-rules.properties 中的规则进行关键字匹配
     * @param title 商品标题
     * @return ItemDetail 包含识别出的商品名称和商品类型，如果无法识别则返回空 ItemDetail
     */
    public ItemDetail identifyProductType(String title) {
        ItemDetail itemDetail = new ItemDetail();
        if (title == null || title.trim().isEmpty()) {
            log.warn("商品标题为空，无法识别商品类型");
            return itemDetail;
        }

        // 使用 ProductTitleRecognitionConfig 的 findMatchingRule 方法进行关键字匹配
        ProductTitleRecognitionConfig.ProductTitleRecognitionRule rule =
            productTitleRecognitionConfig.findMatchingRule(title);

        if (rule != null) {
            itemDetail.setMainProductFlg(true);
            // 设置是否组合产品标识
            itemDetail.setIsComposite(rule.getIsComposite());
            // 设置产品大类（OrderType枚举）
            itemDetail.setOrderType(OrderType.fromOrderTypeCode(rule.getProductCategory()));
            // 设置产品名称（ProductName枚举）
            itemDetail.setProductName(ProductName.fromNameCode(rule.getProductName()));
            log.debug("成功识别商品: 标题=[{}], 产品名称=[{}], 产品大类=[{}], 是否组合产品=[{}]",
                title, rule.getProductName(), rule.getProductCategory(), rule.getIsComposite());
        } else {
            log.warn("无法识别商品, 标题: {}", title);
        }

        return itemDetail;
    }

    /**
     * 重新加载配置文件
     * 当配置文件修改后，可以调用此方法重新加载
     */
    public void reloadConfig() {
        log.info("重新加载商品标题识别配置...");
        productTitleRecognitionConfig.loadConfig();
    }

    /**
     * 获取当前配置统计信息
     */
    public String getConfigStats() {
        List<ProductTitleRecognitionConfig.ProductTitleRecognitionRule> rules =
            productTitleRecognitionConfig.getRecognitionRules();
        return String.format("当前已加载 %d 条商品标题识别规则", rules.size());
    }

    /**
     * 获取所有配置的规则
     */
    public List<ProductTitleRecognitionConfig.ProductTitleRecognitionRule> getAllRules() {
        return productTitleRecognitionConfig.getRecognitionRules();
    }
}

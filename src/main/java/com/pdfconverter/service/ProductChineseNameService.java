package com.pdfconverter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 商品中文名称映射服务
 * 提供根据主商品类型、型号、颜色、产品变量查询中文名称的功能
 */
@Service
public class ProductChineseNameService {

    private static final Logger log = LoggerFactory.getLogger(ProductChineseNameService.class);

    @Resource
    private ProductChineseNameConfig productChineseNameConfig;

    /**
     * 根据主商品类型、型号、颜色、产品变量获取中文名称
     *
     * @param mainProductType 主商品类型（必填）
     * @param model 型号（可选，可为null或空）
     * @param color 颜色（可选，可为null或空）
     * @param productVariable 产品变量（可选，可为null或空）
     * @return 匹配的中文名称，未匹配返回null
     */
    public String getChineseName(String mainProductType, String model, String color, String productVariable) {
        if (mainProductType == null || mainProductType.trim().isEmpty()) {
            log.warn("主商品类型为空，无法查询中文名称");
            return null;
        }

        String chineseName = productChineseNameConfig.findChineseName(
            mainProductType.trim(),
            model != null ? model.trim() : null,
            color != null ? color.trim() : null,
            productVariable != null ? productVariable.trim() : null
        );

        return chineseName;
    }

    /**
     * 根据主商品类型获取中文名称（不限制其他条件）
     *
     * @param mainProductType 主商品类型
     * @return 匹配的中文名称，未匹配返回null
     */
    public String getChineseName(String mainProductType) {
        return getChineseName(mainProductType, null, null, null);
    }

    /**
     * 根据主商品类型和型号获取中文名称
     *
     * @param mainProductType 主商品类型
     * @param model 型号
     * @return 匹配的中文名称，未匹配返回null
     */
    public String getChineseName(String mainProductType, String model) {
        return getChineseName(mainProductType, model, null, null);
    }

    /**
     * 根据主商品类型获取所有可能的中文名称
     *
     * @param mainProductType 主商品类型
     * @return 中文名称列表
     */
    public List<String> getAllChineseNames(String mainProductType) {
        return productChineseNameConfig.findAllChineseNamesByType(mainProductType);
    }

    /**
     * 检查是否存在指定主商品类型的映射
     *
     * @param mainProductType 主商品类型
     * @return true表示存在，false表示不存在
     */
    public boolean hasMapping(String mainProductType) {
        List<String> names = getAllChineseNames(mainProductType);
        return names != null && !names.isEmpty();
    }

    /**
     * 重新加载配置文件
     */
    public void reloadConfig() {
        log.info("重新加载商品中文名称映射配置...");
        productChineseNameConfig.loadConfig();
    }

    /**
     * 获取配置统计信息
     */
    public String getConfigStats() {
        return String.format("商品中文名称映射规则: %d 条",
            productChineseNameConfig.getChineseNameRules().size());
    }

    /**
     * 获取所有配置规则
     */
    public List<ProductChineseNameConfig.ProductChineseNameRule> getAllRules() {
        return productChineseNameConfig.getChineseNameRules();
    }
}

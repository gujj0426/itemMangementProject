package com.pdfconverter.service;

import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;

/**
 * 属性提取器
 * 提供从文本中提取尺寸、颜色、月份等产品属性的服务方法
 * 统一处理所有店铺的动态属性提取逻辑，避免代码重复
 */
@Component
public class AttributeExtractor {

    private static final Logger log = LoggerFactory.getLogger(AttributeExtractor.class);

    @Resource
    private ColorMapper colorMapper;

    @Resource
    private ProductVariableMapper productVariableMapper;

    /**
     * 从 value 中提取 Size（如 L, S, M, XL）
     *
     * @param value 包含尺寸信息的字符串
     * @return 提取的尺寸代码（L/S/M/XL），如果未找到则返回 null
     */
    public ProductSize extractSizeFromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String upperValue = value.toUpperCase();
        if (upperValue.contains("XL")) return ProductSize.XL;
        if (upperValue.contains("L")) return ProductSize.L;
        if (upperValue.contains("M")) return ProductSize.M;
        if (upperValue.contains("S")) return ProductSize.S;

        return null;
    }

    /**
     * 从 value 中提取 Color（如 Gold, Silver, Rose Gold, Red, Yellow, White, Blue, Pink）
     * 使用 ColorMapper 服务进行颜色映射，支持配置化管理
     *
     * @param value 包含颜色信息的字符串
     * @return 提取的中文颜色名称，如果未找到则返回 null
     */
    public ProductColor extractColorFromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        // 使用 ColorMapper 服务获取中文颜色
        ProductColor color= colorMapper.getColorFromValue(value);

        // 如果找到映射，返回中文颜色；否则返回 null
        return color;
    }

    /**
     * 根据月份value返回对应的中文月份
     *
     * @param value 月份value（如 "12 Topaz Snow"）
     * @return 对应的中文月份（如 "十二月"），如果未找到则返回原始value
     *
     * 示例：
     * getMonthFromValue("12 Topaz Snow") → "十二月"
     * getMonthFromValue("1 Garnet Glow") → "一月"
     * getMonthFromValue("6 Pearl Blossom") → "六月"
     */
    public String getMonthFromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }

        // 使用 MonthMapper 服务获取中文月份
        String chineseMonth = productVariableMapper.getMonthFromValue(value);

        // 如果找到映射，返回中文月份；否则返回原始value
        return chineseMonth != null ? chineseMonth : value;
    }

    /**
     * 统一的属性提取方法
     * 从动态属性Map中提取所有标准化的属性（Size、Color、Month等）
     *
     * @param dynamicAttrsMap 动态属性Map（key-value形式）
     * @param productTypes 商品类型集合
     * @return 包含所有提取属性的ItemDetail对象
     */
    public ItemDetail extractAllAttributes(Map<String, String> dynamicAttrsMap, Set<String> productTypes) {
        ItemDetail attr = new ItemDetail();

        for (Map.Entry<String, String> entry : dynamicAttrsMap.entrySet()) {
            extractSize(entry, attr);
            extractColor(entry, attr);
            extractMonth(entry, attr, productTypes);
        }

        return attr;
    }

    /**
     * 提取尺寸信息
     */
    private void extractSize(Map.Entry<String, String> entry, ItemDetail attr) {
        String key = entry.getKey().toLowerCase();
        String value = entry.getValue();

        if (key.contains("size")) {
            // 直接提取尺寸
            String productSize = extractSizeFromValue(value);
            if (productSize != null) {
                attr.setProductSize(productSize);
            }
        }

        // 处理组合字段如 "Size and Color"
        if (key.contains("size") && key.contains("color") && value.contains("_")) {
            String[] parts = value.split("_");
            if (parts.length > 1) {
                attr.setProductSize(parts[1].trim());
            }
        }
    }

    /**
     * 提取颜色信息
     */
    private void extractColor(Map.Entry<String, String> entry, ItemDetail attr) {
        String key = entry.getKey().toLowerCase();
        String value = entry.getValue();

        if (key.contains("color")) {
            // 直接提取颜色
            String productColor = extractColorFromValue(value);
            if (productColor != null) {
                attr.setProductColor(productColor);
            }
        }

        // 处理组合字段如 "Size and Color"
        if (key.contains("size") && key.contains("color") && value.contains("_")) {
            String[] parts = value.split("_");
            attr.setProductColor(extractColorFromValue(parts[0]));
        }

        // 处理 "Size and Color" 格式（例如 "Gold_L"）
        if (value.contains("_")) {
            String colorPart = value.split("_")[0];
            attr.setProductColor(extractColorFromValue(colorPart));
        }
    }

    /**
     * 提取月份信息（仅针对花卉心形相盒吊坠等商品）
     */
    private void extractMonth(Map.Entry<String, String> entry, ItemDetail attr, Set<String> productTypes) {
        String key = entry.getKey().toLowerCase();
        String value = entry.getValue();

        // 只有特定类型的商品才需要提取月份
        if (productTypes == null || productTypes.isEmpty()) {
            return;
        }

        // 检查是否是花卉类商品
        boolean isFlowerProduct = productTypes.stream()
            .anyMatch(type -> type.contains("花卉") || type.contains("Flower") || type.contains("Birth Flower"));

        if (!isFlowerProduct) {
            return;
        }

        if (key.contains("month") || key.contains("birth flower")) {
            String month = getMonthFromValue(value);
            if (month != null && !month.equals(value)) {
                // 将月份信息存入productVariable
                String currentProductVar = attr.getProductVariable();
                if (currentProductVar == null || currentProductVar.isEmpty()) {
                    attr.setProductVariable(month);
                } else {
                    attr.setProductVariable(currentProductVar + " " + month);
                }
            }
        }
    }

    /**
     * 从动态属性文本中解析属性Map
     * 用于将多行动态属性文本转换为key-value格式
     *
     * @param lines 动态属性文本行数组
     * @return 属性Map
     */
    public java.util.Map<String, String> parseDynamicAttributes(String[] lines) {
        java.util.Map<String, String> attrs = new java.util.HashMap<>();

        if (lines == null || lines.length == 0) {
            return attrs;
        }

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // 尝试按冒号分割
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                attrs.put(key, value);
            } else {
                // 如果没有冒号，整行作为value
                attrs.put(line, line);
            }
        }

        return attrs;
    }
}

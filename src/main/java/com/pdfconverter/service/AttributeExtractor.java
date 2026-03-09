package com.pdfconverter.service;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.constant.ProductVariable;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import com.pdfconverter.model.ProductAttribute;
import com.pdfconverter.service.mapper.ColorMapperService;
import com.pdfconverter.service.mapper.ProductVariableMapperService;
import com.pdfconverter.util.PersonalizationParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    private ColorMapperService colorMapper;

    @Resource
    private ProductVariableMapperService productVariableMapper;

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
        if (upperValue.contains("S/M")) return ProductSize.SM;

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
        // 获取颜色名称，返回标准化名称
        ProductColor color = null;
        color.extractFromText(value);
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
     * 使用 ProductAttribute 补充 ItemDetail 的属性信息
     * 从 ProductAttribute 中提取型号、颜色、产品变量、设计风格、字体等信息，补充到 ItemDetail 中
     *
     * @param dynamicAttrsMap 动态属性Map（用于额外提取产品变量等信息）
     * @param productAttribute 产品属性对象，包含已提取的属性
     * @param itemDetail 需要补充属性的商品详情对象
     * @return 补充属性后的 ItemDetail 对象
     */
    public ItemDetail extractAllAttributes(Map<String, String> dynamicAttrsMap,
            ProductAttribute productAttribute, ItemDetail itemDetail) {
        // 补充型号（如果 productAttribute 中有且 itemDetail 中为空）
        if (productAttribute.getSize() != null
                && productAttribute.getSize() != ProductSize.UNKNOWN) {
            itemDetail.setProductSize(productAttribute.getSize());
        }

        // 补充颜色（如果 productAttribute 中有且 itemDetail 中为空）
        if (productAttribute.getColor() != null
                && productAttribute.getColor() != ProductColor.UNKNOWN) {
            itemDetail.setProductColor(productAttribute.getColor());
        }

        // 补充产品变量（优先使用 productAttribute 中的）
        if (productAttribute.getProductVariable() != null
                && !productAttribute.getProductVariable().isEmpty()) {
            itemDetail.setProductVariable(ProductVariable.valueOf(productAttribute.getProductVariable()));
        }

        // 补充设计风格
        if (productAttribute.getStyle() != null && !productAttribute.getStyle().isEmpty()) {
            itemDetail.setStyle(productAttribute.getStyle());
        }

        // 补充字体
        if (productAttribute.getFont() != null && !productAttribute.getFont().isEmpty()) {
            itemDetail.setFont(productAttribute.getFont());
        }

        // 如果动态属性中有月份信息且是花卉类商品，继续提取月份
        if (dynamicAttrsMap != null && !dynamicAttrsMap.isEmpty()) {
            Set<String> productTypes = new HashSet<>();
            if (itemDetail.getOrderType() != null) {
                productTypes.add(itemDetail.getOrderType().getDisplayName());
            }

            for (Map.Entry<String, String> entry : dynamicAttrsMap.entrySet()) {
                extractMonth(entry, itemDetail, productTypes);
            }
        }

        return itemDetail;
    }

    /**
     * 提取尺寸信息
     */
    private void extractSize(Map.Entry<String, String> entry, ItemDetail attr) {
        String key = entry.getKey().toLowerCase();
        String value = entry.getValue();

        if (key.contains("size")) {
            // 直接提取尺寸
            String productSize = String.valueOf(extractSizeFromValue(value));
            if (productSize != null) {
                attr.setProductSize(ProductSize.valueOf(productSize));
            }
        }

        // 处理组合字段如 "Size and Color"
        if (key.contains("size") && key.contains("color") && value.contains("_")) {
            String[] parts = value.split("_");
            if (parts.length > 1) {
                attr.setProductSize(ProductSize.valueOf(parts[1].trim()));
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
            String productColor = String.valueOf(extractColorFromValue(value));
            if (productColor != null) {
                attr.setProductColor(ProductColor.valueOf(productColor));
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
                String currentProductVar = String.valueOf(attr.getProductVariable());
                if (currentProductVar == null || currentProductVar.isEmpty()) {
                    attr.setProductVariable(ProductVariable.valueOf(month));
                } else {
                    attr.setProductVariable(ProductVariable.valueOf(currentProductVar + " " + month));
                }
            }
        }
    }

    /**
     * 解析 Voro 动态信息
     * 从动态属性中提取型号、颜色、产品类型等信息，并确定附属商品列表
     *
     * @param dynamicAttrsMap 动态属性Map
     * @param mainItemDetail 主商品详情
     * @param personalization 个性化内容
     * @return 包含所有提取属性的 ProductAttribute 对象
     */
    public ProductAttribute parseVoroDynamicInfo(Map<String, String> dynamicAttrsMap, ItemDetail mainItemDetail, String personalization) {
        ProductAttribute productAttribute = new ProductAttribute();

        // ========== 第一阶段：收集信息（只收集，不添加） ==========

        // 1. 提取型号、颜色
        extractSizeAndColor(dynamicAttrsMap, productAttribute);

        // 2. 从动态属性中检测到的产品类型（用Set去重）
        Set<OrderType> detectedTypes = detectProductTypesFromDynamicAttrs(dynamicAttrsMap);

        // 3. 检测包装盒类型
        String boxVariable = detectBoxVariable(dynamicAttrsMap);
        if (boxVariable != null) {
            detectedTypes.add(OrderType.BOX);
            productAttribute.setProductVariable(boxVariable);
        }

        // ========== 第二阶段：确定附属商品列表（统一决策） ==========

        Set<OrderType> accessoryTypes = determineAccessoryTypes(
                mainItemDetail, detectedTypes);

        // ========== 第三阶段：统一添加附属商品（只在这里添加一次） ==========

        for (OrderType accessoryType : accessoryTypes) {
            productAttribute.addAdditionalProductName(accessoryType.getDisplayName());
        }

        // ========== 第四阶段：设置其他属性 ==========

        productAttribute.setStyle(PersonalizationParserUtil.extractStyle(personalization));
        productAttribute.setFont(PersonalizationParserUtil.extractFont(personalization));

        return productAttribute;
    }

    /**
     * 提取型号和颜色到 ProductAttribute
     */
    private void extractSizeAndColor(Map<String, String> dynamicAttrsMap,
            ProductAttribute productAttribute) {
        for (Map.Entry<String, String> entry : dynamicAttrsMap.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue();

            // 解析型号
            if (key.contains("size")) {
                productAttribute.setSize(extractSizeFromValue(value));
            }

            // 解析颜色
            if (key.contains("color") || key.contains("colour")
                    || key.contains("locket finish")) {
                productAttribute.setColor(extractColorFromValue(value));
            }

            // Size 和 Color 同字段
            if (key.contains("size") && key.contains("color") && value.contains("_")) {
                String[] parts = value.split("_");
                if (parts.length >= 2) {
                    productAttribute.setColor(extractColorFromValue(parts[0].trim()));
                    productAttribute.setSize(ProductSize.valueOf(parts[1].trim()));
                }
            }
        }
    }

    /**
     * 从动态属性中检测产品类型
     */
    private Set<OrderType> detectProductTypesFromDynamicAttrs(
            Map<String, String> dynamicAttrsMap) {
        Set<OrderType> detectedTypes = new LinkedHashSet<>();

        for (String value : dynamicAttrsMap.values()) {
            String valueLower = value.toLowerCase();

            if (valueLower.contains("cufflink") || valueLower.contains("cufflinks")) {
                detectedTypes.add(OrderType.CUFFLINK);
            }
            if (value.matches(".*Tie\\s{0,}Clip.*")) {
                detectedTypes.add(OrderType.TIE_CLIP);
            }
        }

        return detectedTypes;
    }

    /**
     * 检测包装盒变量
     */
    private String detectBoxVariable(Map<String, String> dynamicAttrsMap) {
        for (String value : dynamicAttrsMap.values()) {
            String valueLower = value.toLowerCase();

            if (valueLower.contains("oval box")) {
                return "Oval Box";
            } else if (valueLower.contains("square box")) {
                return "Square Box";
            } else if (valueLower.contains("box")) {
                return "Box";
            }
        }
        return null;
    }

    /**
     * 确定附属商品类型（核心逻辑）
     */
    private Set<OrderType> determineAccessoryTypes(ItemDetail mainItemDetail,
            Set<OrderType> detectedTypes) {
        Set<OrderType> accessoryTypes = new LinkedHashSet<>();

        boolean isComposite = mainItemDetail.getIsComposite() != null
                && mainItemDetail.getIsComposite();
        OrderType mainOrderType = mainItemDetail.getOrderType();

        if (!isComposite) {
            // ========== 非组合产品 ==========
            // 附属商品类别与主商品类别不同，才添加
            for (OrderType detectedType : detectedTypes) {
                if (mainOrderType != detectedType) {
                    accessoryTypes.add(detectedType);
                }
            }
        } else {
            // ========== 组合产品 ==========
            String mainOrderTypeCode = mainOrderType.getOrderTypeCode();

            if (mainOrderTypeCode != null && mainOrderTypeCode.contains(" and ")) {
                // 拆分组合产品类型
                String[] categoryCodes = mainOrderTypeCode.split(" and ");
                Set<OrderType> compositeTypes = new LinkedHashSet<>();

                for (String code : categoryCodes) {
                    OrderType type = OrderType.fromOrderTypeCode(code.trim());
                    if (type != OrderType.UNKNOWN) {
                        compositeTypes.add(type);
                    }
                }

                // 找到动态属性匹配的主产品类型
                OrderType matchedMainType = null;
                for (OrderType compositeType : compositeTypes) {
                    if (detectedTypes.contains(compositeType)) {
                        matchedMainType = compositeType;
                        break; // 找到一个匹配就停止
                    }
                }

                // 组合产品中未匹配的类型 -> 附属商品
                for (OrderType compositeType : compositeTypes) {
                    if (compositeType != matchedMainType) {
                        accessoryTypes.add(compositeType);
                    }
                }

                // 动态属性中不在组合产品中的类型 -> 附属商品（如包装盒）
                for (OrderType detectedType : detectedTypes) {
                    if (!compositeTypes.contains(detectedType)) {
                        accessoryTypes.add(detectedType);
                    }
                }

            } else {
                // 组合产品但不含 "and"，按非组合产品处理
                for (OrderType detectedType : detectedTypes) {
                    if (mainOrderType != detectedType) {
                        accessoryTypes.add(detectedType);
                    }
                }
            }
        }

        return accessoryTypes;
    }


}

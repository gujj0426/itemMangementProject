package com.pdfconverter.service;

import com.pdfconverter.config.AttributeSemanticsConfig;
import com.pdfconverter.config.ProductAttributeConfig;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.constant.ProductVariable;
import com.pdfconverter.model.ProductAttribute;
import com.pdfconverter.service.mapper.ColorMapperService;
import com.pdfconverter.service.mapper.ProductSizeMapperService;
import com.pdfconverter.service.mapper.ProductVariableMapperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 属性规则引擎
 * 根据产品名称和动态属性Map提取产品属性
 */
@Component
public class AttributeRuleEngine {
    
    private static final Logger log = LoggerFactory.getLogger(AttributeRuleEngine.class);
    
    @Resource
    private ProductAttributeConfig attributeConfig;
    
    @Resource
    private AttributeSemanticsConfig semanticsConfig;
    
    @Resource
    private ColorMapperService colorMapper;
    
    @Resource
    private ProductSizeMapperService sizeMapper;
    
    @Resource
    private ProductVariableMapperService variableMapper;
    
    /**
     * 根据产品名称提取属性
     *
     * @param listingId listing标识（用于区分同一ProductName下的不同listing）
     * @param productName 产品名称（用于兜底）
     * @param dynamicAttrs 动态属性Map（标签名 -> 值）
     * @return 产品属性对象
     */
    public ProductAttribute extractAttributes(String listingId, String productName, Map<String, String> dynamicAttrs) {
        ProductAttribute attribute = new ProductAttribute();

        if (productName == null || productName.isEmpty()) {
            log.warn("产品名称为空，无法提取属性");
            return attribute;
        }

        if (dynamicAttrs == null || dynamicAttrs.isEmpty()) {
            log.warn("动态属性为空，listingId={}，product={}", listingId, productName);
            return attribute;
        }

        log.debug("开始提取属性，listingId={}，product={}，动态属性数量：{}", listingId, productName, dynamicAttrs.size());

        // 获取listing或产品对应的属性标签配置
        List<ProductAttributeConfig.AttributeLabel> labels = attributeConfig.getLabelsForListing(listingId, productName);
        
        if (labels.isEmpty()) {
            log.warn("未找到属性标签配置，listingId={}，product={}，将尝试自动提取", listingId, productName);
            // 使用自动提取模式
            return autoExtractAttributes(listingId, productName, dynamicAttrs);
        }

        // 遍历动态属性，根据配置提取
        for (Map.Entry<String, String> entry : dynamicAttrs.entrySet()) {
            String labelName = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.isEmpty()) {
                continue;
            }

            // 查找对应的标签配置
            ProductAttributeConfig.AttributeLabel labelConfig = findLabelConfig(labels, labelName);
            if (labelConfig == null) {
                log.debug("标签 [{}] 未在配置中找到，跳过，listingId={}", labelName, listingId);
                continue;
            }
            
            // 获取标签语义
            String attributeType = labelConfig.attributeType;
            if (attributeType == null) {
                attributeType = semanticsConfig.getAttributeType(labelName);
            }
            
            if (attributeType == null) {
                log.warn("未找到标签 [{}] 的语义配置", labelName);
                continue;
            }

            log.info("【COLOR_ITEM_COMBO调试】标签 [{}] 匹配到配置，类型={}，值=[{}]，listingId={}", labelName, attributeType, value, listingId);
            
            // 根据属性类型提取
            try {
                switch (attributeType) {
                    case "COLOR":
                        extractColor(attribute, value, labelConfig);
                        break;
                    case "SIZE":
                        extractSize(attribute, value, labelConfig);
                        break;
                    case "PRODUCT_VARIABLE":
                        extractProductVariable(attribute, value, labelConfig);
                        break;
                    case "FONT":
                        extractFont(attribute, value);
                        break;
                    case "STYLE":
                        extractStyle(attribute, value);
                        break;
                    case "COMPOSITE":
                        extractComposite(attribute, value, labelConfig);
                        break;
                    case "ACCESSORY_ITEMS":
                        extractAccessoryItems(attribute, value, labelConfig);
                        break;
                    case "COLOR_WITH_ACCESSORY":
                        extractColorWithAccessory(attribute, value, labelConfig);
                        break;
                    case "COLOR_ITEM_COMBO":
                        extractColorItemCombo(attribute, value, labelConfig);
                        break;
                    case "COLOR_SIZE_ACCESSORY":
                        extractColorSizeAccessory(attribute, value, labelConfig);
                        break;
                    case "QUANTITY_WITH_SIZE":
                        extractQuantityWithSize(attribute, value, labelConfig);
                        break;
                    case "ENGRAVING_SIDES":
                        extractEngravingSides(attribute, value, labelConfig);
                        break;
                    case "PET_ENGRAVING_SIDES":
                        extractPetEngravingSides(attribute, value, labelConfig);
                        break;
                    case "BIRTH_FLOWER_STYLE":
                        extractBirthFlowerStyle(attribute, value, labelConfig);
                        break;
                    case "URN_ENGRAVING_OPTIONS":
                        extractUrnEngravingOptions(attribute, value, labelConfig);
                        break;
                    case "IGNORE":
                        // 忽略此属性，不做任何处理
                        log.debug("忽略属性 [{}]，类型为 IGNORE", labelName);
                        break;
                    default:
                        log.warn("未知的属性类型：{}", attributeType);
                }
            } catch (Exception e) {
                log.error("提取属性 [{}] 失败：{}", labelName, e.getMessage(), e);
            }
        }

        log.debug("属性提取完成：listingId={}, product={}，color={}, size={}, variable={}, font={}, style={}",
                 listingId, productName, attribute.getColor(), attribute.getSize(),
                 attribute.getProductVariable(), attribute.getFont(), attribute.getStyle());

        // listing 级默认产品变量（配置驱动）：仅当未从动态属性提取到变量时应用
        if (attribute.getProductVariable() == null || attribute.getProductVariable().isEmpty()) {
            String defaultVariable = attributeConfig.getDefaultVariableForListing(listingId);
            if (defaultVariable != null && !defaultVariable.isEmpty()) {
                attribute.setProductVariable(defaultVariable);
                log.debug("应用 listing 默认产品变量：listingId={}, variable={}", listingId, defaultVariable);
            }
        }

        return attribute;
    }
    
    /**
     * 自动提取属性（无配置时）
     * 根据标签名称关键字自动判断属性类型
     */
    private ProductAttribute autoExtractAttributes(String listingId, String productName, Map<String, String> dynamicAttrs) {
        log.debug("使用自动提取模式，listingId={}，product={}", listingId, productName);
        
        ProductAttribute attribute = new ProductAttribute();
        
        for (Map.Entry<String, String> entry : dynamicAttrs.entrySet()) {
            String labelName = entry.getKey().toLowerCase();
            String value = entry.getValue();
            
            if (value == null || value.isEmpty()) {
                continue;
            }
            
            // 根据标签名称关键字判断类型
            if (labelName.contains("color") || labelName.contains("colour") || 
                labelName.contains("finish")) {
                // 颜色
                ProductColor color = colorMapper.mapColor(value);
                if (color != null) {
                    attribute.setColor(color);
                }
            } else if (labelName.contains("size")) {
                // 尺寸
                ProductSize size = sizeMapper.mapSize(value);
                if (size != null) {
                    attribute.setSize(size);
                }
            } else if (labelName.contains("font")) {
                // 字体
                attribute.setFont(value);
            } else if (labelName.contains("style")) {
                // 样式
                attribute.setStyle(value);
            } else if (labelName.contains("month") || labelName.contains("birth")) {
                // 产品变量（月份）
                String variable = variableMapper.getMonthFromValue(value);
                if (variable != null) {
                    attribute.setProductVariable(variable);
                }
            }
        }
        
        return attribute;
    }
    
    /**
     * 在标签配置列表中查找匹配的标签
     */
    private ProductAttributeConfig.AttributeLabel findLabelConfig(
            List<ProductAttributeConfig.AttributeLabel> labels, String labelName) {
        // OCR容错：'ltem Options' 实际是 'Item Options' 的OCR误识别（小写l≠大写I）
        // 统一归一化为 "item options"（全小写）后比较，再返回原始配置名
        String normalizedInput = labelName.toLowerCase();
        if (normalizedInput.equals("ltem options")) {
            normalizedInput = "item options";
        }

        for (ProductAttributeConfig.AttributeLabel label : labels) {
            String configName = label.labelName;
            String normalizedConfig = configName.toLowerCase();
            if (normalizedConfig.equals("ltem options")) {
                normalizedConfig = "item options";
            }
            if (normalizedConfig.equals(normalizedInput)) {
                log.debug("findLabelConfig 命中：PDF标签=[{}] -> 配置标签=[{}]，类型={}", labelName, label.labelName, label.attributeType);
                return label;
            }
            // Etsy 同一 listing 可能交替出现 "Color and Size" / "Size and Color"
            if (isColorAndSizeLabel(normalizedInput) && isColorAndSizeLabel(normalizedConfig)) {
                log.debug("findLabelConfig 命中（Color/Size 词序容错）：PDF标签=[{}] -> 配置标签=[{}]，类型={}",
                        labelName, label.labelName, label.attributeType);
                return label;
            }
        }
        log.debug("findLabelConfig 未命中：PDF标签=[{}]，当前配置标签列表={}", labelName,
                labels.stream().map(l -> l.labelName).toArray());
        return null;
    }

    private static boolean isColorAndSizeLabel(String normalizedLabel) {
        return normalizedLabel.contains("color") && normalizedLabel.contains("size");
    }
    
    /**
     * 提取颜色属性
     */
    private void extractColor(ProductAttribute attribute, String value,
                             ProductAttributeConfig.AttributeLabel labelConfig) {
        // 如果有提取模式，先提取有效部分
        String extractedValue = extractByPattern(value, labelConfig.extractionPattern);
        if (extractedValue != null) {
            value = extractedValue;
        }

        // 特殊处理：标签名含"Box"（如 "Color Finish and Box"），且值含 "+"
        // 说明是颜色+附属商品混合格式（如 "Gold + Oval Box"），委托给 extractColorWithAccessory
        if (labelConfig != null &&
            labelConfig.labelName != null &&
            labelConfig.labelName.contains("Box") &&
            value.contains("+")) {
            log.debug("COLOR 标签[{}]含Box且值含+，委托给COLOR_WITH_ACCESSORY处理：{}", labelConfig.labelName, value);
            extractColorWithAccessory(attribute, value, labelConfig);
            return;
        }

        // 映射颜色
        ProductColor color = colorMapper.mapColor(value);
        if (color != null) {
            // 特殊处理：如果是硅胶绑带颜色，存储到siliconeBandColor字段
            if (labelConfig != null && "Silicone Rubber Holder Color".equals(labelConfig.labelName)) {
                attribute.setSiliconeBandColor(color);
                log.debug("提取硅胶绑带颜色：{} -> {}", value, color.getDisplayName());
            } else {
                attribute.setColor(color);
                log.debug("提取颜色：{} -> {}", value, color.getDisplayName());
            }
        } else {
            log.warn("无法映射颜色值：{}", value);
        }
    }
    
    /**
     * 提取尺寸属性
     */
    private void extractSize(ProductAttribute attribute, String value,
                            ProductAttributeConfig.AttributeLabel labelConfig) {
        // 如果有提取模式，先提取有效部分
        String extractedValue = extractByPattern(value, labelConfig.extractionPattern);
        if (extractedValue != null) {
            value = extractedValue;
        }
        
        // 映射尺寸
        ProductSize size = sizeMapper.mapSize(value);
        if (size != null) {
            attribute.setSize(size);
            log.debug("提取尺寸：{} -> {}", value, size.getDisplayName());
        } else {
            log.warn("无法映射尺寸值：{}", value);
        }
    }
    
    /**
     * 提取产品变量属性
     */
    private void extractProductVariable(ProductAttribute attribute, String value,
                                       ProductAttributeConfig.AttributeLabel labelConfig) {
        // 如果有提取模式，先提取有效部分
        String extractedValue = extractByPattern(value, labelConfig.extractionPattern);
        if (extractedValue != null) {
            value = extractedValue;
        }
        
        // 尝试将数字月份映射为中文月份（如 "10" -> "10月", "5" -> "5月"）
        if (value != null && value.matches("\\d+")) {
            int month = Integer.parseInt(value);
            if (month >= 1 && month <= 12) {
                attribute.setProductVariable(month + "月");
                log.debug("提取产品变量（月份映射）：{} -> {}月", value, month);
                return;
            }
        }
        
        // 首先尝试使用 ProductVariable.extractFromText 解析（支持英文月份名）
        ProductVariable productVariable = ProductVariable.extractFromText(value);
        if (productVariable != null && productVariable != ProductVariable.UNKNOWN) {
            attribute.setProductVariable(productVariable.getDisplayName());
            log.debug("提取产品变量（枚举解析）：{} -> {}", value, productVariable.getDisplayName());
            return;
        }
        
        // 映射产品变量（如月份）
        String variable = variableMapper.getMonthFromValue(value);
        if (variable != null) {
            attribute.setProductVariable(variable);
            log.debug("提取产品变量：{} -> {}", value, variable);
        } else {
            // 如果不能映射，直接使用原始值
            attribute.setProductVariable(value);
            log.debug("提取产品变量（原始值）：{}", value);
        }
    }
    
    /**
     * 提取字体属性
     */
    private void extractFont(ProductAttribute attribute, String value) {
        attribute.setFont(value);
        log.debug("提取字体：{}", value);
    }
    
    /**
     * 提取样式属性
     */
    private void extractStyle(ProductAttribute attribute, String value) {
        attribute.setStyle(value);
        log.debug("提取样式：{}", value);
    }
    
    /**
     * 提取复合属性（如Color & Size）
     * 支持多种分隔符：下划线(_)、斜杠(/)、短横线(-)、逗号(,)、空格等
     */
    private void extractComposite(ProductAttribute attribute, String value,
                                 ProductAttributeConfig.AttributeLabel labelConfig) {
        // 获取值模式
        String valuePattern = labelConfig.valuePattern;
        if (valuePattern == null) {
            valuePattern = semanticsConfig.getValuePattern(labelConfig.labelName);
        }
        
        if (valuePattern == null) {
            log.warn("复合属性 [{}] 未配置值模式", labelConfig.labelName);
            return;
        }
        
        // 使用正则分割值
        Pattern pattern = Pattern.compile(valuePattern);
        Matcher matcher = pattern.matcher(value);
        
        // 如果标准模式不匹配，尝试用多种分隔符预处理后再匹配
        if (!matcher.matches()) {
            // 将常见分隔符统一替换为下划线，再尝试匹配
            String normalizedValue = value.trim()
                .replaceAll("[/\\-]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_");
            matcher = pattern.matcher(normalizedValue);
        }
        
        if (matcher.matches()) {
            // 获取分组映射
            Map<String, String> groupMapping = labelConfig.groupMapping;
            if (groupMapping == null) {
                groupMapping = semanticsConfig.getGroupMapping(labelConfig.labelName);
            }
            
            if (groupMapping == null) {
                log.warn("复合属性 [{}] 未配置分组映射", labelConfig.labelName);
                return;
            }
            
            // 遍历分组映射
            for (Map.Entry<String, String> entry : groupMapping.entrySet()) {
                String groupNum = entry.getKey();
                String attrType = entry.getValue();
                
                try {
                    int groupIndex = Integer.parseInt(groupNum);
                    if (groupIndex <= matcher.groupCount()) {
                        String matchedGroup = matcher.group(groupIndex);
                        if (matchedGroup == null) {
                            log.debug("复合属性分组 [{}] 匹配结果为null，跳过", groupNum);
                            continue;
                        }
                        String groupValue = matchedGroup.trim();
                        
                        // 根据属性类型处理
                        switch (attrType) {
                            case "COLOR":
                                ProductColor color = colorMapper.mapColor(groupValue);
                                if (color != null) {
                                    attribute.setColor(color);
                                }
                                break;
                            case "SIZE":
                                ProductSize size = sizeMapper.mapSizeExact(groupValue);
                                if (size == null || size == ProductSize.UNKNOWN) {
                                    size = sizeMapper.mapSize(groupValue);
                                }
                                if (size != null && size != ProductSize.UNKNOWN) {
                                    attribute.setSize(size);
                                }
                                break;
                            case "PRODUCT_VARIABLE":
                                String variable = variableMapper.getMonthFromValue(groupValue);
                                if (variable != null) {
                                    attribute.setProductVariable(variable);
                                }
                                break;
                            case "ENGRAVING_SIDES":
                                // 复用 extractEngravingSides 逻辑，将刻录面数映射到 productVariable
                                if (groupValue.contains("Double") || groupValue.contains("双面")
                                        || groupValue.contains("Front & Back") || groupValue.contains("Both")) {
                                    attribute.setProductVariable("双面");
                                } else {
                                    attribute.setProductVariable("单面");
                                }
                                log.debug("COMPOSITE 提取刻录面数：{} -> {}", groupValue, attribute.getProductVariable());
                                break;
                        }
                    }
                } catch (Exception e) {
                    log.error("处理复合属性分组失败：group={}, type={}", groupNum, attrType, e);
                }
            }
            
            log.debug("提取复合属性：{} -> color={}, size={}, variable={}", 
                     value, attribute.getColor(), attribute.getSize(), attribute.getProductVariable());
        } else {
            log.warn("复合属性值 [{}] 不匹配模式 [{}]", value, valuePattern);
        }
    }
    
    /**
     * 提取附属商品列表
     * 将 Item: Cufflink+TieClip+Box 解析为 additionalProductNames（orderTypeCode）
     * 同时通过 productNameMapping 填充 additionalProductNameCodes（ProductName.nameCode）
     */
    private void extractAccessoryItems(ProductAttribute attribute, String value,
                                       ProductAttributeConfig.AttributeLabel labelConfig) {
        if (value == null || value.isEmpty()) {
            return;
        }

        // 确定分隔符（从配置读取，默认用 +）
        List<String> separators = labelConfig.separators;
        if (separators == null || separators.isEmpty()) {
            separators = Collections.singletonList("+");
        }

        // 使用分隔符分割商品列表
        StringBuilder sepPattern = new StringBuilder();
        for (String sep : separators) {
            if (sepPattern.length() > 0) sepPattern.append("|");
            sepPattern.append(Pattern.quote(sep.trim()));
        }
        String[] parts = value.trim().split(sepPattern.toString());

        // 商品名称 -> OrderType 映射（来自配置）
        Map<String, String> itemTypeMapping = labelConfig.itemTypeMapping;
        // orderTypeCode -> ProductName.nameCode 映射（来自配置）
        Map<String, String> productNameMapping = labelConfig.productNameMapping;
        // 附属商品原始名称 -> 包装盒产品变量（来自配置）
        Map<String, String> boxVariableMapping = labelConfig.boxVariableMapping;

        List<String> accessoryOrderTypeCodes = new ArrayList<>();
        List<String> accessoryProductNameCodes = new ArrayList<>();
        List<String> accessoryBoxVariables = new ArrayList<>();
        List<String> accessorySizes = new ArrayList<>();
        
        for (String part : parts) {
            String itemName = part.trim();
            if (itemName.isEmpty()) continue;

            // 检查是否有前缀尺寸（如 "S_TieClip" 或 "L_Box"）
            String itemSize = "";
            // 匹配开头的单个字母尺寸码（S/M/L/XL/XXL）后跟分隔符
            java.util.regex.Matcher sizeMatcher = java.util.regex.Pattern.compile("^([SML])([_/\\-\\s]+)(.+)$").matcher(itemName);
            if (sizeMatcher.matches()) {
                itemSize = sizeMatcher.group(1);
                itemName = sizeMatcher.group(3).trim();
                log.debug("从附属商品 [{}] 中提取独立尺寸: {} -> itemName={}, size={}", part, itemSize, itemName, itemSize);
            }

            // 去掉选项字母前缀（如 "A: Cufflinks Gift Box" → "Cufflinks Gift Box"，"B: Rectangle Set Box" → "Rectangle Set Box"，"C/D: Oval Gift Box" → "Oval Gift Box"）
            java.util.regex.Matcher optionPrefixMatcher = java.util.regex.Pattern.compile("^[A-Za-z][A-Za-z/]*:\\s*(.+)$").matcher(itemName);
            if (optionPrefixMatcher.matches()) {
                String stripped = optionPrefixMatcher.group(1).trim();
                log.debug("去掉选项前缀：[{}] -> [{}]", itemName, stripped);
                itemName = stripped;
            }

            // 优先从配置映射中找 orderTypeCode
            String orderTypeCode = null;
            if (itemTypeMapping != null) {
                orderTypeCode = itemTypeMapping.get(itemName);
                if (orderTypeCode == null) {
                    for (Map.Entry<String, String> entry : itemTypeMapping.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase(itemName)) {
                            orderTypeCode = entry.getValue();
                            break;
                        }
                    }
                }
            }
            if (orderTypeCode == null) {
                orderTypeCode = itemName;
            }
            accessoryOrderTypeCodes.add(orderTypeCode);
            accessorySizes.add(itemSize);
            
            // 通过 productNameMapping 找 ProductName.nameCode
            String productNameCode = null;
            if (productNameMapping != null) {
                productNameCode = productNameMapping.get(orderTypeCode);
            }
            accessoryProductNameCodes.add(productNameCode != null ? productNameCode : "");
            
            // 通过 boxVariableMapping 找包装盒产品变量（用原始itemName查）
            String boxVariable = "";
            if (boxVariableMapping != null) {
                // 先用原始名称查
                boxVariable = boxVariableMapping.get(itemName);
                if (boxVariable == null) {
                    // 再用orderTypeCode查（兜底）
                    boxVariable = boxVariableMapping.getOrDefault(orderTypeCode, "");
                }
                if (boxVariable == null) boxVariable = "";
            }
            accessoryBoxVariables.add(boxVariable);
            
            log.debug("附属商品解析：[{}] -> orderTypeCode=[{}], productNameCode=[{}], boxVar=[{}], size=[{}]", 
                      part, orderTypeCode, productNameCode, boxVariable, itemSize);
        }

        if (!accessoryOrderTypeCodes.isEmpty()) {
            attribute.setAdditionalProductNames(accessoryOrderTypeCodes);
            attribute.setAdditionalProductNameCodes(accessoryProductNameCodes);
            attribute.setAdditionalBoxVariables(accessoryBoxVariables);
            attribute.setAdditionalSizes(accessorySizes);
            log.info("解析附属商品列表：{} -> orderTypeCodes={}, boxVars={}", 
                     value, accessoryOrderTypeCodes, accessoryBoxVariables);
        }
    }
    
    /**
     * 使用正则表达式提取值
     */
    private String extractByPattern(String value, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }
        
        try {
            Pattern regex = Pattern.compile(pattern);
            Matcher matcher = regex.matcher(value);
            
            if (matcher.find()) {
                return matcher.group();
            }
        } catch (Exception e) {
            log.error("正则表达式提取失败：pattern={}, value={}", pattern, value, e);
        }
        
        return null;
    }

    /**
     * 提取颜色+附属商品（如 Silver + Oval Box）
     *
     * <p>降级策略：当值中不含 "+" 时（如 PDF 只写了颜色 "Gold"），
     * 将整个值作为纯颜色处理，并用配置中的 defaultBoxVariable 作为礼盒变量。
     * 若 defaultBoxVariable 也未配置，则只提取颜色，不补充礼盒（由兜底规则处理）。</p>
     */
    private void extractColorWithAccessory(ProductAttribute attribute, String value,
                                       ProductAttributeConfig.AttributeLabel labelConfig) {
        String valuePattern = labelConfig.valuePattern;
        if (valuePattern == null) {
            log.warn("COLOR_WITH_ACCESSORY 未配置值模式");
            return;
        }

        Pattern pattern = Pattern.compile(valuePattern);
        Matcher matcher = pattern.matcher(value);

        if (matcher.matches()) {
            // ── 正常路径：值含 "+"，提取颜色 + 礼盒 ──────────────────
            Map<String, String> groupMapping = labelConfig.groupMapping;
            if (groupMapping == null) {
                log.warn("COLOR_WITH_ACCESSORY 未配置分组映射");
                return;
            }

            // 提取颜色
            String colorValue = matcher.group(1).trim();
            ProductColor color = colorMapper.mapColor(colorValue);
            if (color != null) {
                attribute.setColor(color);
                log.debug("COLOR_WITH_ACCESSORY 提取颜色：{} -> {}", colorValue, color.getDisplayName());
            }

            // 提取附属商品
            String accessoryValue = matcher.group(2).trim();
            List<String> accessoryOrderTypeCodes = new ArrayList<>();
            List<String> accessoryProductNameCodes = new ArrayList<>();
            List<String> accessoryBoxVariables = new ArrayList<>();
            List<String> accessorySizes = new ArrayList<>();

            accessoryOrderTypeCodes.add("Box");
            accessoryProductNameCodes.add("Packaging Box");

            // 应用 boxVariableMapping 映射
            String boxVariable = accessoryValue; // 默认原始值
            if (labelConfig.boxVariableMapping != null && labelConfig.boxVariableMapping.containsKey(accessoryValue)) {
                boxVariable = labelConfig.boxVariableMapping.get(accessoryValue);
                log.debug("COLOR_WITH_ACCESSORY 映射产品变量：{} -> {}", accessoryValue, boxVariable);
            }

            accessoryBoxVariables.add(boxVariable);
            accessorySizes.add("");

            attribute.setAdditionalProductNames(accessoryOrderTypeCodes);
            attribute.setAdditionalProductNameCodes(accessoryProductNameCodes);
            attribute.setAdditionalBoxVariables(accessoryBoxVariables);
            attribute.setAdditionalSizes(accessorySizes);

            log.debug("COLOR_WITH_ACCESSORY 提取附属商品：{} (映射为: {})", accessoryValue, boxVariable);

        } else {
            // ── 降级路径：值不含 "+"，当作纯颜色处理 ─────────────────
            log.warn("COLOR_WITH_ACCESSORY 值 [{}] 不匹配模式 [{}]，降级为纯颜色处理", value, valuePattern);

            // 尝试将整个值当作颜色解析
            ProductColor color = colorMapper.mapColor(value.trim());
            if (color != null) {
                attribute.setColor(color);
                log.debug("COLOR_WITH_ACCESSORY 降级提取颜色：{} -> {}", value.trim(), color.getDisplayName());
            } else {
                log.warn("COLOR_WITH_ACCESSORY 降级：无法映射颜色值 [{}]", value.trim());
            }

            // 若配置了 defaultBoxVariable，补充默认礼盒
            if (labelConfig.defaultBoxVariable != null && !labelConfig.defaultBoxVariable.isEmpty()) {
                List<String> accessoryOrderTypeCodes = new ArrayList<>();
                List<String> accessoryProductNameCodes = new ArrayList<>();
                List<String> accessoryBoxVariables = new ArrayList<>();
                List<String> accessorySizes = new ArrayList<>();

                accessoryOrderTypeCodes.add("Box");
                accessoryProductNameCodes.add("Packaging Box");
                accessoryBoxVariables.add(labelConfig.defaultBoxVariable);
                accessorySizes.add("");

                attribute.setAdditionalProductNames(accessoryOrderTypeCodes);
                attribute.setAdditionalProductNameCodes(accessoryProductNameCodes);
                attribute.setAdditionalBoxVariables(accessoryBoxVariables);
                attribute.setAdditionalSizes(accessorySizes);

                log.info("COLOR_WITH_ACCESSORY 降级：使用默认礼盒变量 [{}]", labelConfig.defaultBoxVariable);
            }
        }
    }

    /**
     * 提取颜色+商品名+附属商品（如 Sliver TC + Oval Box）
     */
    private void extractColorItemCombo(ProductAttribute attribute, String value,
                                    ProductAttributeConfig.AttributeLabel labelConfig) {
        String valuePattern = labelConfig.valuePattern;
        log.info("【COLOR_ITEM_COMBO调试】原始值=[{}]，pattern=[{}]", value, valuePattern);
        if (valuePattern == null) {
            log.warn("COLOR_ITEM_COMBO 未配置值模式");
            return;
        }

        Pattern pattern = Pattern.compile(valuePattern);
        Matcher matcher = pattern.matcher(value);
        boolean matched = matcher.matches();
        if (matched) {
            log.info("【COLOR_ITEM_COMBO调试】匹配成功，group1=[{}]，group2=[{}]",
                    matcher.group(1), matcher.group(2));
            Map<String, String> groupMapping = labelConfig.groupMapping;
            if (groupMapping == null) {
                log.warn("COLOR_ITEM_COMBO 未配置分组映射");
                return;
            }

            // 提取颜色
            String colorValue = matcher.group(1).trim();
            ProductColor color = colorMapper.mapColor(colorValue);
            if (color != null) {
                attribute.setColor(color);
                log.debug("COLOR_ITEM_COMBO 提取颜色：{} -> {}", colorValue, color.getDisplayName());
            }

            // 提取附属商品（第2个group，即 TC + 后面的部分）
            String rawAccessoryValue = matcher.group(2).trim();
            // 应用 boxVariableMapping 映射（如 "Oval Box" -> "Oval Box-椭圆形开窗木盒"）
            String accessoryValue = rawAccessoryValue;
            if (labelConfig.boxVariableMapping != null && labelConfig.boxVariableMapping.containsKey(rawAccessoryValue)) {
                accessoryValue = labelConfig.boxVariableMapping.get(rawAccessoryValue);
                log.debug("COLOR_ITEM_COMBO 映射盒子变量：{} -> {}", rawAccessoryValue, accessoryValue);
            }
            List<String> accessoryOrderTypeCodes = new ArrayList<>();
            List<String> accessoryProductNameCodes = new ArrayList<>();
            List<String> accessoryBoxVariables = new ArrayList<>();
            List<String> accessorySizes = new ArrayList<>();

            accessoryOrderTypeCodes.add("Box");
            accessoryProductNameCodes.add("Packaging Box");
            accessoryBoxVariables.add(accessoryValue);
            accessorySizes.add("");

            attribute.setAdditionalProductNames(accessoryOrderTypeCodes);
            attribute.setAdditionalProductNameCodes(accessoryProductNameCodes);
            attribute.setAdditionalBoxVariables(accessoryBoxVariables);
            attribute.setAdditionalSizes(accessorySizes);

            log.debug("COLOR_ITEM_COMBO 提取附属商品：{}", accessoryValue);
        } else {
            log.warn("COLOR_ITEM_COMBO 值 [{}] 不匹配模式 [{}]", value, valuePattern);
        }
    }

    /**
     * 提取片数+尺寸（如 2 Disc_Large -> 片数=2, 尺寸=Large）
     * 片数需要在 ExcelWriterService 中用于拆行逻辑
     */
    private void extractQuantityWithSize(ProductAttribute attribute, String value,
                                     ProductAttributeConfig.AttributeLabel labelConfig) {
        String valuePattern = labelConfig.valuePattern;
        if (valuePattern == null) {
            log.warn("QUANTITY_WITH_SIZE 未配置值模式");
            return;
        }

        Pattern pattern = Pattern.compile(valuePattern);
        Matcher matcher = pattern.matcher(value);

        if (matcher.matches()) {
            Map<String, String> groupMapping = labelConfig.groupMapping;
            if (groupMapping == null) {
                log.warn("QUANTITY_WITH_SIZE 未配置分组映射");
                return;
            }

            // 提取片数
            String pieceCountValue = matcher.group(1).trim();
            try {
                int pieceCount = Integer.parseInt(pieceCountValue);
                // 将片数存储到 pieceCount 字段
                attribute.setPieceCount(pieceCount);
                log.debug("QUANTITY_WITH_SIZE 提取片数：{}", pieceCount);
            } catch (NumberFormatException e) {
                log.warn("无法解析片数：{}", pieceCountValue);
            }

            // 提取尺寸
            String sizeValue = matcher.group(2).trim();
            ProductSize size = sizeMapper.mapSize(sizeValue);
            if (size != null) {
                attribute.setSize(size);
                log.debug("QUANTITY_WITH_SIZE 提取尺寸：{} -> {}", sizeValue, size.getDisplayName());
            }
        } else {
            log.warn("QUANTITY_WITH_SIZE 值 [{}] 不匹配模式 [{}]", value, valuePattern);
        }
    }

    /**
     * 提取刻录面数（Front Only / Back / Front & Back）
     * 用于决定双面刻录商品的拆行数量
     */
    private void extractEngravingSides(ProductAttribute attribute, String value,
                                      ProductAttributeConfig.AttributeLabel labelConfig) {
        // 将刻录面数存储到 productVariable 字段（特殊用途）
        String engravingSides = value.trim();

        // 映射为标准值
        if (engravingSides.contains("Front & Back") || engravingSides.contains("Round Disc & Bar")) {
            attribute.setProductVariable("双面");
        } else if (engravingSides.contains("Front Only") || engravingSides.contains("Round Disc Only")) {
            attribute.setProductVariable("单面");
        } else if (engravingSides.contains("Back")) {
            attribute.setProductVariable("背面");
        } else {
            attribute.setProductVariable(engravingSides);
        }

        log.debug("ENGRAVING_SIDES 提取刻录面数：{} -> {}", value, attribute.getProductVariable());
    }

    /**
     * 提取颜色+尺寸+可选附属商品（如 Silver_Small、Silver_S + Oval Box）
     *
     * <p>格式：颜色_尺寸 [+ 附属商品]，下划线分隔颜色和尺寸，加号引出附属商品。</p>
     * <p>例：Silver_Small → 颜色=银色, 尺寸=S；Silver_S + Oval Box → 颜色=银色, 尺寸=S, Box=Oval Box</p>
     */
    private void extractColorSizeAccessory(ProductAttribute attribute, String value,
                                           ProductAttributeConfig.AttributeLabel labelConfig) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        String raw = value.trim();

        // 先检查是否含 " + " 分隔的附属商品
        String colorSizePart = raw;
        String accessoryPart = null;
        int plusIdx = raw.indexOf(" + ");
        if (plusIdx < 0) plusIdx = raw.indexOf("+");
        if (plusIdx >= 0) {
            colorSizePart = raw.substring(0, plusIdx).trim();
            accessoryPart = raw.substring(plusIdx + (raw.charAt(plusIdx + 1) == ' ' ? 3 : 1)).trim();
            // 兼容两种 +：" + " 和 "+"
            if (raw.contains(" + ")) {
                colorSizePart = raw.substring(0, raw.indexOf(" + ")).trim();
                accessoryPart = raw.substring(raw.indexOf(" + ") + 3).trim();
            }
        }

        // 提取颜色和尺寸：以 _ 为分隔符
        String colorValue;
        String sizeValue = null;
        int underscoreIdx = colorSizePart.indexOf('_');
        if (underscoreIdx >= 0) {
            colorValue = colorSizePart.substring(0, underscoreIdx).trim();
            sizeValue = colorSizePart.substring(underscoreIdx + 1).trim();
        } else {
            colorValue = colorSizePart.trim();
        }

        // 映射颜色
        ProductColor color = colorMapper.mapColor(colorValue);
        if (color != null) {
            attribute.setColor(color);
            log.debug("COLOR_SIZE_ACCESSORY 提取颜色：{} -> {}", colorValue, color.getDisplayName());
        } else {
            log.warn("COLOR_SIZE_ACCESSORY 无法映射颜色：{}", colorValue);
        }

        // 映射尺寸
        if (sizeValue != null && !sizeValue.isEmpty()) {
            ProductSize size = sizeMapper.mapSize(sizeValue);
            if (size != null) {
                attribute.setSize(size);
                log.debug("COLOR_SIZE_ACCESSORY 提取尺寸：{} -> {}", sizeValue, size.getDisplayName());
            }
        }

        // 处理附属商品
        if (accessoryPart != null && !accessoryPart.isEmpty()) {
            List<String> accessoryOrderTypeCodes = new ArrayList<>();
            List<String> accessoryProductNameCodes = new ArrayList<>();
            List<String> accessoryBoxVariables = new ArrayList<>();
            List<String> accessorySizes = new ArrayList<>();

            accessoryOrderTypeCodes.add("Box");
            accessoryProductNameCodes.add("Packaging Box");

            // 应用 boxVariableMapping 映射
            String boxVariable = accessoryPart;
            if (labelConfig.boxVariableMapping != null && labelConfig.boxVariableMapping.containsKey(accessoryPart)) {
                boxVariable = labelConfig.boxVariableMapping.get(accessoryPart);
                log.debug("COLOR_SIZE_ACCESSORY 映射盒子变量：{} -> {}", accessoryPart, boxVariable);
            }
            accessoryBoxVariables.add(boxVariable);
            accessorySizes.add("");

            attribute.setAdditionalProductNames(accessoryOrderTypeCodes);
            attribute.setAdditionalProductNameCodes(accessoryProductNameCodes);
            attribute.setAdditionalBoxVariables(accessoryBoxVariables);
            attribute.setAdditionalSizes(accessorySizes);
            log.debug("COLOR_SIZE_ACCESSORY 提取附属商品：{}", accessoryPart);
        }
    }

    /**
     * 提取宠物+刻录面数（如 1Pet + 1 Side、2Pet + 2 Side）
     *
     * <p>格式：{N}Pet + {M} Side，N 为宠物数量（仅记录不影响输出），M 为刻录面数（决定拆行：1=单面1行，2=双面2行，最多5面5行）。</p>
     * <p>注意：宠物数量不影响商品拆行，只有刻录面数 Side 数量决定行数。</p>
     */
    private void extractPetEngravingSides(ProductAttribute attribute, String value,
                                          ProductAttributeConfig.AttributeLabel labelConfig) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        String raw = value.trim();

        // 匹配格式：{N}Pet + {M} Side 或 {N}Pet + {M}Side（大小写不敏感）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(\\d+)\\s*Pet\\s*\\+\\s*(\\d+)\\s*Side", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(raw);

        if (matcher.find()) {
            // petCount 为宠物数量（记录到 productVariable 的前缀，不影响拆行）
            // sideCount 为刻录面数（决定拆行数量，通过 dynamicAttributes 字段传递到 ExcelWriterService）
            int sideCount = Integer.parseInt(matcher.group(2));

            // 将面数映射到标准值（复用 ENGRAVING_SIDES 逻辑）
            if (sideCount == 1) {
                attribute.setProductVariable("单面");
            } else if (sideCount == 2) {
                attribute.setProductVariable("双面");
            } else {
                attribute.setProductVariable(sideCount + "面");
            }
            log.debug("PET_ENGRAVING_SIDES 提取刻录面数：{} -> {} 面", raw, sideCount);
        } else if (raw.toLowerCase().contains("1 side") || raw.toLowerCase().contains("1side")) {
            attribute.setProductVariable("单面");
            log.debug("PET_ENGRAVING_SIDES 提取刻录面数（单面兜底）：{}", raw);
        } else if (raw.toLowerCase().contains("2 side") || raw.toLowerCase().contains("2side")) {
            attribute.setProductVariable("双面");
            log.debug("PET_ENGRAVING_SIDES 提取刻录面数（双面兜底）：{}", raw);
        } else {
            log.warn("PET_ENGRAVING_SIDES 无法解析：{}", raw);
        }
    }

    /**
     * 提取花卉款式（Birth Flower Style），格式如 "1 Garnet Glow"，提取前缀数字映射为月份中文
     *
     * <p>格式：{月份序号} {宝石名}，如 "1 Garnet Glow" → 一月, "12 Turquoise" → 十二月。</p>
     */
    private void extractBirthFlowerStyle(ProductAttribute attribute, String value,
                                         ProductAttributeConfig.AttributeLabel labelConfig) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        String raw = value.trim();

        // 匹配开头的数字（1-12）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\d{1,2})\\s+");
        java.util.regex.Matcher matcher = pattern.matcher(raw);

        if (matcher.find()) {
            int month = Integer.parseInt(matcher.group(1));
            ProductVariable monthVar = null;
            switch (month) {
                case 1:  monthVar = ProductVariable.MONTH_JANUARY; break;
                case 2:  monthVar = ProductVariable.MONTH_FEBRUARY; break;
                case 3:  monthVar = ProductVariable.MONTH_MARCH; break;
                case 4:  monthVar = ProductVariable.MONTH_APRIL; break;
                case 5:  monthVar = ProductVariable.MONTH_MAY; break;
                case 6:  monthVar = ProductVariable.MONTH_JUNE; break;
                case 7:  monthVar = ProductVariable.MONTH_JULY; break;
                case 8:  monthVar = ProductVariable.MONTH_AUGUST; break;
                case 9:  monthVar = ProductVariable.MONTH_SEPTEMBER; break;
                case 10: monthVar = ProductVariable.MONTH_OCTOBER; break;
                case 11: monthVar = ProductVariable.MONTH_NOVEMBER; break;
                case 12: monthVar = ProductVariable.MONTH_DECEMBER; break;
            }
            if (monthVar != null) {
                attribute.setProductVariable(monthVar.getDisplayName());
                log.debug("BIRTH_FLOWER_STYLE 提取月份：{} -> {}", raw, monthVar.getDisplayName());
            } else {
                log.warn("BIRTH_FLOWER_STYLE 月份序号超出范围 1-12：{}", month);
            }
        } else {
            log.warn("BIRTH_FLOWER_STYLE 无法解析格式（期望 '数字 宝石名'）：{}", raw);
        }
    }

    /**
     * 提取骨灰罐刻录选项（Engraving Options），根据 "&" 数量决定面数
     *
     * <p>格式示例：
     * - Lib Only / Body Only / Bottom Only → 1 面（0个&）
     * - Lib & Body / Lib & Bottom / Body & Bottom → 2 面（1个&）
     * - Lib & Body & Bottom → 3 面（2个&）
     * </p>
     */
    private void extractUrnEngravingOptions(ProductAttribute attribute, String value,
                                           ProductAttributeConfig.AttributeLabel labelConfig) {
        String raw = value.trim();
        int ampersandCount = 0;
        for (char c : raw.toCharArray()) {
            if (c == '&') ampersandCount++;
        }
        int sideCount = ampersandCount + 1; // 0个&→1面，1个&→2面，2个&→3面
        // 仅用于刻录面数识别，不再写入 productVariable，避免覆盖产品材质等业务变量（如“不锈钢”）。
        log.debug("URN_ENGRAVING_OPTIONS 识别面数：{} ({}个&) -> {} 面", raw, ampersandCount, sideCount);
    }
}

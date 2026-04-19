package com.pdfconverter.service;

import com.pdfconverter.config.AccessoryRuleConfig;
import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.constant.ProductVariable;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 附属商品工厂
 * 根据配置的规则自动补充附属商品(附加商品)
 */
@Component
public class AccessoryItemFactory {

    private static final Logger log = LoggerFactory.getLogger(AccessoryItemFactory.class);

    @Resource
    private AccessoryRuleConfig accessoryRuleConfig;

    /**
     * 根据主商品列表补充附属商品
     *
     * @param allItems 当前订单的所有商品列表（包括主商品和附属商品）
     * @return 补充后的附加商品列表（不包含原有商品）
     */
    public List<ItemDetail> supplementAddOns(List<ItemDetail> allItems) {
        List<ItemDetail> addOnItems = new ArrayList<>();

        // 如果没有配置规则，跳过补充
        if (accessoryRuleConfig == null || accessoryRuleConfig.getRules() == null ||
            accessoryRuleConfig.getRules().isEmpty()) {
            log.warn("附件商品规则未配置或为空，跳过补充");
            return addOnItems;
        }

        log.info("开始执行附件商品补充，共 {} 条规则，输入商品数: {}", 
                 accessoryRuleConfig.getRules().size(), allItems.size());

        // 输出所有商品的详细信息
        for (ItemDetail item : allItems) {
            log.debug("  商品信息: title={}, orderType={}, quantity={}, mainProductFlg={}, productVariable={}",
                     item.getItemTitle(), item.getOrderType(), item.getItemQuantity(), 
                     item.getMainProductFlg(), item.getProductVariable());
        }

        // 过滤出主商品列表（mainProductFlg=true 或 null 默认为主商品）
        List<ItemDetail> mainItems = allItems.stream()
            .filter(item -> item.getMainProductFlg() == null || item.getMainProductFlg())
            .collect(Collectors.toList());

        // 过滤出附属商品列表（用于判断是否已有box等）
        List<ItemDetail> existingAccessories = allItems.stream()
            .filter(item -> item.getMainProductFlg() != null && !item.getMainProductFlg())
            .collect(Collectors.toList());

        log.info("识别出主商品 {} 个，附属商品 {} 个", mainItems.size(), existingAccessories.size());

        // 按优先级排序规则
        List<AccessoryRuleConfig.AccessoryRule> sortedRules = accessoryRuleConfig.getRules().stream()
            .filter(AccessoryRuleConfig.AccessoryRule::isEnabled)
            .sorted(Comparator.comparingInt(AccessoryRuleConfig.AccessoryRule::getPriority))
            .collect(Collectors.toList());

        log.info("已启用规则数: {}", sortedRules.size());

        // 为每个主商品检查规则
        for (ItemDetail mainItem : mainItems) {
            log.debug("检查主商品: {} (类型: {}, 数量: {})", 
                     mainItem.getItemTitle(), mainItem.getOrderType(), mainItem.getItemQuantity());
            
            for (AccessoryRuleConfig.AccessoryRule rule : sortedRules) {
                log.debug("  评估规则: {} (规则ID: {})", rule.getDescription(), rule.getRuleId());
                
                if (shouldAddAddOn(mainItem, allItems, existingAccessories, rule)) {
                    ItemDetail addOn = createAddOn(mainItem, rule);
                    addOnItems.add(addOn);
                    log.info("  ✓ 为主商品 [{}] 补充附加商品: {} - {} (数量: {})",
                             mainItem.getItemTitle(), addOn.getOrderType(), addOn.getProductVariable(), addOn.getItemQuantity());
                } else {
                    log.debug("  ✗ 规则 {} 未匹配", rule.getRuleId());
                }
            }
        }

        log.info("附件商品补充完成，共生成 {} 个附加商品", addOnItems.size());
        return addOnItems;
    }

    /**
     * 判断是否需要添加附加商品
     */
    private boolean shouldAddAddOn(ItemDetail mainItem, List<ItemDetail> allItems,
                                   List<ItemDetail> existingAccessories,
                                   AccessoryRuleConfig.AccessoryRule rule) {
        log.debug("    检查规则条件: {}", rule.getRuleId());

        // 1. 检查主商品类型匹配
        // 注意：规则中的类型名称应该是 OrderType 枚举的常量名（如 CUFFLINK, TIE_CLIP）
        if (rule.getMainProductTypes() != null && !rule.getMainProductTypes().isEmpty()) {
            OrderType mainType = mainItem.getOrderType();
            // 使用枚举的 name() 方法获取常量名进行比较
            boolean typeMatched = rule.getMainProductTypes().stream()
                .anyMatch(type -> mainType != null && mainType.name().equals(type));
            log.debug("      主商品类型检查: 商品类型(name)={}, 商品类型(toString)={}, 规则要求={}, 结果={}",
                      mainType != null ? mainType.name() : null,
                      mainType != null ? mainType.toString() : null,
                      rule.getMainProductTypes(), typeMatched ? "匹配" : "不匹配");
            if (!typeMatched) {
                return false;
            }
        }

        // 2. 检查排除的商品类型（当前订单中如果有这些类型，则不补充）
        if (rule.getExcludeProductTypes() != null && !rule.getExcludeProductTypes().isEmpty()) {
            Set<String> allTypes = allItems.stream()
                .map(item -> item.getOrderType())
                .filter(Objects::nonNull)
                .map(Enum::name)  // 使用 name() 获取枚举常量名
                .collect(Collectors.toSet());

            log.debug("      当前订单中的商品类型(name): {}", allTypes);
            log.debug("      排除类型检查: 规则要求={}", rule.getExcludeProductTypes());

            boolean excluded = rule.getExcludeProductTypes().stream()
                .anyMatch(allTypes::contains);
            log.debug("      排除检查结果: {}", excluded ? "存在排除项，跳过" : "通过");
            if (excluded) {
                return false;
            }
        }

        // 3. 检查标题关键词
        if (rule.getTitleKeywords() != null && !rule.getTitleKeywords().isEmpty()) {
            String itemTitle = mainItem.getItemTitle() != null ? mainItem.getItemTitle().toLowerCase() : "";
            boolean keywordMatched = rule.getTitleKeywords().stream()
                .allMatch(keyword -> itemTitle.contains(keyword.toLowerCase()));
            log.debug("      标题关键词检查: 商品标题='{}', 关键词={}, 结果={}",
                      itemTitle, rule.getTitleKeywords(), keywordMatched ? "匹配" : "不匹配");
            if (!keywordMatched) {
                return false;
            }
        }

        // 4. 检查订单级条件
        AccessoryRuleConfig.OrderCondition orderCondition = rule.getOrderCondition();
        if (orderCondition != null) {
            // 检查要求同时存在的其他产品类型
            if (orderCondition.getRequireOtherProducts() != null &&
                !orderCondition.getRequireOtherProducts().isEmpty()) {
                Set<String> allTypes = allItems.stream()
                    .map(item -> item.getOrderType())
                    .filter(Objects::nonNull)
                    .map(OrderType::name)
                    .collect(Collectors.toSet());

                boolean hasRequiredType = orderCondition.getRequireOtherProducts().stream()
                    .anyMatch(allTypes::contains);
                if (!hasRequiredType) {
                    return false;
                }
            }

            // 检查要求没有木盒（已购买木盒 = productVariable 枚举name中含 WOOD_BOX）
            if (orderCondition.isRequireNoWoodBox()) {
                boolean hasWoodBox = existingAccessories.stream()
                    .anyMatch(item -> item.getOrderType() == OrderType.BOX &&
                                     item.getProductVariable() != null &&
                                     item.getProductVariable() != com.pdfconverter.constant.ProductVariable.UNKNOWN &&
                                     item.getProductVariable().name().contains("WOOD_BOX"));
                if (hasWoodBox) {
                    log.debug("      木盒检查：订单已包含木盒({})，跳过", 
                              existingAccessories.stream()
                                  .filter(i -> i.getOrderType() == OrderType.BOX)
                                  .map(i -> i.getProductVariable())
                                  .findFirst().orElse(null));
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 创建附加商品
     */
    private ItemDetail createAddOn(ItemDetail mainItem, AccessoryRuleConfig.AccessoryRule rule) {
        ItemDetail addOn = new ItemDetail();

        // 设置附加商品类型
        String accessoryTypeStr = rule.getAccessoryType();
        OrderType accessoryType = OrderType.valueOf(accessoryTypeStr);
        addOn.setOrderType(accessoryType);

        // 设置附加商品的产品变量
        // accessoryVariable 直接存 ProductVariable 枚举常量名（如 BOX_RECTANGLE、CHAIN_FULL）
        // ProductVariable.fromDisplayName() 已支持枚举常量名匹配（name() 比较）
        String accessoryVariable = rule.getAccessoryVariable();
        if (accessoryVariable != null && !accessoryVariable.isEmpty()) {
            ProductVariable pv = ProductVariable.fromDisplayName(accessoryVariable);
            if (pv == ProductVariable.UNKNOWN) {
                log.warn("  createAddOn: 无法识别 accessoryVariable='{}', 将跳过设置", accessoryVariable);
            } else {
                addOn.setProductVariable(pv);
                log.debug("  createAddOn: productVariable={} ({})", pv.name(), pv.getDisplayName());
            }
        }

        // 设置数量
        AccessoryRuleConfig.QuantityRule quantityRule = rule.getQuantityRule();
        if (quantityRule != null) {
            if (quantityRule.getFixedQuantity() != null) {
                addOn.setItemQuantity(quantityRule.getFixedQuantity());
            } else if (quantityRule.isSameAsMain()) {
                int multiplier = quantityRule.getMultiplier() != null ? quantityRule.getMultiplier() : 1;
                addOn.setItemQuantity(mainItem.getItemQuantity() * multiplier);
            } else {
                addOn.setItemQuantity(1);
            }
        } else {
            addOn.setItemQuantity(mainItem.getItemQuantity());
        }

        // 继承配置
        AccessoryRuleConfig.InheritConfig inheritConfig = rule.getInheritConfig();
        if (inheritConfig != null) {
            if (inheritConfig.isInheritColor()) {
                addOn.setProductColor(mainItem.getProductColor());
            }
            if (inheritConfig.isInheritSize()) {
                addOn.setProductSize(mainItem.getProductSize());
            }
            if (inheritConfig.isInheritVariable()) {
                addOn.setProductVariable(mainItem.getProductVariable());
            }
        }

        // 特殊处理：如果是硅胶绑带,从dynamicAttributes中提取Silicone Rubber Holder Color
        if (accessoryType == OrderType.DOG_TAG_HOLDER) {
            ProductColor siliconeBandColor = extractSiliconeBandColor(mainItem.getDynamicAttributes());
            if (siliconeBandColor != null && siliconeBandColor != ProductColor.UNKNOWN) {
                addOn.setProductColor(siliconeBandColor);
                log.debug("  createAddOn: 从dynamicAttributes提取硅胶绑带颜色: {}", siliconeBandColor.getDisplayName());
            }
        }

        // 设置为主商品标志为false（附加商品）
        addOn.setMainProductFlg(false);

        // 继承其他基础信息
        addOn.setItemTitle(mainItem.getItemTitle());
        addOn.setDynamicAttributes(mainItem.getDynamicAttributes());
        addOn.setPersonalization(mainItem.getPersonalization());

        // 包装盒（BOX）不继承主商品的 Font 和 Style，避免输出到 Excel 列中
        if (accessoryType != OrderType.BOX) {
            addOn.setFont(mainItem.getFont());
            addOn.setStyle(mainItem.getStyle());
        }

        return addOn;
    }

    /**
     * 从dynamicAttributes中提取硅胶绑带颜色
     * 示例: "Size Options: Silver_S\nSilicone Rubber Holder Color: Hot Pink" -> Hot Pink
     */
    private ProductColor extractSiliconeBandColor(String dynamicAttributes) {
        if (dynamicAttributes == null || dynamicAttributes.isEmpty()) {
            return null;
        }

        // 查找 "Silicone Rubber Holder Color: " 后面的颜色值
        String[] lines = dynamicAttributes.split("\n");
        for (String line : lines) {
            if (line.contains("Silicone Rubber Holder Color:")) {
                String colorStr = line.substring(line.indexOf(":") + 1).trim();
                // 映射颜色
                ProductColor color = ProductColor.extractFromText(colorStr);
                if (color != null && color != ProductColor.UNKNOWN) {
                    log.debug("  extractSiliconeBandColor: 提取到颜色 '{}' -> {}", colorStr, color.getDisplayName());
                    return color;
                } else {
                    log.warn("  extractSiliconeBandColor: 无法映射颜色值: {}", colorStr);
                }
            }
        }

        return null;
    }

    /**
     * 创建简单的附属商品（用于向后兼容）
     */
    public ItemDetail createSimpleAccessory(OrderType accessoryType, int quantity, OrderType parentType,
                                            ProductColor productColor, ProductSize productSize) {
        ItemDetail accessory = new ItemDetail();
        accessory.setOrderType(accessoryType);
        accessory.setItemQuantity(quantity);
        accessory.setProductColor(productColor);
        accessory.setProductSize(productSize);
        accessory.setProductVariable(ProductVariable.fromDisplayName(accessoryType.getDisplayName()));
        accessory.setMainProductFlg(false);
        return accessory;
    }
}

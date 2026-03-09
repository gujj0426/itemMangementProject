package com.pdfconverter.factory;

import com.pdfconverter.config.AccessoryRuleConfig;
import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.constant.ProductVariable;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import com.pdfconverter.service.mapper.ProductNameMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * 附属商品工厂
 * 根据配置的规则自动补充附属商品
 */
@Component
public class AccessoryItemFactory {

    private static final Logger log = LoggerFactory.getLogger(AccessoryItemFactory.class);

    @Resource
    private AccessoryRuleConfig accessoryRuleConfig;

    @Resource
    private ProductNameMappingService productNameMappingService;

    /**
     * 根据主商品列表补充附属商品
     *
     * @param mainItems 主商品列表
     * @return 补充后的完整商品列表（包含主商品和附属商品）
     */
    public List<ItemDetail> supplementAccessories(List<ItemDetail> mainItems) {
        List<ItemDetail> allItems = new ArrayList<>(mainItems);

        // 如果没有配置规则，跳过补充
        if (accessoryRuleConfig == null || accessoryRuleConfig.getRules() == null ||
            accessoryRuleConfig.getRules().isEmpty()) {
            return allItems;
        }

        for (ItemDetail mainItem : mainItems) {
            // 检查所有规则，补充附属商品
            for (AccessoryRuleConfig.AccessoryRule rule : accessoryRuleConfig.getRules()) {
                if (shouldAddAccessory(mainItem, rule)) {
                    ItemDetail accessory = createAccessory(mainItem, rule);
                    allItems.add(accessory);
                    log.debug("为主商品 {} 补充附属商品: {}", mainItem.getItemTitle(), accessory.getOrderType());
                }
            }
        }

        return allItems;
    }

    /**
     * 判断是否需要添加附属商品
     */
    private boolean shouldAddAccessory(ItemDetail mainItem, AccessoryRuleConfig.AccessoryRule rule) {
        // 检查主商品类型匹配
        if (rule.getMainProductTypes() != null && !rule.getMainProductTypes().isEmpty()) {
            OrderType mainType = mainItem.getOrderType();
            boolean typeMatched = rule.getMainProductTypes().stream()
                .anyMatch(type -> mainType != null && mainType.getDisplayName().contains(type));
            if (!typeMatched) {
                return false;
            }
        }

        // 检查排除的商品类型
        if (rule.getExcludeProductTypes() != null && !rule.getExcludeProductTypes().isEmpty()) {
            OrderType mainType = mainItem.getOrderType();
            boolean excluded = rule.getExcludeProductTypes().stream()
                .anyMatch(type -> mainType != null && mainType.getDisplayName().contains(type));
            if (excluded) {
                return false;
            }
        }

        // 检查标题关键词
        if (rule.getTitleKeywords() != null && !rule.getTitleKeywords().isEmpty()) {
            String itemTitle = mainItem.getItemTitle() != null ? mainItem.getItemTitle().toLowerCase() : "";
            boolean keywordMatched = rule.getTitleKeywords().stream()
                .anyMatch(keyword -> itemTitle.contains(keyword.toLowerCase()));
            if (!keywordMatched) {
                return false;
            }
        }

        return true;
    }

    /**
     * 创建附属商品
     */
    private ItemDetail createAccessory(ItemDetail mainItem, AccessoryRuleConfig.AccessoryRule rule) {
        ItemDetail accessory = new ItemDetail();

        // 设置附属商品类型
        String accessoryTypeStr = rule.getAccessoryProductType();
        OrderType accessoryType = OrderType.fromOrderTypeCode(accessoryTypeStr);

        // 特殊处理：如果是包装盒，需要映射标准名称
        if (accessoryType == OrderType.BOX || accessoryTypeStr.contains("包装盒") || accessoryTypeStr.contains("Box")) {
            // 根据父商品类型映射标准名称
            if (mainItem.getOrderType() != null) {
                if (mainItem.getOrderType() == OrderType.CUFFLINK) {
                    String variableName = productNameMappingService.getStandardName("small.square.box");
                    ProductVariable variable = ProductVariable.fromDisplayName(variableName);
                    accessory.setProductVariable(variable);
                } else if (mainItem.getOrderType() == OrderType.TIE_CLIP) {
                    String variableName = productNameMappingService.getStandardName("rectangle.box");
                    ProductVariable variable = ProductVariable.fromDisplayName(variableName);
                    accessory.setProductVariable(variable);
                } else {
                    String variableName = productNameMappingService.getStandardName(accessoryTypeStr);
                    ProductVariable variable = ProductVariable.fromDisplayName(variableName);
                    accessory.setProductVariable(variable);
                }
            }
        } else {
            // 其他附属商品直接设置
            ProductVariable variable = ProductVariable.fromDisplayName(accessoryTypeStr);
            accessory.setProductVariable(variable);
        }

        accessory.setOrderType(accessoryType);

        // 设置数量
        accessory.setItemQuantity(rule.isQuantitySameAsMain() ? mainItem.getItemQuantity() : 1);

        // 继承颜色
        if (rule.isInheritColor()) {
            accessory.setProductColor(mainItem.getProductColor());
        }

        // 继承尺寸
        if (rule.isInheritSize()) {
            accessory.setProductSize(mainItem.getProductSize());
        }

        // 继承其他基础信息
        accessory.setItemTitle(mainItem.getItemTitle());
        accessory.setDynamicAttributes(mainItem.getDynamicAttributes());
        accessory.setPersonalization(mainItem.getPersonalization());
        accessory.setFont(mainItem.getFont());
        accessory.setStyle(mainItem.getStyle());

        return accessory;
    }

    /**
     * 创建简单的附属商品（用于向后兼容）
     */
    public ItemDetail createSimpleAccessory(OrderType accessoryType, int quantity, OrderType parentType,
                                            String productColor, String productSize) {
        ItemDetail accessory = new ItemDetail();
        accessory.setOrderType(accessoryType);
        accessory.setItemQuantity(quantity);
        // 将字符串转换为枚举类型
        if (productColor != null && !productColor.isEmpty()) {
            try {
                accessory.setProductColor(ProductColor.valueOf(productColor.toUpperCase()));
            } catch (Exception e) {
                // 转换失败，尝试通过displayName查找
                accessory.setProductColor(ProductColor.valueOf(productColor));
            }
        }
        if (productSize != null && !productSize.isEmpty()) {
            try {
                accessory.setProductSize(ProductSize.valueOf(productSize.toUpperCase()));
            } catch (Exception e) {
                // 转换失败，尝试通过displayName查找
                accessory.setProductSize(ProductSize.valueOf(productSize));
            }
        }
        ProductVariable variable = ProductVariable.fromDisplayName(accessoryType.getDisplayName());
        accessory.setProductVariable(variable);
        return accessory;
    }
}

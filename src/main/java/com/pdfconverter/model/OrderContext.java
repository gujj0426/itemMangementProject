package com.pdfconverter.model;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductName;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.constant.ProductVariable;

import java.util.*;

/**
 * 订单上下文 - 保存订单处理过程中的完整信息
 * 用于全局判断附加产品规则
 */
public class OrderContext {
    private String orderNumber;
    private List<ProductItem> items = new ArrayList<>();
    private List<ProductItem> mainProducts = new ArrayList<>();
    private List<ProductItem> accessories = new ArrayList<>();
    private Map<String, Object> metadata = new HashMap<>();

    public OrderContext() {
    }

    public OrderContext(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    /**
     * 添加产品项到订单上下文
     */
    public void addItem(ProductItem item) {
        items.add(item);
        if (item.isMainProduct()) {
            mainProducts.add(item);
        } else {
            accessories.add(item);
        }
    }

    /**
     * 批量添加产品项
     */
    public void addItems(List<ProductItem> items) {
        items.forEach(this::addItem);
    }

    /**
     * 快速查询：判断订单中是否包含指定类型的产品
     */
    public boolean hasProductType(OrderType type) {
        if (type == null) {
            return false;
        }
        return items.stream().anyMatch(item -> item.getProductType() == type);
    }

    /**
     * 快速查询：判断订单中是否包含木盒
     */
    public boolean hasWoodBox() {
        return items.stream().anyMatch(item ->
            item.getProductType() == OrderType.BOX &&
            (item.getVariable() == ProductVariable.WOOD_BOX_SQUARE_WOOD ||
             item.getVariable() == ProductVariable.WOOD_BOX_SQUARE_WINDOW ||
             item.getVariable() == ProductVariable.WOOD_BOX_RECTANGLE_WOOD ||
             item.getVariable() == ProductVariable.WOOD_BOX_RECTANGLE_WINDOW ||
             item.getVariable() == ProductVariable.WOOD_BOX_OVAL_WINDOW ||
             item.getSourceTitle() != null &&
             (item.getSourceTitle().toLowerCase().contains("wood box") ||
              item.getSourceTitle().toLowerCase().contains("木盒")))
        );
    }

    /**
     * 获取指定类型的产品列表
     */
    public List<ProductItem> getItemsByType(OrderType type) {
        if (type == null) {
            return new ArrayList<>();
        }
        return items.stream()
            .filter(item -> item.getProductType() == type)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * 获取主产品列表（排除附属产品）
     */
    public List<ProductItem> getMainProducts() {
        return new ArrayList<>(mainProducts);
    }

    /**
     * 获取附属产品列表
     */
    public List<ProductItem> getAccessories() {
        return new ArrayList<>(accessories);
    }

    /**
     * 获取所有产品项
     */
    public List<ProductItem> getAllItems() {
        return new ArrayList<>(items);
    }

    /**
     * 根据ID获取产品项
     */
    public ProductItem getItemById(String id) {
        if (id == null) {
            return null;
        }
        return items.stream()
            .filter(item -> id.equals(item.getId()))
            .findFirst()
            .orElse(null);
    }

    /**
     * 获取指定父产品的所有附属产品
     */
    public List<ProductItem> getAccessoriesByParent(String parentId) {
        if (parentId == null) {
            return new ArrayList<>();
        }
        return accessories.stream()
            .filter(item -> parentId.equals(item.getParentItemId()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * 计算订单总产品数
     */
    public int getTotalItemCount() {
        return items.size();
    }

    /**
     * 计算订单主产品数
     */
    public int getMainProductCount() {
        return mainProducts.size();
    }

    /**
     * 计算订单附属产品数
     */
    public int getAccessoryCount() {
        return accessories.size();
    }

    /**
     * 添加元数据
     */
    public void putMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * 获取元数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    // Getters and Setters
    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OrderContext{");
        sb.append("orderNumber='").append(orderNumber).append('\'');
        sb.append(", totalItems=").append(items.size());
        sb.append(", mainProducts=").append(mainProducts.size());
        sb.append(", accessories=").append(accessories.size());
        sb.append('}');
        return sb.toString();
    }

    /**
     * 获取订单摘要信息
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("订单号: ").append(orderNumber).append("\n");
        sb.append("主产品 (").append(mainProducts.size()).append("):\n");
        for (ProductItem item : mainProducts) {
            sb.append("  - ").append(item.getSummary()).append("\n");
        }
        if (!accessories.isEmpty()) {
            sb.append("附属产品 (").append(accessories.size()).append("):\n");
            for (ProductItem item : accessories) {
                sb.append("  - ").append(item.getSummary()).append("\n");
            }
        }
        return sb.toString();
    }
}

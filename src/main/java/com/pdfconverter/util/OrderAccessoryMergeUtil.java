package com.pdfconverter.util;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductVariable;
import com.pdfconverter.model.PdfOrderData.ItemDetail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同一订单内：将盒型相同的附属「包装盒」合并为一行并累加数量，避免一行订单多件时每件各占一盒行。
 */
public final class OrderAccessoryMergeUtil {

    private OrderAccessoryMergeUtil() {
    }

    public static List<ItemDetail> mergeIdenticalBoxAccessoriesWithinOrder(List<ItemDetail> items) {
        if (items == null || items.size() < 2) {
            return items;
        }
        List<ItemDetail> nonBox = new ArrayList<>();
        Map<String, Integer> quantityByKey = new LinkedHashMap<>();
        Map<String, ItemDetail> templateByKey = new LinkedHashMap<>();

        for (ItemDetail item : items) {
            if (isBoxAccessoryRow(item)) {
                String key = boxAccessoryMergeKey(item);
                int q = item.getItemQuantity() > 0 ? item.getItemQuantity() : 1;
                if (!templateByKey.containsKey(key)) {
                    templateByKey.put(key, item);
                    quantityByKey.put(key, q);
                } else {
                    quantityByKey.merge(key, q, Integer::sum);
                }
            } else {
                nonBox.add(item);
            }
        }
        if (templateByKey.isEmpty()) {
            return items;
        }
        for (Map.Entry<String, ItemDetail> e : templateByKey.entrySet()) {
            int total = quantityByKey.getOrDefault(e.getKey(), 1);
            e.getValue().setItemQuantity(total);
        }
        List<ItemDetail> out = new ArrayList<>(nonBox.size() + templateByKey.size());
        out.addAll(nonBox);
        out.addAll(templateByKey.values());
        return out;
    }

    private static boolean isBoxAccessoryRow(ItemDetail item) {
        if (item == null || item.getOrderType() != OrderType.BOX) {
            return false;
        }
        Boolean main = item.getMainProductFlg();
        return main == null || !main;
    }

    /**
     * 仅按盒型合并；同一订单内相同 ProductVariable 视为同一礼盒需求。
     */
    private static String boxAccessoryMergeKey(ItemDetail item) {
        ProductVariable v = item.getProductVariable();
        String vKey = (v != null && v != ProductVariable.UNKNOWN) ? v.name() : "VAR_UNKNOWN";
        return "BOX|" + vKey;
    }
}

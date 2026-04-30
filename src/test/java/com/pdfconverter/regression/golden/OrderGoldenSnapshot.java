package com.pdfconverter.regression.golden;

import com.pdfconverter.model.PdfOrderData;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON golden snapshot for regression (minimal stable fields).
 */
public class OrderGoldenSnapshot {

    public int schemaVersion = 1;
    public String caseId;
    public List<OrderGoldenOrder> orders = new ArrayList<>();

    public static OrderGoldenSnapshot from(String caseId, List<PdfOrderData> source) {
        OrderGoldenSnapshot snap = new OrderGoldenSnapshot();
        snap.caseId = caseId;
        for (PdfOrderData o : source) {
            OrderGoldenOrder go = new OrderGoldenOrder();
            go.orderNumber = o.getOrderNumber();
            go.username = o.getUsername();
            go.totalItemQuantity = o.getTotalItemQuantity();
            if (o.getItemDetails() != null) {
                for (PdfOrderData.ItemDetail line : o.getItemDetails()) {
                    OrderGoldenItemLine il = new OrderGoldenItemLine();
                    il.orderType = enumName(line.getOrderType());
                    il.productName = enumName(line.getProductName());
                    il.listingId = line.getListingId();
                    il.itemQuantity = line.getItemQuantity();
                    il.mainProductFlg = line.getMainProductFlg();
                    il.productSize = enumName(line.getProductSize());
                    il.productColor = enumName(line.getProductColor());
                    il.productVariable = enumName(line.getProductVariable());
                    go.itemLines.add(il);
                }
            }
            snap.orders.add(go);
        }
        return snap;
    }

    private static String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }

    public static class OrderGoldenOrder {
        public String orderNumber;
        public String username;
        public int totalItemQuantity;
        public List<OrderGoldenItemLine> itemLines = new ArrayList<>();
    }

    public static class OrderGoldenItemLine {
        public String orderType;
        public String productName;
        public String listingId;
        public int itemQuantity;
        public Boolean mainProductFlg;
        public String productSize;
        public String productColor;
        public String productVariable;
    }
}

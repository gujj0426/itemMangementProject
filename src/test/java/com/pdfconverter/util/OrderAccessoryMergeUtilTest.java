package com.pdfconverter.util;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductName;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductVariable;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OrderAccessoryMergeUtilTest {

    @Test
    void mergesSameBoxVariableAndAppendsAtEnd() {
        List<ItemDetail> in = new ArrayList<>();
        ItemDetail m1 = mainCufflink();
        ItemDetail t1 = tie();
        ItemDetail b1 = boxLargeSquare(1);
        ItemDetail m2 = mainCufflink();
        ItemDetail t2 = tie();
        ItemDetail b2 = boxLargeSquare(1);
        in.add(m1);
        in.add(t1);
        in.add(b1);
        in.add(m2);
        in.add(t2);
        in.add(b2);

        List<ItemDetail> out = OrderAccessoryMergeUtil.mergeIdenticalBoxAccessoriesWithinOrder(in);
        assertEquals(5, out.size());
        assertSame(m1, out.get(0));
        assertSame(t1, out.get(1));
        assertSame(m2, out.get(2));
        assertSame(t2, out.get(3));
        assertSame(b1, out.get(4));
        assertEquals(2, b1.getItemQuantity());
    }

    private static ItemDetail mainCufflink() {
        ItemDetail m = new ItemDetail();
        m.setMainProductFlg(true);
        m.setOrderType(OrderType.CUFFLINK);
        m.setProductName(ProductName.CUFFLINK);
        m.setItemQuantity(1);
        m.setProductSize(ProductSize.L);
        m.setProductColor(ProductColor.GOLD);
        return m;
    }

    private static ItemDetail tie() {
        ItemDetail t = new ItemDetail();
        t.setMainProductFlg(false);
        t.setOrderType(OrderType.TIE_CLIP);
        t.setItemQuantity(1);
        return t;
    }

    private static ItemDetail boxLargeSquare(int qty) {
        ItemDetail b = new ItemDetail();
        b.setMainProductFlg(false);
        b.setOrderType(OrderType.BOX);
        b.setProductVariable(ProductVariable.BOX_LARGE_SQUARE);
        b.setItemQuantity(qty);
        return b;
    }
}

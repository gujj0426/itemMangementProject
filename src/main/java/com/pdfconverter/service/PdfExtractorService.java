package com.pdfconverter.service;

import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfExtractorService {

    public List<PdfOrderData> extractFromPdf(String pdfPath) throws IOException {
        List<PdfOrderData> orders = new ArrayList<>();
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            orders.add(parseOrder(text));
        }
        return orders;
    }

    private PdfOrderData parseOrder(String text) {
        PdfOrderData order = new PdfOrderData();
        List<ItemDetail> items = new ArrayList<>();

        // 提取订单编号
        order.setOrderNumber(extractBetween(text, "Order#", "\\s").trim());

        // 客户姓名 (括号前)
        String customerLine = extractBetween(text, "Order#[^\\n]+", "Ship to");
        order.setCustomerName(customerLine.split("\\(")[0].trim());

        // 用户名 (Ship to 第一行)
        String shipTo = extractBetween(text, "Ship to", "Scheduled to ship by");
        String[] shipLines = shipTo.trim().split("\n");
        order.setUsername(shipLines[0].trim());
        order.setShippingAddress(String.join("\n", Arrays.copyOfRange(shipLines, 1, shipLines.length)).trim());

        // 计划发货日期
        order.setScheduledShippingDate(LocalDate.parse(
                extractBetween(text, "Scheduled to ship by", "Shop").trim(),
                DateTimeFormatter.ofPattern("MMM d, yyyy")));

        // 店铺名
        order.setShopName(extractBetween(text, "Shop", "Order date").trim());

        // 下单日期
        order.setOrderDate(LocalDate.parse(
                extractBetween(text, "Order date", "Payment method").trim(),
                DateTimeFormatter.ofPattern("MMM d, yyyy")));

        // 支付方式
        order.setPaymentMethod(extractBetween(text, "Payment method", "Shipping method").trim());

        // 物流方式
        order.setShippingMethod(extractBetween(text, "Shipping method", "Packaging").trim());

        // 包装信息
        order.setPackagingInfo(extractBetween(text, "Packaging", "Tracking").trim());

        // 跟踪信息
        String trackingLine = extractBetween(text, "Tracking", "\\d+\\s+items").trim();
        order.setTrackingInfo(trackingLine);
        String[] trackParts = trackingLine.split("via");
        order.setTrackingNumber(trackParts[0].trim());
        order.setCourierCompany(trackParts.length > 1 ? trackParts[1].trim() : "");

        // 商品数量
        String itemLine = extractBetween(text, "Tracking.*?\\n", "\\d+\\s+items").trim();
        int itemCount = Integer.parseInt(itemLine.replaceAll("\\D+", ""));
        order.setTotalItemQuantity(itemCount);

        // 商品块提取
        String[] itemBlocks = text.split("(?=Custom Engraved Initials)");
        for (int i = 1; i < itemBlocks.length && i <= itemCount; i++) {
            items.add(parseItemDetail(itemBlocks[i]));
        }

        order.setItemDetails(items);
        order.setAdditionalNote(extractAfter(text, "Do the green thing"));

        return order;
    }

    private ItemDetail parseItemDetail(String block) {
        ItemDetail item = new ItemDetail();

        // 1. 提取商品标题：从块开头到 "Quantity:" 之前
        int qtyIndex = block.indexOf("Quantity:");
        if (qtyIndex == -1) {
            throw new IllegalArgumentException("Invalid block: missing 'Quantity:'");
        }
        item.setItemTitle(block.substring(0, qtyIndex).trim());

        // 2. 提取 Quantity
        String qtyLine = extractLineAfter(block, "Quantity:");
        int quantity = 1;
        try {
            quantity = Integer.parseInt(qtyLine.trim().split("\\s+")[0]);
        } catch (Exception e) {
            quantity = 1; // 默认
        }
        item.setItemQuantity(quantity);

        // 3. 提取 Personalization 内容：从 "Personalization:" 开始，直到下一个商品块或文件结束
        String personalization = "";
        int personalizationStart = block.indexOf("Personalization:");
        if (personalizationStart != -1) {
            personalizationStart += "Personalization:".length();
            int nextItemStart = block.indexOf("Custom Engraved Initials", personalizationStart);
            int end = (nextItemStart == -1) ? block.length() : nextItemStart;
            personalization = block.substring(personalizationStart, end).trim();
        }
        item.setItemPersonalization(personalization);

        // 4. 提取 Quantity 到 Personalization 之间的“动态属性”部分
        String dynamicSection = block.substring(qtyIndex + "Quantity:".length(), block.indexOf("Personalization:")).trim();

        // 5. 按行分割动态属性（保留换行）
        String[] dynamicLines = dynamicSection.split("\n");
        Map<String, String> dynamicAttrsMap = new LinkedHashMap<>(); // 保持顺序
        StringBuilder fullDynamicInfo = new StringBuilder();

        for (String line : dynamicLines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 拼接“商品信息栏完全信息”（保留原始格式）
            if (fullDynamicInfo.length() > 0) fullDynamicInfo.append("\n");
            fullDynamicInfo.append(line);

            // 尝试解析 key: value
            int colon = line.indexOf(":");
            if (colon != -1) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                dynamicAttrsMap.put(key, value);
            } else {
                // 无冒号的行，作为上一个 key 的延续（罕见情况）
                if (!dynamicAttrsMap.isEmpty()) {
                    String lastKey = new ArrayList<>(dynamicAttrsMap.keySet()).get(dynamicAttrsMap.size() - 1);
                    dynamicAttrsMap.put(lastKey, dynamicAttrsMap.get(lastKey) + " " + line);
                }
            }
        }

        // 6. 从所有动态属性中提取 Size 和 Color
        String size = null, color = null;
        for (Map.Entry<String, String> entry : dynamicAttrsMap.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue();

            if (key.contains("size") && size == null) {
                size = extractSizeFromValue(value);
            }
            if ((key.contains("color") || key.contains("colour")) && color == null) {
                color = extractColorFromValue(value);
            }

            // 如果 Size 和 Color 都在同一个字段（如 "Size and Color: Gold_L"）
            if (key.contains("size") && key.contains("color") && value.contains("_")) {
                String[] parts = value.split("_");
                if (parts.length >= 2) {
                    color = parts[0].trim();
                    size = parts[1].trim();
                }
            }
        }

        item.setSize(size);
        item.setColor(color);

        // 7. 设置“商品信息栏完全信息” = Quantity行 + 动态属性部分（含换行）
        item.setInformation("Quantity: " + quantity + "\n" + fullDynamicInfo.toString());

        // 8. 拼装“动态属性完全信息”用于“商品信息栏完全信息”（可选，你需求是上面那行）
        StringBuilder dynamicAttrsFull = new StringBuilder();
        for (Map.Entry<String, String> entry : dynamicAttrsMap.entrySet()) {
            if (dynamicAttrsFull.length() > 0) dynamicAttrsFull.append("\n");
            dynamicAttrsFull.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        item.setDynamicAttributes(dynamicAttrsFull.toString());

        // 9. 判断订单类型
        Set<String> types = new HashSet<>();
        String titleLower = item.getItemTitle().toLowerCase();
        if (titleLower.contains("pet") || (titleLower.contains("cufflinks") && titleLower.contains("pet"))) {
            types.add("宠物头像");
        }
        if (dynamicAttrsMap.values().stream().anyMatch(v -> v.contains("Cufflink"))) {
            types.add("袖扣");
        }
        if (dynamicAttrsMap.values().stream().anyMatch(v -> v.contains("TieClip"))) {
            types.add("领带夹");
        }
        item.setOrderType(String.join(",", types));

        // 10. 提取包装盒类型
        String packagingBox = "";
        for (String value : dynamicAttrsMap.values()) {
            if (value.contains("Oval Box")) {
                packagingBox = "Oval Box";
                break;
            } else if (value.contains("Square Box")) {
                packagingBox = "Square Box";
                break;
            } else if (value.contains("Box")) {
                packagingBox = "Box";
            }
        }
        item.setPackagingBox(packagingBox);

        // 11. 提取字体（调用工具类）
        item.setFont(PersonalizationParser.extractFont(personalization));

        return item;
    }

    private String extractBetween(String text, String startRegex, String endRegex) {
        Pattern pattern = Pattern.compile(startRegex + "(.*?)" + endRegex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractAfter(String text, String marker) {
        int index = text.indexOf(marker);
        return index != -1 ? text.substring(index).trim() : "";
    }
    // 从 value 中提取 Size（如 L, S）
    private String extractSizeFromValue(String value) {
        value = value.toUpperCase();
        if (value.contains("L")) return "L";
        if (value.contains("S")) return "S";
        return null;
    }

    // 从 value 中提取 Color（如 Gold, Silver, Rose Gold）
    private String extractColorFromValue(String value) {
        value = value.toLowerCase();
        if (value.contains("gold") && value.contains("rose")) return "Rose Gold";
        if (value.contains("gold")) return "Gold";
        if (value.contains("silver")) return "Silver";
        if (value.contains("black")) return "Black";
        return null;
    }

    // 提取某关键字后的第一行
    private String extractLineAfter(String text, String keyword) {
        int index = text.indexOf(keyword);
        if (index == -1) return "";
        index += keyword.length();
        int eol = text.indexOf("\n", index);
        return eol == -1 ? text.substring(index).trim() : text.substring(index, eol).trim();
    }
}
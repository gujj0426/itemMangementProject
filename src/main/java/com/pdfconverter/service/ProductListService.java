package com.pdfconverter.service;

import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductName;
import com.pdfconverter.constant.ProductSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 产品清单服务
 * 从 产品清单.csv 加载产品信息
 * 提供根据产品属性查询中文名称的功能
 */
@Component
public class ProductListService {
    
    private static final Logger log = LoggerFactory.getLogger(ProductListService.class);
    
    private static final String CSV_FILE = "产品清单.csv";
    
    // 产品Key -> 中文名称 (精确匹配) Key: 产品名称+变量+颜色+型号
    private Map<ProductKey, String> exactMatchMap;
    
    // 产品名称 -> 产品列表 (模糊匹配，key为CSV"产品名称"列)
    private Map<String, List<ProductInfo>> productNameMap;
    
    // 产品细类 -> 产品列表 (按细类查询，key为CSV"产品细类"列)
    private Map<String, List<ProductInfo>> subClassMap;
    
    // 默认中文名称映射
    private Map<String, String> defaultChineseNameMap;
    
    @PostConstruct
    public void init() {
        loadProductList();
    }
    
    /**
     * 加载产品清单CSV文件
     */
    public void loadProductList() {
        long startTime = System.currentTimeMillis();
        log.info("开始加载产品清单：{}", CSV_FILE);
        
        try {
            // 初始化Map
            exactMatchMap = new ConcurrentHashMap<>();
            productNameMap = new ConcurrentHashMap<>();
            subClassMap = new ConcurrentHashMap<>();
            defaultChineseNameMap = new ConcurrentHashMap<>();
            
            // 自动检测编码：依次尝试 UTF-8(BOM)、UTF-8、GBK
            Charset charset = detectCsvCharset(CSV_FILE);
            log.info("CSV文件编码检测结果：{}", charset.name());
            
            // 读取CSV文件
            FileInputStream fis = new FileInputStream(CSV_FILE);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(fis, charset)
            );
            
            String line;
            int lineNumber = 0;
            int loadedCount = 0;
            
            // 读取表头
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.error("CSV文件为空：{}", CSV_FILE);
                return;
            }
            // 去除 UTF-8 BOM（EF BB BF 在字符串里表现为 \uFEFF）
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            
            // 解析表头
            String[] headers = parseCsvLine(headerLine);
            Map<String, Integer> headerIndexMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerIndexMap.put(headers[i].trim(), i);
            }
            
            // 检查必需字段
            if (!headerIndexMap.containsKey("产品名称")) {
                log.error("CSV文件缺少必需字段：产品名称");
                return;
            }
            
            // 读取数据行
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    String[] fields = parseCsvLine(line);
                    if (fields.length < headerIndexMap.size()) {
                        log.warn("第 {} 行数据不完整，跳过", lineNumber);
                        continue;
                    }
                    
                    // 解析产品信息
                    ProductInfo productInfo = parseProductInfo(fields, headerIndexMap);
                    if (productInfo == null) {
                        continue;
                    }
                    
                    // 添加到精确匹配Map
                    ProductKey key = new ProductKey(
                        productInfo.productName,
                        productInfo.productVariable,
                        productInfo.color,
                        productInfo.size
                    );
                    exactMatchMap.put(key, productInfo.chineseName);
                    
                    // 添加到产品名称模糊匹配Map（key=CSV产品名称列）
                    String productNameKey = productInfo.productName.toLowerCase();
                    productNameMap.computeIfAbsent(productNameKey, k -> new ArrayList<>()).add(productInfo);
                    
                    // 添加到细类Map（key=产品细类，如"袖扣"/"领带夹"/"包装盒"）
                    if (productInfo.subClass != null && !productInfo.subClass.isEmpty()) {
                        subClassMap.computeIfAbsent(productInfo.subClass, k -> new ArrayList<>()).add(productInfo);
                    }
                    
                    // 记录默认中文名称（不带属性的产品名称）
                    if (isDefaultProduct(productInfo)) {
                        defaultChineseNameMap.put(productInfo.productName, productInfo.chineseName);
                    }
                    
                    loadedCount++;
                    
                } catch (Exception e) {
                    log.warn("解析第 {} 行失败：{}", lineNumber, e.getMessage());
                }
            }
            
            reader.close();
            
            long endTime = System.currentTimeMillis();
            log.info("产品清单加载完成，共 {} 条记录，耗时 {}ms", loadedCount, endTime - startTime);
            
        } catch (Exception e) {
            log.error("加载产品清单失败：" + CSV_FILE, e);
        }
    }
    
    /**
     * 解析CSV行（支持包含逗号的字段）
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString().trim());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }
        
        fields.add(currentField.toString().trim());
        return fields.toArray(new String[0]);
    }
    
    /**
     * 解析产品信息
     */
    private ProductInfo parseProductInfo(String[] fields, Map<String, Integer> headerIndexMap) {
        ProductInfo info = new ProductInfo();
        
        // 产品名称（必需）
        Integer nameIdx = headerIndexMap.get("产品名称");
        if (nameIdx != null && nameIdx < fields.length) {
            info.productName = fields[nameIdx].trim();
        }
        
        if (info.productName == null || info.productName.isEmpty()) {
            return null;
        }
        
        // 产品细类（如"袖扣"/"领带夹"/"包装盒"）
        Integer subClassIdx = headerIndexMap.get("产品细类");
        if (subClassIdx != null && subClassIdx < fields.length) {
            info.subClass = fields[subClassIdx].trim();
            if (info.subClass.isEmpty()) {
                info.subClass = null;
            }
        }
        
        // 颜色
        Integer colorIdx = headerIndexMap.get("颜色");
        if (colorIdx != null && colorIdx < fields.length) {
            info.color = fields[colorIdx].trim();
            if (info.color.isEmpty() || "-".equals(info.color)) {
                info.color = null;
            }
        }
        
        // 型号（尺寸）
        Integer sizeIdx = headerIndexMap.get("型号");
        if (sizeIdx != null && sizeIdx < fields.length) {
            info.size = fields[sizeIdx].trim();
            if (info.size.isEmpty() || "-".equals(info.size)) {
                info.size = null;
            }
        }
        
        // 产品变量
        Integer varIdx = headerIndexMap.get("产品变量");
        if (varIdx != null && varIdx < fields.length) {
            info.productVariable = fields[varIdx].trim();
            if (info.productVariable.isEmpty() || "-".equals(info.productVariable)) {
                info.productVariable = null;
            }
        }
        
        // 中文名称：直接使用"产品名称"列（CSV本身的产品名称就是中文名）
        info.chineseName = info.productName;
        
        return info;
    }
    
    /**
     * 判断是否默认产品（不带具体属性）
     */
    private boolean isDefaultProduct(ProductInfo info) {
        return info.color == null && info.size == null && info.productVariable == null;
    }
    
    /**
     * 根据产品细类查询中文产品名称（支持产品名称过滤）
     * 与四参数版本相比，额外通过 productNameFilter 过滤候选记录，
     * 确保返回的产品名称（CSV"产品名称"列）与期望的匹配。
     *
     * @param subClass            产品细类（如"领带夹"）
     * @param sizeCode            型号代码（如"L"）
     * @param color               颜色中文名（如"黑色"）
     * @param productVariable     产品变量（如"Oval Box-椭圆形开窗木盒"）
     * @param productNameFilter   期望的产品名称关键词（如"双面滑入式领带夹"），用于在细类匹配结果中二次过滤
     * @return 产品名称，如果未找到返回null
     */
    public String getChineseProductNameBySubClass(String subClass, String sizeCode, String color,
                                                    String productVariable, String productNameFilter) {
        if (productNameFilter == null || productNameFilter.isEmpty()) {
            return getChineseProductNameBySubClass(subClass, sizeCode, color, productVariable);
        }

        List<ProductInfo> candidates = subClassMap.get(subClass);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        // 先过滤出产品名称包含关键词的候选记录
        List<ProductInfo> filtered = new ArrayList<>();
        for (ProductInfo c : candidates) {
            if (c.chineseName != null && c.chineseName.contains(productNameFilter)) {
                filtered.add(c);
            }
        }

        // 如果有匹配的候选记录，在其中用属性打分查找
        if (!filtered.isEmpty()) {
            List<ProductMatch> matches = new ArrayList<>();
            for (ProductInfo candidate : filtered) {
                int score = 0;
                if (sizeCode != null && sizeCode.equals(candidate.size)) score += 30;
                else if (sizeCode == null && candidate.size == null) score += 15;
                if (color != null && color.equals(candidate.color)) score += 30;
                else if (color == null && candidate.color == null) score += 15;
                if (productVariable != null && productVariable.equals(candidate.productVariable)) score += 50;
                else if (productVariable == null && candidate.productVariable == null) score += 20;
                if (score > 0) matches.add(new ProductMatch(candidate, score));
            }
            if (!matches.isEmpty()) {
                matches.sort((a, b) -> Integer.compare(b.score, a.score));
                return matches.get(0).productInfo.chineseName;
            }
            // 属性不匹配但有名称匹配，返回第一个
            return filtered.get(0).chineseName;
        }

        return null;
    }

    /**
     * 根据产品细类查询中文产品名称
     * 这是产品名称匹配的核心方法：细类(subClass) + 型号(L/S) + 颜色(金色/银色) + 变量 → 产品名称
     *
     * @param subClass        产品细类（如"袖扣"、"领带夹"、"包装盒"）
     * @param sizeCode        型号代码（如"L"、"S"，对应CSV"型号"列）
     * @param color           颜色中文名（如"金色"、"银色"，对应CSV"颜色"列）
     * @param productVariable 产品变量（如"Box-长方形木盒"，对应CSV"产品变量"列）
     * @return 产品名称（CSV的"产品名称"列，如"单抛袖扣-过渡"、"鸭嘴领带夹（薄）"）
     */
    public String getChineseProductNameBySubClass(String subClass, String sizeCode, String color, String productVariable) {
        if (subClass == null || subClass.isEmpty()) {
            return null;
        }
        
        List<ProductInfo> candidates = subClassMap.get(subClass);
        if (candidates == null || candidates.isEmpty()) {
            log.warn("产品细类[{}]在产品清单中无记录", subClass);
            return null;
        }
        
        // 精确匹配：细类+型号+颜色+变量全部匹配
        for (ProductInfo candidate : candidates) {
            boolean sizeMatch = sizeCode == null ? candidate.size == null 
                              : sizeCode.equals(candidate.size);
            boolean colorMatch = color == null ? candidate.color == null 
                               : color.equals(candidate.color);
            boolean varMatch = productVariable == null ? candidate.productVariable == null 
                             : productVariable.equals(candidate.productVariable);
            if (sizeMatch && colorMatch && varMatch) {
                log.debug("细类[{}]精确匹配：size={}, color={}, var={} -> {}", 
                         subClass, sizeCode, color, productVariable, candidate.chineseName);
                return candidate.chineseName;
            }
        }
        
        // 部分匹配：按颜色+型号匹配（变量不参与，允许变量为空的记录）
        List<ProductMatch> matches = new ArrayList<>();
        for (ProductInfo candidate : candidates) {
            int score = 0;
            // 型号匹配
            if (sizeCode != null && sizeCode.equals(candidate.size)) {
                score += 30;
            } else if (sizeCode == null && candidate.size == null) {
                score += 15;
            }
            // 颜色匹配
            if (color != null && color.equals(candidate.color)) {
                score += 30;
            } else if (color == null && candidate.color == null) {
                score += 15;
            }
            // 产品变量匹配（优先级最高）
            if (productVariable != null && productVariable.equals(candidate.productVariable)) {
                score += 50;
            } else if (productVariable == null && candidate.productVariable == null) {
                score += 20;
            }
            if (score > 0) {
                matches.add(new ProductMatch(candidate, score));
            }
        }
        
        if (!matches.isEmpty()) {
            matches.sort((a, b) -> Integer.compare(b.score, a.score));
            String result = matches.get(0).productInfo.chineseName;
            log.info("细类[{}]最优匹配：size={}, color={}, var={} -> {} (score={})", 
                     subClass, sizeCode, color, productVariable, result, matches.get(0).score);
            return result;
        }
        
        // 兜底：如果没有尺寸和颜色信息，返回null让上层用displayName兜底
        // 避免无属性时错误返回CSV第一条记录（如"鸭嘴领带夹（薄）"）
        if (sizeCode == null && color == null && productVariable == null) {
            log.warn("细类[{}]无尺寸/颜色/变量信息，返回null由上层兜底", subClass);
            return null;
        }
        log.warn("细类[{}]无法匹配size={}, color={}, var={}，返回第一条", subClass, sizeCode, color, productVariable);
        return candidates.get(0).chineseName;
    }
    
    /**
     * 获取中文产品名称（原有方法，保持兼容）
     * 优先精确匹配，其次模糊匹配，最后返回默认值
     * @param productName 产品英文名称
     * @param productVariable 产品变量（如月份）
     * @param color 颜色中文名称
     * @param size 尺寸
     * @return 中文产品名称
     */
    public String getChineseProductName(String productName, 
                                         String productVariable, 
                                         String color, 
                                         String size) {
        if (productName == null || productName.isEmpty()) {
            return "未知产品";
        }
        
        // 1. 精确匹配
        ProductKey exactKey = new ProductKey(productName, productVariable, color, size);
        String exactMatch = exactMatchMap.get(exactKey);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // 2. 模糊匹配：查找相同产品名称的所有记录
        String productNameKey = productName.toLowerCase();
        List<ProductInfo> candidates = productNameMap.get(productNameKey);
        
        if (candidates != null && !candidates.isEmpty()) {
            // 按匹配度排序
            List<ProductMatch> matches = new ArrayList<>();
            for (ProductInfo candidate : candidates) {
                int score = calculateMatchScore(candidate, productVariable, color, size);
                if (score > 0) {
                    matches.add(new ProductMatch(candidate, score));
                }
            }
            
            // 按分数排序，返回最高分
            if (!matches.isEmpty()) {
                matches.sort((a, b) -> Integer.compare(b.score, a.score));
                return matches.get(0).productInfo.chineseName;
            }
        }
        
        // 3. 返回默认值
        String defaultName = defaultChineseNameMap.get(productName);
        if (defaultName != null) {
            return defaultName;
        }
        
        // 4. 如果没有默认值，返回原始产品名称
        log.warn("未找到产品 [{}] 的中文名称映射", productName);
        return productName;
    }
    
    /**
     * 计算匹配分数
     */
    private int calculateMatchScore(ProductInfo candidate, String productVariable, String color, String size) {
        int score = 0;
        
        // 产品变量匹配（权重最高）
        if (productVariable != null && productVariable.equals(candidate.productVariable)) {
            score += 100;
        } else if (candidate.productVariable == null && productVariable == null) {
            score += 50;
        }
        
        // 颜色匹配（权重中等）
        if (color != null && color.equals(candidate.color)) {
            score += 30;
        } else if (candidate.color == null && color == null) {
            score += 15;
        }
        
        // 尺寸匹配（权重较低）
        if (size != null && size.equals(candidate.size)) {
            score += 20;
        } else if (candidate.size == null && size == null) {
            score += 10;
        }
        
        return score;
    }
    
    /**
     * 获取所有产品名称列表
     */
    public Set<String> getAllProductNames() {
        return productNameMap.keySet().stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    }
    
    /**
     * 检查产品是否存在
     */
    public boolean containsProduct(String productName) {
        if (productName == null) {
            return false;
        }
        return productNameMap.containsKey(productName.toLowerCase());
    }
    
    /**
     * 自动检测 CSV 文件编码
     * 检测顺序：UTF-8 BOM → UTF-8（含"产品名称"则用UTF-8）→ GBK
     */
    private Charset detectCsvCharset(String filePath) {
        try {
            byte[] bom = new byte[3];
            FileInputStream fis = new FileInputStream(filePath);
            int read = fis.read(bom, 0, 3);
            fis.close();
            // 检测 UTF-8 BOM (EF BB BF)
            if (read >= 3 && bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF) {
                return StandardCharsets.UTF_8;
            }
            // 尝试 UTF-8 读取表头，看是否包含"产品名称"
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
                String line = br.readLine();
                if (line != null && line.contains("产品名称")) {
                    return StandardCharsets.UTF_8;
                }
            }
        } catch (Exception e) {
            log.warn("编码检测失败，降级为GBK：{}", e.getMessage());
        }
        // 默认使用 GBK（Windows 中文环境下 Excel 保存的 CSV）
        return Charset.forName("GBK");
    }

    /**
     * 重新加载产品清单
     */
    public void reloadProductList() {
        log.info("重新加载产品清单...");
        loadProductList();
    }
    
    /**
     * 获取已加载的产品数量
     */
    public int getLoadedProductCount() {
        return exactMatchMap != null ? exactMatchMap.size() : 0;
    }
    
    /**
     * 产品信息类
     */
    private static class ProductInfo {
        String productName;    // CSV"产品名称"列（即中文名称，如"单抛袖扣-过渡"）
        String subClass;       // CSV"产品细类"列（如"袖扣"/"领带夹"/"包装盒"）
        String productVariable;
        String color;
        String size;
        String chineseName;    // 最终输出名称，等同于productName
    }
    
    /**
     * 产品匹配类
     */
    private static class ProductMatch {
        ProductInfo productInfo;
        int score;
        
        ProductMatch(ProductInfo productInfo, int score) {
            this.productInfo = productInfo;
            this.score = score;
        }
    }
    
    /**
     * 产品Key类（用于精确匹配）
     */
    public static class ProductKey {
        private final String productName;
        private final String productVariable;
        private final String color;
        private final String size;
        
        public ProductKey(String productName, String productVariable, String color, String size) {
            this.productName = productName != null ? productName : "";
            this.productVariable = productVariable != null ? productVariable : "";
            this.color = color != null ? color : "";
            this.size = size != null ? size : "";
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProductKey that = (ProductKey) o;
            return productName.equals(that.productName) &&
                   productVariable.equals(that.productVariable) &&
                   color.equals(that.color) &&
                   size.equals(that.size);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(productName, productVariable, color, size);
        }
    }

    /** 返回产品清单条目总数（用于管理接口状态查询） */
    public int getProductCount() {
        return productNameMap.values().stream().mapToInt(List::size).sum();
    }
}

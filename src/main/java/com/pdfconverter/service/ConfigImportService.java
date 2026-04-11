package com.pdfconverter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pdfconverter.config.AccessoryRuleConfig;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 配置模板导入服务
 * 解析 listing-config*.xlsx 模板文件，自动更新3个配置文件：
 *   1. product-title-recognition-rules.properties
 *   2. product-attribute-labels.json
 *   3. accessory-rules.json
 */
@Service
public class ConfigImportService {

    private static final Logger log = LoggerFactory.getLogger(ConfigImportService.class);

    // =========================================
    // 配置路径
    // =========================================
    @Value("${app.pdf.input-folder}")
    private String inputFolder;

    @Value("${app.config.database-file:}")
    private String databaseFilePath;

    // classpath 内的资源路径（用于定位 resources 目录）
    private String resourcesDir;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // =========================================
    // 公共入口：处理输入目录中的所有配置模板
    // =========================================

    /**
     * 扫描输入目录，处理所有 listing-config*.xlsx 文件
     * @return 处理了几个文件
     */
    public int processConfigFiles() {
        File dir = new File(inputFolder);
        if (!dir.exists()) {
            return 0;
        }

        File[] configFiles = dir.listFiles(f ->
                f.isFile() && f.getName().startsWith("listing-config") && f.getName().endsWith(".xlsx"));

        if (configFiles == null || configFiles.length == 0) {
            return 0;
        }

        int processedCount = 0;
        for (File configFile : configFiles) {
            try {
                log.info("发现配置模板文件: {}，开始处理...", configFile.getName());
                ImportResult result = importFromExcel(configFile);
                writeResultLog(configFile, result);
                moveFile(configFile, "processed");
                log.info("✅ 配置模板处理完成: {}，新增 {} 条 Listing，更新 {} 条，附加规则同步 {} 条",
                        configFile.getName(), result.newListings, result.updatedListings, result.syncedRules);
                processedCount++;
            } catch (Exception e) {
                log.error("❌ 配置模板处理失败: {} - {}", configFile.getName(), e.getMessage(), e);
                try {
                    writeErrorLog(configFile, e);
                    moveFile(configFile, "error");
                } catch (Exception ex) {
                    log.error("移动错误文件失败: {}", ex.getMessage());
                }
            }
        }
        return processedCount;
    }

    // =========================================
    // 核心：解析 Excel 并更新配置
    // =========================================

    public ImportResult importFromExcel(File excelFile) throws Exception {
        ImportResult result = new ImportResult();

        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // ---- 读取 Sheet1: Listing基本信息 ----
            Sheet sheet1 = workbook.getSheet("Listing基本信息");
            if (sheet1 == null) {
                throw new IllegalArgumentException("未找到 Sheet 'Listing基本信息'，请确保模板格式正确");
            }
            List<ListingRow> listingRows = parseListingSheet(sheet1, result);

            // ---- 读取 Sheet2: 属性标签配置 ----
            Sheet sheet2 = workbook.getSheet("属性标签配置");
            if (sheet2 == null) {
                throw new IllegalArgumentException("未找到 Sheet '属性标签配置'，请确保模板格式正确");
            }
            Map<String, List<AttrLabelRow>> attrMap = parseAttrSheet(sheet2);

            // ---- 读取 Sheet3: 附加商品规则 ----
            Sheet sheet3 = workbook.getSheet("附加商品规则");
            if (sheet3 == null) {
                throw new IllegalArgumentException("未找到 Sheet '附加商品规则'，请确保模板格式正确");
            }
            List<AccessoryRuleConfig.AccessoryRule> accessoryRules = parseAccessoryRuleSheet(sheet3);
            result.syncedRules = accessoryRules.size();

            // ---- 更新各配置文件 ----
            updateTitleRecognitionRules(listingRows, result);
            updateAttributeLabelsJson(listingRows, attrMap, result);
            updateAccessoryRulesJson(accessoryRules);

            // ---- listingId 回写到 Excel ----
            writeBackListingIds(excelFile, workbook, sheet1, listingRows);
        }

        return result;
    }

    // =========================================
    // Sheet1 解析：Listing 基本信息
    // =========================================

    private List<ListingRow> parseListingSheet(Sheet sheet, ImportResult result) {
        List<ListingRow> rows = new ArrayList<>();
        // 第1行是说明行，第2行是表头，数据从第3行开始（index=2）
        for (int i = 2; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row)) continue;

            ListingRow lr = new ListingRow();
            lr.rowIndex = i;
            lr.listingId    = getStringCell(row, 0);
            lr.titleRuleKey = getStringCell(row, 1);
            lr.titleKeywords = getStringCell(row, 2);
            lr.productName  = getStringCell(row, 3);
            lr.orderType    = getStringCell(row, 4);
            lr.isCombo      = getBoolCell(row, 5);
            lr.comment      = getStringCell(row, 6);

            if (lr.titleKeywords.isEmpty() || lr.productName.isEmpty() || lr.orderType.isEmpty()) {
                log.warn("Sheet1 第 {} 行数据不完整，跳过 (keywords={}, productName={}, orderType={})",
                        i + 1, lr.titleKeywords, lr.productName, lr.orderType);
                continue;
            }

            // 生成 listingId（若未填写）
            if (lr.listingId.isEmpty()) {
                lr.listingId = generateListingId(lr.productName, lr.titleRuleKey);
                lr.isNew = true;
                result.newListings++;
                log.info("  新增 Listing，生成 listingId={} (行{})", lr.listingId, i + 1);
            } else {
                lr.isNew = false;
                result.updatedListings++;
                log.info("  更新 Listing listingId={} (行{})", lr.listingId, i + 1);
            }

            rows.add(lr);
        }
        return rows;
    }

    // =========================================
    // Sheet2 解析：属性标签（业务友好版 v2）
    //
    // 新版列顺序（14列）：
    //  0  listingId / ProductName
    //  1  序号
    //  2  labelName（PDF属性行名称）
    //  3  此行的作用（中文选项）
    //  4  包含颜色?  (YES/NO)
    //  5  包含尺寸?  (YES/NO)
    //  6  包含刻录面数? (YES/NO)
    //  7  包含片数(拆行)? (YES/NO)
    //  8  值的格式示例
    //  9  此行出现的商品名（逗号分隔）
    //  10 礼盒款式映射（A→B 逗号分隔）
    //  11 特殊产品名称覆盖（类型→产品名 逗号分隔）
    //  12 必填? (YES/NO)
    //  13 备注
    // =========================================

    private Map<String, List<AttrLabelRow>> parseAttrSheet(Sheet sheet) {
        Map<String, List<AttrLabelRow>> map = new LinkedHashMap<>();
        // 新版：第1行说明，第2行中文选项说明，第3行表头，数据从第4行开始（index=3）
        // 兼容旧版（第1行说明，第2行表头，数据从第3行开始 index=2）
        int dataStartRow = detectAttrSheetDataStart(sheet);
        log.debug("属性标签配置 Sheet 数据起始行: {}", dataStartRow + 1);

        for (int i = dataStartRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row)) continue;

            String col0 = getStringCell(row, 0);
            if (col0.isEmpty()) continue;

            AttrLabelRow alr = new AttrLabelRow();
            alr.listingId = col0;
            alr.seq       = getStringCell(row, 1);
            alr.labelName = getStringCell(row, 2);

            // 判断是新版（第4列是中文作用选项）还是旧版（第4列是 attributeType 枚举）
            String col3 = getStringCell(row, 3);
            if (isChineseRoleOption(col3)) {
                // ── 新版：从业务描述翻译技术参数 ──────────────────
                boolean hasColor   = getBoolCell(row, 4);
                boolean hasSize    = getBoolCell(row, 5);
                boolean hasEngrave = getBoolCell(row, 6);
                boolean hasPiece   = getBoolCell(row, 7);
                alr.formatExample  = getStringCell(row, 8);
                String itemNames   = getStringCell(row, 9);   // 商品名列表
                String boxMapping  = getStringCell(row, 10);  // 礼盒映射
                String nameOverride= getStringCell(row, 11);  // 产品名覆盖
                alr.required       = getBoolCell(row, 12);

                // 翻译：中文作用 → attributeType + pattern + groupMapping + separators
                translateRoleToAttrType(alr, col3, hasColor, hasSize, hasEngrave, hasPiece);

                // 翻译：商品名列表 → itemTypeMapping（自动推导）
                alr.itemTypeMapping = buildItemTypeMapping(itemNames);

                // 翻译：礼盒映射 A→B,C→D → boxVariableMapping JSON
                alr.boxVariableMapping = buildArrowMapping(boxMapping);

                // 翻译：产品名覆盖 Type→ProductName → productNameMapping（合并默认值）
                alr.productNameMapping = buildProductNameMapping(itemNames, nameOverride);

            } else {
                // ── 旧版兼容：直接读取技术参数 ──────────────────────
                alr.attributeType  = col3;
                alr.valuePattern   = getStringCell(row, 4);
                alr.groupMapping   = getStringCell(row, 5);
                alr.required       = getBoolCell(row, 6);
                alr.separators     = getStringCell(row, 7);
                alr.itemTypeMapping    = getStringCell(row, 8);
                alr.productNameMapping = getStringCell(row, 9);
                alr.boxVariableMapping = getStringCell(row, 10);
            }

            if (alr.listingId.isEmpty() || alr.labelName.isEmpty() || alr.attributeType.isEmpty()) {
                log.warn("属性标签配置第 {} 行数据不完整，跳过 (listingId={}, labelName={}, type={})",
                        i + 1, alr.listingId, alr.labelName, alr.attributeType);
                continue;
            }

            map.computeIfAbsent(alr.listingId, k -> new ArrayList<>()).add(alr);
        }
        return map;
    }

    /**
     * 自动检测 Sheet2 数据行的起始行 index
     * 新版：第3行（index=2）是表头 → 数据从第4行（index=3）开始
     * 旧版：第2行（index=1）是表头 → 数据从第3行（index=2）开始
     */
    private int detectAttrSheetDataStart(Sheet sheet) {
        // 检测第3行第1格是否为表头关键词
        Row row2 = sheet.getRow(2);
        if (row2 != null) {
            String cell0 = getStringCell(row2, 0);
            // 新版表头第一列是 "listingId / ProductName..."
            if (cell0.contains("listingId") || cell0.contains("listingid")
                    || cell0.toLowerCase().contains("listing")) {
                return 3; // 数据从第4行开始
            }
        }
        return 2; // 旧版，数据从第3行开始
    }

    /**
     * 判断是否为新版中文作用选项（区别于旧版的英文 attributeType）
     */
    private boolean isChineseRoleOption(String val) {
        if (val == null || val.isEmpty()) return false;
        // 新版选项都含有中文字符
        return val.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    /**
     * 从中文作用描述翻译到 attributeType / valuePattern / groupMapping / separators
     */
    private void translateRoleToAttrType(AttrLabelRow alr, String role,
                                          boolean hasColor, boolean hasSize,
                                          boolean hasEngrave, boolean hasPiece) {
        switch (role.trim()) {
            case "纯颜色行":
                alr.attributeType = "COLOR";
                break;
            case "硅胶绑带颜色行":
                // 与 COLOR 相同，但标记为硅胶绑带专用（通过 labelName 区分）
                alr.attributeType = "COLOR";
                break;
            case "纯尺寸行":
                alr.attributeType = "SIZE";
                break;
            case "颜色+尺寸行":
                alr.attributeType = "COMPOSITE";
                // 自动推导正则：默认下划线分隔
                alr.valuePattern = "([^_]+)_([^_]+)";
                // 兼容 Color & Color Options 的 /M 后缀（尼龙狗牌）
                if (alr.formatExample != null && alr.formatExample.contains("/M")) {
                    alr.valuePattern = "([^_]+)_([^/]+)(/M)?";
                }
                alr.groupMapping = "{\"1\":\"COLOR\",\"2\":\"SIZE\"}";
                break;
            case "颜色+礼盒行":
                alr.attributeType = "COLOR_WITH_ACCESSORY";
                alr.valuePattern = "([^+]+)\\s*\\+\\s*(.+)";
                alr.groupMapping = "{\"1\":\"COLOR\",\"2\":\"ACCESSORY_ITEM\"}";
                break;
            case "颜色+商品名+礼盒行":
                alr.attributeType = "COLOR_ITEM_COMBO";
                alr.valuePattern = "(\\w+)\\s+(\\w+)\\s*\\+\\s*(.+)";
                alr.groupMapping = "{\"1\":\"COLOR\",\"2\":\"PRODUCT_NAME\",\"3\":\"ACCESSORY_ITEM\"}";
                alr.separators = "+,";
                break;
            case "商品清单行":
                alr.attributeType = "ACCESSORY_ITEMS";
                alr.separators = "+,";
                break;
            case "刻录面数行":
                alr.attributeType = "ENGRAVING_SIDES";
                break;
            case "片数+尺寸行（拆行）":
                alr.attributeType = "QUANTITY_WITH_SIZE";
                alr.valuePattern = "(\\d+)\\s+Disc_([A-Za-z]+)";
                alr.groupMapping = "{\"1\":\"PIECE_COUNT\",\"2\":\"SIZE\"}";
                break;
            case "产品变量行":
                alr.attributeType = "PRODUCT_VARIABLE";
                // 若格式示例含数字开头（月份类），自动加提取前缀正则
                if (alr.formatExample != null && alr.formatExample.matches("\\d+.*")) {
                    alr.valuePattern = "^\\d+";
                }
                break;
            case "设计风格行":
                alr.attributeType = "STYLE";
                break;
            case "字体选项行":
                alr.attributeType = "FONT";
                break;
            default:
                log.warn("未知的属性行作用选项: '{}'，labelName={}", role, alr.labelName);
                alr.attributeType = "COLOR"; // 安全fallback
                break;
        }
    }

    /**
     * 根据「此行出现的商品名」列，自动推导 itemTypeMapping
     * 规则：
     *   - Oval Box / Square Box / Box → Box（礼盒类）
     *   - Tie Clip / TieClip → Tie Clip
     *   - Cufflink / Cufflinks → Cufflinks
     *   - 其他 → 原值
     */
    private String buildItemTypeMapping(String itemNames) {
        if (itemNames == null || itemNames.isEmpty()) return "";
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String name : itemNames.split(",")) {
            name = name.trim();
            if (name.isEmpty()) continue;
            String type = resolveItemType(name);
            mapping.put(name, type);
        }
        if (mapping.isEmpty()) return "";
        try {
            return objectMapper.writeValueAsString(mapping);
        } catch (Exception e) {
            return "";
        }
    }

    /** 将商品名映射到内部类型 */
    private String resolveItemType(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("box")) return "Box";
        if (lower.contains("tie clip") || lower.equals("tieclip")) return "Tie Clip";
        if (lower.startsWith("cufflink")) return "Cufflinks";
        return name; // 原值
    }

    /**
     * 解析 "A→B,C→D" 格式的映射列 → JSON Map
     */
    private String buildArrowMapping(String arrowStr) {
        if (arrowStr == null || arrowStr.isEmpty()) return "";
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String pair : arrowStr.split(",")) {
            pair = pair.trim();
            // 支持 → 和 -> 两种写法
            String[] parts = pair.split("→|->", 2);
            if (parts.length == 2) {
                mapping.put(parts[0].trim(), parts[1].trim());
            }
        }
        if (mapping.isEmpty()) return "";
        try {
            return objectMapper.writeValueAsString(mapping);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 构建 productNameMapping：
     * 1. 先根据 itemNames 中的类型推导默认映射
     * 2. 再用 nameOverride（"类型→产品名"格式）覆盖
     *
     * 默认规则：
     *   Box → Packaging Box
     *   Tie Clip → Tie Clip Duck Bill（默认）
     *   Cufflinks → 无需映射（直接使用）
     */
    private String buildProductNameMapping(String itemNames, String nameOverride) {
        Map<String, String> mapping = new LinkedHashMap<>();

        // 1. 默认映射
        if (itemNames != null && !itemNames.isEmpty()) {
            for (String name : itemNames.split(",")) {
                name = name.trim();
                if (name.isEmpty()) continue;
                String type = resolveItemType(name);
                switch (type) {
                    case "Box":
                        mapping.put("Box", "Packaging Box");
                        break;
                    case "Tie Clip":
                        // 默认映射到 Duck Bill，可被 nameOverride 覆盖
                        mapping.putIfAbsent("Tie Clip", "Tie Clip Duck Bill");
                        break;
                    default:
                        break;
                }
            }
        }

        // 2. 用 nameOverride 覆盖（格式：类型→产品名）
        if (nameOverride != null && !nameOverride.isEmpty()) {
            for (String pair : nameOverride.split(",")) {
                pair = pair.trim();
                String[] parts = pair.split("→|->", 2);
                if (parts.length == 2) {
                    mapping.put(parts[0].trim(), parts[1].trim());
                }
            }
        }

        if (mapping.isEmpty()) return "";
        try {
            return objectMapper.writeValueAsString(mapping);
        } catch (Exception e) {
            return "";
        }
    }

    // =========================================
    // Sheet3 解析：附加商品规则
    // =========================================

    private List<AccessoryRuleConfig.AccessoryRule> parseAccessoryRuleSheet(Sheet sheet) {
        List<AccessoryRuleConfig.AccessoryRule> rules = new ArrayList<>();
        for (int i = 2; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row)) continue;

            String ruleId = getStringCell(row, 0);
            if (ruleId.isEmpty()) continue;

            AccessoryRuleConfig.AccessoryRule rule = new AccessoryRuleConfig.AccessoryRule();
            rule.setRuleId(ruleId);
            rule.setDescription(getStringCell(row, 1));
            rule.setPriority(getIntCell(row, 2, 100));
            rule.setEnabled(getBoolCell(row, 3));
            rule.setMainProductTypes(splitToList(getStringCell(row, 4)));
            rule.setExcludeProductTypes(splitToList(getStringCell(row, 5)));
            rule.setTitleKeywords(splitToList(getStringCell(row, 6)));

            AccessoryRuleConfig.OrderCondition oc = new AccessoryRuleConfig.OrderCondition();
            oc.setRequireOtherProducts(splitToList(getStringCell(row, 7)));
            oc.setRequireNoWoodBox(getBoolCell(row, 8));
            rule.setOrderCondition(oc);

            rule.setAccessoryType(getStringCell(row, 9));
            String av = getStringCell(row, 10);
            rule.setAccessoryVariable(av.isEmpty() ? null : av);

            AccessoryRuleConfig.QuantityRule qr = new AccessoryRuleConfig.QuantityRule();
            qr.setSameAsMain(getBoolCell(row, 11));
            String fq = getStringCell(row, 12);
            qr.setFixedQuantity(fq.isEmpty() ? null : Integer.parseInt(fq.trim()));
            qr.setMultiplier(getIntCell(row, 13, 1));
            rule.setQuantityRule(qr);

            AccessoryRuleConfig.InheritConfig ic = new AccessoryRuleConfig.InheritConfig();
            ic.setInheritColor(getBoolCell(row, 14));
            ic.setInheritSize(getBoolCell(row, 15));
            ic.setInheritVariable(getBoolCell(row, 16));
            rule.setInheritConfig(ic);

            rules.add(rule);
        }
        return rules;
    }

    // =========================================
    // 更新 product-title-recognition-rules.properties
    // =========================================

    private void updateTitleRecognitionRules(List<ListingRow> listingRows, ImportResult result) throws Exception {
        File propsFile = resolveResourceFile("product-title-recognition-rules.properties");
        if (propsFile == null) {
            log.warn("未找到 product-title-recognition-rules.properties，跳过更新");
            return;
        }

        // 读取现有内容
        List<String> lines = Files.readAllLines(propsFile.toPath(), StandardCharsets.UTF_8);

        // 构建 key→行号 的索引（用于覆盖更新）
        // key 格式：tie.clip.slide.1=...
        Map<String, Integer> keyToLineIdx = new LinkedHashMap<>();
        Pattern keyPattern = Pattern.compile("^([^#=\\s]+)\\s*=");
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = keyPattern.matcher(lines.get(i));
            if (m.find()) {
                keyToLineIdx.put(m.group(1), i);
            }
        }

        // 构建 listingId→key 的索引（从现有内容提取）
        Map<String, String> listingIdToKey = new LinkedHashMap<>();
        Pattern valuePattern = Pattern.compile("^([^#=\\s]+)\\s*=.*\\|([^|\\s]+)\\s*$");
        for (String line : lines) {
            Matcher m = valuePattern.matcher(line);
            if (m.find()) {
                String key = m.group(1);
                String lid = m.group(2);
                if (!lid.isEmpty()) {
                    listingIdToKey.put(lid, key);
                }
            }
        }

        List<String> appendLines = new ArrayList<>();
        appendLines.add("");
        appendLines.add("# === 由配置模板导入 [" + now() + "] ===");

        for (ListingRow lr : listingRows) {
            // 格式: key=keywords|ProductName|OrderType|isCombo|listingId
            String value = lr.titleKeywords
                    + "|" + lr.productName
                    + "|" + lr.orderType
                    + "|" + lr.isCombo
                    + "|" + lr.listingId;
            String newLine = lr.titleRuleKey + "=" + value;

            if (keyToLineIdx.containsKey(lr.titleRuleKey)) {
                // key 已存在，原地替换
                int lineIdx = keyToLineIdx.get(lr.titleRuleKey);
                lines.set(lineIdx, newLine);
                log.debug("    覆盖标题规则 key={}", lr.titleRuleKey);
            } else {
                // key 不存在，追加
                appendLines.add("");
                appendLines.add("# " + lr.comment);
                appendLines.add(newLine);
                log.debug("    追加标题规则 key={}", lr.titleRuleKey);
            }
        }

        // 追加新增行
        if (appendLines.size() > 2) {
            lines.addAll(appendLines);
        }

        Files.write(propsFile.toPath(), lines, StandardCharsets.UTF_8);
        log.info("  ✅ product-title-recognition-rules.properties 更新完成");

        // 同步写入 dataBase.xlsx（如配置了路径）
        syncToDatabase();
    }

    // =========================================
    // 更新 product-attribute-labels.json
    // =========================================

    @SuppressWarnings("unchecked")
    private void updateAttributeLabelsJson(List<ListingRow> listingRows,
                                           Map<String, List<AttrLabelRow>> attrMap,
                                           ImportResult result) throws Exception {
        File jsonFile = resolveResourceFile("product-attribute-labels.json");
        if (jsonFile == null) {
            log.warn("未找到 product-attribute-labels.json，跳过更新");
            return;
        }

        Map<String, Object> root = objectMapper.readValue(jsonFile, Map.class);

        // ---- 处理 listingAttributes ----
        List<Map<String, Object>> listingAttributes =
                (List<Map<String, Object>>) root.computeIfAbsent("listingAttributes", k -> new ArrayList<>());

        // 构建 listingId → 现有条目 的索引
        Map<String, Map<String, Object>> existingListingMap = new LinkedHashMap<>();
        for (Map<String, Object> entry : listingAttributes) {
            String lid = (String) entry.get("listingId");
            if (lid != null) existingListingMap.put(lid, entry);
        }

        // 收集所有已在 Sheet2 配置属性标签的 listingId（且在 listingAttributes 中的）
        Set<String> listingAttrIds = new LinkedHashSet<>();
        for (ListingRow lr : listingRows) {
            // 以下条件判断是否属于 listingAttributes（非 productAttributeLabels）
            // 规则：Sheet2 中使用 listingId 作为 key（非 productName）的归入 listingAttributes
            if (isListingIdKey(lr.listingId)) {
                listingAttrIds.add(lr.listingId);
            }
        }
        // Sheet2 中所有以 listingId 为 key 且不是 ProductName 的都归入 listingAttributes
        for (String lid : attrMap.keySet()) {
            if (isListingIdKey(lid)) {
                listingAttrIds.add(lid);
            }
        }

        for (String lid : listingAttrIds) {
            List<AttrLabelRow> attrRows = attrMap.get(lid);
            if (attrRows == null || attrRows.isEmpty()) continue;

            Map<String, Object> entry = existingListingMap.computeIfAbsent(lid, k -> {
                Map<String, Object> newEntry = new LinkedHashMap<>();
                newEntry.put("listingId", lid);
                listingAttributes.add(newEntry);
                return newEntry;
            });

            // 找到对应的 ListingRow 取 comment
            String comment = listingRows.stream()
                    .filter(lr -> lid.equals(lr.listingId))
                    .map(lr -> lr.comment)
                    .findFirst().orElse("");
            if (!comment.isEmpty()) entry.put("comment", comment);

            entry.put("attributeLabels", buildAttrLabelList(attrRows));
        }

        // ---- 处理 productAttributeLabels ----
        List<Map<String, Object>> productAttributeLabels =
                (List<Map<String, Object>>) root.computeIfAbsent("productAttributeLabels", k -> new ArrayList<>());

        Map<String, Map<String, Object>> existingProductMap = new LinkedHashMap<>();
        for (Map<String, Object> entry : productAttributeLabels) {
            String pn = (String) entry.get("productName");
            if (pn != null) existingProductMap.put(pn, entry);
        }

        for (Map.Entry<String, List<AttrLabelRow>> e : attrMap.entrySet()) {
            String key = e.getKey();
            if (!isListingIdKey(key)) {
                // key 是 productName
                Map<String, Object> entry = existingProductMap.computeIfAbsent(key, k -> {
                    Map<String, Object> newEntry = new LinkedHashMap<>();
                    newEntry.put("productName", key);
                    productAttributeLabels.add(newEntry);
                    return newEntry;
                });
                entry.put("attributeLabels", buildAttrLabelList(e.getValue()));
            }
        }

        objectMapper.writeValue(jsonFile, root);
        log.info("  ✅ product-attribute-labels.json 更新完成");
    }

    // =========================================
    // 更新 accessory-rules.json
    // =========================================

    private void updateAccessoryRulesJson(List<AccessoryRuleConfig.AccessoryRule> newRules) throws Exception {
        File jsonFile = resolveResourceFile("accessory-rules.json");
        if (jsonFile == null) {
            log.warn("未找到 accessory-rules.json，跳过更新");
            return;
        }

        AccessoryRuleConfig.RulesWrapper wrapper = objectMapper.readValue(jsonFile, AccessoryRuleConfig.RulesWrapper.class);
        List<AccessoryRuleConfig.AccessoryRule> existingRules = wrapper.getAccessoryRules();
        if (existingRules == null) existingRules = new ArrayList<>();

        // ruleId → 已有规则索引
        Map<String, Integer> ruleIdToIdx = new LinkedHashMap<>();
        for (int i = 0; i < existingRules.size(); i++) {
            ruleIdToIdx.put(existingRules.get(i).getRuleId(), i);
        }

        for (AccessoryRuleConfig.AccessoryRule newRule : newRules) {
            if (ruleIdToIdx.containsKey(newRule.getRuleId())) {
                existingRules.set(ruleIdToIdx.get(newRule.getRuleId()), newRule);
                log.debug("    覆盖附加规则 ruleId={}", newRule.getRuleId());
            } else {
                existingRules.add(newRule);
                log.debug("    追加附加规则 ruleId={}", newRule.getRuleId());
            }
        }

        wrapper.setAccessoryRules(existingRules);
        objectMapper.writeValue(jsonFile, wrapper);
        log.info("  ✅ accessory-rules.json 更新完成");
    }

    // =========================================
    // listingId 回写到 Excel
    // =========================================

    private void writeBackListingIds(File excelFile, Workbook workbook, Sheet sheet1,
                                     List<ListingRow> listingRows) throws Exception {
        // 只有存在新生成 listingId 才需要回写
        boolean hasNew = listingRows.stream().anyMatch(lr -> lr.isNew);
        if (!hasNew) return;

        Map<Integer, String> rowToId = new LinkedHashMap<>();
        for (ListingRow lr : listingRows) {
            if (lr.isNew) {
                rowToId.put(lr.rowIndex, lr.listingId);
            }
        }

        for (Map.Entry<Integer, String> entry : rowToId.entrySet()) {
            Row row = sheet1.getRow(entry.getKey());
            if (row == null) continue;
            Cell cell = row.getCell(0);
            if (cell == null) cell = row.createCell(0);
            cell.setCellValue(entry.getValue());
        }

        // 写回文件（先写到临时文件再替换，防止流冲突）
        File tmpFile = new File(excelFile.getParent(), excelFile.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
            workbook.write(fos);
        }
        Files.move(tmpFile.toPath(), excelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("  ✅ listingId 已回写到 Excel 文件");

        // 如果启用了 dataBase.xlsx 同步，也写一份到数据库文件
        syncToDatabase();
    }

    // =========================================
    // 同步到 dataBase.xlsx（基线文件）
    // =========================================

    private void syncToDatabase() {
        if (databaseFilePath == null || databaseFilePath.isEmpty()) return;
        // 目前仅记录日志，实际同步可按需扩展
        log.debug("  dataBase.xlsx 同步（路径={}）：本次更新已写入配置文件，下次重启后生效", databaseFilePath);
    }

    // =========================================
    // 工具方法
    // =========================================

    /** 判断 key 是 listingId（小写+下划线格式）还是 productName（含空格的英文名） */
    private boolean isListingIdKey(String key) {
        if (key == null || key.isEmpty()) return false;
        // listingId 全部小写+下划线，如 tc_slide_with_box
        // productName 含空格或大写，如 Dog Tag Silicone Matte Silent
        return key.equals(key.toLowerCase()) && !key.contains(" ");
    }

    /** 构建 attributeLabels 列表（JSON结构） */
    private List<Map<String, Object>> buildAttrLabelList(List<AttrLabelRow> attrRows) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AttrLabelRow alr : attrRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("labelName", alr.labelName);
            item.put("attributeType", alr.attributeType);
            if (!alr.valuePattern.isEmpty()) item.put("valuePattern", alr.valuePattern);
            if (!alr.groupMapping.isEmpty()) {
                try {
                    item.put("groupMapping", objectMapper.readValue(alr.groupMapping, Map.class));
                } catch (Exception e) {
                    item.put("groupMapping", alr.groupMapping);
                }
            }
            item.put("required", alr.required);
            if (!alr.separators.isEmpty()) {
                // separators 存成 List
                List<String> sepList = new ArrayList<>();
                for (char c : alr.separators.toCharArray()) {
                    sepList.add(String.valueOf(c));
                }
                item.put("separators", sepList);
            }
            if (!alr.itemTypeMapping.isEmpty()) {
                try { item.put("itemTypeMapping", objectMapper.readValue(alr.itemTypeMapping, Map.class)); }
                catch (Exception e) { /* 忽略格式错误 */ }
            }
            if (!alr.productNameMapping.isEmpty()) {
                try { item.put("productNameMapping", objectMapper.readValue(alr.productNameMapping, Map.class)); }
                catch (Exception e) { /* 忽略格式错误 */ }
            }
            if (!alr.boxVariableMapping.isEmpty()) {
                try { item.put("boxVariableMapping", objectMapper.readValue(alr.boxVariableMapping, Map.class)); }
                catch (Exception e) { /* 忽略格式错误 */ }
            }
            list.add(item);
        }
        return list;
    }

    /** 生成 listingId：从 titleRuleKey 推导，如 tie.clip.slide.1 → tc_slide_001 */
    private Map<String, Integer> listingIdCounter = new HashMap<>();

    private String generateListingId(String productName, String titleRuleKey) {
        if (titleRuleKey != null && !titleRuleKey.isEmpty()) {
            // 将 tie.clip.slide.1 → tie_clip_slide_1（直接用 key 做 ID）
            return titleRuleKey.replace(".", "_");
        }
        // fallback：产品名前缀 + 计数器
        String prefix = productName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+$", "");
        int count = listingIdCounter.getOrDefault(prefix, 0) + 1;
        listingIdCounter.put(prefix, count);
        return prefix + "_" + String.format("%03d", count);
    }

    /** 定位 classpath resources 目录中的文件 */
    private File resolveResourceFile(String filename) {
        // 方式1：通过 ClassLoader 找到实际资源文件路径
        try {
            java.net.URL url = getClass().getClassLoader().getResource(filename);
            if (url != null) {
                File f = new File(url.toURI());
                if (f.exists()) return f;
            }
        } catch (Exception ignored) {}

        // 方式2：相对于 JAR 的同级目录
        File f2 = new File(filename);
        if (f2.exists()) return f2;

        // 方式3：通过 Spring Boot 开发模式路径
        File f3 = new File("src/main/resources/" + filename);
        if (f3.exists()) return f3;

        log.warn("无法定位资源文件: {}", filename);
        return null;
    }

    private void moveFile(File file, String subDir) throws IOException {
        File destDir = new File(inputFolder, subDir);
        if (!destDir.exists()) destDir.mkdirs();
        File dest = new File(destDir, file.getName());
        Files.move(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("  文件已移动至: {}/{}", subDir, file.getName());
    }

    private void writeResultLog(File excelFile, ImportResult result) throws IOException {
        String logName = excelFile.getName().replaceAll("\\.xlsx$", "") + "-result.log";
        File logFile = new File(new File(inputFolder, "processed"), logName);
        new File(inputFolder, "processed").mkdirs();
        String content = String.format(
                "[%s] 配置导入成功\n文件: %s\n新增 Listing: %d 条\n更新 Listing: %d 条\n附加规则同步: %d 条\n",
                now(), excelFile.getName(), result.newListings, result.updatedListings, result.syncedRules);
        Files.write(logFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeErrorLog(File excelFile, Exception e) throws IOException {
        String logName = excelFile.getName().replaceAll("\\.xlsx$", "") + "-error.log";
        File logFile = new File(new File(inputFolder, "error"), logName);
        new File(inputFolder, "error").mkdirs();
        String content = String.format(
                "[%s] 配置导入失败\n文件: %s\n错误: %s\n",
                now(), excelFile.getName(), e.getMessage());
        Files.write(logFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    // --- Cell 读取工具 ---

    private String getStringCell(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:  return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return cell.getStringCellValue().trim(); }
                catch (Exception e) { return String.valueOf(cell.getNumericCellValue()); }
            default: return "";
        }
    }

    private boolean getBoolCell(Row row, int colIndex) {
        String val = getStringCell(row, colIndex).toUpperCase();
        return "TRUE".equals(val) || "YES".equals(val) || "1".equals(val);
    }

    private int getIntCell(Row row, int colIndex, int defaultVal) {
        String val = getStringCell(row, colIndex);
        if (val.isEmpty()) return defaultVal;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getStringCell(row, i);
                if (!val.isEmpty()) return false;
            }
        }
        return true;
    }

    private List<String> splitToList(String s) {
        if (s == null || s.isEmpty()) return new ArrayList<>();
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // =========================================
    // 内部数据类
    // =========================================

    private static class ListingRow {
        int rowIndex;
        String listingId = "";
        String titleRuleKey = "";
        String titleKeywords = "";
        String productName = "";
        String orderType = "";
        boolean isCombo = false;
        String comment = "";
        boolean isNew = false;
    }

    private static class AttrLabelRow {
        String listingId = "";
        String seq = "";
        String labelName = "";
        String attributeType = "";
        String valuePattern = "";
        String groupMapping = "";
        boolean required = true;
        String separators = "";
        String itemTypeMapping = "";
        String productNameMapping = "";
        String boxVariableMapping = "";
        // 新版字段（业务友好版）
        String formatExample = "";   // 值的格式示例（用于推导正则）
    }

    public static class ImportResult {
        public int newListings = 0;
        public int updatedListings = 0;
        public int syncedRules = 0;
        public List<String> errors = new ArrayList<>();
    }
}

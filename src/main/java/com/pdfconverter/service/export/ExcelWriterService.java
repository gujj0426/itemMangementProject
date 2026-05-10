package com.pdfconverter.service.export;

import com.pdfconverter.config.PersonalizationLlmProperties;
import com.pdfconverter.constant.OrderType;
import com.pdfconverter.model.ExcelData;
import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.service.mapper.FontNameMappingService;
import com.pdfconverter.service.mapper.StyleNameMappingService;
import com.pdfconverter.service.ProductListService;
import com.pdfconverter.util.EngravingEnumeratedTokens;
import com.pdfconverter.util.EngravingFaceCountUtil;
import com.pdfconverter.util.StyleEngravingDefaultFontUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.pdfconverter.constant.ExcelConstant.*;
import static com.pdfconverter.constant.ProductName.*;

@Service
public class ExcelWriterService {
    private static final Logger log = LoggerFactory.getLogger(ExcelWriterService.class);

    @Resource
    private StyleNameMappingService styleNameMappingService;

    @Resource
    private FontNameMappingService fontNameMappingService;
    
    @Resource
    private ProductListService productListService;

    @Resource
    private PersonalizationLlmProperties personalizationLlmProperties;

    @Value("${app.excel.output-folder}")
    private String outputFolder;

    @Value("${app.excel.filename-prefix}")
    private String prefix;

    @Value("${app.excel.max-files-per-day}")
    private int maxFilesPerDay;

    @Value("${app.excel.sheet-name}")
    private String sheetName;

    @Value("${app.excel.initial-index}")
    private int initialIndex;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private final DateTimeFormatter fileDateFormatter = DateTimeFormatter.ofPattern("yyMMdd");

    private Workbook workbook;
    private Sheet sheet;
    private Row headerRow;
    private String currentFilePath;

    @PostConstruct
    public void init() {
        new File(outputFolder).mkdirs();
    }

    public synchronized void writeOrders(List<PdfOrderData> orders) throws IOException {
        LocalDate now = LocalDate.now();
        String dateStr = now.format(fileDateFormatter);
        int seq = findNextSequenceNumber(dateStr);
        String fileName = String.format("%s%s%02d.xlsx", prefix, dateStr, seq);
        currentFilePath = Paths.get(outputFolder, fileName).toString();

        boolean isNewFile = !new File(currentFilePath).exists();
        if (isNewFile) {
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet(sheetName);
            createHeader();
        } else {
            workbook = WorkbookFactory.create(new File(currentFilePath));
            sheet = workbook.getSheetAt(0);
        }

        int startRow = sheet.getLastRowNum() + 1;
        if (startRow == 1 && isNewFile) startRow = 1;

        AtomicInteger rowIdx = new AtomicInteger(startRow);
        if (orders != null) {
            for (PdfOrderData order : orders) {
                if (order.getItemDetails() != null) {
                    for (PdfOrderData.ItemDetail detail : order.getItemDetails()) {
                        if (detail.getOrderType() != null && detail.getOrderType() != OrderType.UNKNOWN) {
                            // 型号列：输出 L/S 等sizeCode，而非"大号/小号"等displayName
                            String sizeCode = detail.getProductSize() != null && detail.getProductSize() != com.pdfconverter.constant.ProductSize.UNKNOWN
                                             ? detail.getProductSize().getSizeCode() : "";
                            // 默认型号：如果未提取到型号，根据产品类型设置默认值
                            if (sizeCode.isEmpty()) {
                                sizeCode = getDefaultSize(detail);
                            }
                            String color = detail.getProductColor() != null && detail.getProductColor() != com.pdfconverter.constant.ProductColor.UNKNOWN
                                          ? detail.getProductColor().getDisplayName() : null;
                            String variable = detail.getProductVariable() != null && detail.getProductVariable() != com.pdfconverter.constant.ProductVariable.UNKNOWN
                                             ? detail.getProductVariable().getDisplayName() : null;
                            
                            // 确定产品细类（用于查产品清单）
                            // 优先从 ProductName 的细类推断，若无则从 OrderType 推断
                            String subClass = resolveSubClass(detail);
                            
                            // 调试日志
                            log.debug("Excel输出: 订单={}, ProductName={}, OrderType={}, 细类={}, size={}, color={}, variable={}",
                                     order.getOrderNumber(),
                                     detail.getProductName() != null ? detail.getProductName().getDisplayName() : "null",
                                     detail.getOrderType(),
                                     subClass,
                                     sizeCode,
                                     color,
                                     variable);
                            
                            // 用细类+型号+颜色+变量查产品名称
                            String chineseProductName = null;
                            if (subClass != null) {
                                chineseProductName = productListService.getChineseProductNameBySubClass(
                                    subClass,
                                    sizeCode.isEmpty() ? null : sizeCode,
                                    color,
                                    variable
                                );
                            }
                            // 兜底：使用 ProductName 的 displayName 或 OrderType 的 displayName
                            if (chineseProductName == null || chineseProductName.isEmpty()) {
                                if (detail.getProductName() != null && detail.getProductName() != com.pdfconverter.constant.ProductName.UNKNOWN) {
                                    chineseProductName = detail.getProductName().getDisplayName();
                                } else {
                                    chineseProductName = detail.getOrderType().getDisplayName();
                                }
                            } else {
                                // 产品清单返回了一个中文名，但需要验证它是否匹配正确的产品类型
                                // 例如：TIE_CLIP_DOUBLE_SIDED_SLIDE_IN 查"领带夹"细类可能返回"鸭嘴领带夹（薄）"
                                // 此时应该用 ProductName.displayName 替代
                                if (detail.getProductName() != null && detail.getProductName() != com.pdfconverter.constant.ProductName.UNKNOWN) {
                                    String displayName = detail.getProductName().getDisplayName();
                                    // 如果产品清单结果和ProductName.displayName不同，且ProductName能直接匹配到产品清单记录，
                                    // 则优先使用ProductName对应的记录
                                    String directMatch = productListService.getChineseProductNameBySubClass(
                                        subClass, sizeCode.isEmpty() ? null : sizeCode, color, variable,
                                        displayName
                                    );
                                    if (directMatch != null && !directMatch.isEmpty()) {
                                        chineseProductName = directMatch;
                                    }
                                }
                            }
                            
                            // 计算需要输出的行数和每行数量
                            // 主商品：按 itemQuantity 拆行，每行数量为 1
                            // 附属商品（如box）：输出 1 行，数量为 itemQuantity
                            boolean isMainProduct = detail.getMainProductFlg() != null && detail.getMainProductFlg();
                            int rawQty = detail.getItemQuantity();
                            boolean fanOut = detail.isEngravingFanOutApplied();
                            // 已刻录拆行的明细不再按「面数」倍增 Excel 行（每物理行一条 ItemDetail）
                            int engravingRows = fanOut ? 1 : EngravingFaceCountUtil.countFaces(detail.getDynamicAttributes());

                            int rowCount;
                            int quantityPerRow = 1;
                            boolean suppressQuantity = false;
                            if (isMainProduct) {
                                if (fanOut && rawQty <= 0) {
                                    rowCount = 1;
                                    suppressQuantity = true;
                                } else {
                                    int itemQty = rawQty > 0 ? rawQty : 1;
                                    rowCount = itemQty * engravingRows;
                                    quantityPerRow = 1;
                                }
                            } else {
                                rowCount = 1;
                                quantityPerRow = rawQty > 0 ? rawQty : 1;
                                if (fanOut && rawQty <= 0) {
                                    suppressQuantity = true;
                                }
                            }
                            
                            for (int row = 0; row < rowCount; row++) {
                                // 创建Excel数据
                                ExcelData data = new ExcelData();
                                data.setUsername(order.getUsername());
                                data.setOrderNumber(order.getOrderNumber());
                                data.setProductName(chineseProductName);
                                data.setPersonalization(detail.getPersonalization());
                                data.setItemTitle(detail.getItemTitle());
                                // 订购完全信息：直接使用 dynamicAttributes（原始动态属性+Personalization）
                                data.setInformation(detail.getDynamicAttributes());
                                data.setOrderType(detail.getOrderType());
                                // 主商品输出刻录相关列；袖扣/领带夹作为组合附属且有 Personalization 时也输出（与主商品共用留言）
                                boolean accessoryEngravedLine = !isMainProduct
                                        && detail.getOrderType() != null
                                        && (detail.getOrderType() == OrderType.TIE_CLIP
                                            || detail.getOrderType() == OrderType.CUFFLINK)
                                        && detail.getPersonalization() != null
                                        && !detail.getPersonalization().isBlank();
                                if (isMainProduct || accessoryEngravedLine) {
                                    String engThisRow = effectiveEngravingForExcel(detail, row, rowCount);
                                    data.setFont(effectiveFontForExcel(detail, engThisRow));
                                    data.setStyle(effectiveDesignStyleForExcel(detail));
                                    data.setEngravingContent(engThisRow);
                                    data.setIcon(effectiveIconForExcel(detail));
                                } else {
                                    data.setFont(null);
                                    data.setStyle(null);
                                    data.setEngravingContent("");
                                    data.setIcon("");
                                }
                                // 型号列：输出 L/S 等sizeCode
                                data.setProductSize(sizeCode);
                                data.setProductColor(color != null ? color : "");
                                data.setDynamicAttributes(variable != null ? variable : "");
                                data.setDate(now.format(dateFormatter));
                                if (suppressQuantity) {
                                    data.setQuantity("");
                                } else if (isMainProduct && engravingRows > 1 && (row % engravingRows != 0)) {
                                    data.setQuantity("");
                                } else {
                                    data.setQuantity(String.valueOf(quantityPerRow));
                                }
                                
                                // 创建行并填充
                                Row excelRow = sheet.createRow(rowIdx.getAndIncrement());
                                fillRow(excelRow, data);
                            }
                        }
                    }
                }
                initialIndex++;
            };
        }

        try (FileOutputStream fos = new FileOutputStream(currentFilePath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createHeader() {
        headerRow = sheet.createRow(0);
        for (int i = 0; i < EXCEL_HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(EXCEL_HEADERS[i]);
        }
    }

    private void fillRow(Row row, ExcelData data) {
        // 0: 产品编号 - 空
        row.createCell(PRODUCT_CODE_INDEX).setCellValue("");
        // 1: 用户名 = 用户名
        row.createCell(USERNAME_INDEX).setCellValue(data.getUsername() != null ? data.getUsername() : "");
        // 2: 订单编号 = 订单编号
        row.createCell(ORDER_NUMBER_INDEX).setCellValue(data.getOrderNumber() != null ? data.getOrderNumber() : "");
        // 3: 产品名称 = 订单类型
        row.createCell(PRODUCT_NAME_INDEX).setCellValue(data.getProductName() != null ? data.getProductName() : "");
        // 4: 型号 = 尺寸
        row.createCell(MODEL_INDEX).setCellValue(data.getProductSize() != null ? data.getProductSize() : "");
        // 5: 颜色 = 颜色
        row.createCell(COLOR_INDEX).setCellValue(data.getProductColor() != null ? data.getProductColor() : "");
        // 6: 产品变量 = dynamicAttributes（如"Oval Box-椭圆形开窗木盒"）
        row.createCell(PRODUCT_VARIABLE_INDEX).setCellValue(data.getDynamicAttributes() != null ? data.getDynamicAttributes() : "");
        // 7: 设计风格 = style（从Personalization中提取的设计风格）
        row.createCell(DESIGN_STYLE_INDEX).setCellValue(convertStyle(data.getStyle()));
        // 8: 刻录信息（可由 LLM 填充）
        row.createCell(ENGRAVING_INFO_INDEX).setCellValue(
                data.getEngravingContent() != null ? data.getEngravingContent() : "");
        // 9: 字体 = font（从Personalization中提取的字体）
        row.createCell(FONT_INDEX).setCellValue(convertFont(data.getFont()));
        // 10: icon（LLM 标准化 icon #1 … icon #90）
        row.createCell(ICON_INDEX).setCellValue(data.getIcon() != null ? data.getIcon() : "");
        // 11: 是否派单 = 空
        row.createCell(IS_ASSIGNED_INDEX).setCellValue("");
        // 12: 设计师 - 空
        row.createCell(DESIGNER_INDEX).setCellValue("");
        // 13: 数量 = 默认数量1
        row.createCell(QUANTITY_INDEX).setCellValue(data.getQuantity() != null ? data.getQuantity() : "1");
        // 14: 出库日期 = 系统日期（yyyy年MM月dd日）
        row.createCell(OUTPUT_DATE_INDEX).setCellValue(data.getDate());
        // 15: Personalization - 保留原有逻辑
        row.createCell(PERSONALIZATION_INDEX).setCellValue(data.getPersonalization() != null ? data.getPersonalization() : "");
        // 16: 订购完全信息 - 保留原有逻辑
        row.createCell(FULL_ORDER_INFO_INDEX).setCellValue(data.getInformation() != null ? data.getInformation() : "");
        // 17: 商品标题 - 保留原有逻辑
        row.createCell(ITEM_TITLE_INDEX).setCellValue(data.getItemTitle() != null ? data.getItemTitle() : "");

    }

    private String effectiveDesignStyleForExcel(PdfOrderData.ItemDetail detail) {
        String rule = detail.getStyle() != null ? detail.getStyle() : "";
        String llm = detail.getLlmDesignStyle() != null ? detail.getLlmDesignStyle() : "";
        if (!personalizationLlmProperties.isEnabled() || llm.isEmpty()) {
            return rule;
        }
        if (personalizationLlmProperties.getMergePolicy() == PersonalizationLlmProperties.MergePolicy.OVERLAY) {
            return llm;
        }
        return rule.isEmpty() ? llm : rule;
    }

    /**
     * @param engravingForThisRow 本 Excel 行刻录列已解析后的正文（用于 Style+刻录→默认字体判断）
     */
    private String effectiveFontForExcel(PdfOrderData.ItemDetail detail, String engravingForThisRow) {
        String rule = detail.getFont() != null ? detail.getFont() : "";
        String llm = detail.getLlmFont() != null ? detail.getLlmFont() : "";
        String merged;
        if (!personalizationLlmProperties.isEnabled() || llm.isEmpty()) {
            merged = rule;
        } else if (personalizationLlmProperties.getMergePolicy() == PersonalizationLlmProperties.MergePolicy.OVERLAY) {
            merged = llm;
        } else {
            merged = rule.isEmpty() ? llm : rule;
        }
        merged = merged != null ? merged.trim() : "";
        String style = effectiveDesignStyleForExcel(detail);
        String eng = engravingForThisRow != null ? engravingForThisRow : "";
        return StyleEngravingDefaultFontUtil.applyIfEligible(merged, style, eng,
                detail.getPersonalization(), detail.getDynamicAttributes(),
                personalizationLlmProperties.isEnabled());
    }

    /**
     * 多件导出：若 LLM 返回逗号/顿号分隔且段数等于本明细展开行数，则每行一段。
     */
    private String effectiveEngravingForExcel(PdfOrderData.ItemDetail detail, int pieceIndex0, int totalPieces) {
        if (!personalizationLlmProperties.isEnabled()) {
            return "";
        }
        String eng = detail.getLlmEngravingContent();
        if (eng == null || eng.isBlank()) {
            return "";
        }
        return EngravingEnumeratedTokens.engravingForPiece(eng, pieceIndex0, totalPieces);
    }

    /** Icon 仅来自 LLM，无规则引擎兜底 */
    private String effectiveIconForExcel(PdfOrderData.ItemDetail detail) {
        if (!personalizationLlmProperties.isEnabled()) {
            return "";
        }
        String ic = detail.getLlmIcon();
        return ic != null ? ic : "";
    }

    private String convertFont(String font) {
        if (font == null || font.isEmpty()) {
            return "";
        }
        // 通过映射表转换字体
        return fontNameMappingService.getStandardName(font);
    }

    private String convertStyle(String style) {
        if (style == null || style.isEmpty()) {
            return "";
        }
        // 通过映射表转换设计风格
        return styleNameMappingService.getStandardName(style);
    }

    private int findNextSequenceNumber(String dateStr) {
        File dir = new File(outputFolder);
        if (!dir.exists()) return 1;
        File[] files = dir.listFiles((d, name) -> name.startsWith(prefix + dateStr) && name.endsWith(".xlsx"));
        if (files == null || files.length == 0) return 1;
        Set<Integer> used = new HashSet<>();
        for (File f : files) {
            String name = f.getName();
            try {
                int seq = Integer.parseInt(name.substring(name.length() - 6, name.length() - 5));
                used.add(seq);
            } catch (Exception e) { /* ignore */ }
        }
        for (int i = 1; i <= maxFilesPerDay; i++) {
            if (!used.contains(i)) return i;
        }
        return maxFilesPerDay;
    }

    /**
     * 根据 ItemDetail 推断产品细类名称（用于查产品清单）
     * 优先使用 ProductName 对应的细类，其次从 OrderType 推断
     */
    private String resolveSubClass(com.pdfconverter.model.PdfOrderData.ItemDetail detail) {
        com.pdfconverter.constant.ProductName pn = detail.getProductName();
        // 从 ProductName 推断细类
        if (pn != null && pn != com.pdfconverter.constant.ProductName.UNKNOWN) {
            switch (pn) {
                case CUFFLINK:
                    return "袖扣";
                case TIE_CLIP_DUCK_BILL:
                case TIE_CLIP_DUCK_BILL_THICK:
                case TIE_CLIP_DOUBLE_SIDED_SLIDE_IN:
                case TIE_CLIP_ROUND_DUCK_BILL:
                    return "领带夹";
                case PACKAGING_BOX:
                    return "包装盒";
                case FLOWER_HEART_BOX_PENDANT:
                case PATTERN_HEART_BOX_PENDANT:
                case ROUND_PENDANT:
                    return "吊坠";
                case DOG_TAG_SILICONE_MATTE_SILENT:
                case DOG_TAG_SILICONE_GLOSSY_SILENT:
                case DOG_TAG_OPEN_RECTANGLE_SILENT:
                case DOG_TAG_OPEN_BONE_SILENT:
                case DOG_TAG_NYLON_MATTE_SILENT:
                    return "静音狗牌";
                case DOG_TAG_HOLLOW_CLAW_BONE_SILENT:
                    return "狗牌";
                case ASH_CAN:
                    return "骨灰罐";
                default:
                    break;
            }
        }
        // 从 OrderType 推断细类（附属商品走这里）
        OrderType ot = detail.getOrderType();
        if (ot != null) {
            switch (ot) {
                case CUFFLINK:
                    return "袖扣";
                case TIE_CLIP:
                    return "领带夹";
                case BOX:
                    return "包装盒";
                case PENDANT:
                    return "吊坠";
                case DOG_TAG:
                    return "静音狗牌";
                case DOG_TAG_HOLDER:
                    return "狗牌配件";
                case MEMORIAL:
                    return "纪念饰品";
                default:
                    return null;
            }
        }
        return null;
    }

    /**
     * 根据产品类型返回默认型号
     * 某些产品只有一种型号（如双面滑入领带夹只有L），未在动态属性中明确指定时使用默认值
     */
    private String getDefaultSize(PdfOrderData.ItemDetail detail) {
        if (detail.getProductName() != null) {
            switch (detail.getProductName()) {
                case TIE_CLIP_DOUBLE_SIDED_SLIDE_IN:
                    return "L"; // 双面滑入领带夹只有L码
                case CUFFLINK:
                    return "L"; // 袖扣默认L码（无尺寸信息时的兜底）
                case TIE_CLIP_DUCK_BILL:
                case TIE_CLIP_DUCK_BILL_THICK:
                    return "L"; // 鸭嘴领带夹（薄/厚）默认L码
                case TIE_CLIP_ROUND_DUCK_BILL:
                    return "L"; // 圆片鸭嘴领带夹默认L码（根据用户反馈4012978221订单）
                default:
                    break;
            }
        }
        return "";
    }

}
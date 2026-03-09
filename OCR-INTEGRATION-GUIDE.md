# OCR图片识别功能集成说明

## 功能概述

本次更新将 OCR（光学字符识别）功能集成到系统中，用于从 PDF 中的图片识别产品名称。

## 核心改动

### 1. 服务集成

**文件**: `PdfExtractorService.java`

#### 新增字段
```java
/**
 * 当前处理的PDF文件路径（用于OCR识别）
 */
private String currentPdfPath;
```

#### 修改的方法

**extractFromPdf 方法**
- 在处理 PDF 时保存当前文件路径，供 OCR 使用

**parseProductName 方法** ⭐（核心改动）
- 集成了 OCR 图片识别功能
- 仅用于识别产品名称，不影响其他字段
- 保持原有的文本解析逻辑不变

### 2. 工作流程

```
1. extractFromPdf(pdfPath)
   ↓
   保存 pdfPath 到 currentPdfPath
   ↓
2. parseOrder(text)
   ↓
   parseItemDetail(block)
   ↓
3. parseProductName(titleLower, dynamicAttrsMap)
   ↓
   ├── OCR识别图片（新增）
   │   ├── 从 PDF 第一页提取图片
   │   ├── Tesseract OCR 识别文字
   │   └── 提取产品名称
   │
   └── 文本解析（原有逻辑）
       ├── 检测宠物头像
       ├── 检测袖扣
       ├── 检测领带夹
       └── 检测包装盒
```

### 3. 实现特点

#### ✅ 仅用于产品名称识别
- OCR 功能只在 `parseProductName` 方法中使用
- 用于识别图片中的产品名称
- **不影响其他任何字段的提取和赋值逻辑**

#### ✅ 容错处理
- OCR 识别失败不影响正常流程
- 会回退到文本解析方式
- 记录警告日志便于调试

#### ✅ 映射服务集成
- OCR 识别的产品名称会通过 `ProductNameMapper` 转换
- 保持与其他字段的映射规则一致

## 使用示例

### 场景 1: PDF 包含宠物头像图片

**图片内容**: 宠物头像照片 + 文字 "Pet Avatar Custom"

**OCR 识别**:
```java
// 1. 从 PDF 第一页提取图片
BufferedImage image = renderer.renderImageWithDPI(0, 300, ImageType.RGB);

// 2. OCR 识别文字
String text = tesseract.doOCR(image);
// 结果: "Pet Avatar Custom"

// 3. 提取产品名称
String productName = extractProductName(text);
// 结果: "宠物头像"

// 4. 映射转换
String standardName = productNameMapper.getStandardName("宠物头像");
// 结果: "宠物头像" (如果没有映射则保持原值）
```

**最终输出**: Excel 中产品名称列为 "宠物头像"

### 场景 2: PDF 包含包装盒图片

**图片内容**: 产品图片 + 文字 "Oval Box Wooden"

**OCR 识别**:
```java
// 识别文字: "Oval Box Wooden"
// 提取产品名称: "Oval Box"
// 映射转换: "Oval Box-椭圆形开窗木盒"
```

**最终输出**: Excel 中产品名称列为 "Oval Box-椭圆形开窗木盒"

### 场景 3: OCR 失败

**OCR 错误**: Tesseract 识别失败或超时

**系统行为**:
```java
try {
    // OCR 尝试
    List<String> ocrTexts = ocrService.extractTextFromPdfPage(currentPdfPath, 0);
} catch (Exception e) {
    // 失败时记录警告
    log.warn("OCR识别产品名称失败，继续使用文本解析: {}", e.getMessage());
}

// 继续执行原有的文本解析逻辑
if (titleLower.contains("pet") || ...) {
    types.add("宠物头像");
}
```

**最终输出**: 使用文本解析的结果，系统正常运行

## 配置选项

### 1. 启用/禁用 OCR

**编辑** `application.properties`:

```properties
# 启用 OCR（默认启用）
# 若要禁用，修改代码中的 OCR 调用部分
app.ocr.enabled=true
```

**代码修改**（可选）:
```java
// 在 parseProductName 方法中
if (currentPdfPath != null && !currentPdfPath.isEmpty() && isOcrEnabled()) {
    // OCR 识别逻辑
}

// 添加配置读取方法
@Value("${app.ocr.enabled:true}")
private boolean ocrEnabled;
```

### 2. 调整识别页面

**当前**: 只识别 PDF 第一页（索引 0）

**修改识别多页**（可选）:
```java
// 修改 parseProductName 方法
private Set<String> parseProductName(String titleLower, Map<String, String> dynamicAttrsMap, int pageIndex) {
    // 使用指定页面
    List<String> ocrTexts = ocrService.extractTextFromPdfPage(currentPdfPath, pageIndex);
    // ...
}
```

### 3. 优化识别性能

**建议**:
- 只对特定产品类型使用 OCR（如宠物头像）
- 缓存 OCR 结果避免重复识别
- 异步执行 OCR 不阻塞主流程

## 调试和日志

### 查看识别日志

**开发环境配置** (`logback.xml`):
```xml
<logger name="com.pdfconverter" level="DEBUG"/>
```

**日志输出示例**:
```
DEBUG - OCR识别产品名称: Pet Avatar Custom
DEBUG - 从页面 0 识别到文字: Pet Avatar Custom
WARN  - OCR识别产品名称失败，继续使用文本解析: TesseractException
```

### 测试 OCR 功能

**测试代码**:
```java
@SpringBootTest
public class OcrIntegrationTest {

    @Autowired
    private PdfExtractorService pdfExtractor;

    @Test
    public void testOcrIntegration() {
        // 测试包含图片的 PDF
        List<PdfOrderData> orders = pdfExtractor.extractFromPdf("test.pdf");

        // 检查是否正确识别了产品名称
        orders.forEach(order -> {
            order.getItemDetails().forEach(detail -> {
                System.out.println("产品名称: " + detail.getOrderType());
            });
        });
    }
}
```

## 性能优化

### 当前实现

- **识别页面**: 仅第一页
- **DPI 设置**: 300（平衡质量和速度）
- **语言**: 英语（默认）

### 优化建议

1. **选择性识别**
   ```java
   // 只对宠物头像使用 OCR
   if (titleLower.contains("pet") || titleLower.contains("avatar")) {
       // 执行 OCR
   }
   ```

2. **异步识别**
   ```java
   @Async
   public CompletableFuture<String> recognizeAsync(String pdfPath) {
       return CompletableFuture.completedFuture(ocrService.extractTextFromPdfPage(pdfPath, 0));
   }
   ```

3. **结果缓存**
   ```java
   private Map<String, String> ocrCache = new ConcurrentHashMap<>();

   public String getCachedRecognition(String pdfPath) {
       return ocrCache.computeIfAbsent(pdfPath, path -> ocrService.extractTextFromPdfPage(path, 0));
   }
   ```

## 常见问题

### Q: 为什么 OCR 只识别第一页？
A: 为性能考虑，当前只识别第一页。如需识别其他页面，可修改 `pageIndex` 参数。

### Q: OCR 识别不准确怎么办？
A: 可以：
1. 调整 DPI 设置（当前 300，可提高到 600 提高精度）
2. 安装对应语言包
3. 使用 `productNameMapper` 配置文件修正结果

### Q: OCR 会影响其他字段吗？
A: **不会**。OCR 只用于识别产品名称，其他字段（用户名、订单号等）仍使用原有逻辑。

### Q: 如何禁用 OCR 功能？
A: 在 `parseProductName` 方法中注释掉 OCR 相关代码即可，不影响其他功能。

## 维护建议

1. **监控识别率**: 定期检查 OCR 识别结果的准确性
2. **更新映射配置**: 发现新类型时及时添加到 `product-name-mapping.properties`
3. **性能测试**: 在生产环境测试 OCR 对处理速度的影响
4. **日志分析**: 定期查看 OCR 失败日志，优化识别策略

## 总结

本次集成实现了：
- ✅ OCR 图片识别功能
- ✅ 仅用于产品名称识别
- ✅ 不影响其他字段提取逻辑
- ✅ 完善的容错机制
- ✅ 与现有映射服务无缝集成

系统现在可以从 PDF 图片中智能识别产品名称，提高数据准确性！

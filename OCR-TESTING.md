# OCR 图片识别功能测试指南

## 快速开始

### 1. 确认 Tesseract 已安装

```bash
# 验证安装
tesseract --version

# 应该看到类似输出：
# tesseract 5.3.x
```

### 2. 启动应用

```bash
# Maven 启动
mvn spring-boot:run

# 或使用编译后的 JAR
java -jar target/pdf-to-excel-cufflink-0.0.1-SNAPSHOT.jar
```

### 3. 测试 PDF 处理

将包含产品图片的 PDF 放入输入文件夹：
```bash
# 假设输入目录
cp test_with_image.pdf /path/to/pdf_input/
```

应用会自动处理并生成 Excel。

### 4. 查看日志

启动日志应显示：
```
✅ PDF处理系统启动成功！
OCR服务初始化完成，语言: eng
开始处理PDF，总页数: 5
DEBUG - 从页面 0 识别到文字: Pet Avatar Custom
DEBUG - OCR识别产品名称: 宠物头像
已处理文件：test_with_image.pdf
```

## 测试场景

### 场景 1: 宠物头像订单

**测试文件**: 包含宠物头像的 PDF

**预期结果**:
```
产品名称: 宠物头像
产品类型: 宠物头像
```

**验证 Excel**:
- 产品名称列: "宠物头像"
- 产品类型列: "宠物头像"
- 设计风格列: "小如"
- 袖扣风格列: "见附图"

### 场景 2: 普通袖扣订单

**测试文件**: 包含袖扣的 PDF

**预期结果**:
```
产品名称: 袖扣
产品类型: 袖扣
```

**验证 Excel**:
- 产品名称列: "袖扣"
- 产品类型列: "袖扣"
- 颜色列: "Gold" / "Silver" 等

### 场景 3: 包装盒订单

**测试文件**: 包含包装盒信息的 PDF

**预期结果**:
```
产品名称: Oval Box-椭圆形开窗木盒
产品类型: 包装盒
```

**验证 Excel**:
- 产品名称列: "Oval Box-椭圆形开窗木盒"
- 产品类型列: "包装盒"

## 手动测试 OCR

### 测试代码示例

创建测试类 `OcrManualTest.java`:

```java
import com.pdfconverter.service.OcrImageRecognitionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class OcrManualTest {

    @Autowired
    private OcrImageRecognitionService ocrService;

    @Test
    public void testImageRecognition() {
        // 1. 测试识别 PDF 第一页
        List<String> texts = ocrService.extractTextFromPdfPage("/path/to/test.pdf", 0);
        System.out.println("识别结果: " + texts);

        // 2. 测试识别图片文件
        String imageText = ocrService.extractTextFromImage("/path/to/test.png");
        System.out.println("图片识别: " + imageText);

        // 3. 测试产品名称提取
        String productName = ocrService.extractProductName("Pet Avatar Custom");
        System.out.println("产品名称: " + productName);
    }

    @Test
    public void testAllPages() {
        // 测试识别所有页面
        List<List<String>> allPages = ocrService.extractTextFromAllPages("/path/to/test.pdf");
        for (int i = 0; i < allPages.size(); i++) {
            System.out.println("页面 " + i + ": " + allPages.get(i));
        }
    }
}
```

### 运行测试

```bash
# 运行测试
mvn test -Dtest=OcrManualTest
```

## 问题排查

### 问题 1: OCR 服务未初始化

**错误信息**:
```
java.lang.NullPointerException: Cannot invoke "OcrImageRecognitionService.extractTextFromPdfPage(...)"
```

**解决方案**:
1. 检查 Tesseract 是否安装: `tesseract --version`
2. 检查配置文件 `application.properties`
3. 查看启动日志确认 OCR 服务初始化

### 问题 2: 识别结果为空

**可能原因**:
- PDF 第一页没有文字图片
- Tesseract 语言包不匹配
- 图片质量太低

**排查步骤**:
```bash
# 1. 提取 PDF 页面为图片查看
pdftoppm -png test.pdf

# 2. 手动测试 OCR
tesseract test-0.png output -l eng

# 3. 查看结果
cat output.txt
```

### 问题 3: 中文识别不准确

**解决方案**:
修改配置文件:
```properties
# 使用简体中文
app.ocr.language=chi_sim
```

安装中文语言包:
```bash
# macOS
brew install tesseract-lang

# Linux
sudo apt-get install tesseract-ocr-chi-sim
```

### 问题 4: 性能问题

**症状**: 处理速度明显变慢

**优化建议**:
1. 降低 DPI（当前 300，可尝试 150）
2. 只识别特定产品类型
3. 使用异步处理
4. 增加识别超时

**配置修改**:
```java
// 在 OcrImageRecognitionService 中
@Override
public BufferedImage renderImage(int pageIndex) {
    // 降低 DPI 提高速度
    return renderer.renderImageWithDPI(pageIndex, 150, ImageType.RGB);
}
```

## 性能基准

### 预期处理时间

| 操作 | 预期时间 | 说明 |
|-----|---------|-----|
| PDF 文本提取 | 1-2 秒 | 使用 PDFBox |
| OCR 识别一页 | 2-5 秒 | 取决于图片复杂度 |
| Excel 写入 | 0.5-1 秒 | 使用 Apache POI |

**总体**: 一个包含图片的 PDF 约需 4-8 秒

### 性能对比

| 场景 | 无 OCR | 有 OCR | 增加时间 |
|------|-------|-------|---------|
| 纯文本 PDF | 2-3 秒 | 2-3 秒 | 0 秒 |
| 包含图片的 PDF | 2-3 秒 | 5-8 秒 | 3-5 秒 |

## 调试技巧

### 1. 启用 DEBUG 日志

修改 `logback.xml`:
```xml
<configuration>
    <logger name="com.pdfconverter.service.OcrImageRecognitionService" level="DEBUG"/>
    <logger name="com.pdfconverter.service.PdfExtractorService" level="DEBUG"/>
</configuration>
```

### 2. 保存中间结果

在 `OcrImageRecognitionService` 中添加:
```java
// 保存渲染的图片用于调试
ImageIO.write(image, "PNG", new File("debug-page-0.png"));
```

### 3. 查看识别详情

```java
List<String> texts = ocrService.extractTextFromPdfPage(pdfPath, 0);
texts.forEach(text -> {
    System.out.println("完整识别文字: " + text);
    String productName = ocrService.extractProductName(text);
    System.out.println("提取的产品名称: " + productName);
});
```

## 成功标准

### 功能验证

- ✅ PDF 包含图片时，OCR 能识别文字
- ✅ 识别的产品名称正确映射
- ✅ Excel 中产品名称列填充正确
- ✅ OCR 失败时不影响其他功能

### 性能验证

- ✅ 单个 PDF 处理时间 < 10 秒
- ✅ 内存使用稳定
- ✅ 无内存泄漏

### 质量验证

- ✅ 产品名称识别准确率 > 80%
- ✅ 特殊字符识别准确
- ✅ 中文识别正确（如果配置）

## 下一步优化

1. **训练 Tesseract**
   - 使用自定义字库提高识别率
   - 针对特定产品类型训练

2. **多引擎对比**
   - 尝试 Google Cloud Vision API
   - 尝试 AWS Rekognition
   - 对比结果选择最佳

3. **AI 辅助**
   - 使用 NLP 提取产品名称
   - 建立产品名称数据库

4. **缓存机制**
   - 缓存识别结果
   - 定期更新缓存

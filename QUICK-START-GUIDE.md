# 快速开始 - PDF转Excel系统（含图片识别功能）

## 系统功能

本系统是一个完整的PDF订单处理系统，具有以下功能：

1. ✅ **PDF文本提取** - 解析订单信息（用户名、订单号、地址等）
2. ✅ **产品名称映射** - 将提取的名称映射为标准名称
3. ✅ **OCR图片识别** - 从PDF图片识别产品名称
4. ✅ **PDF图片提取** - 从PDF提取商品图片
5. ✅ **Excel数据写入** - 生成Excel表格，包含商品图片
6. ✅ **定时调度** - 自动监控输入文件夹

## 快速启动

### 1. 安装Tesseract（OCR引擎）

**macOS**:
```bash
brew install tesseract
```

**Windows**:
```powershell
choco install tesseract
```

**Linux (Ubuntu/Debian)**:
```bash
sudo apt-get update
sudo apt-get install tesseract-ocr
```

验证安装：
```bash
tesseract --version
# 应该看到类似输出：tesseract 5.3.x
```

### 2. 配置系统

编辑 `src/main/resources/application.properties`:

```properties
# 路径配置
app.pdf.input-folder=/path/to/pdf_input
app.pdf.bak-folder=/path/to/pdf_bak
app.excel.output-folder=/path/to/excel_output

# OCR图片识别配置
app.ocr.language=eng  # eng=英文, chi_sim=简体中文

# 产品名称映射文件
app.mapping.product-name-file=product-name-mapping.properties
```

### 3. 启动应用

**开发模式**:
```bash
mvn spring-boot:run
```

**生产模式**:
```bash
# 打包
mvn clean package

# 运行
java -jar target/pdf-to-excel-cufflink-0.0.1-SNAPSHOT.jar
```

或使用启动脚本：
```bash
# macOS/Linux
./start.sh

# Windows
start.bat
```

### 4. 使用系统

将包含订单的PDF文件放入 `app.pdf.input-folder` 配置的目录：

```bash
# 示例
cp order_*.pdf /path/to/pdf_input/
```

系统会自动：
1. 每分钟扫描输入文件夹
2. 解析PDF中的订单信息
3. 识别图片中的产品名称（OCR）
4. 提取商品图片
5. 生成Excel文件（包含商品图片）
6. 将处理完的PDF移动到备份文件夹

## Excel输出格式

### 表格结构

| 列 | 列名 | 说明 |
|-----|-------|------|
| 0 | 产品编号 | 空 |
| 1 | 用户名 | 收货人姓名 |
| 2 | 订单编号 | Order #xxx |
| 3 | 产品名称 | 标准化产品名称 |
| 4 | 型号 | 尺寸 |
| 5 | 颜色 | 颜色（Gold/Silver等） |
| 6 | 产品变量 | 产品变量信息 |
| 7 | 设计风格 | 字体 |
| 8 | 刻录信息 | 留空 |
| 9 | 字体 | 字体 |
| 10 | icon | 留空 |
| 11 | 是否派单 | 留空 |
| 12 | 设计师 | 留空 |
| 13 | 数量 | 固定"1" |
| 14 | 出库日期 | yyyy年MM月dd日 |
| 15 | Personalization | 定制信息 |
| 16 | 订购完全信息 | 动态属性 |
| 17 | 商品标题 | 商品标题 |
| **18** | **商品图片** ⭐ | **嵌入的商品图片** |

### 文件命名

```
格式：{prefix}{dateStr}{seq}.xlsx
示例：cufflinksDetails26013101.xlsx
       cufflinksDetails26013102.xlsx
```

## 产品名称映射

编辑 `product-name-mapping.properties` 添加映射：

```properties
# 包装盒类型
BOX=包装盒
Box=包装盒
Oval Box=Oval Box-椭圆形开窗木盒
Square Box=Square Box-方形开窗木盒

# 产品类型
Cufflink=袖扣
Tie Clip=领带夹
Pet Avatar=宠物头像
```

## 日志说明

### 正常启动日志
```
✅ PDF处理系统启动成功！
OCR服务初始化完成，语言: eng
开始处理PDF，总页数: 5
从页面 0 提取到图片，大小: 52340 bytes
提取完成，共 3 张图片
商品 0 分配图片，索引: 0
已处理文件：order_001.pdf
```

### 错误日志
```
WARN  - OCR识别产品名称失败，继续使用文本解析: TesseractException
WARN  - 图片不足，商品 3 无图片分配
ERROR - 从PDF提取图片失败: test.pdf
```

## 高级配置

### 调整OCR语言

**英文**:
```properties
app.ocr.language=eng
```

**简体中文**:
```bash
# 先安装中文语言包
brew install tesseract-lang

# 修改配置
app.ocr.language=chi_sim
```

### 禁用图片功能

如不需要图片功能，修改 `PdfExtractorService.java`:
```java
// 注释掉这行
// currentPdfImages = imageExtractor.extractAllImages(pdfPath);
```

### 自定义图片大小

修改 `ExcelWriterService.fillRow`:
```java
// 调整图片显示大小
anchor.setCol2(anchor.getCol1() + 3);  // 宽度：3列
anchor.setRow2(anchor.getRow1() + 5);  // 高度：5行
```

## 故障排查

### 问题1: Tesseract未安装

**错误**: `java.lang.UnsatisfiedLinkError: no jna in java.library.path`

**解决**: 安装Tesseract OCR引擎（见步骤1）

### 问题2: PDF处理失败

**错误**: `IllegalArgumentException: No valid order number found in text.`

**原因**: PDF格式不符合预期

**解决**:
- 检查PDF是否为Etsy订单格式
- 查看PDF文本内容确认格式

### 问题3: Excel没有图片

**检查**:
1. 查看日志是否有 "分配图片" 信息
2. 检查PDF是否包含图片
3. 确认 `imageExtractor` 正常工作

**调试**: 启用DEBUG日志
```xml
<!-- logback.xml -->
<logger name="com.pdfconverter" level="DEBUG"/>
```

### 问题4: OCR识别不准确

**解决**:
1. 提高DPI设置（修改 `OcrImageRecognitionService`）
2. 使用对应语言包
3. 通过 `product-name-mapping.properties` 修正结果

## 文档索引

- `OCR-SETUP-GUIDE.md` - OCR安装配置指南
- `OCR-INTEGRATION-GUIDE.md` - OCR功能集成说明
- `OCR-TESTING.md` - OCR测试指南
- `IMAGE-EXTRACTION-GUIDE.md` - 图片提取功能说明
- `product-name-mapping-README.md` - 产品名称映射说明

## 性能参考

| 操作 | 预期时间 | 说明 |
|-----|---------|------|
| PDF文本提取 | 1-2 秒 | 使用PDFBox |
| 图片提取 | 2-5 秒 | 取决于图片数量和大小 |
| OCR识别 | 2-5 秒 | 取决于图片复杂度 |
| Excel写入 | 0.5-1 秒 | 包含图片插入 |
| **总计** | **6-13 秒** | **包含图片的订单** |

## 联系支持

如遇到问题，请：
1. 查看上述文档
2. 检查日志输出
3. 验证环境配置

**祝使用愉快！** 🎉

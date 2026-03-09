# PDF图片提取到Excel功能说明

## 功能概述

本次更新实现了从PDF中提取商品图片，并输出到Excel表格最后一列的功能。

## 核心改动

### 1. 新增服务 - `PdfImageExtractor.java`

**主要功能**:
- 从PDF指定页面提取第一张图片
- 从PDF所有页面提取图片
- 将图片转换为PNG格式字节数组
- 支持保存图片到文件（调试用）

**关键方法**:
```java
// 提取单页图片
byte[] extractImageFromPage(String pdfPath, int pageIndex)

// 提取所有页面的图片
List<byte[]> extractAllImages(String pdfPath)

// 转换图片格式
private byte[] convertImageToBytes(BufferedImage image)
```

### 2. 数据模型扩展

#### `ExcelData.java`
```java
/** 商品图片字节数组（PNG格式）*/
private byte[] imageBytes;

public byte[] getImageBytes() { return imageBytes; }
public void setImageBytes(byte[] imageBytes) { this.imageBytes = imageBytes; }
```

#### `PdfExtractorService.java`
```java
/** 当前PDF的所有图片（按页索引存储）*/
private List<byte[]> currentPdfImages;

/** 商品图片索引计数器（用于按顺序分配图片给商品）*/
private int itemImageIndex = 0;

@Resource
private PdfImageExtractor imageExtractor;
```

### 3. Excel写入扩展 - `ExcelWriterService.java`

#### 修改表头
```java
// 添加最后一列：商品图片
String[] headers = {..., "商品图片"};
```

#### 修改fillRow方法
```java
// 18: 商品图片 - 插入图片
if (data.getImageBytes() != null && data.getImageBytes().length > 0) {
    int pictureIdx = workbook.addPicture(data.getImageBytes(), Workbook.PICTURE_TYPE_PNG);
    Drawing<?> drawing = sheet.createDrawingPatriarch();
    CreationHelper helper = workbook.getCreationHelper();
    ClientAnchor anchor = helper.createClientAnchor();
    anchor.setCol1(18); // 图片所在列（第19列，索引18）
    anchor.setRow1(row.getRowNum()); // 图片所在行
    anchor.setCol2(19); // 图片宽度
    anchor.setRow2(row.getRowNum() + 1); // 图片高度
    drawing.createPicture(anchor, pictureIdx);
}
```

### 4. PDF解析集成 - `PdfExtractorService.java`

#### extractFromPdf方法
```java
currentPdfImages = imageExtractor.extractAllImages(pdfPath); // 提取所有图片
itemImageIndex = 0; // 重置图片索引
```

#### parseItemDetail方法
```java
// 按顺序分配图片给商品
if (currentPdfImages != null && itemImageIndex < currentPdfImages.size()) {
    byte[] imageBytes = currentPdfImages.get(itemImageIndex);
    item.setImageBytes(imageBytes);
    log.debug("商品 {} 分配图片，索引: {}", itemImageIndex, itemImageIndex);
    itemImageIndex++;
}
```

## 工作流程

```
1. PDF文件放入输入目录
   ↓
2. PdfProcessingScheduler 检测到PDF
   ↓
3. PdfExtractorService.extractFromPdf(pdfPath)
   ├─ 提取PDF文本（原有逻辑）
   ├─ 提取所有页面图片 ← 新增
   └─ 解析订单和商品信息
       └─ 为每个商品分配图片 ← 新增
   ↓
4. ExcelWriterService.writeOrders(orders)
   ├─ 创建表头（包含"商品图片"列）
   └─ 填充数据
       └─ 在最后一列插入图片 ← 新增
   ↓
5. 生成Excel文件
```

## Excel表格结构

| 列索引 | 列名 | 数据来源 | 说明 |
|--------|-------|---------|------|
| 0 | 产品编号 | 空 | 固定为空 |
| 1 | 用户名 | PDF文本 | 收货人 |
| 2 | 订单编号 | PDF文本 | Order #xxx |
| 3 | 产品名称 | 映射服务 | 标准化产品名称 |
| 4 | 型号 | PDF文本 | 尺寸（L/S/M） |
| 5 | 颜色 | PDF文本 | 颜色（Gold等） |
| 6 | 产品变量 | 映射服务 | 产品变量 |
| 7 | 设计风格 | PDF文本 | 字体 |
| 8 | 刻录信息 | PDF文本 | 留空或原始 |
| 9 | 字体 | PDF文本 | 字体 |
| 10 | icon | PDF文本 | 尺寸 |
| 11 | 是否派单 | PDF文本 | 留空 |
| 12 | 设计师 | PDF文本 | 留空 |
| 13 | 数量 | PDF文本 | 固定"1" |
| 14 | 出库日期 | 系统生成 | yyyy年MM月dd日 |
| 15 | Personalization | PDF文本 | 定制信息 |
| 16 | 订购完全信息 | PDF文本 | 动态属性 |
| 17 | 商品标题 | PDF文本 | 商品标题 |
| **18** | **商品图片** | **PDF图片** ← **新增** | **嵌入的PNG图片** |

## 图片处理说明

### 图片提取方式
- **页面遍历**: 从第1页开始，按顺序提取
- **每页一张**: 每个页面只提取第一张图片
- **PNG格式**: 统一转换为PNG格式
- **字节数组**: 以字节数组形式存储，便于Excel插入

### 图片分配规则
- **顺序分配**: 按商品解析顺序分配图片
- **一对一**: 每个商品对应一张图片
- **超出处理**: 如果商品数 > 图片数，后面的商品无图片
- **不足处理**: 如果图片数 > 商品数，多余的图片忽略

### Excel图片插入
- **位置**: 最后一列（第19列，索引18）
- **格式**: PNG格式
- **尺寸**: 自动适应单元格大小
- **单元格**: 图片单元格内容为空，图片浮在上方

## 使用示例

### 示例场景

**输入PDF**: 包含3个商品，5个页面

**PDF结构**:
```
页面 1: 订单信息 + 商品1图片
页面 2: 商品1详情 + 商品2图片
页面 3: 商品2详情 + 商品3图片
页面 4: 商品3详情
页面 5: 其他信息（无图片）
```

**处理结果**:
```
提取图片: [图片1, 图片2, 图片3, null, null]
分配给商品:
  商品1 ← 图片1
  商品2 ← 图片2
  商品3 ← 图片3
```

**Excel输出**:
```
| ... | 商品标题 | 商品图片 |
|-----|---------|---------|
| ... | 袖扣 | [袖扣图片] |
| ... | 领带夹 | [领带夹图片] |
| ... | 包装盒   | [包装盒图片] |
```

## 配置选项

### 调整图片大小

修改 `ExcelWriterService.fillRow` 中的锚点设置：
```java
anchor.setCol1(18);     // 左边列
anchor.setCol2(20);     // 右边列（增加宽度）
anchor.setRow1(row.getRowNum());      // 上边行
anchor.setRow2(row.getRowNum() + 5); // 下边行（增加高度）
```

### 禁用图片功能

在 `PdfExtractorService.extractFromPdf` 中注释：
```java
// currentPdfImages = imageExtractor.extractAllImages(pdfPath);
currentPdfImages = null; // 不提取图片
```

### 只提取特定页面

修改 `PdfImageExtractor`：
```java
// 只提取前3页的图片
List<byte[]> images = new ArrayList<>();
for (int i = 0; i < Math.min(totalPages, 3); i++) {
    images.add(extractImageFromPage(pdfPath, i));
}
```

## 调试和测试

### 查看图片提取日志

启用DEBUG日志后查看：
```
[DEBUG] 从页面 0 提取到图片，大小: 52340 bytes
[DEBUG] 从页面 1 提取到图片，大小: 48123 bytes
[DEBUG] 从页面 2 提取到图片，大小: 45678 bytes
[INFO] 提取完成，共 3 张图片

[DEBUG] 商品 0 分配图片，索引: 0
[DEBUG] 商品 1 分配图片，索引: 1
[DEBUG] 商品 2 分配图片，索引: 2
```

### 手动测试图片提取

创建测试类：
```java
@SpringBootTest
public class ImageExtractionTest {

    @Autowired
    private PdfImageExtractor imageExtractor;

    @Test
    public void testExtractImages() {
        List<byte[]> images = imageExtractor.extractAllImages("test.pdf");
        System.out.println("提取到 " + images.size() + " 张图片");
        
        // 保存第一张图片查看
        if (!images.isEmpty() && images.get(0) != null) {
            imageExtractor.saveImageToFile(images.get(0), "output/image-0.png");
        }
    }
}
```

### 验证Excel图片

打开生成的Excel文件：
1. 检查最后一列是否显示图片
2. 图片应该正确对应到每行的商品
3. 图片应该清晰可辨认
4. 图片大小适中，不遮挡文字

## 性能考虑

### 内存使用
- **图片缓存**: 所有图片提取后保存在内存中
- **建议**: 对于大文件（>100页），考虑分批处理

### 优化建议
1. **延迟加载**: 只在需要时提取图片
2. **图片压缩**: 对大图片进行压缩
3. **分批写入**: 写入Excel后立即释放图片内存
4. **异步提取**: 在后台提取图片

## 常见问题

### Q: 为什么某些商品没有图片？
A: 可能原因：
- PDF对应页面没有图片
- 图片提取失败
- 商品数 > 图片数

查看日志确认：`商品 X 无图片分配`

### Q: 图片显示太大/太小？
A: 调整 `fillRow` 方法中的锚点设置：
```java
anchor.setCol2(anchor.getCol1() + 2); // 宽度：2列
anchor.setRow2(anchor.getRow1() + 3); // 高度：3行
```

### Q: 能否只提取第一页的图片？
A: 修改 `extractFromPdf`：
```java
// 只提取第一页
List<byte[]> images = new ArrayList<>();
images.add(imageExtractor.extractImageFromPage(pdfPath, 0));
currentPdfImages = images;
```

### Q: 图片质量不佳？
A: PDFBox默认渲染DPI为72，可以提高到300：
```java
BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300, ImageType.RGB);
```

## 总结

本次更新实现了：
- ✅ PDF图片提取服务
- ✅ 图片数据模型扩展
- ✅ Excel图片插入功能
- ✅ 按顺序分配图片给商品
- ✅ 最后一列显示商品图片

现在PDF中的商品图片可以直接显示在Excel表格中了！

# itemMangementProject 项目说明（维护版）

## 1. 项目定位

这是一个基于 Spring Boot 的订单处理工具，核心目标是：

- 定时扫描输入目录中的 PDF 订单文件；
- 抽取订单与商品属性（颜色、型号、变量、字体、风格、附属商品）；
- 按业务规则补充配件；
- 导出标准化 Excel；
- 对命中规则的 PDF 页执行标注并归档源文件。

技术栈：

- Java 17
- Spring Boot 2.7.x
- Apache PDFBox（PDF 解析）
- Apache POI（Excel 写入）
- Jackson（规则配置 JSON）

---

## 2. 代码结构与职责划分

核心包结构位于 `src/main/java/com/pdfconverter`：

- `scheduler`：任务编排
  - `PdfProcessingScheduler`：每分钟触发一次流程编排
- `service`：业务处理
  - `PdfExtractorService`：订单和商品解析主服务
  - `AttributeRuleEngine`：属性规则引擎（配置驱动）
  - `AccessoryItemFactory`：订单级附加商品补充
  - `ProductListService`：产品清单匹配
  - `ConfigImportService`：模板导入并更新配置
  - `ProductTitleRecognitionService`：标题识别和 listingId 识别
- `service/export`：导出
  - `ExcelWriterService`：写入 Excel
- `service/core`：文件和标注等基础服务
  - `FileService`、`PdfMarkService`
- `config`：配置加载
  - `ProductAttributeConfig`、`AccessoryRuleConfig` 等
- `model`：领域数据
  - `PdfOrderData`、`ExcelData`、`ProductAttribute`
- `constant`：业务枚举与常量
  - `OrderType`、`ProductName`、`ProductSize`、`ProductColor`、`ProductVariable`
- `util`：文本提取/解析工具

资源配置位于 `src/main/resources`：

- `application.properties`：路径和运行参数
- `product-attribute-labels.json`：属性抽取规则主配置
- `accessory-rules.json`：附加商品补充规则
- `*.properties`：映射规则（字体、风格、标题等）
- `产品清单.csv`：产品映射基表（项目根目录）

---

## 3. 端到端业务流程

### 3.1 调度入口

`PdfProcessingScheduler.processPdfFiles()`（固定 60 秒）：

1. 处理配置模板导入（可更新规则配置）；
2. 扫描输入目录 PDF；
3. 逐个解析 PDF（收集订单+标注页）；
4. 汇总写入 Excel；
5. 按标注规则修改 PDF；
6. 移动至备份目录。

### 3.2 订单解析

`PdfExtractorService.extractFromPdf()` 采用“两遍扫描”：

- 第一遍：识别 `Order #`，建立订单页范围；
- 第二遍：按范围取完整文本并执行 `parseOrder()`。

`parseOrder()` 负责：

- 订单基础字段提取（收货、店铺、日期、物流）；
- 商品块拆分（以 `Quantity:` 为锚点）；
- 单商品解析 `parseItemDetail()`。

### 3.3 商品属性处理链

单商品 `parseItemDetail()` 主链路：

1. 识别商品类型/`listingId`；
2. 抽取动态属性段；
3. 调用 `AttributeRuleEngine.extractAttributes(listingId, ...)`；
4. 将规则结果写回 `ItemDetail`（颜色/型号/变量/字体/风格）；
5. 组装附属商品（如 Box/TieClip）；
6. 调用 `AccessoryItemFactory.supplementAddOns()` 补充订单级附加商品。

### 3.4 导出与归档

`ExcelWriterService.writeOrders()`：

- 映射产品中文名；
- 主商品按数量拆行，附属商品按单行汇总；
- 多面刻录按面数扩行；
- 写入 Excel 指定列。

后续由 `PdfMarkService` 标注 PDF，并通过 `FileService` 归档。

---

## 4. 配置驱动说明（最关键）

### 4.1 `product-attribute-labels.json`

作用：按 `listingId` 定义标签语义和提取方式，决定属性解析结果。

常见 `attributeType`：

- `COLOR` / `SIZE` / `PRODUCT_VARIABLE`
- `COMPOSITE`
- `ACCESSORY_ITEMS`
- `COLOR_WITH_ACCESSORY`
- `COLOR_ITEM_COMBO`
- `QUANTITY_WITH_SIZE`
- `ENGRAVING_SIDES` 等

### 4.2 `accessory-rules.json`

作用：定义订单级“补件规则”。

关键字段：

- `mainProductTypes`：主商品类型匹配
- `excludeProductTypes`：排除条件
- `orderCondition`：订单上下文条件（如 requireNoWoodBox）
- `accessoryType` / `accessoryVariable`
- `quantityRule` / `inheritConfig`

### 4.3 配置变更建议

- 先在测试样本验证，再发布；
- 每次改规则都记录“影响 listingId 范围”；
- 保留配置版本（建议按日期打 tag 或提交单独 commit）。

---

## 5. Java 规范符合度复核（当前结论）

整体上项目可运行、分层基本清晰，但离“长期维护友好”还有改进空间。

### 5.1 做得较好的部分

- 使用枚举统一业务语义（`OrderType`/`Product*`）；
- 核心流程链路完整，职责大体可追踪；
- 规则配置外置，业务可配置化演进；
- 日志覆盖较多，便于现场问题排查。

### 5.2 主要问题（按优先级）

1. **核心服务过大（高）**
  - `PdfExtractorService`、`AttributeRuleEngine`、`ExcelWriterService` 职责过重，方法体偏长，维护成本高。
2. **业务规则与实现耦合较深（高）**
  - 代码中仍存在较多格式特判与 hardcode 文本锚点，PDF 模板变化风险高。
3. **模型类历史痕迹较多（中）**
  - `PdfOrderData` 注释和字段说明混杂历史样本语义，噪音较多。
4. **配置与文件路径策略不统一（中）**
  - 部分配置按 classpath，部分按运行目录/外部文件，部署一致性需要明确约束。
5. **测试可见性不足（中）**
  - 缺少“规则回归样本 + 自动断言”的显式入口。

---

## 6. 可持续维护建议（落地路线）

### 第一步（1-2 天）

- 为关键链路补充“最小回归样本”：
  - PDF 输入样本（按 listingId）
  - 预期订单 JSON
  - 预期 Excel 关键列断言

### 第二步（3-5 天）

- 拆分 `PdfExtractorService`：
  - `OrderHeaderParser`
  - `ItemBlockSplitter`
  - `ItemAttributeAssembler`
- 拆分 `AttributeRuleEngine`：
  - 各 `attributeType` 提取器策略类

### 第三步（持续）

- 建立规则变更流程：
  - 变更说明模板（原因/影响范围/回滚方式）
  - 合并前自动跑样本回归
- 对“兜底命中”增加告警日志（便于发现潜在误匹配）。

---

## 7. 本地运行与排查

### 7.1 运行

- 打包：`mvn clean package`
- 运行：`java -jar target/itemMangementProject.jar`

### 7.2 必看配置

- `app.pdf.input-folder`
- `app.pdf.bak-folder`
- `app.excel.output-folder`
- `app.config.attribute-labels-file`
- `app.config.accessory-rules-file`

### 7.3 常见问题

- 解析字段为空：先检查 PDF 模板是否变化；
- 产品名误匹配：检查 `产品清单.csv` 与 `resolveSubClass` 映射；
- 补件重复：检查 `accessory-rules.json` 与已有附属商品识别逻辑。

---

## 回归测试标准（当前已确认）

### 1) 判定标准

- **严格一致**：当前以“输出 Excel 的全部字段必须一致”为回归验收标准。
- 实际自动化执行时分两层：
  - `expected/orders/*.orders.json`：先保障解析链路（订单 + 商品属性）稳定；
  - `expected/excel/*.excel.json`：逐列比对最终导出结果（建议作为下一步补齐）。

### 2) P0 样本清单（已纳入）

- 既有 P0：`baseline-3837329233`、`dogtag-batch-5-20260311`、`urn-1-20260328`、`urn-1-20260412`、`cufflink-6-04062026-marked`。
- 新增 P0：`cufflink-9-03252026`（源文件：`袖扣_订单_9单_03252026.pdf`）。

### 3) 新增样本重点核查项（`cufflink-9-03252026`）

- 覆盖商品：圆片项链、翅膀 addon、`Cufflink Box-Add on`、圆片鸭嘴领带夹等多品类组合。
- **重点 1：刻录面数识别**  
  `getEngravingRowCount` 对不同文案（如 Front/Back、N Side、Engraving Options）的面数识别必须稳定。
- **重点 2：拆分行数正确**  
  主商品按 `itemQuantity × engravingRows` 拆行，附属商品保持单行汇总。
- **重点 3：拆分后数量正确**  
  多面刻录时仅第一面写数量，其余面数量列留空；附属商品单行写总数量。

### 4) 变更流程

1. 新增/调整样本后，先更新 `RegressionPdfCases`。
2. 运行黄金快照生成：  
   `mvn -q -Dtest=GoldenSnapshotGeneratorTest -DregenerateGolden=true test`
3. 人工审核 `expected/orders/*.orders.json` 的变更是否符合业务预期。
4. 运行回归：  
   `mvn -q -Dtest=RegressionPdfIntegrationTest test`

### 5) Excel 输出测试标准（补充）

#### 5.1 比对对象与范围

- 比对对象：`expected/excel/<caseId>.excel.json`（由 Excel 导出结果标准化后生成）。
- 比对范围：仅比对业务数据行（不比对 xlsx 文件元数据、样式、列宽、创建时间）。
- 数据来源：按 `ExcelConstant.EXCEL_HEADERS` 的 18 列顺序输出快照。

#### 5.2 严格一致口径

- **逐行逐列全等**：行数、行顺序、每个单元格字符串值必须完全一致。
- 空值处理：空字符串与 `null` 不视为等价，快照中统一为字符串后比较。
- 不做容错：不忽略空格、大小写、标点差异。

#### 5.3 必测字段（18 列全部）

- `产品编号`、`用户名`、`订单编号`、`产品名称`、`型号`、`颜色`、`产品变量`
- `设计风格`、`刻录信息`、`字体`、`icon`、`是否派单`、`设计师`
- `数量`、`出库日期`、`Personalization`、`订购完全信息`、`商品标题`

#### 5.4 关键规则专项断言（P0）

- **刻录面数→拆行**：主商品输出行数必须等于 `itemQuantity × engravingRows`。
- **多面数量列**：同一主商品的多面拆行中，仅第一面行保留数量，其余行数量为空。
- **附属商品数量**：附属商品仅 1 行，数量列等于该附属商品总数量。
- **产品名称匹配链路**：由 listing 识别 + 四要素（细类/型号/颜色/变量）命中 `产品清单.csv` 的结果应稳定。

#### 5.5 通过/失败标准

- 任意 case 出现任意一列不一致：**测试失败（Blocker）**。
- 若规则变更是预期行为：先更新 `expected/orders` 与 `expected/excel`，再提交并附变更说明。

#### 5.6 新增样本 `cufflink-9-03252026` 的 Excel 关注点

- 覆盖混合品类（圆片项链、翅膀 addon、`Cufflink Box-Add on`、圆片鸭嘴领带夹）。
- 重点校验：刻录面数识别、拆分行数、拆分后数量列是否符合 5.4。

### 6) 等价组回归模型（主产品/拆分规则/附加规则一致即可共用案例）

- 回归案例按“规则等价组”建设，不强制每个 listing 单独一条。
- 同组判定条件（需同时满足）：
  1. 主产品类型一致；
  2. 动态属性拆分规则一致（标签语义与提取逻辑一致）；
  3. 附属/附加商品规则一致（补件条件、数量策略一致）。
- 同组可共享 1 个代表 caseId；若组内出现新变体导致规则分叉，再拆新组。

#### 6.1 已确认分组（基于 `cufflink-9-03252026`）

- **G1 袖扣组**：`4010589174`、`4010008490`
- **G2 领带夹组**：`4012978221`、`4010537666`、`4010519942`、`4012826067`、`4012607415`
- **G3 吊坠+add-on组**：`4012450967`
- **G4 附加商品单买组**：`4010519760`

#### 6.2 重要补充规则（已确认）

- “同订单合并同类行”**只适用于**：
  1. `包装盒`附属商品；
  2. 补充生成的附加商品（规则补偿 add-on）。
- 其它商品（尤其可刻录商品）即使名称相同也**不合并**，必须保留逐行输出。

### 7) 详细断言模板（v1，按组填写）

> 用途：每个等价组至少 1 份模板实例；模板经业务确认后转为自动化断言。

#### 7.1 组定义信息

- `groupId`：
- `主产品`：
- `动态属性规则签名`：（例：`Color+Size | Item Options | Engraving Sides`）
- `附加规则签名`：（例：`requireNoWoodBox + BOX_RECTANGLE`）
- `覆盖 listingId`：（逗号分隔）
- `代表 caseId`：

#### 7.2 结构断言（必须）

- 订单数 = `expectedOrderCount`
- 每单商品行数 = `expectedLineCountByOrder`
- 每单至少 1 行主商品（`mainProductFlg=true`）
- 每行关键列非空：`订单编号`、`产品名称`

#### 7.3 附属/附加商品断言（必须）

- `mustAppearProducts`：应出现的附属/附加商品及最小出现次数  
  例：`包装盒 >= 1`、`翅膀 add-on >= 1`
- `mustNotAppearProducts`：不应出现的商品  
  例：已有木盒时不应再补同类礼盒
- `exactCountProducts`：指定商品必须精确次数  
  例：`Box-长方形木盒 == 1`
- `dedupRules`：同一订单是否允许重复补件（默认不允许）

#### 7.4 Personalization 断言（必须）

- `personalizationMustContain`：必须包含的关键词（按订单或按行）  
  例：`Front`、`Back`、`font #xx`
- `personalizationMustNotContain`：禁止出现的噪声词（可选）
- `fullInfoMustContain`（订购完全信息列）：必须包含动态属性标签 + personalization 拼接结果关键片段
- `fontStyleExpected`：字体/风格映射期望值（若适用）

#### 7.5 拆行与数量断言（必须）

- `engravingRowsExpected`：按商品类型的面数期望（单面/双面/N面）
- `mainRowFormula`：主商品行数 = `itemQuantity × engravingRows`
- `multiSideQuantityRule`：多面时仅首行数量非空，其余为空
- `accessoryQuantityRule`：附属商品单行汇总数量

#### 7.6 Excel 全字段断言（必须）

- 18 列逐行逐列全等（含行顺序）
- 空字符串与 `null` 不等价
- `出库日期` 使用 `__TODAY__` 占位比较

#### 7.7 失败分级（建议）

- `Blocker`：主产品识别错、附加/附属商品错、拆行错、数量错、18列任一列不一致
- `Warning`：仅日志/注释/非业务冗余文本差异（默认不启用，需单独批准）

#### 7.8 模板示例（可复制）

```yaml
groupId: G-CUFF-ENGRAVING-BOX
主产品: 袖扣
动态属性规则签名: "Color+Size | Item Options | Engraving Sides"
附加规则签名: "requireNoWoodBox + box-compensation"
覆盖 listingId: "cuff_item_list_1,cuff_with_box_options"
代表 caseId: "cufflink-9-03252026"

expectedOrderCount: 9
expectedLineCountByOrder:
  "4012978221": 5

mustAppearProducts:
  - name: "圆片鸭嘴领带夹"
    min: 2
  - name: "包装盒"
    min: 1
mustNotAppearProducts:
  - "重复补偿礼盒"
exactCountProducts:
  - name: "Box-长方形木盒"
    count: 1

personalizationMustContain:
  "4012978221": ["Clip Customization", "Font #5"]
fullInfoMustContain:
  "4012978221": ["Engraving Sides", "Color Finish and Box"]

engravingRowsExpected:
  "圆片鸭嘴领带夹": 2
mainRowFormula: "itemQty * engravingRows"
multiSideQuantityRule: "first-row-has-qty, others-empty"
accessoryQuantityRule: "single-row-sum"

excelStrictCompare: true
outputDateToken: "__TODAY__"
```

### 7.9 G1~G4 已确认断言快照（冻结）

- `4012978221`：圆片鸭嘴领带夹颜色`金色`、型号`L`；双面拆2行（首行数量`1`，次行空）；包装盒变量`长方形礼盒`。
- `4012450967`：圆片吊坠`L/银色`双面拆2行；必须有`翅膀 add-on`、`生日石 add-on(九月)`、`基础链`。
- `4010519760`：仅1行`包装盒`，变量`Box-长方形木盒`，数量`1`；不应出现主商品。
- `4010537666`：双面滑入领带夹`L/银色`；包装盒变量`Oval Box-椭圆形开窗木盒`，数量`2`。
- `4012826067`：鸭嘴领带夹（薄）`L/金色`数量`1`；包装盒变量`长方形礼盒`数量`1`；`Personalization`含`JKH`。
- `4010519942`：`Front Only` 单面场景；主商品2行且每行数量`1`；包装盒数量`2`。
- `4010008490`：双抛袖扣`S/银色`，`Style 42`、`Font 4(no)`；方形木盒数量`1`。
- `4012607415`：两行主商品（黑/金各1）；相同 `Oval Box-椭圆形开窗木盒` 合并1行，数量`2`。

### 7.10 新增必补案例（下一轮）

按你的要求，需补齐以下品类的代表回归案例（等价组模式）：

- **狗牌组**（DOG_TAG）：至少1个代表 case，覆盖尺寸/颜色/静音或配件分支。
- **骨灰罐组**（MEMORIAL/URN）：至少1个代表 case，覆盖 `Engraving Options` 面数与拆行数量。
- **心形相盒组**（Heart Locket/Heart Box Pendant）：至少1个代表 case，覆盖主商品 + 相关 add-on/盒规则。

> 说明：狗牌与骨灰罐已有基础样本（`dogtag-batch-5-20260311`、`urn-1-20260328`/`urn-1-20260412`）；心形相盒需新增样本 PDF 后纳入基线。

### 7.11 狗牌组（已冻结断言）

#### 代表案例：`dogtag-batch-5-20260311` / 订单 `3997080366`

- 跨页完整性：该订单为两页，解析不得漏页。
- 数量口径：
  - `totalItemQuantity = 7`（主商品数）
  - Excel 总行数 = `14`（7 行主商品 + 7 行硅胶绑带）
- 主商品行（7行）：
  - `产品名称=硅胶哑光静音狗牌`、`型号=S`、`颜色=银色`、`数量=1`
- 附属行（7行）：
  - `产品名称=硅胶绑带`、`型号=S`、`数量=2`
  - 按实际颜色分别输出，颜色不同不得合并
- `订购完全信息` 必含：`Size Options: Silver_S` 与对应 `Silicone Rubber Holder Color`

#### 分支案例：订单 `3999840953`（双面分支）

- `Engraving Option: Front & Back` 视为双面，按每件商品拆 2 行：
  - 首行数量 `1`，次行数量空
- 该订单主商品数量 2，因此主商品总输出 4 行。
- 4 行均满足：`产品名称=镂空爪子骨头形狗牌`、`型号=L`、`颜色=银色`。

#### 分支案例：订单 `4000384867`（尼龙分支）

- 仅 1 行主商品，无硅胶绑带附属行。
- `产品名称=尼龙哑光静音狗牌`、`型号=S/M`、`颜色=银色`、`数量=1`
- 字体标准值：`Font 10(no)`

#### 可降级重复样本（同等价模式）

- `3997225040`、`3999863995`：与代表模式一致，可做抽样或不做完整重复断言。

### 7.12 骨灰罐组（已冻结断言）

#### 代表案例：`urn-1-20260328` / 订单 `4015776715`

- 主商品属性：
  - `产品名称=骨灰罐`、`颜色=银色`、`产品变量=不锈钢`、`型号`为空
- `Engraving Options: Lid & Body` 视为两面：
  - 主商品输出 2 行（首行数量 `1`，次行数量空）
- 同订单需补 1 行礼盒：
  - `产品名称=包装盒`（或等价礼盒名）
  - `产品变量=小方形礼盒`
  - `数量=1`

#### 同组重复样本

- `urn-1-20260412`：与代表案例同产品属性与同规则，继承同一套断言。

### 7.13 心形相盒组（已冻结断言）

#### 代表案例：`袖扣_订单_12单_04192026.pdf` / 订单 `4032032630`

- 主商品行：
  - `产品名称=花卉心形相盒吊坠`
  - `颜色=金色`
  - `产品变量=十月`
  - `型号`为空
  - `数量=1`
- 必须补充 1 行基础链：
  - `产品名称=基础链`
  - `颜色=金色`（与主商品同色）
  - `产品变量=全链`
  - `数量=1`
- 该订单至少 2 行（主商品 + 基础链）。
- 当前不启用文本关键词强断言（按已确认范围执行）。

### 7.14 可执行 YAML（冻结断言 v1）

```yaml
groups:
  - groupId: G-DOGTAG
    caseId: dogtag-batch-5-20260311
    representativeOrder: "3997080366"
    assertions:
      - type: order_page_completeness
        orderNumber: "3997080366"
        expectedMultiPage: true
      - type: order_total_item_quantity
        orderNumber: "3997080366"
        expected: 7
      - type: excel_order_total_rows
        orderNumber: "3997080366"
        expected: 14
      - type: excel_rows_match
        orderNumber: "3997080366"
        productName: "硅胶哑光静音狗牌"
        expectedRows: 7
        requiredColumns:
          型号: "S"
          颜色: "银色"
          数量: "1"
      - type: excel_rows_match
        orderNumber: "3997080366"
        productName: "硅胶绑带"
        expectedRows: 7
        requiredColumns:
          型号: "S"
          数量: "2"
        rule: "颜色不同不得合并"
      - type: excel_contains_keywords
        orderNumber: "3997080366"
        column: "订购完全信息"
        keywords: ["Size Options: Silver_S", "Silicone Rubber Holder Color"]

      - type: branch_double_side
        orderNumber: "3999840953"
        expectedRowsForMainProduct: 4
        mainProduct:
          产品名称: "镂空爪子骨头形狗牌"
          型号: "L"
          颜色: "银色"
        quantityRule: "双面拆行：首行=1，次行空"

      - type: branch_nylon
        orderNumber: "4000384867"
        expectedRows: 1
        requiredColumns:
          产品名称: "尼龙哑光静音狗牌"
          型号: "S/M"
          颜色: "银色"
          数量: "1"
          字体: "Font 10(no)"

    duplicateCanSkipFull:
      - "3997225040"
      - "3999863995"

  - groupId: G-URN
    caseId: urn-1-20260328
    representativeOrder: "4015776715"
    assertions:
      - type: excel_rows_match
        orderNumber: "4015776715"
        productName: "骨灰罐"
        expectedRows: 2
        requiredColumns:
          颜色: "银色"
          产品变量: "不锈钢"
        quantityRule: "Lid&Body 两面：首行=1，次行空"
      - type: excel_rows_match
        orderNumber: "4015776715"
        productName: "包装盒"
        expectedRows: 1
        requiredColumns:
          产品变量: "小方形礼盒"
          数量: "1"

    duplicateInSameRuleGroup:
      - "urn-1-20260412"

  - groupId: G-HEART-LOCKET
    caseId: cufflink-12-04192026
    representativeOrder: "4032032630"
    assertions:
      - type: excel_rows_match
        orderNumber: "4032032630"
        productName: "花卉心形相盒吊坠"
        expectedRows: 1
        requiredColumns:
          颜色: "金色"
          产品变量: "十月"
          数量: "1"
      - type: excel_rows_match
        orderNumber: "4032032630"
        productName: "基础链"
        expectedRows: 1
        requiredColumns:
          颜色: "金色"
          产品变量: "全链"
          数量: "1"
      - type: excel_order_total_rows
        orderNumber: "4032032630"
        minExpected: 2
    options:
      enableTextKeywordAssertions: false
```

---

## 8. 推荐后续文档

建议新增并长期维护以下文档：

- `docs/rules/listing-regression-cases.md`（每个 listing 的样本与预期）
- `docs/architecture/flow.md`（流程图与模块关系）
- `docs/operations/deploy-and-rollback.md`（部署与回滚）

这样可以把项目从“可运行”提升到“可持续维护”。

---

## 9. 可执行重构清单（仅本地）

本清单按“可逐步落地、每步可回归”设计，建议严格按顺序执行。

### 9.1 迭代 A：先补可回归基线（必须先做）

目标：先建立“改完能验证”的护栏，再动核心服务。

- 新建目录：
  - `src/test/resources/samples/pdf/`
  - `src/test/resources/expected/orders/`
  - `src/test/resources/expected/excel/`
- 每个高频 `listingId` 至少准备 1 份样本：
  - 原始 PDF
  - 期望订单结果（JSON）
  - 期望 Excel 关键字段（订单号、产品名称、型号、颜色、变量、数量）
- 新增最小回归测试（建议 JUnit5）：
  - `PdfExtractorService` 解析结果断言
  - `ExcelWriterService` 行输出断言
  - `AttributeRuleEngine` 指定 `listingId` 断言

验收标准：

- 关键 listing 样本测试全部通过；
- 失败时能明确知道是“解析变更”还是“配置变更”。

### 9.2 迭代 B：拆分 `PdfExtractorService`

目标：降低单类复杂度，减少后续修改引发的连锁风险。

建议新增类：

- `service/parser/OrderHeaderParser`
- `service/parser/ItemBlockSplitter`
- `service/parser/ItemDetailParser`

迁移顺序：

1. 先抽出 `parseOrder()` 中“订单头提取”逻辑到 `OrderHeaderParser`；
2. 再抽商品块边界逻辑（`findItemBlockStart/End`）到 `ItemBlockSplitter`；
3. 最后抽 `parseItemDetail()` 到 `ItemDetailParser`，保留原入口编排。

验收标准：

- 老样本输出和重构前一致；
- `PdfExtractorService` 只保留流程编排（不再承载细节规则）。

### 9.3 迭代 C：拆分 `AttributeRuleEngine`

目标：把 `switch(attributeType)` 转成策略模式，降低新增规则成本。

建议结构：

- `service/attribute/AttributeExtractorStrategy`（接口）
- `service/attribute/strategies/ColorExtractor`
- `service/attribute/strategies/CompositeExtractor`
- `service/attribute/strategies/AccessoryItemsExtractor`
- 其余按类型扩展

迁移原则：

- 一次只迁移 1-2 个 `attributeType`；
- 每迁移一类就跑一轮 listing 样本回归。

验收标准：

- 新增 `attributeType` 时不再修改大段 `switch`；
- 每个策略类职责单一、可单测。

### 9.4 迭代 D：稳定导出层 `ExcelWriterService`

目标：把“数据准备”和“写 Excel”分离，便于排查导出错误。

建议拆分：

- `ExcelRowBuilder`：负责把 `ItemDetail` 转为 `ExcelData`
- `ExcelFileWriter`：负责 sheet/row/cell 写入
- `EngravingRowPolicy`：封装多面刻录拆行规则

验收标准：

- 导出字段映射集中在 `ExcelRowBuilder`；
- 行拆分逻辑可单测，避免隐藏在写文件过程里。

### 9.5 迭代 E：配置治理与发布约束

目标：降低“改规则引发线上偏差”的概率。

- 给 `product-attribute-labels.json` 增加基本 schema 校验（字段完整性、枚举值合法性）；
- 给 `accessory-rules.json` 增加规则冲突检查（同条件重复补件）；
- 制定变更模板：
  - 变更原因
  - 影响 listingId
  - 回归样本编号
  - 回滚方式

验收标准：

- 配置异常在启动期可发现；
- 每次规则调整都有可追溯记录。

### 9.6 任务优先级（建议）

- P0：迭代 A（回归基线）
- P1：迭代 B（解析拆分）
- P1：迭代 C（规则引擎拆分）
- P2：迭代 D（导出层拆分）
- P2：迭代 E（配置治理）

### 9.7 本地执行纪律（当前约束）

- 所有改动仅在本地分支进行；
- 不执行 `git push`；
- 每次迭代结束保留本地提交记录（便于回滚和对比）。

---

## 10. 今日持续基线与测试标准落地（2026-04-30）

### 10.1 持续基线（新增/完善）

- 解析快照基线：`src/test/resources/expected/orders/*.orders.json`
- Excel 全字段基线：`src/test/resources/expected/excel/*.excel.json`
- 新增样本：`cufflink-9-03252026`（`袖扣_订单_9单_03252026.pdf`）
- 新增业务断言配置：`src/test/resources/regression/business-assertions.json`

### 10.2 自动化测试入口（新增）

- 解析回归：`RegressionPdfIntegrationTest`
- Excel 回归：`RegressionExcelIntegrationTest`
- 业务规则回归（配置驱动）：`RegressionBusinessRulesTest`
- 基线生成：
  - 解析基线：`GoldenSnapshotGeneratorTest`
  - Excel 基线：`GoldenExcelSnapshotGeneratorTest`

### 10.3 今日确认的业务断言范围

- 分组模型：按“主产品 + 动态属性拆分规则 + 附加规则”建立等价组，不强制每个 listing 单独建 case。
- 狗牌组：跨页完整性、行数与数量、双面分支、尼龙分支。
- 骨灰罐组：`Lid & Body` 两面拆行、补小方形礼盒、`产品变量=不锈钢`。
- 心形相盒组：主商品 + 同色基础链（`全链`），不启用文本关键词强断言。

### 10.4 今日关键修复（支撑上述标准）

- 修复跨页订单漏页：订单页范围按“起始页到下一订单起始页前一页”计算。
- 修复刻录面数识别：支持 `Engraving Option/Options`，并避免被 personalization 内 `&` 干扰。
- 修复尺寸 `S/M` 识别优先级与字体映射 `Font #10 -> Font 10(no)`。
- 将骨灰罐默认产品变量改为配置化（`product-attribute-labels.json` 的 `defaultProductVariable`），移除代码硬编码。
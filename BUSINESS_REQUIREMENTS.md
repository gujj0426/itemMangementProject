# PDF 订单处理系统 — 业务需求说明

## 1. 项目定位

将 Etsy 等平台导出的 **订单 PDF** 自动解析为结构化数据，生成 **Excel 排产/出库表**，并对特定订单 **标注 PDF 页面**（如领带夹 Style 6、静音狗牌 S 码等）。  
系统以 **配置驱动** 为主：标题识别规则、动态属性解析规则、附加商品规则、颜色/字体/样式映射、产品清单等均可通过配置文件维护。

**字段级解析与 Excel 列对应**：见 `[docs/DETAILED_BUSINESS_RULES.md](docs/DETAILED_BUSINESS_RULES.md)`。

---

## 2. 核心业务目标


| 目标            | 说明                                                                                 |
| ------------- | ---------------------------------------------------------------------------------- |
| 批量处理          | 定时扫描输入目录中的 PDF，解析后统一写 Excel，再将原 PDF 移入备份目录                                         |
| 多店铺/多 Listing | 通过标题关键字 + `listingId` 区分不同商品链接与属性布局                                                |
| 属性结构化         | 将 PDF 中的动态标签（Color、Size、Item、Woodbox Options 等）解析为统一枚举：订单类型、产品名称、颜色、型号、产品变量、字体、样式等 |
| 组合订单          | 支持袖扣+领带夹+礼盒等组合；主商品与附属商品分行输出                                                        |
| 礼品盒策略         | 客户未购买木盒时，按业务规则 **补偿礼品盒**（见第 6 节）；客户已在动态属性中选盒则按解析结果输出                               |
| 输出可追溯         | Excel 中产品中文名优先来自 **产品清单 CSV**，按细类 + 型号 + 颜色 + 变量匹配                                 |


---

## 3. 端到端业务流程

1. **入口**：`PdfProcessingScheduler` 每分钟执行一次。
2. **可选**：若输入目录存在 `listing-config*.xlsx`，先由 `ConfigImportService` 生成/更新 `product-title-recognition-rules.properties`、`product-attribute-labels.json`、`accessory-rules.json`（生成后需重启服务生效）。
3. **解析**：`PdfExtractorService` 对 PDF **两遍扫描**：先按页记录每个 `Order #` 的页码范围，再按范围抽取完整文本，避免跨页截断。
4. **单笔订单**：提取收件人、店铺、日期、物流、商品数量等固定字段；按 `Quantity:` 切块解析每个商品行。
5. **单商品**：标题识别 → `listingId` → `AttributeRuleEngine` 按标签配置提取属性 → 组装主商品与附属商品 → `AccessoryItemFactory` 按规则补充附加商品。
6. **汇总输出**：本批次成功解析的订单合并写入一份 Excel；成功后对需标注的 PDF 调用 `PdfMarkService`，最后将 PDF 移至备份目录。

---

## 4. 标题识别（Listing 绑定）

- **配置**：`product-title-recognition-rules.properties`  
- **格式**：`关键字列表 | ProductName.nameCode | OrderType 大类名 | 是否组合 | listingId`  
- **匹配**：标题（小写）须 **全部包含** 规则中的关键字；按文件顺序 **首条命中** 生效。  
- **作用**：确定 `ProductName`、`OrderType`、`listingId`、`是否组合`，供后续属性解析使用。

---

## 5. 动态属性解析

- **配置**：`product-attribute-labels.json`（按 `listingId` 组织多条标签）  
- **引擎**：`AttributeRuleEngine` 根据标签名与 `attributeType` 解析颜色、尺寸、复合字段（如 `颜色_尺寸`）、礼盒映射、`Item` 中带 `+` 的附属列表等。  
- **典型语义**：`COLOR`、`SIZE`、`COMPOSITE`、`ACCESSORY_ITEMS`、`COLOR_WITH_ACCESSORY`、`COLOR_SIZE_ACCESSORY`、`ENGRAVING_SIDES` 等。  
- **业务文档**：仓库内 `动态属性部分样例.xlsx` 记录各 Listing 的 PDF 属性说明，应与上述 JSON 对齐维护。

---

## 6. 礼品盒 / 附加商品规则（袖扣、领带夹）

- **配置**：`accessory-rules.json`，由 `AccessoryItemFactory` 在解析完成后执行。  
- **“未购买木盒”判定**：订单中尚未存在 **木盒类** 包装盒（实现上以 `BOX` 且产品变量枚举名含 `WOOD_BOX` 等为依据），且规则要求 `requireNoWoodBox`。  
- **当前内置逻辑概要**（以配置文件为准，可调）：  
  - 仅袖扣、无其它冲突类型时 → 补 **小方盒**（`BOX_SMALL_SQUARE`）。  
  - 仅领带夹、无其它冲突类型时 → 补 **长方盒**（`BOX_RECTANGLE`）。  
  - 同时存在袖扣与领带夹组合等情况 → 按规则补 **大方盒**（`BOX_LARGE_SQUARE`）等。
- **附属领带夹尺寸**：从组合 `Item` 解析出的附属 **领带夹**，在未单独指定尺寸时 **默认型号为 L**，**不继承主商品（如袖扣）的 S/L**。

客户若在动态属性中 **明确选择礼盒/木盒选项**，优先按解析结果生成对应 `BOX` 行，不与上述兜底冲突处理混淆。

---

## 7. Excel 输出规则（摘要）

- **文件**：按日期 + 序号滚动生成 `.xlsx`。  
- **产品中文名**：`ProductListService` 读取 `**产品清单.csv`**，按 **产品细类 + 型号(sizeCode) + 颜色 + 产品变量** 精确或打分匹配；匹配失败则回退 `ProductName` / `OrderType` 显示名。  
- **主商品**：通常按数量 **拆行**，每行数量为 1；**多面刻录** 时按面数额外拆行。  
- **附属商品**：常见为单行汇总数量（实现以 `ExcelWriterService` 为准）。  
- **字体 / 样式**：经映射服务转为标准名称输出。

---

## 8. PDF 标注（可选）

解析阶段若检测到订单内存在 **Style 6 / S6**（领带夹等）、**静音狗牌 S 码** 等条件，记录对应 PDF 页码，写入完成后由 `PdfMarkService` 在 PDF 上标注，便于车间区分。

---

## 9. 路径与运行参数

- 输入目录、备份目录、Excel 输出目录由 `application.properties` 占位符（如 `inputPath`、`bakPath`、`outputPath`）注入，启动时通过 JVM 或 Spring 参数传入。  
- 可选：`app.config.accessory-rules-file` 指定外部绝对路径的 `accessory-rules.json` 覆盖 classpath 默认文件。

---

## 10. 非功能需求（运维）

- 日志：解析失败单订单可跳过并继续处理其它订单。  
- 配置变更：`listing-config` 导入后需 **重启** 才能加载新生成的 properties/json。  
- Maven：可使用自定义 `settings.xml` 与本地仓库路径进行依赖解析与构建。

---

## 11. 配置一致性维护建议

- `product-title-recognition-rules.properties` 中的每个 **listingId** 应在 `product-attribute-labels.json` 中有对应块（当前仓库设计如此对齐）。  
- 若两条标题规则共用同一 **listingId**（如鸭嘴领带夹多条 listing 共用一套属性），属于有意复用，需在变更时一并评估。  
- 预留仅存在于 labels、未挂在标题规则上的 **listingId**（如备用狗牌规则）时，应备注用途，避免误认为缺失标题规则。

---

*文档版本：随代码与配置演进更新；具体枚举与 JSON 字段以源码及 `src/main/resources` 下文件为准。*
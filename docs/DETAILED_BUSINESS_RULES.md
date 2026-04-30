# 详细业务规则与业务流程说明

本文档描述 PDF 订单处理系统在代码层面的 **业务流程**、**字段解析规则**、**Excel 各列输出规则** 及 **附加规则**。具体枚举值与配置文件请以 `src/main/resources` 及源码为准。

---

## 一、总体业务流程

```
┌─────────────────────────────────────────────────────────────────────────┐
│ PdfProcessingScheduler（约每 60 秒）                                      │
├─────────────────────────────────────────────────────────────────────────┤
│ 1) 可选：扫描输入目录 listing-config*.xlsx → ConfigImportService         │
│    生成/更新 product-title-recognition-rules.properties、               │
│    product-attribute-labels.json、accessory-rules.json（需重启生效）       │
├─────────────────────────────────────────────────────────────────────────┤
│ 2) 扫描输入目录 *.pdf                                                     │
├─────────────────────────────────────────────────────────────────────────┤
│ 3) PdfExtractorService.extractFromPdf（两遍扫描，支持跨页订单）           │
│    → 每个 PdfOrderData + 多个 ItemDetail                                 │
├─────────────────────────────────────────────────────────────────────────┤
│ 4) 汇总本批次成功解析的订单 → ExcelWriterService.writeOrders              │
│    → 追加写入同日序号 Excel（前缀 + yyMMdd + 序号）                       │
├─────────────────────────────────────────────────────────────────────────┤
│ 5) PdfMarkService（按需标注 Style6 / 静音狗牌 S 等）→ FileService 移备份目录 │
└─────────────────────────────────────────────────────────────────────────┘
```

**失败策略**：单个订单解析异常则跳过该订单，不阻断同一 PDF 内其它订单；写入 Excel 失败则本轮不移动 PDF 至备份。

---

## 二、PDF 订单层字段解析（`PdfOrderData`，不写 Excel 列但有业务含义）

以下为 `PdfExtractorService.parseOrder` 从订单全文截取的逻辑锚点（与 Etsy PDF 版式强相关）。


| 字段                                  | PDF 锚点 / 规则                                                       |
| ----------------------------------- | ----------------------------------------------------------------- |
| **orderNumber**                     | 正则 `Order\s*#\s*(\S+)`，第一个捕获组                                     |
| **username**                        | `Ship to` 与 `Scheduled to ship by` 之间文本的第一行                       |
| **shippingAddress**                 | 同上段落从第二行起拼接到段落末尾                                                  |
| **scheduledShippingDate**           | `Scheduled to ship by` 与 `Shop` 之间，`ExtractUtil.cleanDateText`    |
| **shopName**                        | `Shop` 与 `Order date` 之间                                          |
| **orderDate**                       | `Order date` 与 `Payment method` 之间，`cleanDateText`                |
| **paymentMethod**                   | `Payment method` 与 `Shipping method` 之间                           |
| **shippingMethod**                  | `Shipping method` 与 `Packaging` 之间                                |
| **packagingInfo**                   | `Packaging` 与 `Tracking` 之间                                       |
| **trackingInfo**                    | `Tracking` 与 `\d+\s+items` 之间                                     |
| **trackingNumber / courierCompany** | `trackingInfo` 按 `via` 拆分：前半为单号，后半为承运商（可无）                        |
| **totalItemQuantity**               | 首个 `\d+\s*item(s)?` 中的数字                                          |
| **additionalNote**                  | `Do the green thing` 之后的全文                                        |
| **hasTieClipStyle6**                | 任意商品的 `Personalization` 中匹配 `(?i)\b(S6                            |
| **hasSilentDogTagS**                | 存在 `OrderType.DOG_TAG` 且型号为 `S` 或 `SM`，且 `ProductName` 不含 `NYLON` |


**说明**：上述订单级字段主要用于日志与 PDF 标注逻辑；**Excel 当前实现仅输出用户名、订单编号及商品行相关字段**（见第六节）。

---

## 三、商品块切分规则

1. 在 `X items` 之后截取「商品区域」正文。
2. 用所有 `Quantity:` 出现位置切块：每个商品对应一段文本。
3. **块起始**：从当前 `Quantity:` 向前回溯，优先找到最后一个独立行 `\nShop\n`，标题从其后开始（适配左栏 `Shop` + 店铺名与标题同行）。
4. **块结束**：下一商品第一个 `Quantity:` 之前；最后一单可到文末，并截断 `Do the green thing` 之后营销文案。
5. 单块内再调用 `parseItemDetail`：标题、数量、动态标签、`Personalization` 等。

---

## 四、单商品（`ItemDetail`）解析链路

### 4.1 标题与 Listing


| 步骤     | 说明                                                                                                                                              |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| **标题** | `ExtractUtil.getitemTitle`，必要时去掉行首 `shopName` 前缀                                                                                                |
| **识别** | `ProductTitleRecognitionService.identifyProductType(title)` 读取 `product-title-recognition-rules.properties`：**标题（小写）须包含规则全部关键字**，按文件顺序 **首条命中** |
| **产出** | `ProductName`（nameCode）、`OrderType`（大类）、`listingId`、`isComposite`、`mainProductFlg=true`                                                         |


### 4.2 动态属性 Map


| 步骤         | 说明                                                                                                     |
| ---------- | ------------------------------------------------------------------------------------------------------ |
| **区间**     | 默认：`Quantity:` 至 `Personalization:`；若无 Personalization 行则从 Quantity 后扫行并过滤边界行（见 `PdfExtractorService`） |
| **解析**     | `ExtractUtil.parseDynamicAttributes` → `Map<标签名, 值>`                                                   |
| **订购完全信息** | `buildFullOrderInfo`：过滤噪音行后与 `Personalization` 拼接，写入 `ItemDetail.dynamicAttributes`                    |


### 4.3 属性引擎（`AttributeRuleEngine`）

- **输入**：`listingId`、`ProductName.nameCode`（未知时用 `UNKNOWN`）、动态属性 Map。
- **配置**：`product-attribute-labels.json` 中该 `listingId` 下的 `attributeLabels`（标签名须与 PDF 行一致或配置一致）。
- **输出**：`ProductAttribute`（颜色、尺寸、产品变量、字体、样式、`ACCESSORY_ITEMS` 列表等）。
- **应用到主商品**：`applyProductAttribute` → `ItemDetail` 的 `productColor`、`productSize`、`productVariable`、`font`、`style`；必要时从 `PersonalizationParserUtil` 补字体/样式。
- **附属商品展开**：`assembleItemDetailListForVoro` 根据 `additionalProductNames` 等生成多条 `ItemDetail`（跳过与主商品同 `OrderType` 的重复项）。

**附属领带夹尺寸规则（业务要点）**：

- 若 `Item:` 类解析出附属尺寸前缀（如 `S_TieClip`），用附属独立尺寸。
- 若为 **领带夹**且无独立尺寸：**默认型号 L**，**不继承主商品（如袖扣）的尺寸**。

### 4.4 附加礼品盒规则（`AccessoryItemFactory`）

- **配置**：`accessory-rules.json`（可被 `app.config.accessory-rules-file` 指向的外部文件覆盖）。
- **时机**：主商品 + 由属性解析出的附属列表组装成临时列表后，调用 `supplementAddOns`。
- **规则含义概要**（以配置文件为准）：
  - 仅袖扣、订单内无冲突类型、且无木盒 → 补 **小方盒**（`BOX_SMALL_SQUARE`）。
  - 仅领带夹、类似条件 → 补 **长方盒**（`BOX_RECTANGLE`）。
  - 袖扣且订单内另有领带夹等组合条件 → 补 **大方盒**（`BOX_LARGE_SQUARE`，数量可为固定 1）。
- **「已有木盒」判定**：现有附属里存在 `BOX` 且 `productVariable` 枚举名含 `WOOD_BOX` 等时，`requireNoWoodBox` 规则可能不再补盒。

---

## 五、颜色映射补充规则（`ColorMapperService`）

- 主映射：`color-name-mapping.properties`（key 参与 **包含匹配**，长度长的 key 优先）。
- 归一化（降低 OCR/拼写误差）：如 `sliver→silver`、`blackr→black`、`rose goldr→rose gold`、`rosegold→rose gold`。
- 仍未命中 → 尝试把原文当作 `ProductColor` 枚举名解析。

---

## 六、Excel 输出：列定义与填充规则

表头定义见 `ExcelConstant.EXCEL_HEADERS`（共 **18 列**，索引 0–17）。

### 6.1 行过滤

仅当 `ItemDetail.orderType != null` 且 `!= UNKNOWN` 时输出；其它商品行被跳过。

### 6.2 逐列说明（与 `ExcelWriterService.fillRow` 一致）


| 列索引    | 表头名             | 数据来源                           | 转换 / 规则                                                                                |
| ------ | --------------- | ------------------------------ | -------------------------------------------------------------------------------------- |
| **0**  | 产品编号            | 固定                             | 空字符串（预留）                                                                               |
| **1**  | 用户名             | `PdfOrderData.username`        | 原样                                                                                     |
| **2**  | 订单编号            | `PdfOrderData.orderNumber`     | 原样                                                                                     |
| **3**  | 产品名称            | 计算                             | 见 **6.3 中文名解析**                                                                        |
| **4**  | 型号              | `ItemDetail.productSize`       | 输出 **sizeCode**（如 `L`、`S`）；若为空则 `getDefaultSize(detail)`（袖扣/各类领带夹默认等多为 `L`，见代码 switch） |
| **5**  | 颜色              | `ItemDetail.productColor`      | 枚举 **中文 displayName**；未知则输出空字符串                                                        |
| **6**  | 产品变量            | `ItemDetail.productVariable`   | 枚举 **中文 displayName**；未知则空                                                             |
| **7**  | 设计风格            | `ItemDetail.style`             | **仅主商品**：经 `StyleNameMappingService.getStandardName`；附属商品 **不输出样式**（写入 null，表现为空）      |
| **8**  | 刻录信息            | 固定                             | 空                                                                                      |
| **9**  | 字体              | `ItemDetail.font`              | **仅主商品**：经 `FontNameMappingService.getStandardName`；附属商品为空                             |
| **10** | icon            | 固定                             | 空                                                                                      |
| **11** | 是否派单            | 固定                             | 空                                                                                      |
| **12** | 设计师             | 固定                             | 空                                                                                      |
| **13** | 数量              | 计算                             | 见 **6.4 拆行与数量**                                                                        |
| **14** | 出库日期            | 系统日期                           | 格式 `yyyy年MM月dd日`（写入当日）                                                                 |
| **15** | Personalization | `ItemDetail.personalization`   | 原样                                                                                     |
| **16** | 订购完全信息          | `ItemDetail.dynamicAttributes` | 解析阶段拼接的动态区 + 过滤后的完整订购信息（含 personalization 拼接策略见代码）                                     |
| **17** | 商品标题            | `ItemDetail.itemTitle`         | 原样                                                                                     |


### 6.3 产品名称（第 3 列）解析优先级

#### 6.3.1 先定产品，再做 CSV 匹配（核心链路）

这条链路对应当前项目的业务前提：**产品名称并不是直接从 PDF 文本取值，而是先由 listing 标题识别出产品归属，再按四要素去产品清单匹配。**

1. **标题识别 listing 规则**
  `ProductTitleRecognitionService.identifyProductType(title)` 读取 `product-title-recognition-rules.properties`，按「关键字全包含 + 首条命中」产出：`ProductName`、`OrderType`、`listingId`。
2. **listingId 驱动属性解析**
  `AttributeRuleEngine` 按 `listingId` 到 `product-attribute-labels.json` 找到本 listing 的标签定义，解析出型号（`sizeCode`）、颜色（中文）、产品变量（中文）等字段。
3. **产品细类归一（用于查 CSV）**
  `ExcelWriterService.resolveSubClass(detail)` 把 `ProductName`/`OrderType` 映射到细类（如「袖扣」「领带夹」「包装盒」）。
4. **按四要素匹配产品清单**
  调用 `ProductListService.getChineseProductNameBySubClass(subClass, sizeCode, color, productVariable)`，对照 `产品清单.csv`（运行目录固定文件名）匹配最终中文产品名称。

结论：**listing 标题决定“是什么产品 + 用哪套属性模板”，属性模板解析出的四要素决定最终命中的 CSV 产品名称。**

#### 6.3.2 四要素匹配细节（`ProductListService`）

1. **候选集**：先按 `subClass` 取候选记录。
2. **精确匹配优先**：`size/color/variable` 三项逐一相等（含双方都为 null）则直接返回。
3. **打分最优匹配**：若无精确命中，按分数取最高：变量 `+50`、型号 `+30`、颜色 `+30`，空值对空值给予较低加分。
4. **无属性信息保护**：当 `size/color/variable` 全空时返回 `null`，交由上层兜底，避免误匹配到细类第一条。
5. **名称二次过滤（可选）**：上层在已有结果时，可能再调用带 `productNameFilter` 的重载方法，用 `ProductName.displayName` 二次过滤，避免同细类下子类误配。

#### 6.3.3 上层兜底策略（`ExcelWriterService`）

1. 四要素匹配成功：第 3 列输出 CSV 的 `产品名称`。
2. 四要素未命中：输出 `ProductName.getDisplayName()`。
3. 若 `ProductName` 也不可用：再兜底 `OrderType.getDisplayName()`。

### 6.4 拆行与数量（第 13 列）

令 `isMainProduct = (mainProductFlg == true)`，`itemQty = max(1, itemQuantity)`，`engravingRows = getEngravingRowCount(detail)`（依据 `dynamicAttributes` 全文关键词推断刻录面数，**1～5**）。


| 情况             | 输出行数                      | 每行数量列                                                |
| -------------- | ------------------------- | ---------------------------------------------------- |
| **主商品**        | `itemQty × engravingRows` | 通常为 **每行 `"1"*`*                                     |
| **主商品 + 多面刻录** | 同上                        | 同一商品的「非第一面」行：**数量列留空**（`row % engravingRows != 0` 时） |
| **附属商品**       | **1 行**                   | **一行写总量** `itemQty`（字符串）                             |


**刻录面数推断摘要**（`getEngravingRowCount`）：  

- `Customization Option` 中含 `N Side` → 面数为 min(N,5)。  
- 含 `Engraving Options` → 按 `&` 个数 +1。  
- `Engraving Sides` / `Engraving:` 且含 Front/Back、Round Disc & Bar、Double-Side 等 → 2，否则多数为 1。

### 6.5 Excel 文件命名

- `prefix + yyMMdd + 两位序号 + .xlsx`（`prefix`、`max-files-per-day`、`sheet-name` 来自配置）。
- 同日多个文件按已有文件名推算下一个序号。

---

## 七、订单级字段与 Excel 的对照（便于业务核对）


| PdfOrderData 字段                              | 是否进入 Excel    |
| -------------------------------------------- | ------------- |
| username                                     | ✅ 每行重复        |
| orderNumber                                  | ✅ 每行重复        |
| shippingAddress, shopName, dates, tracking 等 | ❌ 当前列未输出（可扩展） |
| ItemDetail 各行                                | ✅ 按上表         |


---

## 八、配置维护清单（建议）


| 文件                                                               | 用途                                       |
| ---------------------------------------------------------------- | ---------------------------------------- |
| `product-title-recognition-rules.properties`                     | 标题 → ProductName / OrderType / listingId |
| `product-attribute-labels.json`                                  | listingId → 动态标签语义与解析类型                  |
| `accessory-rules.json`                                           | 无木盒补礼盒等附加规则                              |
| `color-name-mapping.properties`                                  | 原始颜色串 → ProductColor                     |
| `font-name-mapping.properties` / `style-name-mapping.properties` | Excel 字体列、设计风格列标准化                       |
| `**产品清单.csv`**（运行目录）                                             | Excel **产品名称** 中文匹配                      |
| `动态属性部分样例.xlsx`（项目内文档）                                           | 各 Listing 动态属性业务说明，应与 JSON 对齐            |


---

## 九、版本与变更

- 本文档随业务规则与代码迭代更新；若配置由 `listing-config*.xlsx` 导入生成，以导入后的 JSON/properties 及重启后的运行行为为准。


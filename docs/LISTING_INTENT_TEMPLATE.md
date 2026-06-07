# Listing 配置意图模板（产品填写版）

本文档与主项目订单识别链路对齐，用于 **产品同学按同一套话术描述 listing**，后续可由 DeepSeek 生成 `product-attribute-labels.json` 中的 `attributeLabels`（须经过校验与人工确认后再发布）。

---

## 1. 与现有代码的对应关系（必读）

| 步骤 | 代码/配置 | 说明 |
|------|-----------|------|
| ① 商品标题 → 定 listing | `ProductTitleRecognitionService` + `product-title-recognition-rules.properties` | 关键字全包含命中 → 得到 **`listingId`**、主商品 `ProductName`、`OrderType`、是否组合 |
| ② PDF 商品块 → 动态属性文本 | `PdfExtractorService.parseItemDetail` → `dynamicAttrsMap` | `Quantity:` 与 `Personalization:` 之间：**标签名: 值** 多行 |
| ③ 动态属性 → 颜色/尺寸/附属等 | `AttributeRuleEngine.extractAttributes(listingId, …)` | 按 **`listingId`** 读取 `product-attribute-labels.json` 里该 listing 的 **`attributeLabels`**，按 **`attributeType`** 解析 |

**关键点**：配置里的 **`labelName` 必须与 PDF 里冒号前的标签一致**（系统会做 **不区分大小写** 匹配；已知特例：`ltem` ↔ `Item` 类 OCR 容错）。若标签写错，该标签整条不参与解析。

---

## 2. 模板使用方式

- **每一篇模板对应一个 Listing（一个 `listingId`）**。  
- 建议 **先抄一段真实 PDF 订单里该 listing 的「动态属性区」原文**（模块 F），再填前面各模块。  
- 模板里 **不要写 JSON**；只写业务描述。JSON 由后续 DeepSeek + 校验生成。

---

## 模块 A — Listing 与标题识别（对应 properties 一条）

> 与配置台 demo 中「ruleKey / 关键字 / ProductName / OrderType」一致；此处便于与模块 B 放在同一文档。

| 字段 | 填写说明 |
|------|----------|
| **内部 listingId** | 英文字母开头，仅字母、数字、`_`、`-`，唯一，如 `cuff_new_listing_001` |
| **ruleKey** | properties 里配置键，唯一，如 `cufflinks.new.001` |
| **标题关键字（多条）** | 顾客商品标题里必须 **全部出现** 的词/短语（英文大小写不敏感），每条一行 |
| **主商品 ProductName（nameCode）** | 与枚举一致，如 `Cufflink`、`Round Pendant`（不确定先问开发或对照现有 listing） |
| **订单大类 OrderType（orderTypeCode）** | 与枚举一致，如 `Cufflinks`、`Tie Clip`、`Box`（填 **code**，不是中文） |
| **是否组合商品** | 是否套装/多品类组合：`是` / `否` → 对应 `true` / `false` |

---

## 模块 B — PDF 里会出现哪些「标签行」（列清单）

按 PDF 里 **实际出现的顺序** 列出（标签名建议 **复制粘贴**，避免空格差异）：

| 序号 | PDF 标签名（冒号前） | 一行真实示例值（可复制订单里的） |
|------|----------------------|----------------------------------|
| 1 | | |
| 2 | | |
| 3 | | |

**说明**：若同一 listing 在不同订单里标签名不一致，必须在「模块 E 特例」里写明多种写法。

---

## 模块 C — 每个标签的「拆分逻辑」（逐标签填写）

**下面每个标签复制一节填写。**

### 标签「__________」（与模块 B 完全一致）

1. **解析方式（单选：只勾中文，不必写英文）**  

   **请在下列选项中只选一项，在方框内打勾 ✓。** 无需记忆或抄写 `COLOR`、`COMPOSITE` 等英文；提交后由配置台或生成程序自动对应到程序里的 **`attributeType`**（英文对照见文末 **附录 A**）。

   - [ ] **只要颜色**  
   - [ ] **只要尺寸**  
   - [ ] **只要产品变量**（礼盒类型、月份、选项类变量等）  
   - [ ] **只要字体**（Font 编号等）  
   - [ ] **只要设计风格**（Style 等）  
   - [ ] **同一段里拆成两部分**（常见：颜色与尺寸写在一起，中间用下划线「_」等）  
   - [ ] **主商品和附属商品写在同一栏里**，需要用加号「+」或逗号等拆开（拆法见模块 D）  
   - [ ] **颜色与礼盒（或其它附属）混在同一栏**，且带「+」这类组合  
   - [ ] **颜色与 Item 的特殊组合**（少见；不确定时请勾「不确定」并贴模块 F）  
   - [ ] **颜色、尺寸、附属三类信息缠在一起**（三元组合，少见）  
   - [ ] **数量与尺寸**写在同一栏  
   - [ ] **刻字面**（一般狗牌、人像牌等）  
   - [ ] **宠物牌刻字面**  
   - [ ] **生辰花相关风格**  
   - [ ] **骨灰罐刻字选项**  
   - [ ] **这一项只给顾客看，系统不用来解析**（对应配置里的「忽略」）  
   - [ ] **不确定选哪一项** → 在下一行横线写「不确定」，并保证 **模块 F** 有真实 PDF 样例  

   若选「不确定」：`_______________________________________________`

   > **说明**：上一组选项与程序字段 **一一对应**（见附录 A）。若多个标签需要填写，每个标签 **复制本小节整段** 再勾一次。

2. **值的格式（白话描述）**  
   - 例如：`颜色_尺寸` 中间是下划线；或 `Gold + Oval Box` 用加号连接两部分；或纯英文选项列表。  

3. **是否需要必填**  
   - [ ] 必填 [ ] 可选  

---

## 模块 D — 附属商品与礼盒（仅当存在 Item / Woodbox / Box 类标签时重点填）

1. **附属拆分用哪些分隔符？**（常见：`+`、`,`）  
2. **顾客可能出现的片段词**（原文列举）：如 `Tie Clip`、`TieClip`、`Cufflinks`、`Cufflink`、`Oval Box`、`Square Box`、`Box` …  
3. **每种片段对应**：  
   - 理解为「领带夹 / 袖扣 / 包装盒」中的哪一类？  
   - 包装盒是否要映射到具体 **产品变量中文名**（与 `产品清单.csv` 一致）？若有，请写对照表：  
     - `Oval Box` → 我们希望出现的变量名：___________  
     - `Square Box` → ___________  

4. **若顾客只选颜色、未写礼盒**：是否要默认某种礼盒变量？默认：___________（对应配置里的 `defaultBoxVariable`，可选）

---

## 模块 E — 特例、错别字、与别的 listing 的差异

| 项目 | 填写 |
|------|------|
| OCR/常见错拼（如 Rose→Roser、ltem） | |
| 与本店其它相似 listing 的差异一句话 | |
| 禁止忽略的噪声（若有） | |

---

## 模块 F — 校验样例（强烈建议）

请粘贴 **至少 1 份** 该 listing 的 PDF 文本片段（可脱敏），须包含：

- `Quantity:` …  
- 模块 B 里列出的标签行  
- `Personalization:` 开头若干行（若与属性无关可截断）

```
（粘贴区）
```

---

## 3. 程序侧说明（给开发 / 生成器，产品可跳过）

配置文件与代码里仍使用英文 **`attributeType`**。产品仅在模块 C **勾选中文描述**，对照关系见 **附录 A**。  
具体 JSON 字段（如 `valuePattern`、`groupMapping`、`separators`、`itemTypeMapping`）由后续生成步骤写出；**产品不要求写正则**，只要 **中文选项选对 + 值的格式描述清楚**。

---

## 4. Listing 级可选：`defaultProductVariable`

若整类订单经常在变量字段缺席时应有默认值（见 `product-attribute-labels.json` 的 `defaultProductVariable`），在本模板末尾增加：

| 是否配置默认产品变量 | 否 / 是（若是，写出 CSV 中的变量展示名）：__________ |

---

## 5. 模板自检清单（提交前）

- [ ] **listingId** 未与现有重复  
- [ ] 模块 B 标签名与 PDF **逐字** 核对（或已说明多版本写法）  
- [ ] 每个标签在模块 C **已勾选一种中文解析方式**或已标注「不确定」+ 模块 F 样例  
- [ ] 模块 F 至少一份真实样例  
- [ ] 含附属时模块 D 已填映射或明确「无礼盒」

---

## 6. 下一步（实施节奏约定）

1. 产品按本文档填写 **Word/Markdown 均可**，不必写 JSON。  
2. 开发/配置台：将全文 + 模块 F 送入 **DeepSeek**，提示词约束输出 **单个 listing 的 `listingAttributes` 片段**（用附录 A 做映射校验）。  
3. **Schema 校验** + 表格预览 → 产品确认 → 合并入 `product-attribute-labels.json`。

---

## 附录 A — 中文选项 ↔ `attributeType`（开发 / DeepSeek 用）

| 模块 C 中的中文选项（与勾选文案一致） | 程序内 `attributeType` |
|--------------------------------------|-------------------------|
| 只要颜色 | COLOR |
| 只要尺寸 | SIZE |
| 只要产品变量（礼盒类型、月份、选项类变量等） | PRODUCT_VARIABLE |
| 只要字体（Font 编号等） | FONT |
| 只要设计风格（Style 等） | STYLE |
| 同一段里拆成两部分（常见：颜色与尺寸…） | COMPOSITE |
| 主商品和附属商品写在同一栏里… | ACCESSORY_ITEMS |
| 颜色与礼盒（或其它附属）混在同一栏… | COLOR_WITH_ACCESSORY |
| 颜色与 Item 的特殊组合（少见…） | COLOR_ITEM_COMBO |
| 颜色、尺寸、附属三类信息缠在一起 | COLOR_SIZE_ACCESSORY |
| 数量与尺寸写在同一栏 | QUANTITY_WITH_SIZE |
| 刻字面（一般狗牌、人像牌等） | ENGRAVING_SIDES |
| 宠物牌刻字面 | PET_ENGRAVING_SIDES |
| 生辰花相关风格 | BIRTH_FLOWER_STYLE |
| 骨灰罐刻字选项 | URN_ENGRAVING_OPTIONS |
| 这一项只给顾客看，系统不用来解析 | IGNORE |

若新增解析类型：附录增加一行，并在模块 C 勾选列表同步增加一条中文描述。

---

**文档版本**：v1.1（模块 C 改为仅勾选中文；附录 A 为映射表）。

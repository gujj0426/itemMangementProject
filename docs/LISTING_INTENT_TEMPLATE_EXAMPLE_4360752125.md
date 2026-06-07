# Listing 配置模板 · 填写样例（Listing **4360752125**）

> 本页按 `product-title-recognition-rules.properties`、`product-attribute-labels.json` 中 **`listingId = cuff_item_list_1`** 的真实规则填写，供产品与模板对照。  
> 空白模板见：`docs/LISTING_INTENT_TEMPLATE.md`。

---

## 模块 A — Listing 与标题识别

| 字段 | 样例填写 |
|------|----------|
| **Etsy Listing ID** | 4360752125 |
| **内部 listingId** | `cuff_item_list_1` |
| **ruleKey（properties 键）** | `cufflinks.1` |
| **标题关键字**（每行一个，须全部出现在商品标题中） | `Initials Cufflinks Set`<br>`4 colors`<br>`cufflinks` |
| **主商品 ProductName（nameCode）** | `Cufflink` |
| **订单大类 OrderType（orderTypeCode）** | `Cufflinks` |
| **是否组合商品** | 是（true，含袖扣+可选领带夹+可选盒等） |
| **备注** | 标题全称为 *Custom Engraved Initials Cufflinks Set-4 colors Groomsman Cufflinks-Groom Gift for Wedding Day* |

---

## 模块 B — PDF 里会出现的标签行

| 序号 | PDF 标签名（冒号前） | 一行真实示例值 |
|------|----------------------|----------------|
| 1 | `Size and Color` | `Silver_S` |
| 2 | `Item` | `TieClip+Cufflinks`（示例；顾客选项不同则值不同，如 `Cufflinks+Oval Box`） |

**说明**：第 2 个标签在 PDF 里显示为 **Item**（与 `Item Options` 不是同一条配置）。

---

## 模块 C — 每个标签的拆分逻辑

### 标签「Size and Color」

1. **解析方式（只选一项）**  
   - [x] **同一段里拆成两部分**（常见：颜色与尺寸写在一起，中间用下划线「_」等）  
   - [ ] （其余不选）

2. **值的格式（白话）**  
   - 颜色与尺寸用下划线连接，前段为颜色（如 Silver、Gold、Black、Rose Gold；顾客或 OCR 可能把 Rose Gold 打成 `Rose`+`r` 等，见模块 E），后段为尺寸码 **S** 或 **L** 等。示例：`Silver_S`、`Gold_L`。

3. **是否必填**  
   - [x] 必填  

---

### 标签「Item」

1. **解析方式（只选一项）**  
   - [x] **主商品和附属商品写在同一栏里**，需要用加号「+」或逗号等拆开（拆法见模块 D）  
   - [ ] （其余不选）

2. **值的格式（白话）**  
   - 多个选项用 **+** 连接，表示同时选购：例如只有领带夹、袖扣+椭圆盒、袖扣+领带夹+盒等。示例：`TieClip+Cufflinks`、`Cufflinks+Oval Box`。

3. **是否必填**  
   - [x] 必填  

---

## 模块 D — 附属商品与礼盒（本 listing 重点）

1. **附属拆分用哪些分隔符？**  
   - 主要：`+`

2. **顾客可能出现的片段词（举例）**  
   - `TieClip`、`Tie Clip`、`Cufflinks`、`Cufflink`、`Box`、`Oval Box`、`Square Box` 等（大小写与 PDF 实际一致为佳）。

3. **片段含义与礼盒变量（与产品清单、配置一致）**  
   - `TieClip` / `Tie Clip` → 理解为领带夹附属行  
   - `Cufflinks` / `Cufflink` → 袖扣主商品  
   - `Oval Box` → 包装盒，变量名：`Oval Box-椭圆形开窗木盒`  
   - `Square Box` → `Square Box-方形木盒`  
   - 仅写 `Box` 而无具体形状时 → 配置里按长方形木盒类处理为：`Box-长方形木盒`（与现网 `boxVariableMapping` 一致）

4. **顾客只选颜色、未写礼盒时**  
   - 本 listing 以 **Item 行** 解析附属为主；若业务上需要「无盒时的默认礼盒变量」，由运营与开发单独约定（当前配置未强制写 `defaultBoxVariable`）。

---

## 模块 E — 特例与错别字

| 项目 | 样例 |
|------|------|
| OCR/常见错拼 | `Rose Gold` 有时打成 `Rose Goldr_L` 等，配置注释里用 **r** 表示 Rose 相关 OCR 误差；`Item` 偶发识别成 `ltem`（程序侧有容错） |
| 与其它 listing 的差异 | 本 listing 第二个标签名为 **Item**；若另一 listing 用 **Woodbox Options** 或 **Color** 代替 **Size and Color**，需单独建一条 listing 配置，勿混用 |

---

## 模块 F — 校验样例（PDF 片段示例）

以下为演示用排版，真实 PDF 可能多栏、换行略有不同：

```
Custom Engraved Initials Cufflinks Set-4 colors
Groomsman Cufflinks-Groom Gift for Wedding Day
Quantity: 1
Size and Color: Silver_S
Item: TieClip+Cufflinks
Personalization: cufflink: Icon 40+ S15(BG)
tie clip: S4(JB)
```

（同一 listing 下另一单可能为 `Item: Cufflinks+Oval Box`、`Size and Color: Gold_L` 等，模块 B/C/D 的规则不变，仅示例值变化。）

---

## 与仓库配置的对应关系（便于开发核对）

| 本项目配置位置 | 内容 |
|----------------|------|
| `product-title-recognition-rules.properties` | `cufflinks.1=Initials Cufflinks Set,4 colors,cufflinks|Cufflink|Cufflinks|true|cuff_item_list_1` |
| `product-attribute-labels.json` | `listingId`: `cuff_item_list_1`，含 `Size and Color` → COMPOSITE，`Item` → ACCESSORY_ITEMS（详见该文件） |

---

**文档用途**：产品培训、DeepSeek 生成 JSON 时的 Few-shot 样例；**非**替换仓库内已有配置文件。

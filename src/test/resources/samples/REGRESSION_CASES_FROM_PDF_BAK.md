# 基于真实备份 PDF 的回归测试案例

## 样例来源（本机路径）

- **目录**：`/Users/mac/Desktop/orderManagementFile/Cufflinks/pdf_bak`
- **说明**：以下为该目录内实际文件名（截至整理日期的快照）。后续可增加或替换文件，但请在本文档同步更新表格。

## 使用约定

1. **caseId**：用于 `src/test/resources` 下命名对齐（`pdf/<caseId>.pdf`、期望 JSON 同名）。
2. **优先级**：P0 必须先有自动化断言；P1/P2 按需补齐。
3. **副本文件**：文件名含 `_副本` 的与主文件通常同源，回归任选其一即可，避免重复维护两套期望。

---

## 案例总览表

| caseId | 源文件名 | 场景简述 | 预期覆盖能力 | 优先级 |
|--------|----------|----------|--------------|--------|
| `baseline-3837329233` | `3837329233-download-2025-10-23.pdf` | 代码注释中的历史基线样本订单 | 订单头、`Quantity:` 切块、动态属性基线 | **P0** |
| `download-2025-10-28` | `download-2025-10-28.pdf` | 通用下载批次 | 多订单跨页、通用 listing | P1 |
| `download-2025-11-01` | `download-2025-11-01.pdf` | 通用下载批次 | 与副本二选一做对照 | P1 |
| `download-2025-11-01-dup` | `download-2025-11-01_副本.pdf` | 同上副本 | 与上一案例重复时仅保留一条自动化 | P2 |
| `dogtag-batch-5-20260311` | `狗牌_ 订单_5单_20260311.pdf` | 狗牌类多订单 | `DOG_TAG`、尺寸、静音/尼龙分支、`AccessoryItemFactory` | **P0** |
| `dogtag-batch-5-dup` | `狗牌_ 订单_5单_20260311_副本.pdf` | 副本 | 二选一 | P2 |
| `cufflink-12-04192026` | `袖扣_订单_12单_04192026.pdf` | 袖扣大批量 | `CUFFLINK`、`ProductListService`、Excel 拆行 | P1 |
| `cufflink-13-03112026` | `袖扣_订单_13单_03112026.pdf` | 袖扣大批量 | 同上 | P1 |
| `cufflink-13-03192026` | `袖扣_订单_13单_03192026.pdf` | 袖扣大批量 | 同上 | P1 |
| `cufflink-13-04102026` | `袖扣_订单_13单_04102026.pdf` | 袖扣大批量 | 同上 | P1 |
| `cufflink-13-04112026` | `袖扣_订单_13单_04112026.pdf` | 袖扣大批量 | 同上 | P1 |
| `cufflink-13-04112026-dup` | `袖扣_订单_13单_04112026_副本.pdf` | 副本 | 二选一 | P2 |
| `cufflink-14-03152026` | `袖扣_订单_14单_03152026.pdf` | 袖扣更大批量 | 性能与边界（页数、内存） | P1 |
| `cufflink-5-03082026` | `袖扣_订单_5单_03082026.pdf` | 袖扣中小批量 | 快速冒烟 | P1 |
| `cufflink-6-04062026` | `袖扣_订单_6单_04062026.pdf` | 袖扣批次 | 同上 | P1 |
| `cufflink-6-04062026-marked` | `袖扣_订单_6单_04062026_marked.pdf` | **已标注 PDF** | `PdfMarkService` 行为、二次解析是否受影响 | **P0**（标注链路） |
| `cufflink-9-03252026` | `袖扣_订单_9单_03252026.pdf` | 袖扣混合品类批次 | 圆片项链/翅膀addon/Cufflink Box-Add on/圆片鸭嘴领带夹；重点校验刻录面数与拆行数量 | **P0** |
| `urn-1-20260328` | `骨灰罐_订单_1单_20260328.pdf` | 骨灰罐单品 | `MEMORIAL`/ urn、`URN_ENGRAVING_OPTIONS`、多面拆行 | **P0** |
| `urn-1-20260412` | `骨灰罐_订单_1单_20260412.pdf` | 骨灰罐单品 | 与上一案例形成日期对照 | P1 |

---

## 各案例建议断言要点（后续自动化时填写具体值）

### P0：必须先固化期望快照

1. **`baseline-3837329233`**（`3837329233-download-2025-10-23.pdf`）
   - 断言：`PdfExtractorService.extractFromPdf` 返回订单数 ≥ 1；首个订单 `orderNumber` 非空；`itemDetails` 非空。
   - 可与 `PdfOrderData` 历史注释对齐，作为「最小黄金样本」。

2. **`dogtag-batch-5-20260311`**
   - 断言：解析出的 `OrderType.DOG_TAG` 数量与标题「5单」一致或可追溯说明；至少一笔含尺寸/颜色枚举映射。
   - 若有硅胶绑带描述，断言 `AccessoryItemFactory` / 动态属性中与狗牌配件一致。

3. **`urn-1-20260328` / `urn-1-20260412`**
   - 断言：`resolveSubClass` → 骨灰罐细类；`getEngravingRowCount` / 动态属性中与 `Engraving Options`、`&` 面数逻辑一致（见 `ExcelWriterService`）。
   - Excel：多面时行数与数量列规则（仅首面写数量等）。

4. **`cufflink-6-04062026-marked`**
   - 断言：若管线支持「已标注 PDF」输入：解析仍成功或明确跳过策略；若测试仅针对「未标注原件」，则用 **未标注** 文件 `袖扣_订单_6单_04062026.pdf` 做解析，`marked` 单独测标注读写。

5. **`cufflink-9-03252026`**
   - 断言：覆盖混合商品场景（圆片项链、翅膀 addon、`Cufflink Box-Add on`、圆片鸭嘴领带夹），订单与商品明细应完整输出。
   - 重点：`getEngravingRowCount` 面数识别正确；主商品拆行数 = `itemQty × engravingRows`；多面拆行后数量列仅首面保留数量。

### P1：批量袖扣 / 通用 download

- 断言订单总数与 PDF 内 `Order #` 数量一致（可用正则从文本 strip 统计交叉校验）。
- 抽样 1～2 个订单核对 `listingId` + 中文品名（`产品清单.csv`）是否与当前规则一致。

---

## 落地步骤（与迭代 A 衔接）

1. **拷贝或链接**：将选定 P0 文件复制到  
   `src/test/resources/samples/pdf/<caseId>.pdf`  
   （拷贝便于 CI；链接仅适合本机。）
2. **生成期望 JSON**：对每个 `caseId` 运行当前稳定版本解析，导出  
   `expected/orders/<caseId>.orders.json`（人工审核后入库）。
3. **Excel 期望（可选）**：导出关键列到  
   `expected/excel/<caseId>.excel.json`。
4. **JUnit**：Parameterized 测试：`caseId` 列表从配置文件或枚举读取。

---

## 文件名含空格说明

部分文件名含空格（如 `狗牌_ 订单_...`）。复制到仓库时建议重命名为 `dogtag-batch-5-20260311.pdf` 等与 **caseId** 一致，避免跨平台与脚本问题。

---

## 修订记录

| 日期 | 说明 |
|------|------|
| 初始 | 根据 `/Users/mac/Desktop/orderManagementFile/Cufflinks/pdf_bak` 目录列举文件并划分优先级 |
| 迭代 A 落地 | 以下 P0 文件已复制到仓库：`src/test/resources/samples/pdf/`，并由 `RegressionPdfIntegrationTest` 校验解析订单数（见下表） |

### P0 样例在仓库中的文件名与自动化断言

| caseId（仓库内 PDF 文件名） | 解析订单数断言 | 额外断言 |
|-----------------------------|----------------|----------|
| `baseline-3837329233.pdf` | `== 1` | 首单 `orderNumber == "3837329233"` |
| `dogtag-batch-5-20260311.pdf` | `== 5` | 每单均有商品行 |
| `urn-1-20260328.pdf` | `== 1` | 每单均有商品行 |
| `urn-1-20260412.pdf` | `== 1` | 每单均有商品行 |
| `cufflink-6-04062026-marked.pdf` | `== 6` | 每单均有商品行（含已标注 PDF） |

测试类：`src/test/java/com/pdfconverter/regression/RegressionPdfIntegrationTest.java`  
测试配置：`src/test/resources/application-test.properties`（关闭定时调度，IO 使用临时目录）

运行：`mvn test`（或仅 `mvn -Dtest=RegressionPdfIntegrationTest test`）

### 黄金快照（JSON）

- 解析结果会与 `src/test/resources/expected/orders/<caseId>.orders.json` **全文比对**（订单号、用户名、`totalItemQuantity`、每条商品行的类型/`listingId`/数量/尺寸颜色变量等）。
- 若业务变更导致期望更新，在模块根目录执行（仅本地）：

```bash
mvn -q -Dtest=GoldenSnapshotGeneratorTest -DregenerateGolden=true test
```

然后审查 diff，确认无误后再提交。

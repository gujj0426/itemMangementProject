# Regression Samples Guide

This folder stores baseline samples for parser/export regression tests.

## Directory Layout

- `pdf/`: raw input PDFs used by regression tests.
- `../expected/orders/`: expected extracted order snapshots (JSON).
- `../expected/excel/`: expected Excel key-field snapshots (JSON/CSV).

## Naming Convention

Use one case folder prefix across all artifacts:

- PDF: `pdf/<caseId>.pdf`
- Order snapshot: `../expected/orders/<caseId>.orders.json`
- Excel snapshot: `../expected/excel/<caseId>.excel.json`

Example:

- `pdf/listing-123-basic.pdf`
- `../expected/orders/listing-123-basic.orders.json`
- `../expected/excel/listing-123-basic.excel.json`

## Minimum Case Metadata

Each case should track:

- `caseId`
- `listingId`
- `scenario`
- `expectedOrderCount`
- `notes`

## Real-world catalogue

See **`REGRESSION_CASES_FROM_PDF_BAK.md`**: test cases derived from PDFs under  
`/Users/mac/Desktop/orderManagementFile/Cufflinks/pdf_bak` (case ids, priorities, and assertion hints).

## Golden JSON snapshots

`RegressionPdfIntegrationTest` compares parser output to `expected/orders/<caseId>.orders.json`.

Regenerate after intentional behavior changes:

`mvn -q -Dtest=GoldenSnapshotGeneratorTest -DregenerateGolden=true test`

## Same-order box accessory merge (包装盒)

Parser merges multiple non-main **包装盒** lines on the **same order** when **产品变量** matches (sums **数量**); merged rows are emitted after non-box lines. Different **产品变量** values stay on separate rows.

**Regression:** `cufflink-6-04062026-marked.pdf`, order **4024016441** — seven 「大方形礼盒」 lines collapse to one row with quantity **7**; 「Box-长方形木盒」 remains a separate row with quantity **1**. Assertions live under group **`G-CUFFLINK-SAME-ORDER-BOX-MERGE`** in `../regression/business-assertions.json`.


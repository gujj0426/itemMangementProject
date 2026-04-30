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


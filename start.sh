#!/bin/bash

# 默认参数值
INITIAL_INDEX=1
INPUT_PATH="${inputPath:-D:/orderManagementProject/pdf_input}"
OUTPUT_PATH="${outputPath:-D:/orderManagementProject/excel_output}"
BAK_PATH="${bakPath:-D:/orderManagementProject/pdf_bak}"

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case "$1" in
        --initialIndex=*)
            INITIAL_INDEX="${1#*=}"
            shift
            ;;
        --inputPath=*)
            INPUT_PATH="${1#*=}"
            shift
            ;;
        --outputPath=*)
            OUTPUT_PATH="${1#*=}"
            shift
            ;;
        --bakPath=*)
            BAK_PATH="${1#*=}"
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# 启动 Java 程序（添加UTF-8编码支持）
java -Dfile.encoding=UTF-8 \
     -Dsun.jnu.encoding=UTF-8 \
     -jar target/pdf-to-excel-cufflink-0.0.1-SNAPSHOT.jar \
     --initialIndex="$INITIAL_INDEX" \
     --inputPath="$INPUT_PATH" \
     --outputPath="$OUTPUT_PATH" \
     --bakPath="$BAK_PATH"
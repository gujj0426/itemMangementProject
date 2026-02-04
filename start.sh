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

# 启动 Java 程序
java -jar target/itemMangementProject.jar \
    --initialIndex="$INITIAL_INDEX" \
    --inputPath="$INPUT_PATH" \
    --outputPath="$OUTPUT_PATH" \
    --bakPath="$BAK_PATH"
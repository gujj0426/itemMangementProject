@echo off

REM 默认参数值 INITIAL_INDEX  这个是Excel表格序列号的起始编号
REM INPUT_PATH   这个换成你本地想要使用的订单pdf待处理路径，最好全英文
REM OUTPUT_PATH  这个是Excel的输出路径，BAK_PATH 处理完的pdf会备份到这里
set INITIAL_INDEX=1
set INPUT_PATH=D:\orderPdfToExcelVersion1\file\pdf_input
set OUTPUT_PATH=D:\orderPdfToExcelVersion1\file\excel_output
set BAK_PATH=D:\orderPdfToExcelVersion1\file\pdf_bakup

REM 解析命令行参数
:parse_args
if "%~1"=="" goto end_args

if "%~1"=="--initialIndex" (
    set "INITIAL_INDEX=%~2"
    shift
    shift
    goto parse_args
)

if "%~1"=="--inputPath" (
    set "INPUT_PATH=%~2"
    shift
    shift
    goto parse_args
)

if "%~1"=="--outputPath" (
    set "OUTPUT_PATH=%~2"
    shift
    shift
    goto parse_args
)

if "%~1"=="--bakPath" (
    set "BAK_PATH=%~2"
    shift
    shift
    goto parse_args
)

echo Unknown option: %~1
exit /b 1

:end_args

REM 启动 Java 程序 java -jar target\itemMangementProject.jar ^这个前面的路径改成jar文件的绝对路径
java -jar D:\orderPdfToExcelVersion1\pdf-to-excel-0.0.1-SNAPSHOT.jar ^
    --initialIndex="%INITIAL_INDEX%" ^
    --inputPath="%INPUT_PATH%" ^
    --outputPath="%OUTPUT_PATH%" ^
    --bakPath="%BAK_PATH%"

pause
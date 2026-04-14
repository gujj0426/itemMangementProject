@echo off
chcp 65001 > nul

REM ========================================
REM 开发环境启动脚本（需要本地有 Java 环境）
REM 打包发布请使用 package.bat
REM ========================================

REM ---- 修改这里的路径 ----
set INPUT_PATH=D:\orderManagementProject\pdf_input
set OUTPUT_PATH=D:\orderManagementProject\excel_output
set BAK_PATH=D:\orderManagementProject\pdf_bak
set INITIAL_INDEX=1
REM ------------------------

echo ========================================
echo   PDF Order Converter - Dev Mode
echo ========================================
echo.
echo   PDF input  : %INPUT_PATH%
echo   Excel out  : %OUTPUT_PATH%
echo   PDF bak    : %BAK_PATH%
echo   Init index : %INITIAL_INDEX%
echo.
echo ========================================
echo.

java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar target\itemMangementProject.jar ^
    --initialIndex="%INITIAL_INDEX%" ^
    --inputPath="%INPUT_PATH%" ^
    --outputPath="%OUTPUT_PATH%" ^
    --bakPath="%BAK_PATH%"

pause

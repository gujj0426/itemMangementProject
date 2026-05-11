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

REM ---- DeepSeek 配置 ----
REM 推荐方式 1：在 Windows 系统环境变量中设置 DEEPSEEK_API_KEY
REM 推荐方式 2：在本 bat 同目录创建 deepseek.env，内容示例：
REM set DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxx
if exist "%~dp0deepseek.env" (
    call "%~dp0deepseek.env"
)
if not defined DEEPSEEK_API_KEY (
    echo [提示] 未检测到 DEEPSEEK_API_KEY，DeepSeek 将跳过调用。
    echo        请设置系统环境变量，或在本目录创建 deepseek.env。
    echo.
)
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

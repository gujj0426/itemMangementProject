@echo off
chcp 65001 > nul
REM ========================================
REM PDF Order Converter - 打包并部署
REM Maven 打包 → 复制 JAR + JSON 到部署目录
REM ========================================

echo ========================================
echo   PDF Order Converter - 打包部署
echo ========================================
echo.

REM ---- 配置 ----
set MAVEN_CMD=D:\code\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin\mvn.cmd
set SETTINGS=D:\code\apache-maven-3.9.12-bin\apache-maven-3.9.12\conf\settings.xml
set REPO=D:/code/repository
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot
set DEPLOY_DIR=D:\orderPdfToExcelVersion1
set JAR_NAME=itemMangementProject.jar

REM ---- [1/3] 检查编译 JDK ----
echo [1/3] 检查编译环境...
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [错误] 编译 JDK 不存在: %JAVA_HOME%
    echo 请确认 JDK 17 已安装
    pause
    exit /b 1
)
if not exist "%MAVEN_CMD%" (
    echo [错误] Maven 不存在: %MAVEN_CMD%
    pause
    exit /b 1
)
echo JDK:  %JAVA_HOME%
echo Maven: %MAVEN_CMD%
echo.
echo 编译环境检查通过
echo.

REM ---- [2/3] Maven 打包 ----
echo [2/3] Maven 打包...
call "%MAVEN_CMD%" clean package -DskipTests -s "%SETTINGS%" "-Dmaven.repo.local=%REPO%"
if %errorlevel% neq 0 (
    echo [错误] Maven 打包失败
    pause
    exit /b 1
)

if not exist "target\%JAR_NAME%" (
    echo [错误] 打包产物不存在: target\%JAR_NAME%
    pause
    exit /b 1
)
echo 打包成功: target\%JAR_NAME%
echo.

REM ---- [3/3] 复制到部署目录 ----
echo [3/3] 部署到 %DEPLOY_DIR%...
if not exist "%DEPLOY_DIR%" (
    echo [错误] 部署目录不存在: %DEPLOY_DIR%
    pause
    exit /b 1
)

copy /Y "target\%JAR_NAME%" "%DEPLOY_DIR%\%JAR_NAME%" >nul
if %errorlevel% neq 0 (
    echo [错误] 复制 JAR 失败
    pause
    exit /b 1
)

echo.
echo ========================================
echo   部署完成!
echo ========================================
echo.
echo   JAR:  %DEPLOY_DIR%\%JAR_NAME%
echo.
echo   请手动重启服务使配置生效
echo.
pause

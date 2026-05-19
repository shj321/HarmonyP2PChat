@echo off
chcp 65001 >nul
echo ================================================
echo    星际通 (HarmonyP2PChat) APK 构建脚本
echo ================================================
echo.

:: 检查 ANDROID_HOME
if "%ANDROID_HOME%"=="" (
    echo [错误] 未设置 ANDROID_HOME 环境变量
    echo 请先安装 Android Studio 并配置 Android SDK
    echo 通常路径: C:\Users\你的用户名\AppData\Local\Android\Sdk
    echo.
    set /p ANDROID_HOME=请输入你的 Android SDK 路径: 
)

:: 检查 Java
where java >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Java，请安装 JDK 17+
    echo 下载: https://adoptium.net/
    pause
    exit /b 1
)

echo [✓] Java 版本:
java -version 2>&1
echo.

:: 生成 debug keystore（如果没有）
set KEYSTORE=%USERPROFILE%\.android\debug.keystore
if not exist "%KEYSTORE%" (
    echo [信息] 生成调试签名 keystore...
    keytool -genkeypair -v ^
        -keystore "%KEYSTORE%" ^
        -storepass android ^
        -alias androiddebugkey ^
        -keypass android ^
        -keyalg RSA ^
        -keysize 2048 ^
        -validity 10000 ^
        -dname "CN=Android Debug,O=Android,C=US"
)

:: 进入项目目录
cd /d "%~dp0"

:: 赋予 gradlew 执行权限
if not exist "gradlew.bat" (
    echo [错误] 未找到 gradlew.bat，请确保在项目根目录运行此脚本
    pause
    exit /b 1
)

echo [信息] 开始 Gradle 构建...
call gradlew.bat assembleDebug --stacktrace

if errorlevel 1 (
    echo.
    echo [错误] 构建失败！请查看上方错误信息
    pause
    exit /b 1
)

echo.
echo [✓] 构建成功！
echo APK 路径: app\build\outputs\apk\debug\app-debug.apk
echo.

:: 显示 APK 信息
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
if exist "%APK_PATH%" (
    echo APK 文件大小:
    for %%A in ("%APK_PATH%") do echo   %%~zA 字节
)

echo.
echo 安装到设备 (如已连接 ADB):
echo   adb install -r "%APK_PATH%"
echo.
pause

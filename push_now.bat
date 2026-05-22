@echo off
chcp 65001 >nul
echo.
echo ================================================
echo   星际通 - GitHub 推送工具（自动版）
echo ================================================
echo.
echo [步骤1] 配置 Git 凭据存储...
"C:\Program Files\Git\cmd\git.exe" config --global credential.helper store
"C:\Program Files\Git\cmd\git.exe" config --global user.email "shj321@users.noreply.github.com"
"C:\Program Files\Git\cmd\git.exe" config --global user.name "shj321"

echo.
echo [步骤2] 推送代码到 GitHub...
echo.
echo 首次推送会自动打开浏览器，请在浏览器中完成以下操作：
echo   1. 点击 "Sign in with your browser"（用浏览器登录）
echo   2. 授权后，浏览器窗口可关闭
echo   3. 回到此窗口查看结果
echo.
"C:\Program Files\Git\cmd\git.exe" push -u origin main --force-with-lease

echo.
if %errorlevel% equ 0 (
    echo ================================================
    echo  [成功] 代码已推送到 GitHub！
    echo.
    echo  GitHub Actions 将自动开始构建（约5-8分钟）
    echo  查看进度: https://github.com/shj321/HarmonyP2PChat/actions
    echo.
    echo  构建完成后，在 Actions 页面下载 APK：
    echo  打开最新运行 → 滚到页面底部 Artifacts
    echo  → 点击 "HarmonyP2PChat-debug-apk" 下载 ZIP
    echo  → 解压后得到 app-debug.apk
    echo ================================================
) else (
    echo.
    echo [失败] 推送失败。
    echo 请检查：1) GitHub 账号是否正常登录
    echo         2) 网络连接是否正常
    echo.
    echo 你也可以手动推送：
    echo   在项目目录按住 Shift 右键 → 在此处打开 PowerShell
    echo   然后运行: git push -u origin main
)
echo.
pause

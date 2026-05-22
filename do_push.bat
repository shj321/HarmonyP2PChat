@echo off
chcp 65001 >nul
echo ================================================
echo   正在推送代码到 GitHub...
echo   即将打开浏览器进行授权
echo ================================================
echo.
cd /d "%~dp0"
"C:\Program Files\Git\cmd\git.exe" push -u origin main
echo.
if %errorlevel% equ 0 (
    echo.
    echo ================================================
    echo  [成功] 代码已推送！
    echo  GitHub Actions 将自动开始构建（约5-8分钟）
    echo  稍后刷新: https://github.com/shj321/HarmonyP2PChat/actions
    echo ================================================
)
echo.
pause

@echo off
chcp 65001 >nul
echo ================================================
echo   星际通 HarmonyP2PChat - GitHub 推送脚本
echo ================================================
echo.

:: 检查 Git 是否安装
where git >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Git，请先安装 Git for Windows
    echo 下载地址: https://git-scm.com/download/win
    echo.
    pause
    exit /b 1
)

echo [1/5] 检测到 Git 版本：
git --version
echo.

:: 获取 GitHub 用户名
set /p GITHUB_USER="请输入你的 GitHub 用户名: "
if "%GITHUB_USER%"=="" (
    echo [错误] 用户名不能为空
    pause
    exit /b 1
)

set REPO_URL=https://github.com/%GITHUB_USER%/HarmonyP2PChat.git

echo.
echo [2/5] 初始化 Git 仓库...
git init
git branch -M main

echo.
echo [3/5] 添加所有文件...
git add .
git status

echo.
echo [4/5] 创建提交...
git commit -m "feat: HarmonyP2PChat P2P instant messaging app"

echo.
echo [5/5] 推送到 GitHub...
echo 仓库地址: %REPO_URL%
echo.
echo ================================================
echo  请先在 GitHub 创建名为 HarmonyP2PChat 的仓库！
echo  地址: https://github.com/new
echo  仓库名: HarmonyP2PChat
echo  可见性: Public 或 Private 均可
echo ================================================
echo.
pause

git remote remove origin >nul 2>&1
git remote add origin %REPO_URL%
git push -u origin main

if %errorlevel% equ 0 (
    echo.
    echo ================================================
    echo  [成功] 代码已推送到 GitHub！
    echo.
    echo  下一步：查看 GitHub Actions 构建进度
    echo  地址: https://github.com/%GITHUB_USER%/HarmonyP2PChat/actions
    echo.
    echo  构建完成后，在 Actions 页面点击最新一次运行
    echo  滚动到页面底部 Artifacts 区域下载 APK
    echo ================================================
) else (
    echo.
    echo [失败] 推送失败，可能原因：
    echo   1. 仓库不存在，请先在 GitHub 创建仓库
    echo   2. 网络问题，请检查连接
    echo   3. 认证失败，请检查 GitHub 账号凭据
    echo      （建议使用 GitHub Token 替代密码）
)

echo.
pause

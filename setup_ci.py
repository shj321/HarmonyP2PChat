#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
星际通 (HarmonyP2PChat) - GitHub Actions CI/CD 配置生成器
当本地没有 Android SDK 时，可将代码上传 GitHub 并使用 Actions 自动构建 APK
"""

import os
import subprocess
import sys

GITHUB_WORKFLOW = """
name: Build APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build Debug APK
        run: ./gradlew assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: HarmonyP2PChat-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
"""

os.makedirs(".github/workflows", exist_ok=True)
with open(".github/workflows/build.yml", "w", encoding="utf-8") as f:
    f.write(GITHUB_WORKFLOW)

print("✓ GitHub Actions 工作流已生成：.github/workflows/build.yml")
print("✓ 将此项目上传到 GitHub 后，Actions 会自动构建 APK")
print("  构建完成后在 Actions 页面下载 APK artifact")

# 星际通 (HarmonyP2PChat)

一款**纯 P2P 实时通讯 Android APP**，支持鸿蒙 6 手机（通过卓易通兼容层运行）。

## 功能特性

| 功能 | 说明 |
|------|------|
| **文字聊天** | 实时文字消息，本地 SQLite 存储历史记录 |
| **文件传输** | TCP 直连 + 断点续传（支持任意大小文件） |
| **语音通话** | WebRTC 音频轨道，P2P 直连，回声消除 |
| **视频通话** | WebRTC 视频轨道，前后摄像头切换 |
| **群聊** | Mesh 拓扑，向所有群成员广播消息 |
| **节点发现** | UDP 广播自动发现局域网内节点 |
| **无服务器** | 所有数据点对点传输，不经过任何服务器 |

## 技术架构

```
HarmonyP2PChat/
├── p2p/
│   ├── SignalingServer.java      # UDP 信令服务器（端口 37891/37892）
│   ├── P2PConnectionManager.java # WebRTC 连接管理器
│   ├── CallManager.java          # 音视频通话管理器
│   └── FileTransferManager.java  # 文件传输 + 断点续传（端口 37893）
├── service/
│   └── P2PService.java           # 核心前台服务
├── model/
│   ├── PeerInfo.java             # 节点信息
│   ├── Message.java              # 消息实体（Room DB）
│   ├── Group.java                # 群组实体（Room DB）
│   └── SignalMessage.java        # 信令协议消息
└── ui/
    ├── MainActivity.java          # 联系人列表
    ├── ChatActivity.java          # 单聊界面
    ├── CallActivity.java          # 语音/视频通话
    └── GroupChatActivity.java     # 群聊界面
```

## 信令协议

| 类型 | 说明 |
|------|------|
| `HELLO` / `HELLO_ACK` | 节点发现广播 |
| `OFFER` / `ANSWER` | WebRTC SDP 协商 |
| `ICE` | ICE 候选交换 |
| `CHAT` | 文字消息 |
| `CALL_REQ` / `CALL_ACK` / `CALL_END` | 通话控制 |
| `FILE_META` / `FILE_ACK` | 文件传输协商（含断点信息） |
| `GROUP_MSG` | 群聊消息 |

## 端口占用

| 端口 | 协议 | 用途 |
|------|------|------|
| 37891 | UDP | 单播信令 |
| 37892 | UDP | 广播发现 |
| 37893 | TCP | 文件传输 |

## 编译 APK

### 方式一：本地编译（需要 Android Studio）

```bat
# 1. 安装 Android Studio 及 Android SDK 34
# 2. 安装 JDK 17+
# 3. 运行：
build_apk.bat
```

### 方式二：GitHub Actions 自动构建（推荐）

1. 将项目推送到 GitHub
2. Actions 自动触发构建
3. 在 Actions 页面下载 APK artifact

```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/你的用户名/HarmonyP2PChat.git
git push -u origin main
```

### 方式三：Android Studio 直接打开

在 Android Studio 中打开此目录，点击 **Build > Build APK**。

## 安装到鸿蒙 6 手机

鸿蒙 6 通过**卓易通**（Android 兼容层）运行 Android APK：

1. 在华为应用市场搜索**卓易通**或使用系统自带兼容框架
2. 将 `app-debug.apk` 传输到手机
3. 使用文件管理器安装 APK
4. 首次运行需授予：**麦克风、摄像头、存储**权限

## 使用说明

1. 首次启动设置昵称
2. 确保所有设备连接到**同一 Wi-Fi 网络**
3. APP 启动后自动广播，附近节点会出现在联系人列表
4. 点击联系人 → 文字聊天，点击📞/📹按钮发起通话

> 注意：跨网络通话需要路由器开放端口 37891-37893，或使用 STUN 穿透。

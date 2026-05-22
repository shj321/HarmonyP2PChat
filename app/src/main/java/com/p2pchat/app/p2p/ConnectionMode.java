package com.p2pchat.app.p2p;

/**
 * 连接模式枚举
 */
public enum ConnectionMode {
    /** 局域网 UDP 广播+单播（原有模式） */
    LAN_UDP,
    /** 公网 WebSocket 信令（新模式） */
    PUBLIC_WS,
    /** 未确定（自动检测中） */
    UNDETERMINED
}

package com.p2pchat.app.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * 本地节点信息（身份）
 */
public class PeerInfo implements Serializable {

    public String peerId;       // 唯一标识
    public String nickname;     // 昵称
    public String avatarColor;  // 头像颜色（无图片，用颜色区分）
    public String address;      // IP 地址（发现后填充）
    public int    port;         // 信令端口
    public boolean online;

    public PeerInfo() {
        this.peerId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        this.avatarColor = generateColor(peerId);
    }

    public PeerInfo(String id, String nick, String addr, int port) {
        this.peerId = id;
        this.nickname = nick;
        this.address = addr;
        this.port = port;
        this.online = true;
        this.avatarColor = generateColor(id);
    }

    private static String generateColor(String id) {
        String[] colors = {"#E53935","#1E88E5","#43A047","#FB8C00","#8E24AA",
                           "#00ACC1","#F4511E","#3949AB","#00897B","#FFB300"};
        int idx = Math.abs(id.hashCode()) % colors.length;
        return colors[idx];
    }

    public String getInitials() {
        if (nickname == null || nickname.isEmpty()) return "?";
        return nickname.substring(0, Math.min(2, nickname.length())).toUpperCase();
    }
}

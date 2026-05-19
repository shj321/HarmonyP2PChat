package com.p2pchat.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import java.util.UUID;

/**
 * 聊天消息实体（持久化到本地数据库）
 */
@Entity(tableName = "messages")
public class Message {

    public static final int TYPE_TEXT   = 1;
    public static final int TYPE_IMAGE  = 2;
    public static final int TYPE_FILE   = 3;
    public static final int TYPE_VOICE  = 4;
    public static final int TYPE_SYSTEM = 5;

    public static final int STATE_SENDING  = 0;
    public static final int STATE_SENT     = 1;
    public static final int STATE_RECEIVED = 2;
    public static final int STATE_FAILED   = 3;

    @PrimaryKey
    @NonNull
    public String msgId;

    public String sessionId;    // 对话 ID（单聊=对方 peerId，群聊=groupId）
    public String fromPeerId;   // 发送方
    public String toPeerId;     // 接收方（群聊为 groupId）
    public int    msgType;
    public String content;      // 文字内容 / 文件路径 / 语音路径
    public long   timestamp;
    public int    state;
    public boolean isGroupMsg;
    public long   fileSize;     // 文件消息时有效
    public long   transferredBytes; // 断点续传进度
    public String mimeType;

    public Message() {
        this.msgId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.state = STATE_SENDING;
    }

    public static Message text(String from, String to, String text, boolean group) {
        Message m = new Message();
        m.fromPeerId = from;
        m.toPeerId = to;
        m.sessionId = group ? to : (from.compareTo(to) < 0 ? from + "_" + to : to + "_" + from);
        m.msgType = TYPE_TEXT;
        m.content = text;
        m.isGroupMsg = group;
        return m;
    }
}

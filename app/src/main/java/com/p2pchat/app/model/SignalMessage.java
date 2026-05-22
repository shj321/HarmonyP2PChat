package com.p2pchat.app.model;

/**
 * P2P 信令协议消息（JSON 传输）
 */
public class SignalMessage {

    // 信令类型
    public static final String TYPE_HELLO       = "HELLO";       // 节点发现广播
    public static final String TYPE_HELLO_ACK   = "HELLO_ACK";  // 响应发现
    public static final String TYPE_OFFER       = "OFFER";       // WebRTC offer
    public static final String TYPE_ANSWER      = "ANSWER";      // WebRTC answer
    public static final String TYPE_ICE         = "ICE";         // ICE candidate
    public static final String TYPE_BYE         = "BYE";         // 断开连接
    public static final String TYPE_CHAT        = "CHAT";        // 文字消息
    public static final String TYPE_CALL_REQ    = "CALL_REQ";   // 发起通话
    public static final String TYPE_CALL_ACK    = "CALL_ACK";   // 接受/拒绝
    public static final String TYPE_CALL_END    = "CALL_END";   // 结束通话
    public static final String TYPE_FILE_META   = "FILE_META";  // 文件元数据
    public static final String TYPE_FILE_ACK    = "FILE_ACK";   // 文件断点确认
    public static final String TYPE_GROUP_MSG   = "GROUP_MSG";  // 群聊消息
    public static final String TYPE_GROUP_INFO  = "GROUP_INFO"; // 群组信息同步
    public static final String TYPE_PING        = "PING";
    public static final String TYPE_PONG        = "PONG";

    // 服务器控制消息类型
    public static final String TYPE_REGISTER    = "REGISTER";
    public static final String TYPE_TURN_CREDENTIALS = "TURN_CREDENTIALS";

    public String type;
    public String fromId;
    public String toId;
    public String payload;   // JSON 字符串负载
    public long   ts;        // 时间戳

    public SignalMessage() { this.ts = System.currentTimeMillis(); }

    public SignalMessage(String type, String from, String to, String payload) {
        this.type = type;
        this.fromId = from;
        this.toId = to;
        this.payload = payload;
        this.ts = System.currentTimeMillis();
    }
}

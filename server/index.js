/**
 * HarmonyP2PChat WebSocket 信令服务器
 * 功能：客户端注册/注销、在线列表推送、对等信令转发、心跳保活
 * 部署：Render.com 免费版（Node.js 18+）
 */

const { WebSocketServer } = require('ws');
const http = require('http');
const crypto = require('crypto');

// ===== 配置 =====
const PORT = process.env.PORT || 3000;
const HEARTBEAT_TIMEOUT = 60000; // 60秒无心跳则断开

// TURN 配置（用于生成动态凭据）
const TURN_SECRET = process.env.TURN_SECRET || 'p2pchat_default_secret_2024';
const TURN_SERVER_URL = process.env.TURN_URL || 'turn:openrelay.metered.ca:443';

// ===== 在线客户端存储 =====
// Map<peerId, { ws, nickname, avatarColor, lastHeartbeat, registeredAt }>
const clients = new Map();

// ===== HTTP Server（健康检查端点）=====
const server = http.createServer((req, res) => {
    // CORS
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET');

    if (req.url === '/health') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({
            status: 'ok',
            uptime: process.uptime(),
            onlineClients: clients.size,
            peers: Array.from(clients.keys())
        }));
    }

    res.writeHead(404);
    res.end('Not Found');
});

// ===== WebSocket Server =====
const wss = new WebSocketServer({ server, path: '/ws' });

wss.on('connection', (ws, req) => {
    console.log('[CONN] 新连接来自', req.socket.remoteAddress);

    let peerId = null;

    ws.on('message', (raw) => {
        try {
            const msg = JSON.parse(raw.toString());
            handleMessage(ws, msg);
        } catch (e) {
            console.error('[ERR] 解析消息失败:', e.message, raw.toString().substring(0, 100));
        }
    });

    ws.on('close', (code, reason) => {
        console.log(`[DISC] ${peerId || '未注册'} 断开 (code=${code})`);
        if (peerId && clients.has(peerId)) {
            const info = clients.get(peerId);
            clients.delete(peerId);
            // 通知其他人该节点下线
            broadcastPeerList();
        }
    });

    ws.on('error', (err) => {
        console.error('[ERR] WebSocket 错误:', err.message);
    });
});

// ===== 消息处理 =====
function handleMessage(ws, msg) {
    switch (msg.type) {

        // ---- 客户端注册 ----
        case 'REGISTER': {
            peerId = msg.fromId;
            if (!peerId || peerId.length < 4) {
                ws.send(JSON.stringify({ type: 'ERROR', payload: 'peerId 无效' }));
                return;
            }

            // 如果已存在旧连接，关闭它
            if (clients.has(peerId)) {
                const old = clients.get(peerId);
                old.ws.close(4000, 'replaced by new connection');
            }

            // 解析 payload 获取用户信息
            let userInfo = { nickname: '匿名', avatarColor: '#999999' };
            try {
                userInfo = JSON.parse(msg.payload);
            } catch (e) { /* 使用默认值 */ }

            clients.set(peerId, {
                ws: ws,
                nickname: userInfo.nickname || '匿名',
                avatarColor: userInfo.avatarColor || '#999999',
                lastHeartbeat: Date.now(),
                registeredAt: Date.now()
            });

            console.log(`[REG] ${peerId} (${userInfo.nickname}) 已注册，当前在线: ${clients.size}`);

            // 回复注册成功
            ws.send(JSON.stringify({ type: 'REGISTER_OK', toId: peerId, ts: Date.now() }));

            // 推送当前在线列表
            sendPeerList(ws);

            // 广播给所有人（在线列表更新）
            broadcastPeerList();

            break;
        }

        // ---- 心跳 ----
        case 'PING': {
            if (peerId && clients.has(peerId)) {
                clients.get(peerId).lastHeartbeat = Date.now();
                ws.send(JSON.stringify({ type: 'PONG', toId: peerId, ts: Date.now() }));
            }
            break;
        }

        // ---- TURN 凭据请求 ----
        case 'TURN_CREDENTIALS': {
            // 使用 HMAC-SHA1 生成临时 TURN 凭据
            const timestamp = Math.floor(Date.now() / 1000) + 86400; // 24小时有效
            const username = `${timestamp}:p2pchat`;
            const credential = crypto.createHmac('sha1', TURN_SECRET).update(username).digest('base64');

            const turnInfo = {
                urls: [
                    `turn:openrelay.metered.ca:80`,
                    `turn:openrelay.metered.ca:443`,
                    `turn:openrelay.metered.ca:443?transport=tcp`
                ],
                username: username,
                credential: credential
            };

            ws.send(JSON.stringify({
                type: 'TURN_CREDENTIALS_OK',
                toId: peerId,
                payload: JSON.stringify(turnInfo),
                ts: Date.now()
            }));
            break;
        }

        // ---- 对等信令转发 ----
        // OFFER / ANSWER / ICE / CHAT / BYE / CALL_REQ / CALL_ACK / CALL_END
        // FILE_META / FILE_ACK / GROUP_MSG / GROUP_INFO / HELLO 等
        default: {
            const targetId = msg.toId;
            if (!targetId || targetId === '*') {
                // 广播类消息：发给所有其他客户端
                for (const [id, client] of clients) {
                    if (id !== peerId) {
                        try {
                            client.ws.send(JSON.stringify(msg));
                        } catch (e) {
                            console.error(`[ERR] 广播给 ${id} 失败:`, e.message);
                        }
                    }
                }
            } else if (clients.has(targetId)) {
                // 单播：转发给指定 peer
                try {
                    clients.get(targetId).ws.send(JSON.stringify(msg));
                } catch (e) {
                    console.error(`[ERR] 转发给 ${targetId} 失败:`, e.message);
                    // 通知发送方目标不在线
                    ws.send(JSON.stringify({
                        type: 'ERROR',
                        toId: peerId,
                        payload: `目标 ${targetId} 不在线`,
                        ts: Date.now()
                    }));
                }
            } else {
                // 目标不在线
                ws.send(JSON.stringify({
                    type: 'PEER_OFFLINE',
                    toId: peerId,
                    payload: targetId,
                    ts: Date.now()
                }));
            }
            break;
        }
    }
}

// ===== 在线列表推送 =====

function buildPeerList() {
    const list = [];
    for (const [id, info] of clients) {
        list.push({
            peerId: id,
            nickname: info.nickname,
            avatarColor: info.avatarColor,
            online: true
        });
    }
    return list;
}

function sendPeerList(ws) {
    const list = buildPeerList();
    ws.send(JSON.stringify({
        type: 'PEER_LIST',
        toId: '*',
        payload: JSON.stringify(list),
        ts: Date.now()
    }));
}

function broadcastPeerList() {
    const list = buildPeerList();
    const msg = JSON.stringify({
        type: 'PEER_LIST',
        toId: '*',
        payload: JSON.stringify(list),
        ts: Date.now()
    });
    for (const [id, client] of clients) {
        try {
            client.ws.send(msg);
        } catch (e) { /* ignore */ }
    }
}

// ===== 心跳超时检测 =====
setInterval(() => {
    const now = Date.now();
    for (const [peerId, client] of clients) {
        if (now - client.lastHeartbeat > HEARTBEAT_TIMEOUT) {
            console.log(`[TIMEOUT] ${peerId} 心跳超时，断开连接`);
            client.ws.close(4001, 'heartbeat timeout');
        }
    }
}, 30000); // 每 30 秒检查一次

// ===== 启动 =====
server.listen(PORT, () => {
    console.log('========================================');
    console.log('  HarmonyP2PChat 信令服务器已启动');
    console.log(`  端口: ${PORT}`);
    console.log(`  WebSocket: ws://localhost:${PORT}/ws`);
    console.log(`  健康检查: http://localhost:${PORT}/health`);
    console.log('========================================');
});

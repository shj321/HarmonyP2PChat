package com.p2pchat.app.ui.activity;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.gson.Gson;
import com.p2pchat.app.R;
import com.p2pchat.app.db.ChatDatabase;
import com.p2pchat.app.model.Message;
import com.p2pchat.app.p2p.FileTransferManager;
import com.p2pchat.app.service.P2PService;
import com.p2pchat.app.ui.adapter.MessageAdapter;
import com.p2pchat.app.util.*;
import java.io.File;
import java.util.*;

/**
 * 单聊界面
 */
public class ChatActivity extends AppCompatActivity {

    private static final int REQ_PICK_FILE = 2001;
    private final Gson gson = new Gson();

    private String peerId, peerNick, peerColor;
    private String selfId;

    private RecyclerView rvMessages;
    private EditText etInput;
    private MessageAdapter msgAdapter;
    private List<Message> messages = new ArrayList<>();

    private P2PService p2pService;
    private boolean bound = false;
    private ChatDatabase db;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            p2pService = ((P2PService.LocalBinder) b).getService();
            bound = true;
            // 建立 WebRTC 连接
            p2pService.getConnectionManager().connectToPeer(peerId);
        }
        @Override public void onServiceDisconnected(ComponentName n) { bound = false; }
    };

    private final BroadcastReceiver msgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String from = intent.getStringExtra(P2PService.EXTRA_FROM_ID);
            if (!peerId.equals(from)) return;
            String payload = intent.getStringExtra(P2PService.EXTRA_PAYLOAD);
            P2PService.ChatMsgPayload mp = gson.fromJson(payload, P2PService.ChatMsgPayload.class);
            if (mp == null) return;

            Message m = new android.os.Message();
            m.fromPeerId = from;
            m.content = mp.text;
            m.msgType = Message.TYPE_TEXT;
            m.state = Message.STATE_RECEIVED;
            messages.add(m);
            msgAdapter.notifyItemInserted(messages.size() - 1);
            rvMessages.scrollToPosition(messages.size() - 1);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        peerId    = getIntent().getStringExtra("peerId");
        peerNick  = getIntent().getStringExtra("peerNick");
        peerColor = getIntent().getStringExtra("peerColor");
        selfId    = PrefsUtil.getSelfId(this);
        db        = ChatDatabase.getInstance(this);

        getSupportActionBar().setTitle(peerNick);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        rvMessages = findViewById(R.id.rvMessages);
        etInput    = findViewById(R.id.etInput);
        ImageButton btnSend    = findViewById(R.id.btnSend);
        ImageButton btnAttach  = findViewById(R.id.btnAttach);
        ImageButton btnVoice   = findViewById(R.id.btnVoiceCall);
        ImageButton btnVideo   = findViewById(R.id.btnVideoCall);

        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        msgAdapter = new MessageAdapter(messages, selfId);
        rvMessages.setAdapter(msgAdapter);

        // 加载历史消息
        String sessionId = selfId.compareTo(peerId) < 0 ?
                selfId + "_" + peerId : peerId + "_" + selfId;
        messages.addAll(db.messageDao().getBySession(sessionId));
        msgAdapter.notifyDataSetChanged();
        if (!messages.isEmpty()) rvMessages.scrollToPosition(messages.size() - 1);

        btnSend.setOnClickListener(v -> sendText());
        btnAttach.setOnClickListener(v -> pickFile());
        btnVoice.setOnClickListener(v -> startCall(false));
        btnVideo.setOnClickListener(v -> startCall(true));

        // 绑定服务
        bindService(new Intent(this, P2PService.class), serviceConn, BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter(P2PService.ACTION_MSG_RECEIVED);
        registerReceiver(msgReceiver, filter);
    }

    private void sendText() {
        String text = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        etInput.setText("");

        if (bound) p2pService.sendTextMessage(peerId, text, false, null);

        Message m = Message.text(selfId, peerId, text, false);
        m.state = Message.STATE_SENT;
        messages.add(m);
        msgAdapter.notifyItemInserted(messages.size() - 1);
        rvMessages.scrollToPosition(messages.size() - 1);
    }

    private void pickFile() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("*/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(pick, REQ_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK_FILE && res == RESULT_OK && data != null && bound) {
            Uri uri = data.getData();
            String fileName = FileUtil.getFileName(this, uri);
            long fileSize = FileUtil.getFileSize(this, uri);
            sendFile(uri, fileName, fileSize);
        }
    }

    private void sendFile(Uri uri, String fileName, long fileSize) {
        new Thread(() -> {
            try {
                File tmpFile = FileUtil.copyToTemp(this, uri, fileName);
                FileTransferManager.FileMeta meta = new FileTransferManager.FileMeta();
                meta.transferId = UUID.randomUUID().toString();
                meta.fileName = fileName;
                meta.fileSize = fileSize;
                meta.fromPeerId = selfId;

                // 先发信令通知对方
                com.p2pchat.app.model.SignalMessage sig = new com.p2pchat.app.model.SignalMessage(
                    com.p2pchat.app.model.SignalMessage.TYPE_FILE_META,
                    selfId, peerId, new Gson().toJson(meta));
                if (bound) {
                    p2pService.getConnectionManager().sendSignal(sig, peerId);

                    // 稍等对方就绪后发送文件
                    Thread.sleep(500);
                    String remoteIp = p2pService.getConnectionManager().getPeer(peerId) != null ?
                            p2pService.getConnectionManager().getPeer(peerId).address : null;
                    if (remoteIp != null)
                        p2pService.getFileTransferManager().sendFile(meta, remoteIp, tmpFile);
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "文件发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void startCall(boolean isVideo) {
        Intent i = new Intent(this, CallActivity.class);
        i.putExtra("fromId", peerId);
        i.putExtra("isCaller", true);
        i.putExtra("isVideo", isVideo);
        i.putExtra("sdp", "");
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(msgReceiver);
        if (bound) unbindService(serviceConn);
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }
}

package com.p2pchat.app.ui.activity;

import android.content.*;
import android.os.*;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.gson.Gson;
import com.p2pchat.app.R;
import com.p2pchat.app.db.ChatDatabase;
import com.p2pchat.app.model.*;
import com.p2pchat.app.service.P2PService;
import com.p2pchat.app.ui.adapter.MessageAdapter;
import com.p2pchat.app.util.PrefsUtil;
import java.util.*;

/**
 * 群聊界面（Mesh P2P：向所有成员广播消息）
 */
public class GroupChatActivity extends AppCompatActivity {

    private final Gson gson = new Gson();
    private String groupId, groupName;
    private String selfId;
    private Group group;

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
            if (group == null && getIntent().getBooleanExtra("createGroup", false)) {
                createGroup();
            }
        }
        @Override public void onServiceDisconnected(ComponentName n) { bound = false; }
    };

    private final BroadcastReceiver groupReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String gid = intent.getStringExtra(P2PService.EXTRA_GROUP_ID);
            if (groupId == null || !groupId.equals(gid)) return;
            String from = intent.getStringExtra(P2PService.EXTRA_FROM_ID);
            String payload = intent.getStringExtra(P2PService.EXTRA_PAYLOAD);
            P2PConnectionManager.GroupMsgPayload gmp =
                gson.fromJson(payload, P2PConnectionManager.GroupMsgPayload.class);
            if (gmp == null) return;

            Message m = new Message();
            m.fromPeerId = from;
            m.content = gmp.content;
            m.msgType = Message.TYPE_TEXT;
            m.state = Message.STATE_RECEIVED;
            m.isGroupMsg = true;
            messages.add(m);
            msgAdapter.notifyItemInserted(messages.size() - 1);
            rvMessages.scrollToPosition(messages.size() - 1);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat); // 复用聊天布局

        selfId  = PrefsUtil.getSelfId(this);
        db      = ChatDatabase.getInstance(this);
        groupId = getIntent().getStringExtra("groupId");

        // 尝试从数据库加载群
        if (groupId != null) {
            group = db.groupDao().getById(groupId);
            groupName = group != null ? group.groupName : "群聊";
        } else {
            groupName = getIntent().getStringExtra("groupName");
        }
        getSupportActionBar().setTitle(groupName);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        rvMessages = findViewById(R.id.rvMessages);
        etInput    = findViewById(R.id.etInput);
        ImageButton btnSend = findViewById(R.id.btnSend);

        // 隐藏通话按钮（群聊暂不支持多方通话）
        findViewById(R.id.btnVoiceCall).setVisibility(android.view.View.GONE);
        findViewById(R.id.btnVideoCall).setVisibility(android.view.View.GONE);

        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        msgAdapter = new MessageAdapter(messages, selfId);
        rvMessages.setAdapter(msgAdapter);

        if (groupId != null) {
            messages.addAll(db.messageDao().getBySession(groupId));
            msgAdapter.notifyDataSetChanged();
        }

        btnSend.setOnClickListener(v -> sendGroupText());

        bindService(new Intent(this, P2PService.class), serviceConn, BIND_AUTO_CREATE);
        registerReceiver(groupReceiver, new IntentFilter(P2PService.ACTION_GROUP_MSG));
    }

    private void createGroup() {
        group = new Group();
        group.groupName = groupName;
        group.ownerId = selfId;
        group.memberIds = gson.toJson(new ArrayList<>(Arrays.asList(selfId)));
        db.groupDao().insert(group);
        groupId = group.groupId;
        Toast.makeText(this, "群组 "" + groupName + "" 已创建，分享 ID: " + groupId, Toast.LENGTH_LONG).show();
    }

    private void sendGroupText() {
        if (group == null || groupId == null) { Toast.makeText(this,"群组未就绪",Toast.LENGTH_SHORT).show(); return; }
        String text = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        etInput.setText("");

        // 向群内所有成员发送消息（Mesh）
        if (bound) {
            try {
                List<String> members = gson.fromJson(group.memberIds,
                    new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
                for (String memberId : members) {
                    if (!memberId.equals(selfId)) {
                        p2pService.sendTextMessage(memberId, text, true, groupId);
                    }
                }
            } catch (Exception ignored) {}
        }

        Message m = Message.text(selfId, groupId, text, true);
        messages.add(m);
        msgAdapter.notifyItemInserted(messages.size() - 1);
        rvMessages.scrollToPosition(messages.size() - 1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(groupReceiver);
        if (bound) unbindService(serviceConn);
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    // 简化引用
    static class P2PConnectionManager {
        static class GroupMsgPayload { String groupId; String content; String msgType; }
    }
}

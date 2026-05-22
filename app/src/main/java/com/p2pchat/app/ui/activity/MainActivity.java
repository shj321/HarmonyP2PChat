package com.p2pchat.app.ui.activity;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.p2pchat.app.R;
import com.p2pchat.app.model.PeerInfo;
import com.p2pchat.app.service.P2PService;
import com.p2pchat.app.ui.adapter.PeerListAdapter;
import com.p2pchat.app.util.PrefsUtil;
import android.view.Menu;
import android.view.MenuItem;
import java.util.*;

/**
 * 主界面：联系人列表 + 群组列表
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMS = 1001;
    private static final String[] REQUIRED_PERMS = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.POST_NOTIFICATIONS
    };

    private final Gson gson = new Gson();
    private PeerListAdapter peerAdapter;
    private final List<PeerInfo> peers = new ArrayList<>();
    private P2PService p2pService;
    private boolean bound = false;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            p2pService = ((P2PService.LocalBinder) b).getService();
            bound = true;
        }
        @Override public void onServiceDisconnected(ComponentName n) { bound = false; }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (P2PService.ACTION_PEER_DISCOVERED.equals(action)) {
                String json = intent.getStringExtra(P2PService.EXTRA_PAYLOAD);
                PeerInfo peer = gson.fromJson(json, PeerInfo.class);
                addOrUpdatePeer(peer);
            } else if (P2PService.ACTION_PEER_OFFLINE.equals(action)) {
                String id = intent.getStringExtra(P2PService.EXTRA_PEER_ID);
                markOffline(id);
            } else if (P2PService.ACTION_MODE_CHANGED.equals(action)) {
                String mode = intent.getStringExtra(P2PService.EXTRA_MODE);
                updateModeStatus(mode);
            } else if (P2PService.ACTION_CALL_REQUEST.equals(action)) {
                String fromId = intent.getStringExtra(P2PService.EXTRA_FROM_ID);
                boolean isVideo = intent.getBooleanExtra(P2PService.EXTRA_CALL_IS_VIDEO, false);
                String sdp = intent.getStringExtra(P2PService.EXTRA_PAYLOAD);
                openCallActivity(fromId, false, isVideo, sdp);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PeerInfo self = PrefsUtil.getSelfInfo(this);
        getSupportActionBar().setTitle("星际通  " + (self != null ? self.nickname : ""));

        setupRecyclerView();
        setupBottomNav();

        requestPermissionsIfNeeded();
        startAndBindService();

        IntentFilter filter = new IntentFilter();
        filter.addAction(P2PService.ACTION_PEER_DISCOVERED);
        filter.addAction(P2PService.ACTION_PEER_OFFLINE);
        filter.addAction(P2PService.ACTION_CALL_REQUEST);
        filter.addAction(P2PService.ACTION_MODE_CHANGED);
        registerReceiver(receiver, filter);
    }

    private void setupRecyclerView() {
        RecyclerView rv = findViewById(R.id.rvPeers);
        rv.setLayoutManager(new LinearLayoutManager(this));
        peerAdapter = new PeerListAdapter(peers, peer -> {
            // 点击打开聊天
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("peerId", peer.peerId);
            i.putExtra("peerNick", peer.nickname);
            i.putExtra("peerColor", peer.avatarColor);
            startActivity(i);
        });
        rv.setAdapter(peerAdapter);
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_contacts) {
                // 已在联系人页
            } else if (item.getItemId() == R.id.nav_groups) {
                startActivity(new Intent(this, GroupChatActivity.class));
            }
            return true;
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showAddGroupDialog());
    }

    private void showAddGroupDialog() {
        EditText et = new EditText(this);
        et.setHint("群名称");
        new android.app.AlertDialog.Builder(this)
            .setTitle("创建群组")
            .setView(et)
            .setPositiveButton("创建", (d, w) -> {
                String name = et.getText().toString().trim();
                if (!name.isEmpty()) {
                    startActivity(new Intent(this, GroupChatActivity.class)
                        .putExtra("createGroup", true)
                        .putExtra("groupName", name));
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void startAndBindService() {
        Intent svc = new Intent(this, P2PService.class);
        startForegroundService(svc);
        bindService(svc, serviceConn, BIND_AUTO_CREATE);
    }

    private void requestPermissionsIfNeeded() {
        List<String> needed = new ArrayList<>();
        for (String p : REQUIRED_PERMS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        }
        if (!needed.isEmpty())
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
    }

    private void addOrUpdatePeer(PeerInfo peer) {
        for (int i = 0; i < peers.size(); i++) {
            if (peers.get(i).peerId.equals(peer.peerId)) {
                peers.set(i, peer);
                peerAdapter.notifyItemChanged(i);
                return;
            }
        }
        peers.add(peer);
        peerAdapter.notifyItemInserted(peers.size() - 1);
    }

    private void markOffline(String peerId) {
        for (int i = 0; i < peers.size(); i++) {
            if (peers.get(i).peerId.equals(peerId)) {
                peers.get(i).online = false;
                peerAdapter.notifyItemChanged(i);
                return;
            }
        }
    }

    private void openCallActivity(String fromId, boolean isCaller, boolean isVideo, String sdp) {
        Intent i = new Intent(this, CallActivity.class);
        i.putExtra("fromId", fromId);
        i.putExtra("isCaller", isCaller);
        i.putExtra("isVideo", isVideo);
        i.putExtra("sdp", sdp);
        startActivity(i);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, ServerConfigActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        if (bound) unbindService(serviceConn);
    }
}

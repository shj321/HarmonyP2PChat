package com.p2pchat.app.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.p2pchat.app.R;
import com.p2pchat.app.util.PrefsUtil;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

public class ServerConfigActivity extends AppCompatActivity {

    private EditText etServerUrl;
    private RadioGroup rgMode;
    private Button btnSave, btnTest, btnCopy;
    private TextView tvSelfId, tvTestResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setTitle("服务器设置");
        setContentView(R.layout.activity_server_config);

        etServerUrl = findViewById(R.id.etServerUrl);
        rgMode = findViewById(R.id.rgMode);
        btnSave = findViewById(R.id.btnSave);
        btnTest = findViewById(R.id.btnTest);
        btnCopy = findViewById(R.id.btnCopy);
        tvSelfId = findViewById(R.id.tvSelfId);
        tvTestResult = findViewById(R.id.tvTestResult);

        // 加载保存的配置
        String savedUrl = PrefsUtil.getServerUrl(this);
        etServerUrl.setText(savedUrl.isEmpty() ? "wss://harmonyp2chat-signal.onrender.com/ws" : savedUrl);

        String savedMode = PrefsUtil.getPreferredMode(this);
        if ("lan".equals(savedMode)) rgMode.check(R.id.rbLan);
        else if ("ws".equals(savedMode)) rgMode.check(R.id.rbWs);
        else rgMode.check(R.id.rbAuto);

        tvSelfId.setText(PrefsUtil.getSelfId(this));
        tvSelfId.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("peerId", PrefsUtil.getSelfId(this)));
            Toast.makeText(this, "Peer ID 已复制", Toast.LENGTH_SHORT).show();
        });

        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("peerId", PrefsUtil.getSelfId(this)));
            Toast.makeText(this, "Peer ID 已复制", Toast.LENGTH_SHORT).show();
        });

        btnTest.setOnClickListener(v -> testConnection());

        btnSave.setOnClickListener(v -> {
            String url = etServerUrl.getText().toString().trim();
            PrefsUtil.saveServerUrl(this, url);

            int checkedId = rgMode.getCheckedRadioButtonId();
            String mode = "auto";
            if (checkedId == R.id.rbLan) mode = "lan";
            else if (checkedId == R.id.rbWs) mode = "ws";
            PrefsUtil.savePreferredMode(this, mode);

            Toast.makeText(this, "已保存，重启应用后生效", Toast.LENGTH_LONG).show();
        });
    }

    private void testConnection() {
        String url = etServerUrl.getText().toString().trim();
        if (url.isEmpty()) {
            tvTestResult.setText("请输入服务器地址");
            tvTestResult.setTextColor(Color.RED);
            return;
        }
        tvTestResult.setText("连接中...");
        tvTestResult.setTextColor(Color.GRAY);

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .build();
        Request request = new Request.Builder().url(url).build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                runOnUiThread(() -> {
                    tvTestResult.setText("连接成功!");
                    tvTestResult.setTextColor(Color.parseColor("#4CAF50"));
                });
                webSocket.close(1000, "test");
            }
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                runOnUiThread(() -> {
                    tvTestResult.setText("连接失败: " + t.getMessage());
                    tvTestResult.setTextColor(Color.RED);
                });
            }
        });
    }
}

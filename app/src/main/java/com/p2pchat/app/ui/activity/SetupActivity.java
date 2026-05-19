package com.p2pchat.app.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.p2pchat.app.R;
import com.p2pchat.app.model.PeerInfo;
import com.p2pchat.app.util.PrefsUtil;

/**
 * 首次启动：设置昵称
 */
public class SetupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        EditText etNickname = findViewById(R.id.etNickname);
        Button btnConfirm = findViewById(R.id.btnConfirm);

        btnConfirm.setOnClickListener(v -> {
            String nick = etNickname.getText().toString().trim();
            if (TextUtils.isEmpty(nick)) {
                etNickname.setError("请输入昵称");
                return;
            }
            PeerInfo self = new PeerInfo();
            self.nickname = nick;
            PrefsUtil.saveSelfInfo(this, self);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}

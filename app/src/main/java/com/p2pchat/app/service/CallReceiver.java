package com.p2pchat.app.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 来电广播接收器（处理来电通知）
 */
public class CallReceiver extends BroadcastReceiver {

    private static final String TAG = "CallReceiver";

    public static final String ACTION_ANSWER = "com.p2pchat.ANSWER_CALL";
    public static final String ACTION_REJECT = "com.p2pchat.REJECT_CALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "来电动作: " + action);
        // CallActivity 处理来电接听/拒绝
    }
}

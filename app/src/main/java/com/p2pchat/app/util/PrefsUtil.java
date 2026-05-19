package com.p2pchat.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.p2pchat.app.model.PeerInfo;

/**
 * 本地用户配置持久化工具
 */
public class PrefsUtil {

    private static final String PREF_NAME  = "p2pchat_prefs";
    private static final String KEY_SELF   = "self_info";
    private static final Gson   gson       = new Gson();

    public static SharedPreferences getPrefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveSelfInfo(Context ctx, PeerInfo info) {
        getPrefs(ctx).edit().putString(KEY_SELF, gson.toJson(info)).apply();
    }

    public static PeerInfo getSelfInfo(Context ctx) {
        String json = getPrefs(ctx).getString(KEY_SELF, null);
        if (json == null) return null;
        return gson.fromJson(json, PeerInfo.class);
    }

    public static String getSelfId(Context ctx) {
        PeerInfo self = getSelfInfo(ctx);
        return self != null ? self.peerId : "unknown";
    }

    public static boolean isSetup(Context ctx) {
        return getPrefs(ctx).getString(KEY_SELF, null) != null;
    }
}

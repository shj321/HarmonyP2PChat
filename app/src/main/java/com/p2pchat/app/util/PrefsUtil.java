package com.p2pchat.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.p2pchat.app.model.PeerInfo;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 本地用户配置持久化工具
 */
public class PrefsUtil {

    private static final String PREF_NAME      = "p2pchat_prefs";
    private static final String KEY_SELF       = "self_info";
    private static final String KEY_CONTACTS   = "saved_contacts";
    private static final Gson   gson           = new Gson();
    private static final Type   CONTACTS_TYPE  = new TypeToken<List<PeerInfo>>(){}.getType();

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

    // ---- 联系人持久化 ----

    public static List<PeerInfo> getSavedContacts(Context ctx) {
        String json = getPrefs(ctx).getString(KEY_CONTACTS, null);
        if (json == null) return new ArrayList<>();
        try {
            List<PeerInfo> list = gson.fromJson(json, CONTACTS_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void saveContacts(Context ctx, List<PeerInfo> contacts) {
        getPrefs(ctx).edit().putString(KEY_CONTACTS, gson.toJson(contacts)).apply();
    }

    public static void addContact(Context ctx, PeerInfo contact) {
        List<PeerInfo> contacts = getSavedContacts(ctx);
        // 去重：按 peerId 或 address+port
        for (int i = 0; i < contacts.size(); i++) {
            PeerInfo c = contacts.get(i);
            if (c.peerId.equals(contact.peerId) ||
                (c.address != null && c.address.equals(contact.address) && c.port == contact.port)) {
                contacts.set(i, contact);
                saveContacts(ctx, contacts);
                return;
            }
        }
        contacts.add(contact);
        saveContacts(ctx, contacts);
    }

    public static void removeContact(Context ctx, String peerId) {
        List<PeerInfo> contacts = getSavedContacts(ctx);
        contacts.removeIf(c -> c.peerId.equals(peerId));
        saveContacts(ctx, contacts);
    }

    public static boolean isContactSaved(Context ctx, String peerId) {
        for (PeerInfo c : getSavedContacts(ctx)) {
            if (c.peerId.equals(peerId)) return true;
        }
        return false;
    }

    // ---- 服务器配置 ----

    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_CONN_MODE   = "preferred_mode";

    /** 保存信令服务器地址 */
    public static void saveServerUrl(Context ctx, String url) {
        getPrefs(ctx).edit().putString(KEY_SERVER_URL, url).apply();
    }

    /** 读取信令服务器地址 */
    public static String getServerUrl(Context ctx) {
        return getPrefs(ctx).getString(KEY_SERVER_URL, "");
    }

    /** 保存首选连接模式 */
    public static void savePreferredMode(Context ctx, String mode) {
        getPrefs(ctx).edit().putString(KEY_CONN_MODE, mode).apply();
    }

    /** 读取首选连接模式 ("auto"/"lan"/"ws") */
    public static String getPreferredMode(Context ctx) {
        return getPrefs(ctx).getString(KEY_CONN_MODE, "auto");
    }
}

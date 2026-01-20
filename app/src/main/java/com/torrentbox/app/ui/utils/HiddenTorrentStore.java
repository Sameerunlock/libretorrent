package com.torrentbox.app.ui.utils;

import android.content.Context;


import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;


public class HiddenTorrentStore {

    private static final String PREF = "hidden_torrents_pref";
    private static final String KEY_HIDDEN_IDS = "hidden_ids";

    private static final String KEY_UNLOCKED = "hidden_unlocked";

    private static final String KEY_BIOMETRIC_ENABLED = "hidden_biometric";

    public static boolean isBiometricEnabled(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_BIOMETRIC_ENABLED, true); // default ON
    }

    private HiddenTorrentStore() {
        // no instances
    }

    public static Set<String> getHiddenIds(Context context) {
        return new HashSet<>(
                context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                        .getStringSet(KEY_HIDDEN_IDS, new HashSet<>())
        );
    }

    public static void saveHiddenIds(Context context, Set<String> ids) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_HIDDEN_IDS, ids)
                .apply();
    }

    public static boolean isHidden(Context context, String torrentId) {
        return getHiddenIds(context).contains(torrentId);
    }

    public static void unhide(Context context, Set<String> ids) {
        Set<String> hidden = getHiddenIds(context);
        hidden.removeAll(ids);
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_HIDDEN_IDS, hidden)
                .apply();
    }

    public static boolean isUnlocked(@NonNull Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_UNLOCKED, false);
    }

    public static void unlock(@NonNull Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UNLOCKED, true)
                .apply();
    }

    public static void lock(@NonNull Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UNLOCKED, false)
                .apply();
    }
}

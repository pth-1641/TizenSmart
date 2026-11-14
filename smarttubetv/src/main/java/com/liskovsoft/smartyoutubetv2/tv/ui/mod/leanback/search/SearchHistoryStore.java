package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Remembers queries the user already searched, to show a "history" icon in suggestions.
 */
public final class SearchHistoryStore {
    private static final String PREFS = "tvtube_search_history";
    private static final String KEY = "queries";
    private static final String SEP = "\n";
    private static final int MAX = 100;

    private SearchHistoryStore() {
    }

    private static List<String> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, "");
        return TextUtils.isEmpty(raw) ? new ArrayList<>() : new ArrayList<>(Arrays.asList(raw.split(SEP)));
    }

    public static boolean contains(Context context, String query) {
        return query != null && context != null && load(context).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    public static void add(Context context, String query) {
        if (context == null || TextUtils.isEmpty(query) || TextUtils.isEmpty(query.trim())) {
            return;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<String> list = load(context);
        list.remove(q);
        list.add(q);
        while (list.size() > MAX) {
            list.remove(0);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, TextUtils.join(SEP, list)).apply();
    }
}

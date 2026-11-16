package com.nellis.bid;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saved searches — persisted locally (query + location + category). */
public class SavedSearch {
    private static final String PREFS = "saved_searches";
    private static final String KEY = "list";

    public static void add(Context ctx, String query, String location, String category) {
        List<String[]> all = getAll(ctx);
        all.add(new String[]{query, location, category});
        save(ctx, all);
    }

    public static List<String[]> getAll(Context ctx) {
        List<String[]> out = new ArrayList<>();
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(new String[]{o.optString("q"), o.optString("loc"), o.optString("cat")});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void remove(Context ctx, int index) {
        List<String[]> all = getAll(ctx);
        if (index >= 0 && index < all.size()) {
            all.remove(index);
            save(ctx, all);
        }
    }

    private static void save(Context ctx, List<String[]> all) {
        JSONArray arr = new JSONArray();
        try {
            for (String[] s : all) {
                JSONObject o = new JSONObject();
                o.put("q", s[0]);
                o.put("loc", s[1]);
                o.put("cat", s[2]);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply();
    }
}

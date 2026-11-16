package com.nellis.bid;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local watchlist — watched items persisted in SharedPreferences (JSON). */
public class Watchlist {
    private static final String PREFS = "watchlist";

    public static boolean isWatched(Context ctx, String id) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("w_" + id, false);
    }

    public static void toggle(Context ctx, Item item) {
        if (isWatched(ctx, item.id)) remove(ctx, item.id);
        else add(ctx, item);
    }

    public static void add(Context ctx, Item item) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString("item_" + item.id, itemToJson(item)).putBoolean("w_" + item.id, true).apply();
    }

    public static void remove(Context ctx, String id) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().remove("item_" + id).remove("w_" + id).apply();
    }

    public static List<Item> getAll(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<Item> items = new ArrayList<>();
        for (String key : sp.getAll().keySet()) {
            if (key.startsWith("item_")) {
                Item it = itemFromJson(sp.getString(key, null));
                if (it != null) items.add(it);
            }
        }
        Collections.sort(items, (a, b) -> Long.compare(a.closeTime, b.closeTime));
        return items;
    }

    private static String itemToJson(Item it) {
        try {
            JSONObject j = new JSONObject();
            j.put("id", it.id);
            j.put("title", it.title);
            j.put("currentBid", it.currentBid);
            j.put("retail", it.retail);
            j.put("bidCount", it.bidCount);
            j.put("closeTime", it.closeTime);
            j.put("location", it.location);
            j.put("condition", it.condition);
            j.put("photo", it.photo);
            j.put("inventoryNumber", it.inventoryNumber);
            return j.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static Item itemFromJson(String json) {
        try {
            JSONObject j = new JSONObject(json);
            Item it = new Item();
            it.id = j.optString("id");
            it.title = j.optString("title");
            it.currentBid = j.optDouble("currentBid", -1);
            it.retail = j.optDouble("retail", -1);
            it.bidCount = j.optInt("bidCount", 0);
            it.closeTime = j.optLong("closeTime", 0);
            it.location = j.optString("location");
            it.condition = j.optString("condition");
            it.photo = j.optString("photo");
            it.inventoryNumber = j.optString("inventoryNumber");
            return it;
        } catch (Exception e) {
            return null;
        }
    }
}

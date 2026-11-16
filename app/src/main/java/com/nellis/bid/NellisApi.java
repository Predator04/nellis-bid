package com.nellis.bid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Data layer: fetch + decode Nellis Auction's Remix flight data (public, no auth).
 * The search page HTML embeds a Remix single-fetch payload as
 * streamController.enqueue("<json>"). The payload is a flat list; decode it
 * via the index-indirection rules below.
 */
public class NellisApi {

    private static final String UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Safari/537.36";

    /** Search Nellis and return decoded listings (with current bid price). */
    public static List<Item> search(String query, String location, String category) throws Exception {
        return search(query, location, category, "");
    }

    public static List<Item> search(String query, String location, String category, String taxonomy2) throws Exception {
        List<String> params = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            params.add("query=" + URLEncoder.encode(query, "UTF-8"));
        }
        if (location != null && !location.isEmpty()) {
            params.add(URLEncoder.encode("Location Name", "UTF-8") + "=" + URLEncoder.encode(location, "UTF-8"));
        }
        if (category != null && !category.isEmpty()) {
            params.add(URLEncoder.encode("Taxonomy Level 1", "UTF-8") + "=" + URLEncoder.encode(category, "UTF-8"));
        }
        if (taxonomy2 != null && !taxonomy2.isEmpty()) {
            params.add(URLEncoder.encode("Taxonomy Level 2", "UTF-8") + "=" + URLEncoder.encode(taxonomy2, "UTF-8"));
        }
        String url = "https://www.nellisauction.com/search"
                + (params.isEmpty() ? "" : "?" + String.join("&", params));
        String html = httpGet(url);
        JSONArray data = extractFlight(html);
        if (data == null) throw new Exception("no flight data (site may have changed)");
        return parseProducts(data);
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        int code = c.getResponseCode();
        BufferedReader r = new BufferedReader(
            new InputStreamReader(code == 200 ? c.getInputStream() : c.getErrorStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        c.disconnect();
        return sb.toString();
    }

    /** Extract the flat-list JSON from the Remix streamController.enqueue payload. */
    private static JSONArray extractFlight(String html) {
        int s = html.indexOf("streamController.enqueue(\"");
        if (s < 0) return null;
        s += "streamController.enqueue(\"".length();
        int e = html.indexOf("\");", s);
        if (e < 0) return null;
        String raw = html.substring(s, e);
        // JS-unescape
        raw = jsUnescape(raw);
        try {
            return new JSONArray(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String jsUnescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case 'n': out.append('\n'); i++; break;
                    case 't': out.append('\t'); i++; break;
                    case 'r': out.append('\r'); i++; break;
                    case '"': out.append('"'); i++; break;
                    case '\\': out.append('\\'); i++; break;
                    case '/': out.append('/'); i++; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            out.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                            i += 5;
                        } else out.append(n);
                        break;
                    default: out.append(n); i++; break;
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // ---- Remix flat-list decoder (ported from the Python scrape.py) ----

    private static Object decode(Object v, JSONArray data) {
        try {
            if (v instanceof JSONObject) {
                JSONObject obj = (JSONObject) v;
                JSONObject out = new JSONObject();
                Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    String key = k;
                    if (k.startsWith("_")) {
                        try {
                            key = data.getString(Integer.parseInt(k.substring(1)));
                        } catch (Exception ignored) { key = k; }
                    }
                    out.put(key, deref(obj.get(k), data));
                }
                return out;
            }
            if (v instanceof JSONArray) {
                JSONArray arr = (JSONArray) v;
                if (arr.length() > 0 && "D".equals(arr.opt(0))) {
                    return arr.opt(1); // date ["D", epoch_ms]
                }
                JSONArray out = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    out.put(deref(arr.get(i), data));
                }
                return out;
            }
            return v; // scalar -> terminal
        } catch (Exception e) {
            return JSONObject.NULL;
        }
    }

    private static Object deref(Object x, JSONArray data) {
        try {
            if (x instanceof Integer || x instanceof Long) {
                long n = ((Number) x).longValue();
                if (n < 0 || n >= data.length()) return JSONObject.NULL;
                return decode(data.get((int) n), data);
            }
            return decode(x, data);
        } catch (Exception e) {
            return JSONObject.NULL;
        }
    }

    private static List<Item> parseProducts(JSONArray data) {
        List<Item> items = new ArrayList<>();
        try {
            Object search = null;
            Object root = decode(data.opt(0), data);
            if (root instanceof JSONObject) {
                JSONObject loaderData = ((JSONObject) root).optJSONObject("loaderData");
                if (loaderData != null) {
                    search = loaderData.optJSONObject("routes/search._index");
                    if (search == null) search = loaderData.optJSONObject("routes/search");
                }
            }
            if (!(search instanceof JSONObject)) return items;
            JSONArray products = ((JSONObject) search).optJSONArray("products");
            if (products == null) return items;
            for (int i = 0; i < products.length(); i++) {
                Object p = products.opt(i);
                if (p instanceof JSONObject) {
                    Item it = parseItem((JSONObject) p);
                    if (it != null) items.add(it);
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private static Item parseItem(JSONObject p) {
        try {
            Item it = new Item();
            it.id = p.optString("id");
            it.title = p.optString("title");
            it.currentBid = p.optDouble("currentPrice", -1);
            if (it.currentBid <= 0) it.currentBid = p.optDouble("currentBid", -1);
            it.retail = p.optDouble("retailPrice", -1);
            it.bidCount = p.optInt("bidCount", 0);
            it.closeTime = toEpochMs(p.opt("closeTime"));
            it.openTime = toEpochMs(p.opt("openTime"));
            it.marketStatus = p.optString("marketStatus");
            it.inventoryNumber = p.optString("inventoryNumber");
            it.notes = p.optString("notes");
            it.buyerPremium = p.optDouble("buyerPremium", -1);
            it.notReturnable = p.optBoolean("notReturnable", false);

            Object loc = p.opt("location");
            if (loc instanceof JSONObject) {
                JSONObject l = (JSONObject) loc;
                it.location = l.optString("name");
                it.address = l.optString("address");
                it.city = l.optString("city");
                it.state = l.optString("state");
                Object z = l.opt("zipCode");
                if (z != null) it.zip = String.valueOf(z);
            }

            Object grade = p.opt("grade");
            if (grade instanceof JSONObject) {
                JSONObject g = (JSONObject) grade;
                it.condition = gradeDesc(g, "conditionType");
                it.functional = mapGrade("functional", gradeDesc(g, "functionalType"));
                it.missingParts = mapGrade("missing", gradeDesc(g, "missingPartsType"));
                it.damage = mapGrade("damage", gradeDesc(g, "damageType"));
                it.assembly = mapGrade("assembly", gradeDesc(g, "assemblyType"));
                it.packaging = mapGrade("package", gradeDesc(g, "packageType"));
                it.rating = g.optInt("rating", 0);
            }

            Object photos = p.opt("photos");
            if (photos instanceof JSONObject) {
                String u = ((JSONObject) photos).optString("url");
                if (!u.isEmpty()) it.photos.add(u);
            } else if (photos instanceof JSONArray) {
                JSONArray arr = (JSONArray) photos;
                for (int i = 0; i < arr.length(); i++) {
                    Object ph = arr.opt(i);
                    if (ph instanceof JSONObject) {
                        String u = ((JSONObject) ph).optString("url");
                        if (!u.isEmpty()) it.photos.add(u);
                    } else if (ph instanceof String && !((String) ph).isEmpty()) {
                        it.photos.add((String) ph);
                    }
                }
            }
            it.taxonomy1 = p.optString("taxonomyLevel1");
            it.taxonomy2 = p.optString("taxonomyLevel2");
            it.brand = p.optString("brand");
            it.color = p.optString("color");
            it.dimensions = p.optString("dimensions");
            it.weight = p.optString("weight");

            Object desc = p.opt("descriptions");
            if (desc instanceof JSONArray) {
                JSONArray da = (JSONArray) desc;
                StringBuilder dsb = new StringBuilder();
                for (int i = 0; i < da.length(); i++) {
                    String d = da.optString(i);
                    if (!d.isEmpty()) dsb.append(dsb.length() > 0 ? "\n\n" : "").append(d);
                }
                it.description = dsb.toString();
            } else if (desc instanceof String) {
                it.description = (String) desc;
            }

            Object feat = p.opt("features");
            if (feat instanceof JSONArray) {
                JSONArray fa = (JSONArray) feat;
                for (int i = 0; i < fa.length(); i++) {
                    String f = fa.optString(i);
                    if (!f.isEmpty()) it.features.add(f);
                }
            }

            it.photo = it.photos.isEmpty() ? "" : it.photos.get(0);
            return it;
        } catch (Exception e) {
            return null;
        }
    }

    private static String gradeDesc(JSONObject grade, String key) {
        Object v = grade.opt(key);
        if (v instanceof JSONObject) return ((JSONObject) v).optString("description");
        return "";
    }

    /** Map raw grade descriptions ("Yes"/"No"/"None") to the friendly labels the site shows. */
    private static String mapGrade(String type, String desc) {
        if (desc == null || desc.isEmpty()) return "";
        switch (type) {
            case "functional": return "Yes".equalsIgnoreCase(desc) ? "Functional" : "No".equalsIgnoreCase(desc) ? "Non-Functional" : desc;
            case "damage": return "None".equalsIgnoreCase(desc) ? "No Damage" : desc;
            case "assembly": return "No".equalsIgnoreCase(desc) ? "No Assembly Needed" : "Yes".equalsIgnoreCase(desc) ? "Assembly Needed" : desc;
            case "package": return "Yes".equalsIgnoreCase(desc) ? "In Package" : "No".equalsIgnoreCase(desc) ? "Not in Package" : desc;
            case "missing": return "No".equalsIgnoreCase(desc) ? "No Missing Parts" : "Missing: " + desc;
            default: return desc;
        }
    }

    private static long toEpochMs(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) {
            try { return Long.parseLong((String) o); } catch (Exception e) { return 0; }
        }
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            if (a.length() >= 2 && "D".equals(a.opt(0))) {
                Object v = a.opt(1);
                if (v instanceof Number) return ((Number) v).longValue();
            }
        }
        return 0;
    }

    /** Fetch the full item detail (all photos + richer fields) from the /p/ page. */
    public static Item fetchDetail(Item summary) throws Exception {
        if (summary == null || summary.id.isEmpty()) return summary;
        String html = httpGet(summary.url());
        JSONArray data = extractFlight(html);
        if (data == null) return summary;
        Object root = decode(data.opt(0), data);
        if (!(root instanceof JSONObject)) return summary;
        JSONObject product = findProduct((JSONObject) root);
        if (product != null) {
            Item full = parseItem(product);
            if (full != null) return full;
        }
        return summary;
    }

    /** Find the product object under loaderData/routes/p.* (the detail route). */
    private static JSONObject findProduct(JSONObject root) {
        try {
            JSONObject ld = root.optJSONObject("loaderData");
            if (ld == null) return null;
            Iterator<String> it = ld.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k.startsWith("routes/p.")) {
                    JSONObject r = ld.optJSONObject(k);
                    if (r != null && r.has("product")) return r.optJSONObject("product");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

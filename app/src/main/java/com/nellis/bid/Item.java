package com.nellis.bid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A single Nellis Auction listing.
 * Fee math: buyer's premium 15% + sales tax 8.375% (on bid+premium).
 * All-in = bid * 1.15 * 1.08375 = bid * 1.2463125.
 */
public class Item implements Serializable {
    public String id = "";
    public String title = "";
    public double currentBid = -1;   // raw current bid (before fees)
    public double retail = -1;       // suggested retail
    public int bidCount = 0;
    public long closeTime = 0;       // epoch milliseconds
    public String marketStatus = "";
    public String location = "";
    public String condition = "";
    public String functional = "";
    public String missingParts = "";
    public String damage = "";
    public String photo = "";
    public List<String> photos = new ArrayList<>();
    public String inventoryNumber = "";
    public String notes = "";
    public long openTime = 0;
    public String address = "";
    public String city = "";
    public String state = "";
    public double buyerPremium = -1;
    public int rating = 0;
    public String taxonomy1 = "";
    public String taxonomy2 = "";
    public String description = "";
    public List<String> features = new ArrayList<>();
    public String assembly = "";
    public String packaging = "";
    public String zip = "";
    public String brand = "";
    public String color = "";
    public String dimensions = "";
    public String weight = "";
    public boolean notReturnable = false;

    public static final double PREMIUM = 0.15;
    public static final double TAX = 0.08375;
    public static final double ALL_IN_FACTOR = (1 + PREMIUM) * (1 + TAX); // 1.2463125

    /** Bid + 15% buyer's premium + 8.375% tax. -1 if no bid. */
    public double premium() {
        return currentBid > 0 ? currentBid * PREMIUM : -1;
    }

    public double tax() {
        return currentBid > 0 ? (currentBid + premium()) * TAX : -1;
    }

    public double allIn() {
        return currentBid > 0 ? currentBid * ALL_IN_FACTOR : -1;
    }

    /** Website item URL (the id is what routes; slug is cosmetic). */
    public String url() {
        String slug = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.length() > 80) slug = slug.substring(0, 80);
        return "https://www.nellisauction.com/p/" + slug + "/" + id;
    }

    /** Amazon search URL from a cleaned title (drops condition notes + dashes). */
    public String amazonUrl() {
        String t = title == null ? "" : title;
        t = t.split("\\*\\*")[0];
        String[] segs = t.split("-{2,}");
        String best = "";
        for (String s : segs) {
            s = s.trim();
            if (s.length() > best.length()) best = s;
        }
        if (!best.isEmpty()) t = best;
        t = t.replaceFirst("^(?i)(MAJOR DAMAGE|MINOR DAMAGE|SELLING FOR PARTS ONLY|PARTS ONLY|FOR PARTS|"
                + "AS[- ]IS|UNTESTED|NOT TESTED|DAMAGED|BROKEN|MISSING [A-Z]+|NO POWER)\\s*[-:.]*\\s*", "");
        t = t.replaceAll("\\(.*?\\)|\\[.*?\\]", " ");
        t = t.replaceAll("\\s+", " ").trim();
        if (t.length() > 90) t = t.substring(0, 90);
        try {
            return "https://www.amazon.com/s?k=" + java.net.URLEncoder.encode(t, "UTF-8");
        } catch (Exception e) {
            return "https://www.amazon.com/s?k=" + t;
        }
    }
}

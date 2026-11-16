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
}

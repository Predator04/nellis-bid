package com.nellis.bid;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {
    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);
    private ImageView mainImg;
    private LinearLayout gallery;
    private Item item;
    private TextView title, quality, cond, bid, allin, retail, premium, bids, closes, time, inv, category;
    private TextView descHeader, description, featHeader, features, detailsHeader, details, notes;
    private EditText bidAmount;
    private TextView bidStatus;
    private List<String> currentPhotos = new ArrayList<>();
    private int currentIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        item = (Item) getIntent().getSerializableExtra("item");
        if (item == null) { finish(); return; }

        mainImg = findViewById(R.id.d_img);
        gallery = findViewById(R.id.d_gallery);
        title = findViewById(R.id.d_title);
        quality = findViewById(R.id.d_quality);
        cond = findViewById(R.id.d_condition);
        bid = findViewById(R.id.d_bid);
        allin = findViewById(R.id.d_allin);
        retail = findViewById(R.id.d_retail);
        premium = findViewById(R.id.d_premium);
        bids = findViewById(R.id.d_bids);
        closes = findViewById(R.id.d_closes);
        time = findViewById(R.id.d_time);
        inv = findViewById(R.id.d_inventory);
        category = findViewById(R.id.d_category);
        descHeader = findViewById(R.id.d_desc_header);
        description = findViewById(R.id.d_description);
        featHeader = findViewById(R.id.d_feat_header);
        features = findViewById(R.id.d_features);
        detailsHeader = findViewById(R.id.d_details_header);
        details = findViewById(R.id.d_details);
        notes = findViewById(R.id.d_notes);
        Button bidBtn = findViewById(R.id.d_bid_btn);
        bidAmount = findViewById(R.id.d_bid_amount);
        bidStatus = findViewById(R.id.d_bid_status);
        Button viewNellis = findViewById(R.id.d_view_nellis);
        Button viewAmazon = findViewById(R.id.d_view_amazon);
        Button similarBtn = findViewById(R.id.d_similar_btn);
        Button shareBtn = findViewById(R.id.d_share_btn);

        render();
        bidBtn.setOnClickListener(v -> onBid());
        viewNellis.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.url()))));
        viewAmazon.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.amazonUrl()))));
        shareBtn.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, item.title + "\n" + item.url());
            startActivity(Intent.createChooser(send, "Share item"));
        });
        similarBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("similar_tax2", item.taxonomy2);
            i.putExtra("similar_tax1", item.taxonomy1);
            i.putExtra("similar_title", item.title);
            startActivity(i);
        });

        Button watchBtn = findViewById(R.id.d_watch_btn);
        updateWatchBtn(watchBtn);
        watchBtn.setOnClickListener(v -> {
            Watchlist.toggle(this, item);
            updateWatchBtn(watchBtn);
        });

        // Similar button only makes sense once we have a category
        similarBtn.setVisibility((item.taxonomy1.isEmpty() && item.taxonomy2.isEmpty()) ? View.GONE : View.VISIBLE);

        // Fetch the full gallery + rich fields from the item detail page.
        new Thread(() -> {
            try {
                Item full = NellisApi.fetchDetail(item);
                runOnUiThread(() -> {
                    item = full;
                    render();
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void render() {
        title.setText(item.title.isEmpty() ? "(no title)" : item.title);

        // Quality: stars + condition tags
        quality.setText(item.rating > 0 ? stars(item.rating) : "");
        String c = buildCondition(item);
        cond.setText(c);
        cond.setVisibility(c.isEmpty() ? View.GONE : View.VISIBLE);

        bid.setText("Current bid: " + (item.currentBid > 0 ? usd.format(item.currentBid) : "—"));
        allin.setText("All-in (with fees): " + (item.allIn() > 0 ? usd.format(item.allIn()) : "—"));
        retail.setText("Retail: " + (item.retail > 0 ? usd.format(item.retail) : "—"));
        premium.setText("Buyer's premium: " + (item.buyerPremium > 0 ? pct(item.buyerPremium) : "15") + "%");
        bids.setText(item.bidCount + " bids");

        closes.setText(item.closeTime > 0 ? "Auction closes " + formatClose(item.closeTime) : "");
        time.setText(timeLeft(item.closeTime));
        inv.setText("Inventory #" + item.inventoryNumber);

        // Category breadcrumb ("Found in")
        if (item.taxonomy1.isEmpty()) {
            category.setVisibility(View.GONE);
        } else {
            category.setVisibility(View.VISIBLE);
            category.setText("Found in: " + item.taxonomy1
                    + (item.taxonomy2.isEmpty() ? "" : "  →  " + item.taxonomy2));
        }

        // Description
        if (item.description.isEmpty()) {
            descHeader.setVisibility(View.GONE);
            description.setVisibility(View.GONE);
        } else {
            descHeader.setVisibility(View.VISIBLE);
            description.setVisibility(View.VISIBLE);
            description.setText(item.description);
        }

        // Features
        if (item.features.isEmpty()) {
            featHeader.setVisibility(View.GONE);
            features.setVisibility(View.GONE);
        } else {
            featHeader.setVisibility(View.VISIBLE);
            features.setVisibility(View.VISIBLE);
            features.setText("• " + String.join("\n• ", item.features));
        }

        // Details (seller + pickup + specs)
        detailsHeader.setVisibility(View.VISIBLE);
        details.setVisibility(View.VISIBLE);
        details.setText(buildDetails(item));

        // Notes
        if (item.notes.isEmpty()) {
            notes.setVisibility(View.GONE);
        } else {
            notes.setVisibility(View.VISIBLE);
            notes.setText("Notes: " + item.notes);
        }

        showGallery(item.photos);
        if (bidAmount != null && bidAmount.getText().toString().trim().isEmpty() && item.currentBid > 0) {
            long suggest = (long) Math.floor(item.currentBid) + 1;
            bidAmount.setText(String.valueOf(suggest));
        }
    }

    private void onBid() {
        String token = AuthApi.getToken(this);
        if (token == null) {
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        String amtStr = bidAmount.getText().toString().trim();
        if (amtStr.isEmpty()) { bidStatus.setText("Enter a bid amount"); return; }
        double amt;
        try { amt = Double.parseDouble(amtStr); } catch (Exception e) { bidStatus.setText("Invalid amount"); return; }
        final String productId = item.id;
        bidStatus.setText("Placing bid…");
        new Thread(() -> {
            try {
                AuthApi.placeBid(token, productId, amt);
                runOnUiThread(() -> bidStatus.setText("Bid placed ✓"));
            } catch (Exception e) {
                runOnUiThread(() -> bidStatus.setText(e.getMessage()));
            }
        }).start();
    }

    private void showGallery(List<String> photos) {
        gallery.removeAllViews();
        currentPhotos = photos == null ? new ArrayList<>() : photos;
        currentIndex = 0;
        if (currentPhotos.isEmpty()) {
            mainImg.setImageResource(R.drawable.ic_placeholder);
            return;
        }
        loadInto(mainImg, currentPhotos.get(0));
        mainImg.setOnClickListener(v -> openFullScreen(currentIndex));
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < currentPhotos.size(); i++) {
            final int idx = i;
            String url = currentPhotos.get(i);
            ImageView thumb = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (80 * d), (int) (80 * d));
            lp.setMarginEnd((int) (8 * d));
            thumb.setLayoutParams(lp);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundResource(R.drawable.bg_glass);
            thumb.setTag(idx);
            loadInto(thumb, url);
            thumb.setOnClickListener(v -> {
                currentIndex = idx;
                loadInto(mainImg, currentPhotos.get(idx));
            });
            gallery.addView(thumb);
        }
    }

    private void openFullScreen(int index) {
        if (currentPhotos.isEmpty()) return;
        Intent i = new Intent(this, PhotoViewActivity.class);
        i.putStringArrayListExtra("photos", new ArrayList<>(currentPhotos));
        i.putExtra("index", index);
        startActivity(i);
    }

    private void loadInto(ImageView view, String url) {
        view.setImageResource(R.drawable.ic_placeholder);
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0");
                c.connect();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream in = c.getInputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
                in.close();
                c.disconnect();
                byte[] bytes = baos.toByteArray();
                // Downsample large images to ~1200px max to save memory
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                int sample = 1;
                int maxDim = Math.max(opts.outWidth, opts.outHeight);
                while (maxDim / sample > 1200) sample *= 2;
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sample;
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                if (bmp != null) {
                    runOnUiThread(() -> view.setImageBitmap(bmp));
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void updateWatchBtn(Button b) {
        b.setText(Watchlist.isWatched(this, item.id) ? "★ Watching" : "☆ Watch");
    }

    private String buildCondition(Item it) {
        List<String> tags = new ArrayList<>();
        if (!it.condition.isEmpty()) tags.add(it.condition);
        if (!it.functional.isEmpty()) tags.add(it.functional);
        if (!it.damage.isEmpty()) tags.add(it.damage);
        if (!it.assembly.isEmpty()) tags.add(it.assembly);
        if (!it.packaging.isEmpty()) tags.add(it.packaging);
        if (!it.missingParts.isEmpty()) tags.add(it.missingParts);
        return String.join(" · ", tags);
    }

    private String buildDetails(Item it) {
        StringBuilder sb = new StringBuilder();
        sb.append("Seller: Nellis Auction");
        String addr = fullAddress(it);
        if (!addr.isEmpty()) sb.append("\nPickup: ").append(addr);
        if (!it.brand.isEmpty()) sb.append("\nBrand: ").append(it.brand);
        if (!it.color.isEmpty()) sb.append("  ·  Color: ").append(it.color);
        if (!it.dimensions.isEmpty()) sb.append("\nDimensions: ").append(it.dimensions);
        if (!it.size.isEmpty()) sb.append("\nSize: ").append(it.size);
        if (!it.weight.isEmpty()) sb.append("\nWeight: ").append(it.weight);
        return sb.toString();
    }

    private String fullAddress(Item it) {
        StringBuilder sb = new StringBuilder();
        if (!it.address.isEmpty()) sb.append(it.address);
        if (!it.city.isEmpty()) sb.append(sb.length() > 0 ? ", " : "").append(it.city);
        if (!it.state.isEmpty()) sb.append(" ").append(it.state);
        if (!it.zip.isEmpty()) sb.append(" ").append(it.zip);
        return sb.toString();
    }

    private String stars(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < n ? "★" : "☆");
        return sb.toString();
    }

    private String pct(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    private String formatClose(long ms) {
        try {
            return new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US).format(new Date(ms));
        } catch (Exception e) {
            return "";
        }
    }

    private String timeLeft(long closeMs) {
        if (closeMs <= 0) return "";
        long d = closeMs - System.currentTimeMillis();
        if (d < 0) return "ENDED";
        long hh = d / 3600000, mm = d % 3600000 / 60000, ss = d % 60000 / 1000;
        if (hh > 0) return "Ends in " + hh + "h " + mm + "m";
        if (mm > 0) return "Ends in " + mm + "m " + ss + "s";
        return "Ends in " + ss + "s";
    }
}

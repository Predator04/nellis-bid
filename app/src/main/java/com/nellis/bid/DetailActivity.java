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

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {
    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);
    private ImageView mainImg;
    private LinearLayout gallery;
    private Item item;
    private TextView title, cond, bid, allin, retail, bids, time, loc, notes, inv;
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
        cond = findViewById(R.id.d_condition);
        bid = findViewById(R.id.d_bid);
        allin = findViewById(R.id.d_allin);
        retail = findViewById(R.id.d_retail);
        bids = findViewById(R.id.d_bids);
        time = findViewById(R.id.d_time);
        loc = findViewById(R.id.d_location);
        notes = findViewById(R.id.d_notes);
        inv = findViewById(R.id.d_inventory);
        Button bidBtn = findViewById(R.id.d_bid_btn);
        bidAmount = findViewById(R.id.d_bid_amount);
        bidStatus = findViewById(R.id.d_bid_status);

        render();
        bidBtn.setOnClickListener(v -> onBid());

        // Fetch the full gallery from the item detail page.
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
        cond.setText(buildCondition(item));
        bid.setText("Current bid: " + (item.currentBid > 0 ? usd.format(item.currentBid) : "—"));
        allin.setText("All-in (with fees): " + (item.allIn() > 0 ? usd.format(item.allIn()) : "—"));
        retail.setText("Retail: " + (item.retail > 0 ? usd.format(item.retail) : "—"));
        bids.setText(item.bidCount + " bids");
        time.setText(timeLeft(item.closeTime));

        StringBuilder locStr = new StringBuilder(item.location);
        if (!item.address.isEmpty()) locStr.append(" · ").append(item.address);
        if (!item.city.isEmpty()) locStr.append(", ").append(item.city);
        if (!item.state.isEmpty()) locStr.append(" ").append(item.state);
        loc.setText(locStr.toString());

        if (item.notes.isEmpty()) {
            notes.setVisibility(View.GONE);
        } else {
            notes.setVisibility(View.VISIBLE);
            notes.setText("Notes: " + item.notes);
        }
        inv.setText("Inv #" + item.inventoryNumber);
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
                Bitmap bmp = BitmapFactory.decodeStream(c.getInputStream());
                c.disconnect();
                if (bmp != null) {
                    runOnUiThread(() -> view.setImageBitmap(bmp));
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private String buildCondition(Item it) {
        StringBuilder sb = new StringBuilder();
        if (!it.condition.isEmpty()) sb.append(it.condition);
        if (!it.functional.isEmpty() && !it.functional.equalsIgnoreCase(it.condition))
            sb.append(sb.length() > 0 ? " · " : "").append(it.functional);
        if (!it.missingParts.isEmpty())
            sb.append(sb.length() > 0 ? " · " : "").append("Missing: ").append(it.missingParts);
        if (!it.damage.isEmpty())
            sb.append(sb.length() > 0 ? " · " : "").append(it.damage);
        if (it.rating > 0)
            sb.append(sb.length() > 0 ? " · " : "").append(it.rating).append("★");
        return sb.toString();
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

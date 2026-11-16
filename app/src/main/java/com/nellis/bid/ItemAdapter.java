package com.nellis.bid;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.VH> {

    private final List<Item> items = new ArrayList<>();
    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);
    private final LruCache<String, Bitmap> imgCache = new LruCache<>(32);
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    public void setItems(List<Item> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item it = items.get(position);
        h.itemView.setOnClickListener(v -> {
            Intent i = new Intent(v.getContext(), DetailActivity.class);
            i.putExtra("item", it);
            v.getContext().startActivity(i);
        });
        h.title.setText(it.title.isEmpty() ? "(no title)" : it.title);

        String bid = it.currentBid > 0 ? usd.format(it.currentBid) : "—";
        String allIn = it.allIn() > 0 ? usd.format(it.allIn()) : "—";
        h.bid.setText("Bid " + bid);
        h.allIn.setText("All-in " + allIn);

        if (it.retail > 0) {
            h.retail.setText("Retail " + usd.format(it.retail));
            h.retail.setVisibility(View.VISIBLE);
        } else {
            h.retail.setVisibility(View.GONE);
        }

        h.condition.setText(buildCondition(it));
        h.condition.setVisibility(it.condition.isEmpty() ? View.GONE : View.VISIBLE);

        String time = timeLeft(it.closeTime);
        h.time.setText(time);
        h.location.setText(it.location.isEmpty() ? "" : it.location + " · " + it.bidCount + " bids");

        // image
        h.img.setImageResource(R.drawable.ic_placeholder);
        if (it.photo != null && !it.photo.isEmpty()) {
            String url = it.photo;
            Bitmap cached = imgCache.get(url);
            if (cached != null) {
                h.img.setImageBitmap(cached);
            } else {
                h.img.setTag(url);
                pool.execute(() -> {
                    Bitmap bmp = fetchBitmap(url);
                    if (bmp != null) {
                        imgCache.put(url, bmp);
                        if (url.equals(h.img.getTag())) {
                            h.img.post(() -> h.img.setImageBitmap(bmp));
                        }
                    }
                });
            }
        }
    }

    private String buildCondition(Item it) {
        List<String> tags = new ArrayList<>();
        if (!it.condition.isEmpty()) tags.add(it.condition);
        if (!it.functional.isEmpty()) tags.add(it.functional);
        if (!it.damage.isEmpty()) tags.add(it.damage);
        if (!it.assembly.isEmpty()) tags.add(it.assembly);
        if (!it.packaging.isEmpty()) tags.add(it.packaging);
        if (!it.missingParts.isEmpty()) tags.add(it.missingParts);
        String s = String.join(" · ", tags);
        if (it.rating > 0) s = (s.isEmpty() ? "" : s + "   ") + stars(it.rating);
        return s;
    }

    private String stars(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < n ? "★" : "☆");
        return sb.toString();
    }

    private String timeLeft(long closeMs) {
        if (closeMs <= 0) return "";
        long d = closeMs - System.currentTimeMillis();
        if (d < 0) return "ENDED";
        long hh = d / 3600000;
        long mm = (d % 3600000) / 60000;
        long ss = (d % 60000) / 1000;
        if (hh > 0) return hh + "h " + mm + "m";
        if (mm > 0) return mm + "m " + ss + "s";
        return ss + "s";
    }

    private Bitmap fetchBitmap(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            c.connect();
            if (c.getResponseCode() != 200) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream in = c.getInputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
            in.close();
            c.disconnect();
            byte[] bytes = baos.toByteArray();
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            int sample = 1;
            int maxDim = Math.max(opts.outWidth, opts.outHeight);
            while (maxDim / sample > 800) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView img;
        final TextView title, bid, allIn, retail, condition, time, location;
        VH(View v) {
            super(v);
            img = v.findViewById(R.id.img);
            title = v.findViewById(R.id.title);
            bid = v.findViewById(R.id.bid);
            allIn = v.findViewById(R.id.allin);
            retail = v.findViewById(R.id.retail);
            condition = v.findViewById(R.id.condition);
            time = v.findViewById(R.id.time);
            location = v.findViewById(R.id.location);
        }
    }
}

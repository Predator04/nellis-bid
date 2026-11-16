package com.nellis.bid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.github.chrisbanes.photoview.PhotoView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Full-screen, swipeable, pinch-zoom photo viewer. */
public class PhotoViewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);

        ArrayList<String> photos = getIntent().getStringArrayListExtra("photos");
        int index = getIntent().getIntExtra("index", 0);
        if (photos == null || photos.isEmpty()) { finish(); return; }

        ViewPager2 pager = findViewById(R.id.photo_pager);
        pager.setAdapter(new PhotoAdapter(photos));
        pager.setCurrentItem(Math.min(index, photos.size() - 1), false);
    }

    static class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.VH> {
        private final List<String> urls;

        PhotoAdapter(List<String> urls) { this.urls = urls; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PhotoView pv = new PhotoView(parent.getContext());
            pv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            pv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            return new VH(pv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            load(h.photoView, urls.get(position));
        }

        @Override
        public int getItemCount() { return urls.size(); }

        private void load(PhotoView view, String url) {
            new Thread(() -> {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(20000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0");
                    c.connect();
                    Bitmap bmp = BitmapFactory.decodeStream(c.getInputStream());
                    c.disconnect();
                    if (bmp != null) {
                        view.post(() -> view.setImageBitmap(bmp));
                    }
                } catch (Exception ignored) {
                }
            }).start();
        }

        static class VH extends RecyclerView.ViewHolder {
            final PhotoView photoView;
            VH(PhotoView v) {
                super(v);
                photoView = v;
            }
        }
    }
}

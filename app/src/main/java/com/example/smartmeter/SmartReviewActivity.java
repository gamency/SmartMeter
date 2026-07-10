package com.example.smartmeter;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SmartReviewActivity extends AppCompatActivity {

    private RecyclerView reviewRecyclerView;
    private TextView progressText;
    private Button submitBtn;
    private ReviewAdapter adapter;
    private List<ReviewItem> reviewItems = new ArrayList<>();
    private String batchId;

    // ===== 注意：ReviewItem 改为 public static =====
    public static class ReviewItem {
        public int id;
        public String roomName, readDate, readTime, timeLabel, photoPath;
        public int pointId;
        public double reading;
        public boolean confirmed, modified;

        public ReviewItem(int id, String roomName, String readDate, String readTime,
                          int pointId, String timeLabel, String photoPath,
                          double reading, boolean confirmed, boolean modified) {
            this.id = id;
            this.roomName = roomName;
            this.readDate = readDate;
            this.readTime = readTime;
            this.pointId = pointId;
            this.timeLabel = timeLabel;
            this.photoPath = photoPath;
            this.reading = reading;
            this.confirmed = confirmed;
            this.modified = modified;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_review);

        reviewRecyclerView = findViewById(R.id.reviewRecyclerView);
        progressText = findViewById(R.id.progressText);
        submitBtn = findViewById(R.id.submitBtn);

        reviewRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        batchId = getIntent().getStringExtra("batchId");
        if (batchId == null) {
            Toast.makeText(this, "缺少批次号", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadReviewData();

        submitBtn.setOnClickListener(v -> submitAll());
    }

    private void loadReviewData() {
        new Thread(() -> {
            try {
                SmartReadingDatabase db = SmartReadingDatabase.getInstance(this);
                List<SmartReadingEntity> entities = db.smartReadingDao().getByBatchId(batchId);
                if (entities.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "没有复核数据", Toast.LENGTH_SHORT).show());
                    return;
                }
                reviewItems.clear();
                for (SmartReadingEntity entity : entities) {
                    reviewItems.add(new ReviewItem(
                            (int) entity.id,
                            entity.roomName,
                            entity.readDate,
                            entity.readTime,
                            entity.pointId,
                            entity.timeLabel,
                            entity.photoPath,
                            entity.manualReading > 0 ? entity.manualReading : 0,
                            false,
                            false
                    ));
                }
                runOnUiThread(() -> {
                    adapter = new ReviewAdapter(reviewItems, this::onConfirm, this::onEdit);
                    reviewRecyclerView.setAdapter(adapter);
                    updateProgress();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void onConfirm(int position) {
        reviewItems.get(position).confirmed = true;
        adapter.notifyItemChanged(position);
        updateProgress();
    }

    private void onEdit(int position, double newReading) {
        ReviewItem item = reviewItems.get(position);
        item.reading = newReading;
        item.modified = true;
        item.confirmed = true;
        adapter.notifyItemChanged(position);
        updateProgress();
    }

    private void updateProgress() {
        int total = reviewItems.size();
        int done = 0;
        for (ReviewItem item : reviewItems) {
            if (item.confirmed) done++;
        }
        progressText.setText(done + " / " + total + " 已确认");
        submitBtn.setEnabled(done == total);
        if (done == total) {
            submitBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF22C55E));
        }
    }

    private void submitAll() {
        JSONArray confirmArray = new JSONArray();
        for (ReviewItem item : reviewItems) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("reading", item.reading);
                confirmArray.put(obj);
            } catch (Exception e) {}
        }

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();
                JSONObject bodyJson = new JSONObject();
                bodyJson.put("batch_id", batchId);
                bodyJson.put("confirmations", confirmArray);
                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        bodyJson.toString()
                );
                Request request = new Request.Builder()
                        .url(MainActivity.BASE_URL + "/api/smart/confirm_batch")
                        .post(body)
                        .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "✅ 提交成功！", Toast.LENGTH_SHORT).show();
                        new Thread(() -> {
                            SmartReadingDatabase db = SmartReadingDatabase.getInstance(this);
                            db.smartReadingDao().deleteSynced();
                        }).start();
                        finish();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "提交失败", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "异常: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
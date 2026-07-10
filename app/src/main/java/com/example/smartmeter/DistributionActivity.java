package com.example.smartmeter;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DistributionActivity extends AppCompatActivity {

    private LinearLayout distributionContainer;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_distribution);

        distributionContainer = findViewById(R.id.distribution_container);
        emptyText = findViewById(R.id.empty_text);

        findViewById(R.id.back_btn).setOnClickListener(v -> finish());

        fetchAllDistribution();
    }

    private void fetchAllDistribution() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                Request request = new Request.Builder()
                        .url(MainActivity.BASE_URL + "/api/rooms_usage")
                        .get()
                        .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    JSONArray jsonArray = new JSONArray(jsonData);
                    List<RoomItem> items = new ArrayList<>();
                    double maxKwh = 0;
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        double kwh = obj.optDouble("total_kwh", 0);
                        if (kwh > maxKwh) maxKwh = kwh;
                        items.add(new RoomItem(
                                obj.getInt("id"),
                                obj.getString("name"),
                                obj.getInt("floor"),
                                obj.getString("room_type"),
                                kwh,
                                obj.optDouble("price", 0)
                        ));
                    }
                    double finalMaxKwh = maxKwh > 0 ? maxKwh : 1;
                    runOnUiThread(() -> {
                        distributionContainer.removeAllViews();
                        if (items.isEmpty()) {
                            emptyText.setVisibility(View.VISIBLE);
                            return;
                        }
                        emptyText.setVisibility(View.GONE);

                        items.sort((a, b) -> Double.compare(b.getTotalKwh(), a.getTotalKwh()));

                        int[] colors = {
                                0xFF4A6CF7, 0xFF22C55E, 0xFFF59E0B, 0xFFEF4444,
                                0xFF8B5CF6, 0xFFEC4899, 0xFF14B8A6, 0xFFF97316,
                                0xFF6366F1, 0xFF84CC16, 0xFF06B6D4, 0xFF8B5CF6
                        };

                        for (int i = 0; i < items.size(); i++) {
                            RoomItem item = items.get(i);
                            double ratio = item.getTotalKwh() / finalMaxKwh;
                            int color = colors[i % colors.length];

                            View barView = getLayoutInflater().inflate(R.layout.item_distribution_bar_full, null);
                            TextView rankView = barView.findViewById(R.id.bar_rank);
                            TextView nameView = barView.findViewById(R.id.bar_name);
                            TextView floorView = barView.findViewById(R.id.bar_floor);
                            View barFill = barView.findViewById(R.id.bar_fill);
                            TextView valueView = barView.findViewById(R.id.bar_value);

                            rankView.setText(String.valueOf(i + 1));
                            nameView.setText(item.getName());
                            floorView.setText(item.getFloor() + "层");
                            barFill.setBackgroundColor(color);
                            barFill.getLayoutParams().width = (int) (ratio * 500) + 20;
                            valueView.setText(String.format("%.1f 度", item.getTotalKwh()));

                            distributionContainer.addView(barView);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
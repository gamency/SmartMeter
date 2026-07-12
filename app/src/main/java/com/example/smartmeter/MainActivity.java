package com.example.smartmeter;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    // ===== 修改成你的笔记本 IP =====
    public static final String BASE_URL = "http://192.168.10.12:5000";

    // UI 组件
    private TextView updateTime;
    private TextView cardCrossValue, cardCostValue, cardDiffValue, cardDiffLabel;
    private TextView landlordCost, landlordChange, tenantCost, tenantChange;
    private TextView periodText;
    private LinearLayout distributionContainer, trendContainer;
    private TextView distributionEmpty, trendEmpty, distributionTotal, totalCheck;
    private RecyclerView roomRecyclerView;
    private RoomAdapter roomAdapter;
    private SwipeRefreshLayout swipeRefresh;

    // 日期变量
    private String periodStart = "";
    private String periodEnd = "";
    private long lastRefreshTime = 0;

    // ===== 柔和分户色板（马卡龙色系） =====
    private final int[] roomColors = {
            R.color.room_1, R.color.room_2, R.color.room_3, R.color.room_4,
            R.color.room_5, R.color.room_6, R.color.room_7, R.color.room_8,
            R.color.room_9, R.color.room_10
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 绑定 UI
        updateTime = findViewById(R.id.update_time);
        cardCrossValue = findViewById(R.id.card_cross_value);
        cardCostValue = findViewById(R.id.card_cost_value);
        cardDiffValue = findViewById(R.id.card_diff_value);
        cardDiffLabel = findViewById(R.id.card_diff_label);
        landlordCost = findViewById(R.id.landlord_cost);
        landlordChange = findViewById(R.id.landlord_change);
        tenantCost = findViewById(R.id.tenant_cost);
        tenantChange = findViewById(R.id.tenant_change);
        distributionContainer = findViewById(R.id.distribution_container);
        trendContainer = findViewById(R.id.trend_container);
        distributionEmpty = findViewById(R.id.distribution_empty);
        trendEmpty = findViewById(R.id.trend_empty);
        distributionTotal = findViewById(R.id.distribution_total);
        totalCheck = findViewById(R.id.total_check);
        roomRecyclerView = findViewById(R.id.roomRecyclerView);
        periodText = findViewById(R.id.period_text);
        swipeRefresh = findViewById(R.id.swipe_refresh);

        roomRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 设置默认日期（本月）
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        Calendar cal = Calendar.getInstance();
        periodEnd = sdf.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        periodStart = sdf.format(cal.getTime());
        periodText.setText(periodStart + " ~ " + periodEnd);

        // 更新时间
        updateTime.setText("更新于 " + new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()));

        // ===== 日期选择 =====
        TextView changePeriod = findViewById(R.id.change_period);
        changePeriod.setOnClickListener(v -> showDatePickerDialog());

        // ===== 查看全部跳转 =====
        TextView distributionMore = findViewById(R.id.distribution_more);
        distributionMore.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DistributionActivity.class);
            startActivity(intent);
        });

        // ===== 智能抄表入口 =====
        CardView smartEntry = findViewById(R.id.smart_entry_card);
        smartEntry.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SmartPhotoActivity.class);
            startActivity(intent);
        });

        // ===== 下拉刷新 =====
        swipeRefresh.setOnRefreshListener(() -> {
            refreshAllData();
            swipeRefresh.setRefreshing(false);
        });

        // 加载数据
        refreshAllData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到前台刷新数据（60秒防抖）
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime > 60000) {
            refreshAllData();
            lastRefreshTime = now;
        }
    }

    // ===== 日期选择对话框 =====
    private void showDatePickerDialog() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog startDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    String start = year + "-" + String.format("%02d", month + 1) + "-" + String.format("%02d", dayOfMonth);
                    DatePickerDialog endDialog = new DatePickerDialog(this,
                            (v, y, m, d) -> {
                                String end = y + "-" + String.format("%02d", m + 1) + "-" + String.format("%02d", d);
                                periodStart = start;
                                periodEnd = end;
                                periodText.setText(periodStart + " ~ " + periodEnd);
                                refreshAllData();
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
                    endDialog.show();
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        startDialog.show();
    }

    // ===== 刷新所有数据 =====
    private void refreshAllData() {
        fetchDashboardData();
        fetchRoomList();
        fetchDistribution();
        fetchTrend();
    }

    // ===== API：核心指标 =====
    private void fetchDashboardData() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/dashboard?start=" + periodStart + "&end=" + periodEnd)
                        .get()
                        .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    JSONObject json = new JSONObject(jsonData);

                    double totalCross = json.optDouble("total_cross", 0);
                    double totalCost = json.optDouble("total_cost", 0);
                    double diff = json.optDouble("diff", 0);
                    double landlordCostVal = json.optDouble("landlord_cost", 0);
                    double landlordDiff = json.optDouble("landlord_diff", 0);
                    String landlordStatus = json.optString("landlord_status", "same");
                    double tenantCostVal = json.optDouble("tenant_cost", 0);
                    double tenantDiff = json.optDouble("tenant_diff", 0);
                    String tenantStatus = json.optString("tenant_status", "same");

                    runOnUiThread(() -> {
                        // 更新主卡片
                        cardCrossValue.setText(String.format("%.1f", totalCross));
                        cardCostValue.setText(String.format("%.1f", totalCost));
                        cardDiffValue.setText(String.format("%.1f", diff));
                        if (diff > 0) {
                            cardDiffValue.setTextColor(0xFF22C55E);
                            cardDiffLabel.setText("盈余");
                        } else if (diff < 0) {
                            cardDiffValue.setTextColor(0xFFEF4444);
                            cardDiffLabel.setText("亏损");
                        } else {
                            cardDiffLabel.setText("平衡");
                        }

                        // 房东承担
                        landlordCost.setText(String.format("%.1f", landlordCostVal));
                        if ("up".equals(landlordStatus)) {
                            landlordChange.setText("↑ +" + landlordDiff + " 度");
                            landlordChange.setTextColor(0xFFEF4444);
                        } else if ("down".equals(landlordStatus)) {
                            landlordChange.setText("↓ " + landlordDiff + " 度");
                            landlordChange.setTextColor(0xFF22C55E);
                        } else {
                            landlordChange.setText("持平");
                        }

                        // 租户承担
                        tenantCost.setText(String.format("%.1f", tenantCostVal));
                        if ("up".equals(tenantStatus)) {
                            tenantChange.setText("↑ +" + tenantDiff + " 度");
                            tenantChange.setTextColor(0xFFEF4444);
                        } else if ("down".equals(tenantStatus)) {
                            tenantChange.setText("↓ " + tenantDiff + " 度");
                            tenantChange.setTextColor(0xFF22C55E);
                        } else {
                            tenantChange.setText("持平");
                        }

                        // 更新汇总校验行
                        double total = landlordCostVal + tenantCostVal;
                        totalCheck.setText("本期总电费 " + String.format("%.1f", totalCost) +
                                " 元 = 房东 " + String.format("%.1f", landlordCostVal) +
                                " 元 + 租户 " + String.format("%.1f", tenantCostVal) + " 元");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ===== API：房间列表 =====
    private void fetchRoomList() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/rooms_usage?start=" + periodStart + "&end=" + periodEnd)
                        .get()
                        .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    JSONArray jsonArray = new JSONArray(jsonData);
                    List<RoomItem> roomList = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        roomList.add(new RoomItem(
                                obj.getInt("id"),
                                obj.getString("name"),
                                obj.getInt("floor"),
                                obj.getString("room_type"),
                                obj.optDouble("total_kwh", 0),
                                obj.optDouble("price", 0)
                        ));
                    }
                    runOnUiThread(() -> {
                        roomAdapter = new RoomAdapter(roomList);
                        roomRecyclerView.setAdapter(roomAdapter);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ===== API：用电分布（含汇总校验） =====
    private void fetchDistribution() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/rooms_usage?start=" + periodStart + "&end=" + periodEnd)
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
                    double finalTotalKwh = items.stream().mapToDouble(RoomItem::getTotalKwh).sum();
                    runOnUiThread(() -> {
                        distributionContainer.removeAllViews();
                        if (items.isEmpty() || finalMaxKwh == 0) {
                            distributionEmpty.setVisibility(View.VISIBLE);
                            distributionTotal.setText("暂无数据");
                            return;
                        }
                        distributionEmpty.setVisibility(View.GONE);
                        items.sort((a, b) -> Double.compare(b.getTotalKwh(), a.getTotalKwh()));
                        int count = Math.min(items.size(), 10);

                        for (int i = 0; i < count; i++) {
                            RoomItem item = items.get(i);
                            double ratio = item.getTotalKwh() / finalMaxKwh;
                            int colorResId = roomColors[i % roomColors.length];

                            View barView = getLayoutInflater().inflate(R.layout.item_distribution_bar, null);
                            TextView nameView = barView.findViewById(R.id.bar_name);
                            View barFill = barView.findViewById(R.id.bar_fill);
                            TextView valueView = barView.findViewById(R.id.bar_value);

                            nameView.setText(item.getName());
                            barFill.setBackgroundColor(ContextCompat.getColor(MainActivity.this, colorResId));
                            barFill.getLayoutParams().width = (int) (ratio * 300) + 20;
                            valueView.setText(String.format("%.1f", item.getTotalKwh()));
                            distributionContainer.addView(barView);
                        }

                        // 更新汇总校验
                        distributionTotal.setText("分户用电合计：" + String.format("%.1f", finalTotalKwh) + " 度");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ===== API：近7天趋势 =====
    private void fetchTrend() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/daily_trend?days=7")
                        .get()
                        .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    JSONArray jsonArray = new JSONArray(jsonData);
                    List<TrendItem> items = new ArrayList<>();
                    double maxVal = 0;
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        double val = obj.optDouble("total", 0);
                        if (val > maxVal) maxVal = val;
                        items.add(new TrendItem(obj.getString("date"), val));
                    }
                    double finalMaxVal = maxVal > 0 ? maxVal : 1;
                    runOnUiThread(() -> {
                        trendContainer.removeAllViews();
                        if (items.isEmpty()) {
                            trendEmpty.setVisibility(View.VISIBLE);
                            return;
                        }
                        trendEmpty.setVisibility(View.GONE);
                        for (TrendItem item : items) {
                            double ratio = item.value / finalMaxVal;
                            View barView = getLayoutInflater().inflate(R.layout.item_trend_bar, null);
                            TextView dateView = barView.findViewById(R.id.trend_date);
                            View barFill = barView.findViewById(R.id.trend_fill);
                            TextView valueView = barView.findViewById(R.id.trend_value);
                            dateView.setText(item.date.substring(5));
                            int height = (int) (ratio * 120) + 10;
                            barFill.getLayoutParams().height = height;
                            barFill.setBackgroundColor(item.value == finalMaxVal ? 0xFFF59E0B : 0xFF4A6CF7);
                            valueView.setText(String.format("%.0f", item.value));
                            trendContainer.addView(barView);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ===== 内部类：趋势数据 =====
    private static class TrendItem {
        String date;
        double value;
        TrendItem(String date, double value) {
            this.date = date;
            this.value = value;
        }
    }
}
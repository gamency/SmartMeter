package com.example.smartmeter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OverviewFragment extends Fragment {

    private static final String BASE_URL = "http://192.168.10.12:5000";

    // UI 组件
    private TextView tvTotalUsage, tvTenantUsage, tvLandlordUsage;
    private TextView tvPowerKwh, tvMeterTotal, tvDiffValue, tvDiffHint;
    private View llDiff;
    private RecyclerView rvTopTen;
    private TextView tvEmptyTenant;
    private TextView tvPeriod;

    // 日期标签
    private TextView tagMonth, tagLastMonth, tag30d, tag90d;
    private View btnCustomDate;

    // 日期变量
    private String periodStart = "";
    private String periodEnd = "";

    // 适配器
    private TopTenAdapter topTenAdapter;
    private List<TopTenItem> topTenList = new ArrayList<>();

    private static final double DIFF_THRESHOLD = 20.0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_overview, container, false);

        // 绑定视图
        tvTotalUsage = view.findViewById(R.id.tv_total_usage);
        tvTenantUsage = view.findViewById(R.id.tv_tenant_usage);
        tvLandlordUsage = view.findViewById(R.id.tv_landlord_usage);
        tvPowerKwh = view.findViewById(R.id.tv_power_kwh);
        tvMeterTotal = view.findViewById(R.id.tv_meter_total);
        tvDiffValue = view.findViewById(R.id.tv_diff_value);
        tvDiffHint = view.findViewById(R.id.tv_diff_hint);
        llDiff = view.findViewById(R.id.ll_diff);
        rvTopTen = view.findViewById(R.id.rv_top_ten);
        tvEmptyTenant = view.findViewById(R.id.tv_empty_tenant);
        tvPeriod = view.findViewById(R.id.tv_period);

        tagMonth = view.findViewById(R.id.tag_month);
        tagLastMonth = view.findViewById(R.id.tag_last_month);
        tag30d = view.findViewById(R.id.tag_30d);
        tag90d = view.findViewById(R.id.tag_90d);
        btnCustomDate = view.findViewById(R.id.btn_custom_date);

        // 设置RecyclerView
        rvTopTen.setLayoutManager(new LinearLayoutManager(getContext()));
        topTenAdapter = new TopTenAdapter(topTenList);
        rvTopTen.setAdapter(topTenAdapter);

        // 设置默认日期（本月）
        setDefaultPeriod();

        // 设置筛选标签点击
        setupFilterTags();

        // 自定义日期按钮
        btnCustomDate.setOnClickListener(v -> {
            Toast.makeText(getContext(), "自定义日期功能开发中", Toast.LENGTH_SHORT).show();
        });

        // 加载数据
        loadAllData();

        return view;
    }

    private void setDefaultPeriod() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        Calendar cal = Calendar.getInstance();
        periodEnd = sdf.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        periodStart = sdf.format(cal.getTime());
        tvPeriod.setText(periodStart + " ~ " + periodEnd);
        updateTagState(tagMonth);
    }

    private void setupFilterTags() {
        View.OnClickListener tagListener = v -> {
            TextView tag = (TextView) v;
            updateTagState(tag);
            String tagText = tag.getText().toString();
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

            switch (tagText) {
                case "本月":
                    periodEnd = sdf.format(cal.getTime());
                    cal.set(Calendar.DAY_OF_MONTH, 1);
                    periodStart = sdf.format(cal.getTime());
                    break;
                case "上月":
                    cal.add(Calendar.MONTH, -1);
                    cal.set(Calendar.DAY_OF_MONTH, 1);
                    periodStart = sdf.format(cal.getTime());
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                    periodEnd = sdf.format(cal.getTime());
                    break;
                case "近30天":
                    periodEnd = sdf.format(cal.getTime());
                    cal.add(Calendar.DAY_OF_MONTH, -29);
                    periodStart = sdf.format(cal.getTime());
                    break;
                case "近90天":
                    periodEnd = sdf.format(cal.getTime());
                    cal.add(Calendar.DAY_OF_MONTH, -89);
                    periodStart = sdf.format(cal.getTime());
                    break;
            }
            tvPeriod.setText(periodStart + " ~ " + periodEnd);
            loadAllData();
        };

        tagMonth.setOnClickListener(tagListener);
        tagLastMonth.setOnClickListener(tagListener);
        tag30d.setOnClickListener(tagListener);
        tag90d.setOnClickListener(tagListener);
    }

    private void updateTagState(TextView selectedTag) {
        TextView[] tags = {tagMonth, tagLastMonth, tag30d, tag90d};
        for (TextView tag : tags) {
            tag.setSelected(false);
            tag.setTextColor(getResources().getColor(R.color.text_secondary));
            tag.setBackgroundResource(R.drawable.bg_filter_tag);
        }
        selectedTag.setSelected(true);
        selectedTag.setTextColor(getResources().getColor(R.color.bg_card));
        selectedTag.setBackgroundResource(R.drawable.bg_filter_tag);
    }

    private void loadAllData() {
        fetchDashboardData();
        fetchRoomsUsage();
    }

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
                    double landlordCost = json.optDouble("landlord_cost", 0);
                    double tenantCost = json.optDouble("tenant_cost", 0);
                    double powerKwh = json.optDouble("power_kwh", 0);
                    double diff = json.optDouble("diff", 0);

                    getActivity().runOnUiThread(() -> {
                        tvTotalUsage.setText(String.format("%.1f 度", totalCross));
                        tvTenantUsage.setText(String.format("%.1f 度", tenantCost));
                        tvLandlordUsage.setText(String.format("%.1f 度", landlordCost));
                        tvPowerKwh.setText(String.format("%.1f 度", powerKwh));
                        tvMeterTotal.setText(String.format("%.1f 度", totalCross));
                        tvDiffValue.setText(String.format("%.1f 度", diff));

                        if (Math.abs(diff) <= DIFF_THRESHOLD) {
                            llDiff.setBackgroundResource(R.drawable.bg_diff_normal);
                            tvDiffValue.setTextColor(getResources().getColor(R.color.text_primary));
                            tvDiffHint.setText("理想状态下两者数值基本持平");
                            tvDiffHint.setTextColor(getResources().getColor(R.color.text_hint));
                        } else {
                            llDiff.setBackgroundResource(R.drawable.bg_diff_warning);
                            tvDiffValue.setTextColor(getResources().getColor(R.color.warning_orange));
                            tvDiffHint.setText("差值异常，建议排查漏电/分表故障/偷电");
                            tvDiffHint.setTextColor(getResources().getColor(R.color.warning_orange));
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "加载数据失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void fetchRoomsUsage() {
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

                    List<TopTenItem> items = new ArrayList<>();

                    double totalKwh = 0;
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        double kwh = obj.optDouble("total_kwh", 0);
                        totalKwh += kwh;
                    }
                    double avgKwh = jsonArray.length() > 0 ? totalKwh / jsonArray.length() : 0;

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        double kwh = obj.optDouble("total_kwh", 0);
                        String name = obj.getString("name");
                        int floor = obj.getInt("floor");
                        String roomType = obj.getString("room_type");

                        if ("分户出租".equals(roomType) || "商铺".equals(roomType)) {
                            boolean isWarning = avgKwh > 0 && kwh > avgKwh * 2;
                            items.add(new TopTenItem(name, floor + "层", kwh, isWarning));
                        }
                    }

                    items.sort((a, b) -> Double.compare(b.usage, a.usage));
                    List<TopTenItem> topTen = items.size() > 10 ? items.subList(0, 10) : items;

                    getActivity().runOnUiThread(() -> {
                        topTenList.clear();
                        topTenList.addAll(topTen);
                        topTenAdapter.notifyDataSetChanged();

                        if (topTenList.isEmpty()) {
                            tvEmptyTenant.setVisibility(View.VISIBLE);
                            rvTopTen.setVisibility(View.GONE);
                        } else {
                            tvEmptyTenant.setVisibility(View.GONE);
                            rvTopTen.setVisibility(View.VISIBLE);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static class TopTenItem {
        public String room;
        public String floor;
        public double usage;
        public boolean isWarning;

        public TopTenItem(String room, String floor, double usage, boolean isWarning) {
            this.room = room;
            this.floor = floor;
            this.usage = usage;
            this.isWarning = isWarning;
        }
    }
}
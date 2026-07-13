package com.example.smartmeter;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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
    private TextView updateTime;
    private TextView tvTotalUsage, tvTenantUsage, tvLandlordUsage;
    private TextView tvPowerKwh, tvMeterTotal, tvDiffValue, tvDiffHint;
    private View llDiff;
    private TextView tvPeriod;
    private LinearLayout distributionContainer;
    private TextView tvEmptyTenant;

    // 日期标签
    private TextView tagMonth, tagLastMonth, tag30d, tag90d;
    private TextView btnCustomDate;

    // 日期变量
    private String periodStart = "";
    private String periodEnd = "";

    private static final double DIFF_THRESHOLD = 20.0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_overview, container, false);

        // 绑定视图
        updateTime = view.findViewById(R.id.update_time);
        tvTotalUsage = view.findViewById(R.id.tv_total_usage);
        tvTenantUsage = view.findViewById(R.id.tv_tenant_usage);
        tvLandlordUsage = view.findViewById(R.id.tv_landlord_usage);
        tvPowerKwh = view.findViewById(R.id.tv_power_kwh);
        tvMeterTotal = view.findViewById(R.id.tv_meter_total);
        tvDiffValue = view.findViewById(R.id.tv_diff_value);
        tvDiffHint = view.findViewById(R.id.tv_diff_hint);
        llDiff = view.findViewById(R.id.ll_diff);
        tvPeriod = view.findViewById(R.id.tv_period);
        distributionContainer = view.findViewById(R.id.distribution_container);
        tvEmptyTenant = view.findViewById(R.id.tv_empty_tenant);

        tagMonth = view.findViewById(R.id.tag_month);
        tagLastMonth = view.findViewById(R.id.tag_last_month);
        tag30d = view.findViewById(R.id.tag_30d);
        tag90d = view.findViewById(R.id.tag_90d);
        btnCustomDate = view.findViewById(R.id.btn_custom_date);

        // 设置默认日期（本月）
        setDefaultPeriod();

        // 设置筛选标签点击
        setupFilterTags();

        // 自定义日期按钮
        btnCustomDate.setOnClickListener(v -> showDatePickerDialog());

        // ===== 查看完整账单点击 =====
        TextView tvViewFull = view.findViewById(R.id.tv_view_full);
        tvViewFull.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), DistributionActivity.class);
            startActivity(intent);
        });

        // 更新时间
        updateTime.setText("更新于 " + new SimpleDateFormat("HH:mm", Locale.CHINA).format(new java.util.Date()));

        // 加载数据
        refreshAllData();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 回到前台时自动刷新
        refreshAllData();
    }

    // ===== 日期选择对话框 =====
    private void showDatePickerDialog() {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog startDialog = new DatePickerDialog(getContext(),
                (view, startYear, startMonth, startDay) -> {
                    String start = startYear + "-" + String.format("%02d", startMonth + 1)
                            + "-" + String.format("%02d", startDay);
                    DatePickerDialog endDialog = new DatePickerDialog(getContext(),
                            (v, endYear, endMonth, endDay) -> {
                                String end = endYear + "-" + String.format("%02d", endMonth + 1)
                                        + "-" + String.format("%02d", endDay);
                                periodStart = start;
                                periodEnd = end;
                                tvPeriod.setText(periodStart + " ~ " + periodEnd);
                                // 取消所有标签的选中状态
                                TextView[] tags = {tagMonth, tagLastMonth, tag30d, tag90d};
                                for (TextView tag : tags) {
                                    tag.setSelected(false);
                                    tag.setTextColor(getResources().getColor(R.color.text_secondary));
                                    tag.setBackgroundResource(R.drawable.bg_filter_tag);
                                }
                                refreshAllData();
                            },
                            year, month, day);
                    endDialog.show();
                },
                year, month, day);
        startDialog.show();
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
            refreshAllData();
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

    // ===== 刷新所有数据 =====
    private void refreshAllData() {
        // 打印当前周期
        android.util.Log.d("OverviewFragment", "刷新数据，周期: " + periodStart + " ~ " + periodEnd);
        fetchDashboardData();
        fetchDistribution();
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
                    double landlordCost = json.optDouble("landlord_cost", 0);
                    double tenantCost = json.optDouble("tenant_cost", 0);
                    double powerKwh = json.optDouble("power_kwh", 0);
                    double diff = json.optDouble("diff", 0);

                    if (getActivity() != null) {
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
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "加载数据失败: HTTP " + response.code(), Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "网络异常: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    // ===== API：用电分布（柱状图） =====
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
                        String name = obj.getString("name");
                        int floor = obj.getInt("floor");
                        String roomType = obj.getString("room_type");
                        // 显示所有房间（分户出租、商铺、公共、房东自住都显示）
                        items.add(new RoomItem(name, floor + "层", kwh));
                    }
                    double finalMaxKwh = maxKwh > 0 ? maxKwh : 1;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            distributionContainer.removeAllViews();
                            if (items.isEmpty() || finalMaxKwh == 0) {
                                tvEmptyTenant.setVisibility(View.VISIBLE);
                                return;
                            }
                            tvEmptyTenant.setVisibility(View.GONE);

                            // 按用电量降序排列，取前10
                            items.sort((a, b) -> Double.compare(b.usage, a.usage));
                            int count = Math.min(items.size(), 10);

                            int[] colors = {
                                    0xFF4A6CF7, 0xFF22C55E, 0xFFF59E0B, 0xFFEF4444,
                                    0xFF8B5CF6, 0xFFEC4899, 0xFF14B8A6, 0xFFF97316,
                                    0xFF6366F1, 0xFF84CC16
                            };

                            for (int i = 0; i < count; i++) {
                                RoomItem item = items.get(i);
                                double ratio = item.usage / finalMaxKwh;
                                View barView = getLayoutInflater().inflate(R.layout.item_distribution_bar, null);
                                TextView nameView = barView.findViewById(R.id.bar_name);
                                View barFill = barView.findViewById(R.id.bar_fill);
                                TextView valueView = barView.findViewById(R.id.bar_value);
                                nameView.setText(item.name);
                                barFill.setBackgroundColor(colors[i % colors.length]);
                                barFill.getLayoutParams().width = (int) (ratio * 300) + 20;
                                valueView.setText(String.format("%.1f", item.usage));
                                distributionContainer.addView(barView);
                            }
                        });
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "加载用电分布失败: HTTP " + response.code(), Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "加载用电分布异常: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    // ===== 内部数据类 =====
    private static class RoomItem {
        String name;
        String floor;
        double usage;
        RoomItem(String name, String floor, double usage) {
            this.name = name;
            this.floor = floor;
            this.usage = usage;
        }
    }
}
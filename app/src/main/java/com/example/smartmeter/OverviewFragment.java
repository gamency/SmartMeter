package com.example.smartmeter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class OverviewFragment extends Fragment {

    private TextView tvTotalUsage, tvTenantUsage, tvLandlordUsage;
    private TextView tvPowerKwh, tvMeterTotal, tvDiffValue;
    private View llDiff;
    private TextView tvDiffHint;
    private RecyclerView rvTopTen;
    private TextView tvEmptyTenant;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_overview, container, false);

        // 绑定视图
        tvTotalUsage = view.findViewById(R.id.tv_total_usage);
        tvTenantUsage = view.findViewById(R.id.tv_tenant_usage);
        tvLandlordUsage = view.findViewById(R.id.tv_landlord_usage);
        tvPowerKwh = view.findViewById(R.id.tv_power_kwh);
        tvMeterTotal = view.findViewById(R.id.tv_meter_total);
        tvDiffValue = view.findViewById(R.id.tv_diff_value);
        llDiff = view.findViewById(R.id.ll_diff);
        tvDiffHint = view.findViewById(R.id.tv_diff_hint);
        rvTopTen = view.findViewById(R.id.rv_top_ten);
        tvEmptyTenant = view.findViewById(R.id.tv_empty_tenant);

        rvTopTen.setLayoutManager(new LinearLayoutManager(getContext()));

        // 演示数据
        populateDummyData();
        setupFilters(view);

        return view;
    }

    private void populateDummyData() {
        // 演示数据（实际应调用API）
        tvTotalUsage.setText("390.9 度");
        tvTenantUsage.setText("314.7 度");
        tvLandlordUsage.setText("76.2 度");
        tvPowerKwh.setText("504.8 度");
        tvMeterTotal.setText("390.9 度");
        tvDiffValue.setText("-113.9 度");

        // 差值超标演示
        // 若差值超标，可改为以下样式：
        // llDiff.setBackgroundResource(R.drawable.bg_diff_warning);
        // tvDiffValue.setTextColor(getResources().getColor(R.color.warning_orange));
        // tvDiffHint.setText("差值异常，建议排查漏电/分表故障/偷电");

        // TOP10演示数据
        List<TopTenItem> list = new ArrayList<>();
        list.add(new TopTenItem("201", "王五", "45.2", false));
        list.add(new TopTenItem("202", "赵六", "38.7", false));
        list.add(new TopTenItem("203", "孙七", "34.5", true));
        list.add(new TopTenItem("204", "周八", "28.3", false));
        list.add(new TopTenItem("205", "吴九", "25.1", false));
        list.add(new TopTenItem("206", "郑十", "22.8", false));
        list.add(new TopTenItem("207", "钱甲", "18.4", false));
        list.add(new TopTenItem("208", "冯乙", "15.9", false));
        list.add(new TopTenItem("209", "陈丙", "12.6", false));
        list.add(new TopTenItem("210", "褚丁", "10.2", false));

        TopTenAdapter adapter = new TopTenAdapter(list);
        rvTopTen.setAdapter(adapter);

        if (list.isEmpty()) {
            tvEmptyTenant.setVisibility(View.VISIBLE);
        } else {
            tvEmptyTenant.setVisibility(View.GONE);
        }
    }

    private void setupFilters(View view) {
        // 日期筛选标签点击逻辑
        TextView[] tags = {
                view.findViewById(R.id.tag_month),
                view.findViewById(R.id.tag_last_month),
                view.findViewById(R.id.tag_30d),
                view.findViewById(R.id.tag_90d)
        };
        for (TextView tag : tags) {
            tag.setOnClickListener(v -> {
                for (TextView t : tags) {
                    t.setSelected(false);
                    t.setTextColor(getResources().getColor(R.color.text_secondary));
                }
                tag.setSelected(true);
                tag.setTextColor(getResources().getColor(R.color.bg_card));
                // TODO: 刷新数据
            });
        }

        // 自定义日期按钮
        view.findViewById(R.id.btn_custom_date).setOnClickListener(v -> {
            // TODO: 弹出底部日期选择器
        });
    }

    // 内部数据类
    static class TopTenItem {
        String room, name, usage;
        boolean isWarning;
        TopTenItem(String room, String name, String usage, boolean isWarning) {
            this.room = room;
            this.name = name;
            this.usage = usage;
            this.isWarning = isWarning;
        }
    }
}
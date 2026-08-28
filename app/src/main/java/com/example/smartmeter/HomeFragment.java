package com.example.smartmeter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 先用简单的视图占位
        TextView tv = new TextView(getContext());
        tv.setText("🏠 首页\n\n新设计正在加载...");
        tv.setTextSize(20f);
        tv.setGravity(View.TEXT_ALIGNMENT_CENTER);
        tv.setPadding(0, 100, 0, 0);
        tv.setTextColor(getResources().getColor(R.color.text_primary, null));
        return tv;
    }
}

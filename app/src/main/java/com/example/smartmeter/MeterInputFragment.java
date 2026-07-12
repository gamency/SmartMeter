package com.example.smartmeter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MeterInputFragment extends Fragment {

    private TextView tvSelectedRoom, tvRoomHint, tvPhotoHint;
    private Button btnTakePhoto, btnSubmitAll;
    private RecyclerView rvCached;
    private TextView tvEmptyCached;
    private TextView tvNetworkHint;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_meter_input, container, false);

        tvSelectedRoom = view.findViewById(R.id.tv_selected_room);
        tvRoomHint = view.findViewById(R.id.tv_room_hint);
        tvPhotoHint = view.findViewById(R.id.tv_photo_hint);
        btnTakePhoto = view.findViewById(R.id.btn_take_photo);
        btnSubmitAll = view.findViewById(R.id.btn_submit_all);
        rvCached = view.findViewById(R.id.rv_cached);
        tvEmptyCached = view.findViewById(R.id.tv_empty_cached);
        tvNetworkHint = view.findViewById(R.id.tv_network_hint);

        rvCached.setLayoutManager(new LinearLayoutManager(getContext()));

        // 演示：未选择房间时按钮置灰
        btnTakePhoto.setEnabled(false);
        tvRoomHint.setText("请先选择抄表房间");
        tvPhotoHint.setText("请先选择抄表房间");

        // 选择房间点击（弹出底部抽屉）
        tvSelectedRoom.setOnClickListener(v -> {
            // TODO: 弹出底部房间选择器
        });

        // 拍照按钮
        btnTakePhoto.setOnClickListener(v -> {
            // TODO: 调用相机
        });

        // 批量提交
        btnSubmitAll.setOnClickListener(v -> {
            // TODO: 批量上传
        });

        // 演示缓存列表（空状态）
        showEmptyCached();

        return view;
    }

    private void showEmptyCached() {
        tvEmptyCached.setVisibility(View.VISIBLE);
        rvCached.setVisibility(View.GONE);
    }

    private void showCachedList(List<CachedRecord> list) {
        tvEmptyCached.setVisibility(View.GONE);
        rvCached.setVisibility(View.VISIBLE);
        // 设置适配器
    }

    static class CachedRecord {
        String room, reading, time;
        // 缩略图路径等
    }
}
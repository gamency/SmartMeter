package com.example.smartmeter;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MeterInputFragment extends Fragment {

    private static final String TAG = "MeterInput";
    private static final String BASE_URL = Config.BASE_URL;
    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_PERMISSION = 101;
    private static final String PHOTO_FILE_PROVIDER = "com.example.smartmeter.fileprovider";

    // SharedPreferences 缓存
    private static final String PREFS_NAME = "smart_meter_prefs";
    private static final String KEY_ROOM_LIST = "room_list";

    // ===== 新布局控件 =====
    private TextView tvSelectedRoom, tvSelectedType, tvProgressLight;
    private TextView tvCameraHint, tvCameraSub, tvNetworkStatus, tvCountBadge;
    private LinearLayout llCamera;
    private Button btnTypeElectric, btnTypeCold, btnTypeHot;
    private TextView btnClearAll;
    private RecyclerView rvCached;
    private TextView tvEmptyCached;
    private Button btnSubmitAll;

    // ===== 状态变量 =====
    private int selectedRoomId = -1;
    private String selectedRoomName = "";
    private int selectedTypeIndex = 0;
    private List<CachedRecord> cachedList = new ArrayList<>();
    private CachedRecordAdapter cachedAdapter;
    private List<RoomItem> cachedRoomList = new ArrayList<>(); // 改为全局 RoomItem
    private String currentPhotoPath;

    // ===== 常量 =====
    private static final String[] TYPE_LABELS = {"⚡电表", "💧冷水", "🔥热水"};
    private static final String[] TYPE_VALUES = {"electric", "cold_water", "hot_water"};

    // ================================================================
    // 生命周期
    // ================================================================
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_meter_input, container, false);

        // ===== 初始化控件 =====
        tvSelectedRoom = view.findViewById(R.id.tv_selected_room);
        tvSelectedType = view.findViewById(R.id.tv_selected_type);
        tvProgressLight = view.findViewById(R.id.tv_progress_light);
        tvCameraHint = view.findViewById(R.id.tv_camera_hint);
        tvCameraSub = view.findViewById(R.id.tv_camera_sub);
        tvNetworkStatus = view.findViewById(R.id.tv_network_status);
        tvCountBadge = view.findViewById(R.id.tv_count_badge);
        llCamera = view.findViewById(R.id.ll_camera);
        btnTypeElectric = view.findViewById(R.id.btn_type_electric);
        btnTypeCold = view.findViewById(R.id.btn_type_cold);
        btnTypeHot = view.findViewById(R.id.btn_type_hot);
        btnClearAll = view.findViewById(R.id.btn_clear_all);
        rvCached = view.findViewById(R.id.rv_cached);
        tvEmptyCached = view.findViewById(R.id.tv_empty_cached);
        btnSubmitAll = view.findViewById(R.id.btn_submit_all);

        // ===== 设置 RecyclerView =====
        rvCached.setLayoutManager(new LinearLayoutManager(getContext()));
        cachedAdapter = new CachedRecordAdapter(cachedList, record -> {
            cachedList.remove(record);
            cachedAdapter.notifyDataSetChanged();
            updateUI();
            checkNetworkStatus();
        });
        rvCached.setAdapter(cachedAdapter);

        // ===== 点击事件 =====
        tvSelectedRoom.setOnClickListener(v -> showRoomSelectorDialog());
        llCamera.setOnClickListener(v -> checkPermissionAndTakePhoto());

        btnTypeElectric.setOnClickListener(v -> selectType(0));
        btnTypeCold.setOnClickListener(v -> selectType(1));
        btnTypeHot.setOnClickListener(v -> selectType(2));

        btnSubmitAll.setOnClickListener(v -> submitAllCached());

        btnClearAll.setOnClickListener(v -> {
            if (!cachedList.isEmpty()) {
                new AlertDialog.Builder(getContext())
                        .setTitle("确认清空")
                        .setMessage("确定要删除所有缓存记录吗？")
                        .setPositiveButton("清空", (d, w) -> {
                            cachedList.clear();
                            cachedAdapter.notifyDataSetChanged();
                            updateUI();
                            checkNetworkStatus();
                            Toast.makeText(getContext(), "已清空所有缓存", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        // ===== 加载数据 =====
        loadRoomListFromCache();
        loadRoomListFromNetwork();
        loadCachedRecords();
        updateUI();
        checkNetworkStatus();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCachedRecords();
        updateUI();
        checkNetworkStatus();
    }

    // ================================================================
    // 表型选择
    // ================================================================
    private void selectType(int index) {
        selectedTypeIndex = index;
        Button[] buttons = {btnTypeElectric, btnTypeCold, btnTypeHot};
        int[] bgColors = {0xFF4A6CF7, 0xFF22C55E, 0xFFF59E0B};
        for (int i = 0; i < buttons.length; i++) {
            if (i == index) {
                buttons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColors[i]));
                buttons[i].setTextColor(Color.WHITE);
            } else {
                buttons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE5E7EB));
                buttons[i].setTextColor(0xFF9CA3AF);
            }
        }
        tvSelectedType.setText(TYPE_LABELS[index]);
    }

    // ================================================================
    // UI 更新
    // ================================================================
    private void updateUI() {
        int count = cachedList.size();
        tvCountBadge.setText(String.valueOf(count));
        tvProgressLight.setText("已拍 " + count);

        if (count > 0) {
            btnSubmitAll.setEnabled(true);
            btnSubmitAll.setText("同步 " + count + " 条");
            btnClearAll.setVisibility(View.VISIBLE);
            tvCameraSub.setText("已拍 " + count + " 张，点击同步上传");
        } else {
            btnSubmitAll.setEnabled(false);
            btnSubmitAll.setText("同步 0 条");
            btnClearAll.setVisibility(View.GONE);
            tvCameraSub.setText("");
        }
        if (cachedList.isEmpty()) {
            tvEmptyCached.setVisibility(View.VISIBLE);
            rvCached.setVisibility(View.GONE);
        } else {
            tvEmptyCached.setVisibility(View.GONE);
            rvCached.setVisibility(View.VISIBLE);
        }
    }

    private void checkNetworkStatus() {
        tvNetworkStatus.setText("📶 在线");
        tvNetworkStatus.setTextColor(ResourcesCompat.getColor(getResources(), R.color.success_green, null));
    }

    // ================================================================
    // 房间列表（缓存 + 网络）
    // ================================================================
    private void loadRoomListFromCache() {
        if (getActivity() == null) return;
        SharedPreferences prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonData = prefs.getString(KEY_ROOM_LIST, null);
        if (jsonData != null) {
            try {
                JSONArray jsonArray = new JSONArray(jsonData);
                List<RoomItem> rooms = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    rooms.add(new RoomItem(
                            obj.getInt("id"),
                            obj.getString("name"),
                            obj.getInt("floor"),
                            obj.getString("room_type"),
                            0,
                            0.0
                    ));
                }
                cachedRoomList = rooms;
                if (!rooms.isEmpty()) {
                    selectedRoomId = rooms.get(0).getId();
                    selectedRoomName = rooms.get(0).getName();
                    tvSelectedRoom.setText(selectedRoomName);
                    llCamera.setEnabled(true);
                    tvCameraHint.setText("点击拍照");
                } else {
                    tvSelectedRoom.setText("暂无房间");
                    llCamera.setEnabled(false);
                }
            } catch (Exception e) {
                Log.e(TAG, "解析缓存房间列表失败", e);
            }
        } else {
            tvSelectedRoom.setText("加载中...");
            llCamera.setEnabled(false);
        }
    }

    private void loadRoomListFromNetwork() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .build();
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/smart/rooms")
                        .get()
                        .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    if (getActivity() != null) {
                        getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit().putString(KEY_ROOM_LIST, jsonData).apply();
                        JSONArray jsonArray = new JSONArray(jsonData);
                        List<RoomItem> rooms = new ArrayList<>();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject obj = jsonArray.getJSONObject(i);
                            rooms.add(new RoomItem(
                                    obj.getInt("id"),
                                    obj.getString("name"),
                                    obj.getInt("floor"),
                                    obj.getString("room_type"),
                                    0,
                                    0.0
                            ));
                        }
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                cachedRoomList = rooms;
                                if (!rooms.isEmpty()) {
                                    selectedRoomId = rooms.get(0).getId();
                                    selectedRoomName = rooms.get(0).getName();
                                    tvSelectedRoom.setText(selectedRoomName);
                                    llCamera.setEnabled(true);
                                    tvCameraHint.setText("点击拍照");
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "网络获取房间列表失败，使用缓存");
            }
        }).start();
    }

    // ================================================================
    // 房间选择对话框（BottomSheet）
    // ================================================================
    private void showRoomSelectorDialog() {
        if (cachedRoomList.isEmpty()) {
            Toast.makeText(getContext(), "无房间列表，请连接网络加载", Toast.LENGTH_SHORT).show();
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_room_selector, null);
        RecyclerView recyclerView = sheetView.findViewById(R.id.rv_rooms);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        RoomSelectorAdapter adapter = new RoomSelectorAdapter(cachedRoomList, room -> {
            selectedRoomId = room.getId();
            selectedRoomName = room.getName();
            tvSelectedRoom.setText(selectedRoomName);
            dialog.dismiss();
        });
        recyclerView.setAdapter(adapter);

        dialog.setContentView(sheetView);
        dialog.show();
    }

    // ================================================================
    // 缓存记录加载
    // ================================================================
    private void loadCachedRecords() {
        updateUI();
    }

    // ================================================================
    // 拍照相关
    // ================================================================
    private void checkPermissionAndTakePhoto() {
        if (selectedRoomId == -1) {
            Toast.makeText(getContext(), "请先选择房间", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION);
        } else {
            dispatchTakePictureIntent();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(getContext(), "需要相机权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
                Toast.makeText(getContext(), "创建文件失败", Toast.LENGTH_SHORT).show();
                return;
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(getContext(),
                        PHOTO_FILE_PROVIDER, photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_CAMERA);
            }
        } else {
            Toast.makeText(getContext(), "无法启动相机", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private String compressImageToBase64(String imagePath) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);

            int sampleSize = 1;
            while (options.outWidth / sampleSize > 800) {
                sampleSize *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAMERA && resultCode == getActivity().RESULT_OK) {
            String base64Image = compressImageToBase64(currentPhotoPath);
            if (base64Image == null) {
                Toast.makeText(getContext(), "图片处理失败", Toast.LENGTH_SHORT).show();
                return;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.CHINA);
            Date now = new Date();

            int hour = now.getHours();
            int pointId;
            String timeLabel;
            if (hour >= 0 && hour < 5) { pointId = 1; timeLabel = "夜间"; }
            else if (hour >= 5 && hour < 11) { pointId = 2; timeLabel = "上午"; }
            else if (hour >= 11 && hour < 13) { pointId = 3; timeLabel = "中午"; }
            else if (hour >= 13 && hour < 16) { pointId = 4; timeLabel = "下午"; }
            else if (hour >= 16 && hour < 19) { pointId = 5; timeLabel = "傍晚"; }
            else if (hour >= 19 && hour < 22) { pointId = 6; timeLabel = "晚上"; }
            else { pointId = 7; timeLabel = "深夜"; }

            String resourceType = TYPE_VALUES[selectedTypeIndex];

            CachedRecord record = new CachedRecord(
                    selectedRoomId,
                    selectedRoomName,
                    sdf.format(now),
                    timeSdf.format(now),
                    pointId,
                    timeLabel,
                    base64Image,
                    null,
                    false,
                    "pending",
                    resourceType
            );
            cachedList.add(0, record);
            cachedAdapter.notifyDataSetChanged();
            updateUI();
            checkNetworkStatus();
            Toast.makeText(getContext(), "📸 已保存 (" + TYPE_LABELS[selectedTypeIndex] + ")", Toast.LENGTH_SHORT).show();

            if (currentPhotoPath != null) {
                new File(currentPhotoPath).delete();
            }
        }
    }

    // ================================================================
    // 批量提交
    // ================================================================
    private void submitAllCached() {
        if (cachedList.isEmpty()) {
            Toast.makeText(getContext(), "没有待提交数据", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<CachedRecord> toSubmit = new ArrayList<>(cachedList);
        final int totalCount = toSubmit.size();

        JSONArray recordsArray = new JSONArray();
        for (CachedRecord record : toSubmit) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("local_id", UUID.randomUUID().toString());
                obj.put("room_id", record.roomId);
                obj.put("read_date", record.readDate);
                obj.put("read_time", record.readTime);
                obj.put("point_id", record.pointId);
                obj.put("time_label", record.timeLabel);
                obj.put("resource_type", record.resourceType != null ? record.resourceType : "electric");
                obj.put("photo_base64", "data:image/jpeg;base64," + record.photoBase64);
                obj.put("manual_reading", JSONObject.NULL);
                obj.put("status", "pending_ocr");
                recordsArray.put(obj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        btnSubmitAll.setEnabled(false);
        btnSubmitAll.setText("⏳ 提交中...");

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build();

                JSONObject requestBody = new JSONObject();
                requestBody.put("records", recordsArray);

                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        requestBody.toString()
                );

                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/smart/batch_upload")
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body() != null ? response.body().string() : "空响应";

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            try {
                                JSONObject result = new JSONObject(responseBody);
                                String batchId = result.optString("batch_id");
                                int count = result.optInt("uploaded_count", 0);
                                String msg = result.optString("message", "提交成功");

                                Toast.makeText(getContext(),
                                        "✅ " + msg + "\n批次: " + batchId.substring(0, 8) + "...",
                                        Toast.LENGTH_LONG).show();

                                cachedList.clear();
                                cachedAdapter.notifyDataSetChanged();
                                updateUI();
                                checkNetworkStatus();

                            } catch (Exception e) {
                                Toast.makeText(getContext(), "解析响应失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                btnSubmitAll.setEnabled(true);
                                btnSubmitAll.setText("同步 " + cachedList.size() + " 条");
                            }
                        } else {
                            Toast.makeText(getContext(), "❌ 提交失败: HTTP " + response.code() + "\n" + responseBody,
                                    Toast.LENGTH_LONG).show();
                            btnSubmitAll.setEnabled(true);
                            btnSubmitAll.setText("同步 " + cachedList.size() + " 条");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "❌ 提交异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        btnSubmitAll.setEnabled(true);
                        btnSubmitAll.setText("同步 " + cachedList.size() + " 条");
                    });
                }
            }
        }).start();
    }

    // ================================================================
    // 内部数据类（只保留 CachedRecord）
    // ================================================================
    static class CachedRecord {
        int roomId;
        String roomName;
        String readDate;
        String readTime;
        int pointId;
        String timeLabel;
        String photoBase64;
        String manualReading;
        boolean synced;
        String status;
        String resourceType;

        CachedRecord(int roomId, String roomName, String readDate, String readTime,
                     int pointId, String timeLabel, String photoBase64,
                     String manualReading, boolean synced, String status, String resourceType) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.readDate = readDate;
            this.readTime = readTime;
            this.pointId = pointId;
            this.timeLabel = timeLabel;
            this.photoBase64 = photoBase64;
            this.manualReading = manualReading;
            this.synced = synced;
            this.status = status;
            this.resourceType = resourceType;
        }
    }

    // ================================================================
    // 缓存记录适配器
    // ================================================================
    static class CachedRecordAdapter extends RecyclerView.Adapter<CachedRecordAdapter.ViewHolder> {

        private List<CachedRecord> list;
        private OnDeleteListener deleteListener;

        interface OnDeleteListener {
            void onDelete(CachedRecord record);
        }

        CachedRecordAdapter(List<CachedRecord> list, OnDeleteListener deleteListener) {
            this.list = list;
            this.deleteListener = deleteListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_cached_record, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CachedRecord item = list.get(position);
            holder.tvRoom.setText(item.roomName);
            String typeLabel;
            if ("cold_water".equals(item.resourceType)) typeLabel = "💧冷水";
            else if ("hot_water".equals(item.resourceType)) typeLabel = "🔥热水";
            else typeLabel = "⚡电表";
            holder.tvReading.setText(typeLabel);
            holder.tvTime.setText(item.readDate + " " + item.readTime);
            holder.tvStatus.setText("未上传");
            holder.itemView.setOnLongClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onDelete(item);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvRoom, tvReading, tvTime, tvStatus;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRoom = itemView.findViewById(R.id.tv_room);
                tvReading = itemView.findViewById(R.id.tv_reading);
                tvTime = itemView.findViewById(R.id.tv_time);
                tvStatus = itemView.findViewById(R.id.tv_status);
            }
        }
    }
}

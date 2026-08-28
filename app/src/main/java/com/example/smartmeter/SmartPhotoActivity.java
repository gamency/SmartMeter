package com.example.smartmeter;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SmartPhotoActivity extends AppCompatActivity {

    private static final String TAG = "SmartPhoto";
    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_FILE_PICKER = 101;
    private static final int REQUEST_PERMISSION = 102;
    private static final String PHOTO_FILE_PROVIDER = "com.example.smartmeter.fileprovider";

    // SharedPreferences 缓存
    private static final String PREFS_NAME = "smart_meter_prefs";
    private static final String KEY_ROOM_LIST = "room_list_cache";
    private static final String KEY_MODE = "mode_preference";
    private static final String KEY_CONTINUOUS = "continuous_preference";
    private static final String KEY_AUTO_TYPE = "auto_type_preference";

    // 模式常量
    private static final int MODE_BY_ROOM = 0;
    private static final int MODE_BY_TYPE = 1;

    // ===== 新布局控件 =====
    private TextView tvSelectedRoom, tvSelectedType, tvProgressLight;
    private TextView tvCameraHint, tvCameraSub, tvNetworkStatus, tvCountBadge;
    private LinearLayout llCamera;
    private Button btnTypeElectric, btnTypeCold, btnTypeHot;
    private TextView btnClearAll;
    private RecyclerView pendingRecyclerView;   // 修改：与布局ID一致
    private TextView tvEmptyCached;
    private Button btnSubmitAll;

    // ===== 状态变量 =====
    private SmartReadingDatabase db;
    private List<RoomItem> roomList = new ArrayList<>();
    private List<CachedRecord> cachedList = new ArrayList<>();
    private CachedRecordAdapter cachedAdapter;
    private String currentPhotoPath;
    private int selectedRoomId = -1;
    private String selectedRoomName = "";
    private int selectedTypeIndex = 0; // 0=电, 1=冷水, 2=热水
    private int currentMode = MODE_BY_TYPE;
    private boolean isContinuous = true;
    private boolean isAutoType = false;

    // 资源类型常量
    private static final String[] TYPE_LABELS = {"⚡电表", "💧冷水", "🔥热水"};
    private static final String[] TYPE_VALUES = {"electric", "cold_water", "hot_water"};
    private static final int[] TYPE_COLORS = {0xFF4A6CF7, 0xFF22C55E, 0xFFF59E0B};

    // ================================================================
    // 生命周期
    // ================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_photo);

        // 状态栏颜色
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getColor(R.color.primary_blue));
            getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        db = SmartReadingDatabase.getInstance(this);

        // ===== 初始化新布局控件 =====
        tvSelectedRoom = findViewById(R.id.tv_selected_room);
        tvSelectedType = findViewById(R.id.tv_selected_type);
        tvProgressLight = findViewById(R.id.tv_progress_light);
        tvCameraHint = findViewById(R.id.tv_camera_hint);
        tvCameraSub = findViewById(R.id.tv_camera_sub);
        tvNetworkStatus = findViewById(R.id.tv_network_status);
        tvCountBadge = findViewById(R.id.tv_count_badge);
        llCamera = findViewById(R.id.ll_camera);
        btnTypeElectric = findViewById(R.id.btn_type_electric);
        btnTypeCold = findViewById(R.id.btn_type_cold);
        btnTypeHot = findViewById(R.id.btn_type_hot);
        btnClearAll = findViewById(R.id.btn_clear_all);
        // 修正：使用 pendingRecyclerView
        pendingRecyclerView = findViewById(R.id.pendingRecyclerView);
        tvEmptyCached = findViewById(R.id.tv_empty_cached);
        btnSubmitAll = findViewById(R.id.btn_submit_all);

        // ===== 设置 RecyclerView =====
        pendingRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        cachedAdapter = new CachedRecordAdapter(cachedList, record -> {
            cachedList.remove(record);
            cachedAdapter.notifyDataSetChanged();
            updateUI();
        });
        pendingRecyclerView.setAdapter(cachedAdapter);

        // ===== 加载偏好 =====
        loadPreferences();

        // ===== 默认选中电表 =====
        selectType(0);

        // ===== 点击事件 =====
        tvSelectedRoom.setOnClickListener(v -> showRoomSelectorDialog());
        llCamera.setOnClickListener(v -> checkPermissionAndTakePhoto());

        btnTypeElectric.setOnClickListener(v -> selectType(0));
        btnTypeCold.setOnClickListener(v -> selectType(1));
        btnTypeHot.setOnClickListener(v -> selectType(2));

        btnSubmitAll.setOnClickListener(v -> syncRecords());

        btnClearAll.setOnClickListener(v -> {
            if (!cachedList.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("确认清空")
                        .setMessage("确定要删除所有缓存记录吗？")
                        .setPositiveButton("清空", (d, w) -> {
                            cachedList.clear();
                            cachedAdapter.notifyDataSetChanged();
                            updateUI();
                            Toast.makeText(this, "已清空所有缓存", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        // ===== 加载房间列表 =====
        loadRoomListFromCache();
        loadRoomListFromNetwork();

        // ===== 加载本地缓存记录 =====
        loadCachedRecords();
        updateUI();
        checkNetworkStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCachedRecords();
        updateUI();
        checkNetworkStatus();
    }

    // ================================================================
    // 偏好存储
    // ================================================================
    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentMode = prefs.getInt(KEY_MODE, MODE_BY_TYPE);
        isContinuous = prefs.getBoolean(KEY_CONTINUOUS, true);
        isAutoType = prefs.getBoolean(KEY_AUTO_TYPE, false);
        // 更新模式UI（目前没有模式切换按钮在SmartPhotoActivity，但保留状态）
    }

    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_MODE, currentMode)
                .putBoolean(KEY_CONTINUOUS, isContinuous)
                .putBoolean(KEY_AUTO_TYPE, isAutoType)
                .apply();
    }

    // ================================================================
    // 表型选择
    // ================================================================
    private void selectType(int index) {
        selectedTypeIndex = index;
        Button[] buttons = {btnTypeElectric, btnTypeCold, btnTypeHot};
        for (int i = 0; i < buttons.length; i++) {
            if (i == index) {
                buttons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(TYPE_COLORS[i]));
                buttons[i].setTextColor(Color.WHITE);
            } else {
                buttons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE5E7EB));
                buttons[i].setTextColor(0xFF9CA3AF);
            }
        }
        tvSelectedType.setText(TYPE_LABELS[index]);
    }

    private int getCurrentTypePosition() {
        return selectedTypeIndex;
    }

    private String getCurrentResourceType() {
        return TYPE_VALUES[selectedTypeIndex];
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
            pendingRecyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyCached.setVisibility(View.GONE);
            pendingRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void checkNetworkStatus() {
        // 简单显示在线
        tvNetworkStatus.setText("📶 在线");
        tvNetworkStatus.setTextColor(ResourcesCompat.getColor(getResources(), R.color.success_green, null));
    }

    // ================================================================
    // 房间列表（缓存 + 网络）
    // ================================================================
    private void loadRoomListFromCache() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_ROOM_LIST, null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                List<RoomItem> cachedRooms = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    RoomItem item = new RoomItem(
                            obj.getInt("id"),
                            obj.getString("name"),
                            obj.getInt("floor"),
                            obj.getString("room_type"),
                            0,
                            obj.optDouble("price", 0)
                    );
                    cachedRooms.add(item);
                }
                if (!cachedRooms.isEmpty()) {
                    roomList = cachedRooms;
                    if (!roomList.isEmpty()) {
                        selectedRoomId = roomList.get(0).getId();
                        selectedRoomName = roomList.get(0).getName();
                        tvSelectedRoom.setText(selectedRoomName);
                        llCamera.setEnabled(true);
                        tvCameraHint.setText("点击拍照");
                    }
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
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(3, TimeUnit.SECONDS)
                        .build();
                Request request = new Request.Builder()
                        .url(Config.BASE_URL + "/api/smart/rooms")
                        .get()
                        .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    saveRoomListToCache(jsonData);
                    parseAndSetRooms(jsonData);
                }
            } catch (Exception e) {
                Log.w(TAG, "网络获取房间列表失败，使用缓存");
            }
        }).start();
    }

    private void saveRoomListToCache(String jsonData) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_ROOM_LIST, jsonData).apply();
    }

    private void parseAndSetRooms(String jsonData) {
        try {
            JSONArray jsonArray = new JSONArray(jsonData);
            List<RoomItem> newRooms = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                newRooms.add(new RoomItem(
                        obj.getInt("id"),
                        obj.getString("name"),
                        obj.getInt("floor"),
                        obj.getString("room_type"),
                        0,
                        obj.optDouble("price", 0)
                ));
            }
            if (!newRooms.isEmpty()) {
                runOnUiThread(() -> {
                    roomList = newRooms;
                    if (!roomList.isEmpty()) {
                        selectedRoomId = roomList.get(0).getId();
                        selectedRoomName = roomList.get(0).getName();
                        tvSelectedRoom.setText(selectedRoomName);
                        llCamera.setEnabled(true);
                        tvCameraHint.setText("点击拍照");
                    }
                    Toast.makeText(this, "已更新房间列表", Toast.LENGTH_SHORT).show();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "解析房间列表失败", e);
        }
    }

    // ================================================================
    // 房间选择对话框（BottomSheet）
    // ================================================================
    private void showRoomSelectorDialog() {
        if (roomList.isEmpty()) {
            Toast.makeText(this, "无房间列表，请连接网络加载", Toast.LENGTH_SHORT).show();
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_room_selector, null);
        RecyclerView recyclerView = sheetView.findViewById(R.id.rv_rooms);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        RoomSelectorAdapter adapter = new RoomSelectorAdapter(roomList, room -> {
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
        // 从内存加载（拍照后已添加到cachedList）
        // 无需额外操作
        updateUI();
    }

    // ================================================================
    // 拍照相关
    // ================================================================
    private void checkPermissionAndTakePhoto() {
        if (selectedRoomId == -1) {
            Toast.makeText(this, "请先选择房间", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION);
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
                Toast.makeText(this, "需要相机和存储权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            File photoFile = createImageFile();
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this, PHOTO_FILE_PROVIDER, photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_CAMERA);
            } else {
                openGallery();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "无法启动相机，切换到相册选择", Toast.LENGTH_SHORT).show();
            openGallery();
        }
    }

    private void openGallery() {
        Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickIntent.setType("image/*");
        startActivityForResult(Intent.createChooser(pickIntent, "选择电表照片"), REQUEST_FILE_PICKER);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        String photoPath = null;
        if (requestCode == REQUEST_CAMERA) {
            photoPath = currentPhotoPath;
        } else if (requestCode == REQUEST_FILE_PICKER) {
            if (data != null && data.getData() != null) {
                Uri selectedUri = data.getData();
                try {
                    String[] projection = {MediaStore.Images.Media.DATA};
                    Cursor cursor = getContentResolver().query(selectedUri, projection, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                        photoPath = cursor.getString(columnIndex);
                        cursor.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        if (photoPath == null || photoPath.isEmpty()) {
            Toast.makeText(this, "未获取到图片", Toast.LENGTH_SHORT).show();
            return;
        }

        // 如果是水表，弹出校验对话框
        String resourceType = getCurrentResourceType();
        if (!resourceType.equals("electric")) {
            showWaterMeterValidationDialog(photoPath);
        } else {
            saveReading(photoPath, null);
        }
    }

    // ================================================================
    // 水表校验对话框
    // ================================================================
    private void showWaterMeterValidationDialog(String photoPath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔍 水表读数确认");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("请输入读数（保留1位小数）");
        input.setInputType(android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        builder.setView(input);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String val = input.getText().toString().trim();
            if (val.isEmpty()) {
                Toast.makeText(this, "请手动输入读数", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                double reading = Double.parseDouble(val);
                saveReading(photoPath, reading);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("跳过", (dialog, which) -> {
            saveReading(photoPath, null);
        });

        builder.setMessage("水表读数可能包含小数（如123.4），请手动输入读数。\n如果选择「跳过」，系统将尝试AI识别。");
        builder.show();
    }

    // ================================================================
    // 保存读数到本地缓存（内存）
    // ================================================================
    private void saveReading(String photoPath, Double manualValue) {
        Date now = new Date();
        SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.CHINA);

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

        String resourceType = getCurrentResourceType();

        CachedRecord record = new CachedRecord(
                selectedRoomId,
                selectedRoomName,
                dateSdf.format(now),
                timeSdf.format(now),
                pointId,
                timeLabel,
                photoPath,
                manualValue,
                false,
                "pending",
                resourceType
        );
        cachedList.add(0, record);
        cachedAdapter.notifyDataSetChanged();
        updateUI();
        Toast.makeText(this, "📸 已保存 (" + TYPE_LABELS[selectedTypeIndex] + ")", Toast.LENGTH_SHORT).show();

        // 如果连续拍照开启，则自动推进
        if (isContinuous) {
            advanceToNext();
        }
    }

    // ================================================================
    // 连续拍照推进逻辑（简化，仅按表型模式推进同楼层下一个房间）
    // ================================================================
    private void advanceToNext() {
        if (roomList.isEmpty()) return;
        int roomPos = -1;
        for (int i = 0; i < roomList.size(); i++) {
            if (roomList.get(i).getId() == selectedRoomId) {
                roomPos = i;
                break;
            }
        }
        if (roomPos == -1) return;

        int currentFloor = roomList.get(roomPos).getFloor();
        int nextRoomPos = -1;
        // 找同楼层下一个房间
        for (int i = roomPos + 1; i < roomList.size(); i++) {
            if (roomList.get(i).getFloor() == currentFloor) {
                nextRoomPos = i;
                break;
            }
        }
        if (nextRoomPos != -1) {
            // 跳到下一个房间，保持当前表型
            selectedRoomId = roomList.get(nextRoomPos).getId();
            selectedRoomName = roomList.get(nextRoomPos).getName();
            tvSelectedRoom.setText(selectedRoomName);
            Snackbar.make(findViewById(android.R.id.content),
                    "📌 " + selectedRoomName + " → " + TYPE_LABELS[selectedTypeIndex],
                    Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(findViewById(android.R.id.content),
                    "✅ " + currentFloor + "楼 " + TYPE_LABELS[selectedTypeIndex] + " 已全部拍完",
                    Snackbar.LENGTH_LONG).show();
        }
    }

    // ================================================================
    // 同步功能（批量提交）
    // ================================================================
    private void syncRecords() {
        if (cachedList.isEmpty()) {
            Toast.makeText(this, "没有待同步的记录", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<CachedRecord> toSubmit = new ArrayList<>(cachedList);

        JSONArray recordsArray = new JSONArray();
        for (CachedRecord record : toSubmit) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("local_id", String.valueOf(System.currentTimeMillis()));
                obj.put("room_id", record.roomId);
                obj.put("read_date", record.readDate);
                obj.put("read_time", record.readTime);
                obj.put("point_id", record.pointId);
                obj.put("time_label", record.timeLabel);
                obj.put("resource_type", record.resourceType != null ? record.resourceType : "electric");
                obj.put("manual_reading", record.manualReading != null ? record.manualReading : JSONObject.NULL);
                // 这里我们需要将本地照片转为Base64，但record中存储的是路径，需读取文件
                File file = new File(record.photoPath);
                if (file.exists()) {
                    byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                    String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT);
                    obj.put("photo_base64", "data:image/jpeg;base64," + base64);
                } else {
                    obj.put("photo_base64", JSONObject.NULL);
                }
                recordsArray.put(obj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        btnSubmitAll.setEnabled(false);
        btnSubmitAll.setText("⏳ 同步中...");

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build();
                JSONObject requestBody = new JSONObject();
                requestBody.put("records", recordsArray);
                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        requestBody.toString()
                );
                Request request = new Request.Builder()
                        .url(Config.BASE_URL + "/api/smart/batch_sync")
                        .post(body)
                        .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    JSONObject result = new JSONObject(jsonData);
                    String batchId = result.getString("batch_id");
                    JSONArray resultsArray = result.getJSONArray("results");

                    runOnUiThread(() -> {
                        Toast.makeText(this, "✅ 同步成功！共 " + resultsArray.length() + " 条", Toast.LENGTH_SHORT).show();
                        // 清空缓存
                        cachedList.clear();
                        cachedAdapter.notifyDataSetChanged();
                        updateUI();
                        // 跳转到复核界面
                        Intent intent = new Intent(SmartPhotoActivity.this, SmartReviewActivity.class);
                        intent.putExtra("batchId", batchId);
                        startActivity(intent);
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "同步失败: " + response.message(), Toast.LENGTH_SHORT).show();
                        btnSubmitAll.setEnabled(true);
                        btnSubmitAll.setText("同步 " + cachedList.size() + " 条");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "同步异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSubmitAll.setEnabled(true);
                    btnSubmitAll.setText("同步 " + cachedList.size() + " 条");
                });
            }
        }).start();
    }

    // ================================================================
    // 内部类：缓存记录
    // ================================================================
    static class CachedRecord {
        int roomId;
        String roomName;
        String readDate;
        String readTime;
        int pointId;
        String timeLabel;
        String photoPath;
        Double manualReading;
        boolean synced;
        String status;
        String resourceType;

        CachedRecord(int roomId, String roomName, String readDate, String readTime,
                     int pointId, String timeLabel, String photoPath,
                     Double manualReading, boolean synced, String status, String resourceType) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.readDate = readDate;
            this.readTime = readTime;
            this.pointId = pointId;
            this.timeLabel = timeLabel;
            this.photoPath = photoPath;
            this.manualReading = manualReading;
            this.synced = synced;
            this.status = status;
            this.resourceType = resourceType;
        }
    }

    // ================================================================
    // 内部类：适配器
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理临时文件
    }
}

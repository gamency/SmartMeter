package com.example.smartmeter;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    // SharedPreferences 缓存 Key
    private static final String PREFS_NAME = "smart_meter_prefs";
    private static final String KEY_ROOM_LIST = "room_list_cache";
    private static final String KEY_MODE = "mode_preference";
    private static final String KEY_CONTINUOUS = "continuous_preference";
    private static final String KEY_AUTO_TYPE = "auto_type_preference";

    // 模式常量
    private static final int MODE_BY_ROOM = 0;
    private static final int MODE_BY_TYPE = 1;

    // 视图变量
    private Spinner roomSpinner;
    private TextView lastReadingText;
    private RecyclerView pendingRecyclerView;
    private Button takePhotoBtn;
    private Button syncBtn;

    // 新增控件
    private TextView tvCurrentRoom, tvCurrentType, tvProgress;
    private Button btnElectric, btnColdWater, btnHotWater;
    private Button btnModeRoom, btnModeType;
    private Button btnContinuous;
    private Button btnAutoType;
    private Button btnPrevRoom, btnNextRoom;  // 左右切换按钮

    private SmartReadingDatabase db;
    private List<RoomItem> roomList = new ArrayList<>();
    private String currentPhotoPath;
    private int selectedRoomId = -1;
    private String selectedResourceType = "electric";

    // 状态
    private int currentMode = MODE_BY_TYPE;
    private boolean isContinuous = true;
    private boolean isAutoType = false;

    // 资源类型常量
    private static final String TYPE_ELECTRIC = "electric";
    private static final String TYPE_COLD_WATER = "cold_water";
    private static final String TYPE_HOT_WATER = "hot_water";
    private static final String[] TYPE_LABELS = {"⚡电表", "💧冷水", "🔥热水"};
    private static final String[] TYPE_VALUES = {TYPE_ELECTRIC, TYPE_COLD_WATER, TYPE_HOT_WATER};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_photo);

        db = SmartReadingDatabase.getInstance(this);

        // 初始化视图
        roomSpinner = findViewById(R.id.roomSpinner);
        lastReadingText = findViewById(R.id.lastReadingText);
        pendingRecyclerView = findViewById(R.id.pendingRecyclerView);
        takePhotoBtn = findViewById(R.id.takePhotoBtn);
        syncBtn = findViewById(R.id.syncBtn);

        tvCurrentRoom = findViewById(R.id.tv_current_room);
        tvCurrentType = findViewById(R.id.tv_current_type);
        tvProgress = findViewById(R.id.tv_progress);

        btnElectric = findViewById(R.id.btn_electric);
        btnColdWater = findViewById(R.id.btn_cold_water);
        btnHotWater = findViewById(R.id.btn_hot_water);

        btnModeRoom = findViewById(R.id.btn_mode_room);
        btnModeType = findViewById(R.id.btn_mode_type);

        btnContinuous = findViewById(R.id.btn_continuous);
        btnAutoType = findViewById(R.id.btn_auto_type);

        btnPrevRoom = findViewById(R.id.btn_prev_room);
        btnNextRoom = findViewById(R.id.btn_next_room);

        pendingRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 加载用户偏好
        loadPreferences();

        // 表型按钮点击
        btnElectric.setOnClickListener(v -> selectResourceType(0));
        btnColdWater.setOnClickListener(v -> selectResourceType(1));
        btnHotWater.setOnClickListener(v -> selectResourceType(2));

        // 模式切换
        btnModeRoom.setOnClickListener(v -> switchMode(MODE_BY_ROOM));
        btnModeType.setOnClickListener(v -> switchMode(MODE_BY_TYPE));

        // 连续拍照开关
        btnContinuous.setOnClickListener(v -> toggleContinuous());

        // 表型自动切换开关
        btnAutoType.setOnClickListener(v -> toggleAutoType());

        // 左右切换按钮
        btnPrevRoom.setOnClickListener(v -> switchRoom(-1));
        btnNextRoom.setOnClickListener(v -> switchRoom(1));

        // 拍照按钮
        takePhotoBtn.setOnClickListener(v -> checkPermissionAndTakePhoto());
        syncBtn.setOnClickListener(v -> syncRecords());

        // 加载房间列表（先缓存，后网络）
        loadRoomListFromCache();
        loadRoomListFromNetwork();

        loadPendingList();
        updateCurrentDisplay();
    }

    // ================================================================
    // 偏好存储
    // ================================================================
    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentMode = prefs.getInt(KEY_MODE, MODE_BY_TYPE);
        isContinuous = prefs.getBoolean(KEY_CONTINUOUS, true);
        isAutoType = prefs.getBoolean(KEY_AUTO_TYPE, false);
        updateModeUI();
        updateContinuousUI();
        updateAutoTypeUI();
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
    private void selectResourceType(int index) {
        selectedResourceType = TYPE_VALUES[index];
        resetTypeButtons();
        Button[] buttons = {btnElectric, btnColdWater, btnHotWater};
        buttons[index].setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4A6CF7));
        buttons[index].setTextColor(0xFFFFFFFF);
        tvCurrentType.setText(TYPE_LABELS[index]);
        updateLastReading(selectedRoomId);
        updateCurrentDisplay();
    }

    private void resetTypeButtons() {
        Button[] buttons = {btnElectric, btnColdWater, btnHotWater};
        for (Button btn : buttons) {
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE2E8F0));
            btn.setTextColor(0xFF64748B);
        }
    }

    // ================================================================
    // 模式切换
    // ================================================================
    private void switchMode(int mode) {
        if (currentMode == mode) return;
        currentMode = mode;
        updateModeUI();
        savePreferences();
        // 切换模式时，自动调整到合理位置
        if (mode == MODE_BY_TYPE) {
            int currentFloor = roomList.get(roomSpinner.getSelectedItemPosition()).getFloor();
            int firstRoom = findFirstRoomInFloor(currentFloor);
            if (firstRoom != -1) roomSpinner.setSelection(firstRoom);
        } else {
            selectResourceType(0);
        }
        updateCurrentDisplay();
        Snackbar.make(findViewById(android.R.id.content),
                "已切换到 " + (mode == MODE_BY_ROOM ? "按房间" : "按表型") + " 模式",
                Snackbar.LENGTH_SHORT).show();
    }

    private void updateModeUI() {
        if (currentMode == MODE_BY_ROOM) {
            btnModeRoom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4A6CF7));
            btnModeRoom.setTextColor(0xFFFFFFFF);
            btnModeType.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE2E8F0));
            btnModeType.setTextColor(0xFF64748B);
        } else {
            btnModeType.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4A6CF7));
            btnModeType.setTextColor(0xFFFFFFFF);
            btnModeRoom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE2E8F0));
            btnModeRoom.setTextColor(0xFF64748B);
        }
    }

    // ================================================================
    // 连续拍照开关
    // ================================================================
    private void toggleContinuous() {
        isContinuous = !isContinuous;
        updateContinuousUI();
        savePreferences();
        Snackbar.make(findViewById(android.R.id.content),
                "连续拍照 " + (isContinuous ? "已开启" : "已关闭"),
                Snackbar.LENGTH_SHORT).show();
    }

    private void updateContinuousUI() {
        if (isContinuous) {
            btnContinuous.setText("开");
            btnContinuous.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF22C55E));
            btnContinuous.setTextColor(0xFFFFFFFF);
        } else {
            btnContinuous.setText("关");
            btnContinuous.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444));
            btnContinuous.setTextColor(0xFFFFFFFF);
        }
    }

    // ================================================================
    // 表型自动切换开关
    // ================================================================
    private void toggleAutoType() {
        isAutoType = !isAutoType;
        updateAutoTypeUI();
        savePreferences();
        Snackbar.make(findViewById(android.R.id.content),
                "表型自动切换 " + (isAutoType ? "已开启" : "已关闭"),
                Snackbar.LENGTH_SHORT).show();
    }

    private void updateAutoTypeUI() {
        if (isAutoType) {
            btnAutoType.setText("开");
            btnAutoType.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF22C55E));
            btnAutoType.setTextColor(0xFFFFFFFF);
        } else {
            btnAutoType.setText("关");
            btnAutoType.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444));
            btnAutoType.setTextColor(0xFFFFFFFF);
        }
    }

    // ================================================================
    // 房间切换（左右箭头按钮）
    // ================================================================
    private void switchRoom(int direction) {
        if (roomList.isEmpty()) return;
        int currentPos = roomSpinner.getSelectedItemPosition();
        if (currentPos < 0) currentPos = 0;
        int newPos = (currentPos + direction) % roomList.size();
        if (newPos < 0) newPos = roomList.size() - 1;
        roomSpinner.setSelection(newPos);
        // 触发选中事件，由 OnItemSelectedListener 处理
        updateCurrentDisplay();
        Snackbar.make(findViewById(android.R.id.content),
                "切换到 " + roomList.get(newPos).getName(),
                Snackbar.LENGTH_SHORT).show();
    }

    // ================================================================
    // 房间列表（缓存 + 网络）
    // ================================================================
    private void loadRoomListFromCache() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
                    updateSpinner();
                    if (roomList.size() > 0) {
                        selectedRoomId = roomList.get(0).getId();
                        takePhotoBtn.setEnabled(true);
                        selectResourceType(0);
                        updateCurrentDisplay();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "解析缓存房间列表失败", e);
            }
        } else {
            takePhotoBtn.setEnabled(false);
            Toast.makeText(this, "暂无缓存，正在联网加载...", Toast.LENGTH_SHORT).show();
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
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
                    updateSpinner();
                    if (!roomList.isEmpty()) {
                        selectedRoomId = roomList.get(0).getId();
                        takePhotoBtn.setEnabled(true);
                        selectResourceType(0);
                        updateCurrentDisplay();
                    }
                    Toast.makeText(this, "已更新房间列表", Toast.LENGTH_SHORT).show();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "解析房间列表失败", e);
        }
    }

    // ================================================================
    // Spinner 更新
    // ================================================================
    private void updateSpinner() {
        if (roomList.isEmpty()) return;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                roomList.stream().map(RoomItem::getName).toArray(String[]::new));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roomSpinner.setAdapter(adapter);
        roomSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < roomList.size()) {
                    selectedRoomId = roomList.get(position).getId();
                    updateLastReading(selectedRoomId);
                    updateCurrentDisplay();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        if (!roomList.isEmpty()) {
            roomSpinner.setSelection(0);
            selectedRoomId = roomList.get(0).getId();
            updateCurrentDisplay();
        }
    }

    // ================================================================
    // 显示更新
    // ================================================================
    private void updateCurrentDisplay() {
        int roomPos = roomSpinner.getSelectedItemPosition();
        if (roomPos >= 0 && roomPos < roomList.size()) {
            String roomName = roomList.get(roomPos).getName();
            tvCurrentRoom.setText(roomName);
            tvProgress.setText((roomPos + 1) + "/" + roomList.size());
        } else {
            tvCurrentRoom.setText("未选择");
            tvProgress.setText("0/0");
        }
        // 表型已经由 selectResourceType 更新
    }

    private int getCurrentTypePosition() {
        for (int i = 0; i < TYPE_VALUES.length; i++) {
            if (TYPE_VALUES[i].equals(selectedResourceType)) return i;
        }
        return 0;
    }

    private void updateLastReading(int roomId) {
        lastReadingText.setText("上月: -- (" + getResourceLabel(selectedResourceType) + ")");
    }

    private String getResourceLabel(String type) {
        switch (type) {
            case TYPE_ELECTRIC: return "电表";
            case TYPE_COLD_WATER: return "冷水表";
            case TYPE_HOT_WATER: return "热水表";
            default: return "电表";
        }
    }

    // ================================================================
    // 待同步列表
    // ================================================================
    private void loadPendingList() {
        new Thread(() -> {
            List<SmartReadingEntity> pending = db.smartReadingDao().getPendingReadings();
            runOnUiThread(() -> {
                PendingAdapter adapter = new PendingAdapter(pending, this::deletePending);
                pendingRecyclerView.setAdapter(adapter);
            });
        }).start();
    }

    private void deletePending(SmartReadingEntity entity) {
        new Thread(() -> {
            db.smartReadingDao().delete(entity);
            loadPendingList();
        }).start();
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

        // 获取房间名
        String roomName = roomList.stream()
                .filter(r -> r.getId() == selectedRoomId)
                .findFirst()
                .map(RoomItem::getName)
                .orElse("");

        // 如果选择的是水表，弹出校验对话框
        if (!selectedResourceType.equals(TYPE_ELECTRIC)) {
            showWaterMeterValidationDialog(photoPath, roomName);
        } else {
            saveReading(photoPath, roomName, null);
        }
    }

    // ================================================================
    // 水表校验对话框
    // ================================================================
    private void showWaterMeterValidationDialog(String photoPath, String roomName) {
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
                saveReading(photoPath, roomName, reading);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("跳过", (dialog, which) -> {
            saveReading(photoPath, roomName, null);
        });

        builder.setMessage("水表读数可能包含小数（如123.4），请手动输入读数。\n如果选择「跳过」，系统将尝试AI识别。");
        builder.show();
    }

    // ================================================================
    // 保存读数到本地数据库
    // ================================================================
    private void saveReading(String photoPath, String roomName, Double manualValue) {
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

        boolean isManual = (manualValue != null);
        double manualReading = isManual ? manualValue : 0;

        SmartReadingEntity entity = new SmartReadingEntity(
                selectedRoomId,
                roomName,
                dateSdf.format(now),
                timeSdf.format(now),
                pointId,
                timeLabel,
                photoPath,
                manualReading,
                "pending",
                isManual,
                selectedResourceType
        );

        new Thread(() -> {
            db.smartReadingDao().insert(entity);
            runOnUiThread(() -> {
                Toast.makeText(this, "✅ 已保存 (" + getResourceLabel(selectedResourceType) + ")", Toast.LENGTH_SHORT).show();
                loadPendingList();
                // 如果连续拍照开启，则自动推进
                if (isContinuous) {
                    advanceToNext();
                }
            });
        }).start();
    }

    // ================================================================
    // 连续拍照推进逻辑（核心）
    // ================================================================
    private void advanceToNext() {
        if (roomList.isEmpty()) return;

        int roomPos = roomSpinner.getSelectedItemPosition();
        if (roomPos < 0 || roomPos >= roomList.size()) return;

        int typePos = getCurrentTypePosition();
        int currentFloor = roomList.get(roomPos).getFloor();

        if (currentMode == MODE_BY_ROOM) {
            // ===== 按房间模式：先推进表型，表型到头再推进同楼层下一个房间 =====
            if (typePos < TYPE_VALUES.length - 1) {
                // 同一房间，下一个表型
                selectResourceType(typePos + 1);
                Snackbar.make(findViewById(android.R.id.content),
                        "📌 " + roomList.get(roomPos).getName() + " → " + TYPE_LABELS[typePos + 1],
                        Snackbar.LENGTH_LONG).show();
            } else {
                // 表型到头，找同楼层下一个房间（不跨楼层）
                int nextRoomPos = findNextRoomInSameFloor(roomPos, currentFloor);
                if (nextRoomPos != -1) {
                    selectResourceType(0);
                    roomSpinner.setSelection(nextRoomPos);
                    Snackbar.make(findViewById(android.R.id.content),
                            "✅ " + roomList.get(roomPos).getName() + " 已完成，转到 " + roomList.get(nextRoomPos).getName(),
                            Snackbar.LENGTH_LONG).show();
                } else {
                    Snackbar.make(findViewById(android.R.id.content),
                            "✅ " + currentFloor + "楼已全部抄完，请手动切换到下一层",
                            Snackbar.LENGTH_LONG).show();
                }
            }
        } else {
            // ===== 按表型模式 =====
            // 找同楼层下一个房间（同表型）
            int nextRoomPos = findNextRoomInSameFloor(roomPos, currentFloor);
            if (nextRoomPos != -1) {
                // 同楼层有下一个房间 → 跳转，保持当前表型
                roomSpinner.setSelection(nextRoomPos);
                Snackbar.make(findViewById(android.R.id.content),
                        "📌 " + roomList.get(nextRoomPos).getName() + " → " + TYPE_LABELS[typePos],
                        Snackbar.LENGTH_LONG).show();
            } else {
                // 当前楼层该表型已全部拍完
                if (isAutoType) {
                    // ===== 自动切换到下一个表型 =====
                    if (typePos < TYPE_VALUES.length - 1) {
                        // 切换到下一个表型，回到当前楼层第一个房间
                        int firstRoomInFloor = findFirstRoomInFloor(currentFloor);
                        if (firstRoomInFloor != -1) {
                            selectResourceType(typePos + 1);
                            roomSpinner.setSelection(firstRoomInFloor);
                            Snackbar.make(findViewById(android.R.id.content),
                                    "🔄 " + TYPE_LABELS[typePos] + " 已完成，自动切换到 " + TYPE_LABELS[typePos + 1] + "，从 " + roomList.get(firstRoomInFloor).getName() + " 开始",
                                    Snackbar.LENGTH_LONG).show();
                        } else {
                            Snackbar.make(findViewById(android.R.id.content),
                                    "当前楼层无更多房间，请手动切换",
                                    Snackbar.LENGTH_LONG).show();
                        }
                    } else {
                        // 所有表型已完成
                        Snackbar.make(findViewById(android.R.id.content),
                                "🎉 " + currentFloor + "楼所有表型已全部抄完！",
                                Snackbar.LENGTH_LONG).show();
                    }
                } else {
                    // ===== 自动切换关闭：只提示，不跳转 =====
                    Snackbar.make(findViewById(android.R.id.content),
                                    "✅ " + currentFloor + "楼 " + TYPE_LABELS[typePos] + " 已全部抄完，请手动切换表型或楼层",
                                    Snackbar.LENGTH_LONG)
                            .setAction("切换表型", v -> showTypePicker())
                            .show();
                }
            }
        }
        updateCurrentDisplay();
    }

    // ================================================================
    // 辅助方法
    // ================================================================
    private int findNextRoomInSameFloor(int currentPos, int floor) {
        for (int i = currentPos + 1; i < roomList.size(); i++) {
            if (roomList.get(i).getFloor() == floor) {
                return i;
            }
        }
        return -1;
    }

    private int findFirstRoomInFloor(int floor) {
        for (int i = 0; i < roomList.size(); i++) {
            if (roomList.get(i).getFloor() == floor) {
                return i;
            }
        }
        return -1;
    }

    private void showTypePicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择表型");
        builder.setItems(TYPE_LABELS, (dialog, which) -> {
            selectResourceType(which);
            // 切换后，房间自动定位到当前楼层第一个房间
            int currentFloor = roomList.get(roomSpinner.getSelectedItemPosition()).getFloor();
            int firstRoom = findFirstRoomInFloor(currentFloor);
            if (firstRoom != -1) roomSpinner.setSelection(firstRoom);
            updateCurrentDisplay();
            Snackbar.make(findViewById(android.R.id.content),
                    "已切换到 " + TYPE_LABELS[which],
                    Snackbar.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // ================================================================
    // 同步功能
    // ================================================================
    private void syncRecords() {
        new Thread(() -> {
            List<SmartReadingEntity> pending = db.smartReadingDao().getPendingReadings();
            if (pending.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "没有待同步的记录", Toast.LENGTH_SHORT).show());
                return;
            }

            JSONArray recordsArray = new JSONArray();
            for (SmartReadingEntity entity : pending) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("local_id", String.valueOf(entity.id));
                    obj.put("room_id", entity.roomId);
                    obj.put("read_date", entity.readDate);
                    obj.put("read_time", entity.readTime);
                    obj.put("point_id", entity.pointId);
                    obj.put("time_label", entity.timeLabel);
                    obj.put("manual_reading", entity.isManual ? entity.manualReading : JSONObject.NULL);
                    obj.put("resource_type", entity.resourceType != null ? entity.resourceType : "electric");

                    File file = new File(entity.photoPath);
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

            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();
                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        new JSONObject().put("records", recordsArray).toString()
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

                    for (int i = 0; i < resultsArray.length(); i++) {
                        JSONObject res = resultsArray.getJSONObject(i);
                        String localId = res.getString("local_id");
                        long id = Long.parseLong(localId);
                        for (SmartReadingEntity entity : pending) {
                            if (entity.id == id) {
                                entity.status = "synced";
                                entity.batchId = batchId;
                                db.smartReadingDao().update(entity);
                                break;
                            }
                        }
                    }

                    runOnUiThread(() -> {
                        Toast.makeText(this, "✅ 同步成功！共 " + resultsArray.length() + " 条", Toast.LENGTH_SHORT).show();
                        loadPendingList();
                        Intent intent = new Intent(SmartPhotoActivity.this, SmartReviewActivity.class);
                        intent.putExtra("batchId", batchId);
                        startActivity(intent);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "同步失败: " + response.message(), Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "同步异常: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
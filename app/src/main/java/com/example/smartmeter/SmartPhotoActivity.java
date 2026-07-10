package com.example.smartmeter;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_PERMISSION = 101;
    private static final String PHOTO_FILE_PROVIDER = "com.example.smartmeter.fileprovider";

    private Spinner roomSpinner;
    private TextView lastReadingText;
    private RecyclerView pendingRecyclerView;
    private PendingAdapter pendingAdapter;
    private SmartReadingDatabase db;
    private List<RoomItem> roomList = new ArrayList<>();
    private String currentPhotoPath;
    private int selectedRoomId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_photo);

        db = SmartReadingDatabase.getInstance(this);

        roomSpinner = findViewById(R.id.roomSpinner);
        lastReadingText = findViewById(R.id.lastReadingText);
        pendingRecyclerView = findViewById(R.id.pendingRecyclerView);
        pendingRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.takePhotoBtn).setOnClickListener(v -> checkPermissionAndTakePhoto());
        findViewById(R.id.syncBtn).setOnClickListener(v -> syncRecords());

        loadRoomList();
        loadPendingList();
    }

    private void loadRoomList() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .build();
                Request request = new Request.Builder()
                        .url(MainActivity.BASE_URL + "/api/smart/rooms")
                        .get()
                        .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    JSONArray jsonArray = new JSONArray(jsonData);
                    roomList.clear();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        RoomItem item = new RoomItem(
                                obj.getInt("id"),
                                obj.getString("name"),
                                obj.getInt("floor"),
                                obj.getString("room_type"),
                                0,
                                obj.optDouble("price", 0)
                        );
                        roomList.add(item);
                    }
                    runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(SmartPhotoActivity.this,
                                android.R.layout.simple_spinner_item,
                                roomList.stream().map(r -> r.getName()).toArray(String[]::new));
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        roomSpinner.setAdapter(adapter);
                        roomSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                                selectedRoomId = roomList.get(position).getId();
                                updateLastReading(selectedRoomId);
                            }
                            @Override
                            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                        });
                        if (roomList.size() > 0) {
                            roomSpinner.setSelection(0);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateLastReading(int roomId) {
        // 从本地数据库或内存中获取，简化：直接显示占位
        lastReadingText.setText("上月: --");
        // 实际可调用后端 /api/room_last_reading 接口
    }

    private void loadPendingList() {
        new Thread(() -> {
            List<SmartReadingEntity> pending = db.smartReadingDao().getPendingReadings();
            runOnUiThread(() -> {
                pendingAdapter = new PendingAdapter(pending, this::deletePending);
                pendingRecyclerView.setAdapter(pendingAdapter);
            });
        }).start();
    }

    private void deletePending(SmartReadingEntity entity) {
        new Thread(() -> {
            db.smartReadingDao().delete(entity);
            loadPendingList();
        }).start();
    }

    // 权限检查和拍照
    private void checkPermissionAndTakePhoto() {
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
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this, PHOTO_FILE_PROVIDER, photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_CAMERA);
            }
        }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAMERA && resultCode == RESULT_OK) {
            // 拍照成功，保存到数据库
            if (selectedRoomId == -1) {
                Toast.makeText(this, "请选择房间", Toast.LENGTH_SHORT).show();
                return;
            }
            String roomName = roomList.stream().filter(r -> r.getId() == selectedRoomId).findFirst().map(RoomItem::getName).orElse("");
            // 计算归属时间点（这里简化为默认12:00，实际可传入当前时间）
            int pointId = 3; // 12:00
            String timeLabel = "中午休息";

            SmartReadingEntity entity = new SmartReadingEntity(
                    selectedRoomId,
                    roomName,
                    new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date()),
                    new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()),
                    pointId,
                    timeLabel,
                    currentPhotoPath,
                    0, // 手动输入值，0 表示待AI识别
                    "pending",
                    false
            );
            new Thread(() -> {
                db.smartReadingDao().insert(entity);
                runOnUiThread(() -> {
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                    loadPendingList();
                });
            }).start();
        }
    }

    // ===== 同步功能 =====
    private void syncRecords() {
        new Thread(() -> {
            List<SmartReadingEntity> pending = db.smartReadingDao().getPendingReadings();
            if (pending.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "没有待同步的记录", Toast.LENGTH_SHORT).show());
                return;
            }
            // 构建请求体
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
                    // 读取照片文件转为 base64
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
                        .url(MainActivity.BASE_URL + "/api/smart/batch_sync")
                        .post(body)
                        .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    JSONObject result = new JSONObject(jsonData);
                    String batchId = result.getString("batch_id");
                    JSONArray resultsArray = result.getJSONArray("results");

                    // 更新本地记录状态为 synced，并保存 batchId 和服务端返回的 ID
                    for (int i = 0; i < resultsArray.length(); i++) {
                        JSONObject res = resultsArray.getJSONObject(i);
                        String localId = res.getString("local_id");
                        long id = Long.parseLong(localId);
                        int serverId = res.optInt("id", 0);
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
                        Toast.makeText(this, "同步成功！共 " + resultsArray.length() + " 条", Toast.LENGTH_SHORT).show();
                        loadPendingList();
                        // 跳转到复核页面
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
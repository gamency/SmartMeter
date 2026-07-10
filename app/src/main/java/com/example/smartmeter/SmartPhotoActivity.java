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
import androidx.annotation.Nullable;
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
    private static final int REQUEST_FILE_PICKER = 101;
    private static final int REQUEST_PERMISSION = 102;
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
        lastReadingText.setText("上月: --");
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

    // ===== 核心拍照逻辑 =====
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
        // 保存到数据库
        String roomName = roomList.stream()
                .filter(r -> r.getId() == selectedRoomId)
                .findFirst()
                .map(RoomItem::getName)
                .orElse("");
        int hour = new Date().getHours();
        int pointId;
        String timeLabel;
        if (hour >= 0 && hour < 5) { pointId = 1; timeLabel = "夜间"; }
        else if (hour >= 5 && hour < 11) { pointId = 2; timeLabel = "上午"; }
        else if (hour >= 11 && hour < 13) { pointId = 3; timeLabel = "中午"; }
        else if (hour >= 13 && hour < 16) { pointId = 4; timeLabel = "下午"; }
        else if (hour >= 16 && hour < 19) { pointId = 5; timeLabel = "傍晚"; }
        else if (hour >= 19 && hour < 22) { pointId = 6; timeLabel = "晚上"; }
        else { pointId = 7; timeLabel = "深夜"; }

        SmartReadingEntity entity = new SmartReadingEntity(
                selectedRoomId,
                roomName,
                new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date()),
                new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()),
                pointId,
                timeLabel,
                photoPath,
                0,
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

    // ===== 同步功能 =====
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
                        Toast.makeText(this, "同步成功！共 " + resultsArray.length() + " 条", Toast.LENGTH_SHORT).show();
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
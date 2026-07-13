package com.example.smartmeter;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private static final String BASE_URL = "http://192.168.10.12:5000";
    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_PERMISSION = 101;
    private static final String PHOTO_FILE_PROVIDER = "com.example.smartmeter.fileprovider";

    private TextView tvSelectedRoom, tvPhotoHint, tvNetworkHint;
    private Button btnTakePhoto, btnSubmitAll;
    private RecyclerView rvCached;
    private TextView tvEmptyCached;
    private TextView btnCached;

    private int selectedRoomId = -1;
    private String selectedRoomName = "";
    private List<CachedRecord> cachedList = new ArrayList<>();
    private CachedRecordAdapter cachedAdapter;
    private String currentPhotoPath;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_meter_input, container, false);

        tvSelectedRoom = view.findViewById(R.id.tv_selected_room);
        tvPhotoHint = view.findViewById(R.id.tv_photo_hint);
        tvNetworkHint = view.findViewById(R.id.tv_network_hint);
        btnTakePhoto = view.findViewById(R.id.btn_take_photo);
        btnSubmitAll = view.findViewById(R.id.btn_submit_all);
        rvCached = view.findViewById(R.id.rv_cached);
        tvEmptyCached = view.findViewById(R.id.tv_empty_cached);
        btnCached = view.findViewById(R.id.btn_cached);

        rvCached.setLayoutManager(new LinearLayoutManager(getContext()));

        cachedAdapter = new CachedRecordAdapter(cachedList, record -> {
            cachedList.remove(record);
            cachedAdapter.notifyDataSetChanged();
            if (cachedList.isEmpty()) {
                tvEmptyCached.setVisibility(View.VISIBLE);
                rvCached.setVisibility(View.GONE);
            }
            checkNetworkStatus();
        });
        rvCached.setAdapter(cachedAdapter);

        loadRoomList();
        loadCachedRecords();

        tvSelectedRoom.setOnClickListener(v -> showRoomSelectorDialog());
        btnTakePhoto.setOnClickListener(v -> checkPermissionAndTakePhoto());
        btnSubmitAll.setOnClickListener(v -> submitAllCached());

        btnCached.setOnClickListener(v -> {
            rvCached.smoothScrollToPosition(0);
        });

        checkNetworkStatus();

        return view;
    }

    private void loadRoomList() {
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
                    JSONArray jsonArray = new JSONArray(jsonData);
                    List<RoomItem> rooms = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        rooms.add(new RoomItem(
                                obj.getInt("id"),
                                obj.getString("name"),
                                obj.getInt("floor"),
                                obj.getString("room_type")
                        ));
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (!rooms.isEmpty()) {
                                selectedRoomId = rooms.get(0).id;
                                selectedRoomName = rooms.get(0).name;
                                tvSelectedRoom.setText(selectedRoomName);
                                btnTakePhoto.setEnabled(true);
                                tvPhotoHint.setText("点击拍照按钮拍摄电表");
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "加载房间列表失败", Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private void showRoomSelectorDialog() {
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
                    JSONArray jsonArray = new JSONArray(jsonData);
                    String[] roomNames = new String[jsonArray.length()];
                    int[] roomIds = new int[jsonArray.length()];
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        roomNames[i] = obj.getString("name");
                        roomIds[i] = obj.getInt("id");
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                            builder.setTitle("选择房间");
                            builder.setItems(roomNames, (dialog, which) -> {
                                selectedRoomId = roomIds[which];
                                selectedRoomName = roomNames[which];
                                tvSelectedRoom.setText(selectedRoomName);
                                btnTakePhoto.setEnabled(true);
                                tvPhotoHint.setText("点击拍照按钮拍摄电表");
                            });
                            builder.setNegativeButton("取消", null);
                            builder.show();
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

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

            Log.d(TAG, "图片Base64长度: " + base64Image.length());

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
                    "pending"
            );
            cachedList.add(0, record);
            cachedAdapter.notifyDataSetChanged();
            tvEmptyCached.setVisibility(View.GONE);
            rvCached.setVisibility(View.VISIBLE);
            Toast.makeText(getContext(), "📸 拍照已缓存，点击批量提交上传", Toast.LENGTH_SHORT).show();
            checkNetworkStatus();

            if (currentPhotoPath != null) {
                new File(currentPhotoPath).delete();
            }
        }
    }

    private void loadCachedRecords() {
        if (cachedList.isEmpty()) {
            tvEmptyCached.setVisibility(View.VISIBLE);
            rvCached.setVisibility(View.GONE);
        } else {
            tvEmptyCached.setVisibility(View.GONE);
            rvCached.setVisibility(View.VISIBLE);
        }
    }

    private void checkNetworkStatus() {
        if (!cachedList.isEmpty()) {
            btnSubmitAll.setEnabled(true);
            btnSubmitAll.setText("📤 批量提交全部缓存数据 (" + cachedList.size() + " 条)");
            tvNetworkHint.setVisibility(View.GONE);
        } else {
            btnSubmitAll.setEnabled(false);
            btnSubmitAll.setText("📤 批量提交全部缓存数据");
            tvNetworkHint.setVisibility(View.GONE);
        }
    }

    private void submitAllCached() {
        if (cachedList.isEmpty()) {
            Toast.makeText(getContext(), "没有待提交数据", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<CachedRecord> toSubmit = new ArrayList<>(cachedList);
        final int totalCount = toSubmit.size();

        for (CachedRecord rec : toSubmit) {
            if (rec.photoBase64 == null || rec.photoBase64.isEmpty()) {
                Toast.makeText(getContext(), "记录 " + rec.roomName + " 缺少图片，请重新拍照", Toast.LENGTH_SHORT).show();
                return;
            }
        }

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
                obj.put("photo_base64", "data:image/jpeg;base64," + record.photoBase64);
                obj.put("manual_reading", JSONObject.NULL);
                obj.put("status", "pending_ocr");
                recordsArray.put(obj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("records", recordsArray);
            String jsonStr = requestBody.toString();
            Log.d(TAG, "===== 请求体大小: " + jsonStr.length() + " 字节 =====");
            Log.d(TAG, "请求体预览: " + jsonStr.substring(0, Math.min(500, jsonStr.length())) + "...");
        } catch (Exception e) {
            e.printStackTrace();
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

                Log.d(TAG, "===== 请求URL: " + BASE_URL + "/api/smart/batch_upload =====");

                Response response = client.newCall(request).execute();
                String responseBody = response.body() != null ? response.body().string() : "空响应";

                Log.d(TAG, "===== 响应状态码: " + response.code() + " =====");
                Log.d(TAG, "===== 响应内容: " + responseBody + " =====");

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
                                tvEmptyCached.setVisibility(View.VISIBLE);
                                rvCached.setVisibility(View.GONE);
                                checkNetworkStatus();

                            } catch (Exception e) {
                                Toast.makeText(getContext(), "解析响应失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                btnSubmitAll.setEnabled(true);
                                btnSubmitAll.setText("📤 批量提交全部缓存数据 (" + cachedList.size() + " 条)");
                            }
                        } else {
                            Toast.makeText(getContext(), "❌ 提交失败: HTTP " + response.code() + "\n" + responseBody,
                                    Toast.LENGTH_LONG).show();
                            btnSubmitAll.setEnabled(true);
                            btnSubmitAll.setText("📤 批量提交全部缓存数据 (" + cachedList.size() + " 条)");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "提交异常: " + e.getMessage());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "❌ 提交异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        btnSubmitAll.setEnabled(true);
                        btnSubmitAll.setText("📤 批量提交全部缓存数据 (" + cachedList.size() + " 条)");
                    });
                }
            }
        }).start();
    }

    // ===== 内部类 =====
    static class RoomItem {
        int id;
        String name;
        int floor;
        String type;
        RoomItem(int id, String name, int floor, String type) {
            this.id = id;
            this.name = name;
            this.floor = floor;
            this.type = type;
        }
    }

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

        CachedRecord(int roomId, String roomName, String readDate, String readTime,
                     int pointId, String timeLabel, String photoBase64,
                     String manualReading, boolean synced, String status) {
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
        }
    }

    // ===== 缓存记录适配器 =====
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
            holder.tvReading.setText("待识别");
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
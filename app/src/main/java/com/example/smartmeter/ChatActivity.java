package com.example.smartmeter;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final String PREF_CURRENT_SESSION = "current_session_id";

    private RecyclerView rvMessages;
    private EditText etMessage;
    private Button btnSend;
    private TextView tvSessionTitle;
    private ImageView btnBack, btnNewSession, ivDropdown;

    private ChatMessageAdapter adapter;
    private List<ChatMessage> messageList = new ArrayList<>();

    private OkHttpClient client;
    private int currentSessionId = -1;
    private List<ChatSession> sessions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // 初始化视图
        Toolbar toolbar = findViewById(R.id.toolbar_chat);
        tvSessionTitle = findViewById(R.id.tv_session_title);
        btnBack = findViewById(R.id.btn_back);
        btnNewSession = findViewById(R.id.btn_new_session);
        ivDropdown = findViewById(R.id.iv_dropdown);
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);

        // 设置 RecyclerView
        adapter = new ChatMessageAdapter(messageList);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        // 设置思考过程切换监听（用于保存状态，当前仅用于日志）
        adapter.setThinkingToggleListener((position, isExpanded) -> {
            // 可以保存展开状态，暂不实现持久化
            Log.d(TAG, "Thinking toggled: position=" + position + ", expanded=" + isExpanded);
        });

        // OkHttp 客户端
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // 事件绑定
        btnBack.setOnClickListener(v -> finish());
        btnNewSession.setOnClickListener(v -> createNewSession());
        ivDropdown.setOnClickListener(v -> showSessionPicker());
        tvSessionTitle.setOnClickListener(v -> showSessionPicker());
        btnSend.setOnClickListener(v -> sendMessage());

        // 加载会话
        loadSessions();
    }

    // ======================== 会话管理 ========================

    private void loadSessions() {
        Request request = new Request.Builder()
                .url(Config.BASE_URL + "/chat/api/sessions")
                .get()
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(ChatActivity.this, "加载会话失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    try {
                        JSONArray array = new JSONArray(json);
                        sessions.clear();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            ChatSession s = new ChatSession(
                                    obj.getInt("id"),
                                    obj.getString("title"),
                                    obj.getString("created_at"),
                                    obj.getString("updated_at"),
                                    obj.getInt("message_count")
                            );
                            sessions.add(s);
                        }
                        // 获取上次选中的会话 ID
                        SharedPreferences prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE);
                        int lastId = prefs.getInt(PREF_CURRENT_SESSION, -1);
                        int targetId = -1;
                        if (lastId != -1) {
                            for (ChatSession s : sessions) {
                                if (s.getId() == lastId) {
                                    targetId = s.getId();
                                    break;
                                }
                            }
                        }
                        if (targetId == -1 && !sessions.isEmpty()) {
                            targetId = sessions.get(0).getId();
                        }
                        final int finalTargetId = targetId;
                        runOnUiThread(() -> {
                            if (finalTargetId != -1) {
                                selectSession(finalTargetId);
                            } else {
                                createNewSession();
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "解析会话列表失败", e);
                    }
                }
            }
        });
    }

    private void createNewSession() {
        Request request = new Request.Builder()
                .url(Config.BASE_URL + "/chat/api/sessions")
                .post(RequestBody.create(MediaType.parse("application/json"), "{}"))
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(ChatActivity.this, "创建会话失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    try {
                        JSONObject obj = new JSONObject(json);
                        int id = obj.getInt("id");
                        ChatSession newSession = new ChatSession(
                                id,
                                "新对话",
                                obj.getString("created_at"),
                                obj.getString("created_at"),
                                0
                        );
                        sessions.add(0, newSession);
                        runOnUiThread(() -> selectSession(id));
                    } catch (Exception e) {
                        Log.e(TAG, "解析新会话失败", e);
                    }
                }
            }
        });
    }

    private void selectSession(int sessionId) {
        currentSessionId = sessionId;
        SharedPreferences prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE);
        prefs.edit().putInt(PREF_CURRENT_SESSION, sessionId).apply();

        // 更新标题
        for (ChatSession s : sessions) {
            if (s.getId() == sessionId) {
                tvSessionTitle.setText(s.getTitle());
                break;
            }
        }

        // 加载消息
        loadMessages(sessionId);
    }

    private void showSessionPicker() {
        if (sessions.isEmpty()) {
            Toast.makeText(this, "暂无会话", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[sessions.size()];
        for (int i = 0; i < sessions.size(); i++) {
            titles[i] = sessions.get(i).getTitle();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("切换对话");
        builder.setItems(titles, (dialog, which) -> {
            int id = sessions.get(which).getId();
            if (id != currentSessionId) {
                selectSession(id);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // ======================== 消息加载与发送 ========================

    private void loadMessages(int sessionId) {
        Request request = new Request.Builder()
                .url(Config.BASE_URL + "/chat/api/sessions/" + sessionId + "/messages")
                .get()
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(ChatActivity.this, "加载消息失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    try {
                        JSONArray array = new JSONArray(json);
                        List<ChatMessage> newMessages = new ArrayList<>();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            String role = obj.getString("role");
                            String content = obj.getString("content");
                            String createdAt = obj.getString("created_at");
                            // 解析 steps
                            List<Map<String, Object>> steps = null;
                            if (obj.has("steps") && !obj.isNull("steps")) {
                                JSONArray stepsArray = obj.getJSONArray("steps");
                                steps = new ArrayList<>();
                                for (int j = 0; j < stepsArray.length(); j++) {
                                    JSONObject stepObj = stepsArray.getJSONObject(j);
                                    steps.add((Map<String, Object>) stepObj.toMap());
                                }
                            }
                            newMessages.add(new ChatMessage(
                                    obj.getInt("id"),
                                    role,
                                    content,
                                    createdAt,
                                    steps
                            ));
                        }
                        runOnUiThread(() -> {
                            messageList.clear();
                            messageList.addAll(newMessages);
                            adapter.notifyDataSetChanged();
                            rvMessages.scrollToPosition(messageList.size() - 1);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "解析消息失败", e);
                    }
                }
            }
        });
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text) || currentSessionId == -1) return;

        etMessage.setText("");
        // 添加用户消息
        ChatMessage userMsg = new ChatMessage(0, "user", text, "", null);
        messageList.add(userMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        rvMessages.scrollToPosition(messageList.size() - 1);

        // 显示打字指示器
        adapter.setTyping(true);

        // 发送请求
        JSONObject body = new JSONObject();
        try {
            body.put("message", text);
            body.put("debug", true);
        } catch (Exception e) {}

        Request request = new Request.Builder()
                .url(Config.BASE_URL + "/chat/api/sessions/" + currentSessionId + "/messages")
                .post(RequestBody.create(MediaType.parse("application/json"), body.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    adapter.setTyping(false);
                    Toast.makeText(ChatActivity.this, "发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> adapter.setTyping(false));
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    try {
                        JSONObject obj = new JSONObject(json);
                        String role = obj.getString("role");
                        String content = obj.getString("content");
                        String createdAt = obj.getString("created_at");
                        List<Map<String, Object>> steps = null;
                        if (obj.has("steps") && !obj.isNull("steps")) {
                            JSONArray stepsArray = obj.getJSONArray("steps");
                            steps = new ArrayList<>();
                            for (int j = 0; j < stepsArray.length(); j++) {
                                JSONObject stepObj = stepsArray.getJSONObject(j);
                                steps.add((Map<String, Object>) stepObj.toMap());
                            }
                        }
                        ChatMessage assistantMsg = new ChatMessage(
                                obj.getInt("id"),
                                role,
                                content,
                                createdAt,
                                steps
                        );
                        runOnUiThread(() -> {
                            messageList.add(assistantMsg);
                            adapter.notifyItemInserted(messageList.size() - 1);
                            rvMessages.scrollToPosition(messageList.size() - 1);
                            // 刷新标题（可能已更新）
                            refreshSessionTitle();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "解析回复失败", e);
                    }
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(ChatActivity.this, "服务器错误: " + response.code(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void refreshSessionTitle() {
        // 重新加载会话列表，更新标题
        loadSessions(); // 简单处理，会重载所有会话
    }
}
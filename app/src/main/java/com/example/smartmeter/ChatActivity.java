package com.example.smartmeter;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

    private DrawerLayout drawerLayout;
    private RecyclerView rvMessages, rvSessions;
    private EditText etMessage;
    private ImageView btnSend;
    private TextView tvSessionTitle;
    private TextView tvSessionsCount;
    private ImageView btnMenu, btnNewSession;

    private ChatMessageAdapter messageAdapter;
    private ChatSessionAdapter sessionAdapter;
    private List<ChatMessage> messageList = new ArrayList<>();
    private List<ChatSession> sessions = new ArrayList<>();

    private OkHttpClient client;
    private Call currentCall;
    private int currentSessionId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getColor(R.color.surface));
            getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        rvMessages = findViewById(R.id.rv_messages);
        rvSessions = findViewById(R.id.rv_sessions);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        tvSessionTitle = findViewById(R.id.tv_session_title);
        tvSessionsCount = findViewById(R.id.tv_sessions_count);
        btnMenu = findViewById(R.id.btn_menu);
        btnNewSession = findViewById(R.id.btn_new_session);

        messageAdapter = new ChatMessageAdapter(messageList);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(messageAdapter);

        sessionAdapter = new ChatSessionAdapter(sessions, currentSessionId, session -> {
            if (currentCall != null && !currentCall.isCanceled()) {
                currentCall.cancel();
            }
            selectSession(session.getId());
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        rvSessions.setAdapter(sessionAdapter);

        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        btnNewSession.setOnClickListener(v -> createNewSession());
        btnSend.setOnClickListener(v -> sendMessage());

        loadSessions();
    }

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
                            updateSessionList();
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
                        runOnUiThread(() -> {
                            updateSessionList();
                            selectSession(id);
                        });
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

        for (ChatSession s : sessions) {
            if (s.getId() == sessionId) {
                tvSessionTitle.setText(s.getTitle());
                break;
            }
        }
        sessionAdapter.setCurrentSessionId(sessionId);
        loadMessages(sessionId);
    }

    private void updateSessionList() {
        sessionAdapter.updateData(sessions, currentSessionId);
        tvSessionsCount.setText(sessions.size() + " 个对话");
    }

    // ===== 消息加载（含 steps 解析） =====
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

                            // ===== 解析 steps =====
                            List<Map<String, Object>> steps = null;
                            if (obj.has("steps") && !obj.isNull("steps")) {
                                JSONArray stepsArray = obj.getJSONArray("steps");
                                steps = new ArrayList<>();
                                for (int j = 0; j < stepsArray.length(); j++) {
                                    JSONObject stepObj = stepsArray.getJSONObject(j);
                                    Map<String, Object> stepMap = new HashMap<>();
                                    Iterator<String> keys = stepObj.keys();
                                    while (keys.hasNext()) {
                                        String key = keys.next();
                                        stepMap.put(key, stepObj.get(key));
                                    }
                                    steps.add(stepMap);
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
                            messageAdapter.notifyDataSetChanged();
                            rvMessages.scrollToPosition(messageList.size() - 1);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "解析消息失败", e);
                    }
                }
            }
        });
    }

    // ===== 发送消息（含 steps 解析） =====
    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text) || currentSessionId == -1) return;

        etMessage.setText("");

        ChatMessage userMsg = new ChatMessage(0, "user", text, "", null);
        messageList.add(userMsg);
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        rvMessages.scrollToPosition(messageList.size() - 1);

        JSONObject body = new JSONObject();
        try {
            body.put("message", text);
            body.put("debug", true);
        } catch (Exception e) {}

        Request request = new Request.Builder()
                .url(Config.BASE_URL + "/chat/api/sessions/" + currentSessionId + "/messages")
                .post(RequestBody.create(MediaType.parse("application/json"), body.toString()))
                .build();

        currentCall = client.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(ChatActivity.this, "发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    try {
                        JSONObject obj = new JSONObject(json);
                        String role = obj.getString("role");
                        String content = obj.getString("content");
                        String createdAt = obj.getString("created_at");

                        // ===== 解析 steps =====
                        List<Map<String, Object>> steps = null;
                        if (obj.has("steps") && !obj.isNull("steps")) {
                            JSONArray stepsArray = obj.getJSONArray("steps");
                            steps = new ArrayList<>();
                            for (int j = 0; j < stepsArray.length(); j++) {
                                JSONObject stepObj = stepsArray.getJSONObject(j);
                                Map<String, Object> stepMap = new HashMap<>();
                                Iterator<String> keys = stepObj.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next();
                                    stepMap.put(key, stepObj.get(key));
                                }
                                steps.add(stepMap);
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
                            messageAdapter.notifyItemInserted(messageList.size() - 1);
                            rvMessages.scrollToPosition(messageList.size() - 1);
                            loadSessions();
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

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}

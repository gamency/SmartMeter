package com.example.smartmeter;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
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
    private TextView tvTimer;              // 计时器显示
    private ImageView btnBack, btnNewSession, ivDropdown, btnStop;

    private ChatMessageAdapter adapter;
    private List<ChatMessage> messageList = new ArrayList<>();

    private OkHttpClient client;
    private Call currentCall;              // 当前请求，用于取消
    private int currentSessionId = -1;
    private List<ChatSession> sessions = new ArrayList<>();

    // 计时器相关
    private Timer timer;
    private int elapsedSeconds = 0;
    private boolean isWaitingResponse = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

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
        tvTimer = findViewById(R.id.tv_timer);
        btnStop = findViewById(R.id.btn_stop);
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);

        // 设置 RecyclerView
        adapter = new ChatMessageAdapter(messageList);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        // 思考过程监听
        adapter.setThinkingToggleListener((position, isExpanded) -> {
            Log.d(TAG, "Thinking toggled: position=" + position + ", expanded=" + isExpanded);
        });

        // 初始化 OkHttp（超时设为一个非常大的值，实际永不超时）
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)    // 5分钟，实际够用
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        // 事件绑定
        btnBack.setOnClickListener(v -> finish());
        btnNewSession.setOnClickListener(v -> createNewSession());
        ivDropdown.setOnClickListener(v -> showSessionPicker());
        tvSessionTitle.setOnClickListener(v -> showSessionPicker());
        btnSend.setOnClickListener(v -> sendMessage());
        btnStop.setOnClickListener(v -> stopRequest());

        // 加载会话
        loadSessions();
    }

    // ================================================================
    // 计时器控制
    // ================================================================
    private void startTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        elapsedSeconds = 0;
        isWaitingResponse = true;
        tvTimer.setVisibility(View.VISIBLE);
        btnStop.setVisibility(View.VISIBLE);
        btnStop.setEnabled(true);
        updateTimerDisplay();

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                elapsedSeconds++;
                mainHandler.post(() -> updateTimerDisplay());
            }
        }, 1000, 1000);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        isWaitingResponse = false;
        tvTimer.setVisibility(View.GONE);
        btnStop.setVisibility(View.GONE);
        btnStop.setEnabled(false);
    }

    private void updateTimerDisplay() {
        int min = elapsedSeconds / 60;
        int sec = elapsedSeconds % 60;
        if (min > 0) {
            tvTimer.setText(String.format("%d分%d秒", min, sec));
        } else {
            tvTimer.setText(sec + "秒");
        }
    }

    // ================================================================
    // 停止请求
    // ================================================================
    private void stopRequest() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
            currentCall = null;
        }
        stopTimer();
        // 移除打字指示器
        adapter.setTyping(false);
        // 添加一条中断消息
        ChatMessage stopMsg = new ChatMessage(0, "assistant", "⏹️ 已停止生成", "", null);
        messageList.add(stopMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        rvMessages.scrollToPosition(messageList.size() - 1);
        Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show();
        enableInput(true);
    }

    // ================================================================
    // 输入控制
    // ================================================================
    private void enableInput(boolean enable) {
        btnSend.setEnabled(enable);
        etMessage.setEnabled(enable);
        if (enable) {
            etMessage.requestFocus();
        }
    }

    // ================================================================
    // 会话管理
    // ================================================================
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

        for (ChatSession s : sessions) {
            if (s.getId() == sessionId) {
                tvSessionTitle.setText(s.getTitle());
                break;
            }
        }
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
                // 如果有请求正在进行，先取消
                if (currentCall != null && !currentCall.isCanceled()) {
                    currentCall.cancel();
                }
                stopTimer();
                selectSession(id);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // ================================================================
    // 消息加载
    // ================================================================
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

    // ================================================================
    // 发送消息
    // ================================================================
    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text) || currentSessionId == -1) return;
        if (isWaitingResponse) {
            Toast.makeText(this, "请等待当前请求完成", Toast.LENGTH_SHORT).show();
            return;
        }

        etMessage.setText("");
        enableInput(false);

        // 添加用户消息
        ChatMessage userMsg = new ChatMessage(0, "user", text, "", null);
        messageList.add(userMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        rvMessages.scrollToPosition(messageList.size() - 1);

        // 显示打字指示器
        adapter.setTyping(true);

        // 启动计时器
        startTimer();

        // 构建请求
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
                runOnUiThread(() -> {
                    // 如果是因为手动取消，不显示错误
                    if (call.isCanceled()) {
                        return;
                    }
                    adapter.setTyping(false);
                    stopTimer();
                    enableInput(true);
                    String errorMsg;
                    if (e instanceof java.net.SocketTimeoutException) {
                        errorMsg = "⏳ 服务器响应超时";
                    } else {
                        errorMsg = "❌ 网络异常: " + e.getMessage();
                    }
                    Toast.makeText(ChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    addErrorMessage(errorMsg);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    stopTimer();
                    adapter.setTyping(false);
                    enableInput(true);
                });

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
                            adapter.notifyItemInserted(messageList.size() - 1);
                            rvMessages.scrollToPosition(messageList.size() - 1);
                            refreshSessionTitle();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "解析回复失败", e);
                        runOnUiThread(() -> {
                            addErrorMessage("❌ 解析响应失败: " + e.getMessage());
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        addErrorMessage("❌ 服务器错误: " + response.code());
                    });
                }
                runOnUiThread(() -> {
                    currentCall = null;
                });
            }
        });
    }

    // ================================================================
    // 辅助方法
    // ================================================================
    private void addErrorMessage(String message) {
        ChatMessage errorMsg = new ChatMessage(0, "assistant", message, "", null);
        messageList.add(errorMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        rvMessages.scrollToPosition(messageList.size() - 1);
    }

    private void refreshSessionTitle() {
        loadSessions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
            currentCall = null;
        }
    }
}
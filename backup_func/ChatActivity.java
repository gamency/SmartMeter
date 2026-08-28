package com.example.smartmeter;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageView btnSend;
    private TextView tvSessionTitle;

    private ChatMessageAdapter messageAdapter;
    private List<ChatMessage> messageList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        tvSessionTitle = findViewById(R.id.tv_session_title);

        tvSessionTitle.setText("对话");

        messageAdapter = new ChatMessageAdapter(messageList);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(messageAdapter);

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = etMessage.getText().toString().trim();
                if (TextUtils.isEmpty(text)) return;

                etMessage.setText("");
                // 添加用户消息
                ChatMessage userMsg = new ChatMessage(0, "user", text, "", null);
                messageList.add(userMsg);
                messageAdapter.notifyItemInserted(messageList.size() - 1);
                rvMessages.scrollToPosition(messageList.size() - 1);

                // 模拟助手回复
                ChatMessage assistantMsg = new ChatMessage(0, "assistant", "收到：" + text, "", null);
                messageList.add(assistantMsg);
                messageAdapter.notifyItemInserted(messageList.size() - 1);
                rvMessages.scrollToPosition(messageList.size() - 1);
            }
        });
    }
}

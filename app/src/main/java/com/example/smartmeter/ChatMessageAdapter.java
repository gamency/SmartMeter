package com.example.smartmeter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;
    private static final int TYPE_TYPING = 2;

    private List<ChatMessage> messages;
    private boolean isTyping = false;
    private OnThinkingToggleListener thinkingToggleListener;

    public interface OnThinkingToggleListener {
        void onThinkingToggle(int position, boolean isExpanded);
    }

    public ChatMessageAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public void setThinkingToggleListener(OnThinkingToggleListener listener) {
        this.thinkingToggleListener = listener;
    }

    public void setTyping(boolean typing) {
        if (isTyping != typing) {
            isTyping = typing;
            if (typing) {
                notifyItemInserted(messages.size());
            } else {
                notifyItemRemoved(messages.size());
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == messages.size() && isTyping) {
            return TYPE_TYPING;
        }
        ChatMessage msg = messages.get(position);
        return "user".equals(msg.getRole()) ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserViewHolder(inflater.inflate(R.layout.item_chat_message_user, parent, false));
        } else if (viewType == TYPE_TYPING) {
            return new TypingViewHolder(inflater.inflate(R.layout.item_chat_message_typing, parent, false));
        } else {
            return new AssistantViewHolder(inflater.inflate(R.layout.item_chat_message_assistant, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(messages.get(position));
        } else if (holder instanceof AssistantViewHolder) {
            ((AssistantViewHolder) holder).bind(messages.get(position), position);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size() + (isTyping ? 1 : 0);
    }

    // ===== User ViewHolder =====
    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent, tvTime;
        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_user_content);
            tvTime = itemView.findViewById(R.id.tv_user_time);
        }
        void bind(ChatMessage msg) {
            tvContent.setText(msg.getContent());
            tvTime.setText(formatTime(msg.getCreatedAt()));
        }
    }

    // ===== Assistant ViewHolder =====
    class AssistantViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent, tvTime;
        TextView tvThinkingToggle;
        LinearLayout llThinking, llThinkingContent;
        TextView tvThinkingSteps;
        boolean isExpanded = false;

        AssistantViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_assistant_content);
            tvTime = itemView.findViewById(R.id.tv_assistant_time);
            llThinking = itemView.findViewById(R.id.ll_thinking);
            tvThinkingToggle = itemView.findViewById(R.id.tv_thinking_toggle);
            llThinkingContent = itemView.findViewById(R.id.ll_thinking_content);
            tvThinkingSteps = itemView.findViewById(R.id.tv_thinking_steps);
        }

        void bind(ChatMessage msg, int position) {
            tvContent.setText(msg.getContent());
            tvTime.setText(formatTime(msg.getCreatedAt()));

            // 思考过程
            if (msg.hasSteps()) {
                llThinking.setVisibility(View.VISIBLE);
                // 构建思考内容
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> step : msg.getSteps()) {
                    String type = step.get("type") != null ? step.get("type").toString() : "step";
                    String content = step.get("content") != null ? step.get("content").toString() : "";
                    if (type.equals("thinking")) {
                        sb.append("💭 ").append(content).append("\n");
                    } else if (type.equals("action")) {
                        sb.append("🔧 ").append(content).append("\n");
                    } else if (type.equals("observation")) {
                        sb.append("📤 ").append(content).append("\n\n");
                    } else {
                        sb.append(content).append("\n");
                    }
                }
                tvThinkingSteps.setText(sb.toString().trim());

                // 恢复展开状态（通过 tag 保存）
                Boolean savedState = (Boolean) itemView.getTag(R.id.tag_thinking_state);
                if (savedState != null) {
                    isExpanded = savedState;
                } else {
                    isExpanded = false;
                }
                updateThinkingUI();

                // 点击切换
                tvThinkingToggle.setOnClickListener(v -> {
                    isExpanded = !isExpanded;
                    itemView.setTag(R.id.tag_thinking_state, isExpanded);
                    updateThinkingUI();
                    if (thinkingToggleListener != null) {
                        thinkingToggleListener.onThinkingToggle(position, isExpanded);
                    }
                });
            } else {
                llThinking.setVisibility(View.GONE);
            }
        }

        private void updateThinkingUI() {
            if (isExpanded) {
                tvThinkingToggle.setText("💭 思考过程 ▼");
                llThinkingContent.setVisibility(View.VISIBLE);
            } else {
                tvThinkingToggle.setText("💭 思考过程 ▶");
                llThinkingContent.setVisibility(View.GONE);
            }
        }
    }

    // ===== Typing ViewHolder =====
    static class TypingViewHolder extends RecyclerView.ViewHolder {
        TypingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    // ===== 工具方法 =====
    private static String formatTime(String iso) {
        if (TextUtils.isEmpty(iso)) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            java.util.Date date = sdf.parse(iso);
            SimpleDateFormat out = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return out.format(date);
        } catch (Exception e) {
            return iso;
        }
    }
}
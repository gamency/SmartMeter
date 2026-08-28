package com.example.smartmeter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;

    private List<ChatMessage> messages;

    public ChatMessageAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        return "user".equals(msg.getRole()) ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserViewHolder(inflater.inflate(R.layout.item_chat_message_user, parent, false));
        } else {
            return new AssistantViewHolder(inflater.inflate(R.layout.item_chat_message_assistant, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).tvContent.setText(msg.getContent());
        } else if (holder instanceof AssistantViewHolder) {
            ((AssistantViewHolder) holder).bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ===== User ViewHolder =====
    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_user_content);
        }
    }

    // ===== Assistant ViewHolder（含思考过程） =====
    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        LinearLayout llThinking;
        TextView tvThinkingToggle;
        TextView tvThinkingSteps;

        private boolean isExpanded = false;

        AssistantViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_assistant_content);
            llThinking = itemView.findViewById(R.id.ll_thinking);
            tvThinkingToggle = itemView.findViewById(R.id.tv_thinking_toggle);
            tvThinkingSteps = itemView.findViewById(R.id.tv_thinking_steps);
        }

        void bind(ChatMessage msg) {
            tvContent.setText(msg.getContent());

            List<Map<String, Object>> steps = msg.getSteps();
            if (steps != null && !steps.isEmpty()) {
                llThinking.setVisibility(View.VISIBLE);

                // 构建思考内容文本
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> step : steps) {
                    String name = step.get("name") != null ? step.get("name").toString() : "";
                    String input = step.get("input") != null ? step.get("input").toString() : "";
                    String output = step.get("output") != null ? step.get("output").toString() : "";
                    String status = step.get("status") != null ? step.get("status").toString() : "";
                    String icon = step.get("icon") != null ? step.get("icon").toString() : "";

                    if (!TextUtils.isEmpty(name)) {
                        sb.append(icon).append(" ").append(name);
                        if ("success".equals(status)) sb.append(" ✅");
                        else if ("error".equals(status)) sb.append(" ❌");
                        sb.append("\n");
                    }
                    if (!TextUtils.isEmpty(input)) {
                        sb.append("  📥 ").append(input).append("\n");
                    }
                    if (!TextUtils.isEmpty(output)) {
                        sb.append("  📤 ").append(output).append("\n");
                    }
                    // 处理 children
                    List<Map<String, Object>> children = (List<Map<String, Object>>) step.get("children");
                    if (children != null) {
                        for (Map<String, Object> child : children) {
                            String label = child.get("label") != null ? child.get("label").toString() : "";
                            String content = child.get("content") != null ? child.get("content").toString() : "";
                            sb.append("    🔹 ").append(label);
                            if (!TextUtils.isEmpty(content)) sb.append(": ").append(content);
                            sb.append("\n");
                        }
                    }
                    sb.append("\n");
                }
                tvThinkingSteps.setText(sb.toString().trim());

                // 点击切换展开/折叠
                tvThinkingToggle.setOnClickListener(v -> {
                    isExpanded = !isExpanded;
                    updateThinkingUI();
                });

                // 默认折叠
                if (!isExpanded) {
                    tvThinkingToggle.setText("💭 思考过程 ▶");
                    tvThinkingSteps.setVisibility(View.GONE);
                } else {
                    tvThinkingToggle.setText("💭 思考过程 ▼");
                    tvThinkingSteps.setVisibility(View.VISIBLE);
                }
            } else {
                llThinking.setVisibility(View.GONE);
            }
        }

        private void updateThinkingUI() {
            if (isExpanded) {
                tvThinkingToggle.setText("💭 思考过程 ▼");
                tvThinkingSteps.setVisibility(View.VISIBLE);
            } else {
                tvThinkingToggle.setText("💭 思考过程 ▶");
                tvThinkingSteps.setVisibility(View.GONE);
            }
        }
    }
}

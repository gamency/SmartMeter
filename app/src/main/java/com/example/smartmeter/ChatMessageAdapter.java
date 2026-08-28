package com.example.smartmeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

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
            ((AssistantViewHolder) holder).tvContent.setText(msg.getContent());
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_user_content);
        }
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        AssistantViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_assistant_content);
        }
    }
}

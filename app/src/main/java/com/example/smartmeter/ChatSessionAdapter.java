package com.example.smartmeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatSessionAdapter extends RecyclerView.Adapter<ChatSessionAdapter.ViewHolder> {

    private List<ChatSession> sessions;
    private int currentSessionId;
    private OnSessionClickListener listener;

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
    }

    public ChatSessionAdapter(List<ChatSession> sessions, int currentSessionId, OnSessionClickListener listener) {
        this.sessions = sessions;
        this.currentSessionId = currentSessionId;
        this.listener = listener;
    }

    public void setCurrentSessionId(int id) {
        this.currentSessionId = id;
        notifyDataSetChanged();
    }

    public void updateData(List<ChatSession> newSessions, int currentId) {
        this.sessions = newSessions;
        this.currentSessionId = currentId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatSession session = sessions.get(position);
        holder.tvTitle.setText(session.getTitle());
        boolean isCurrent = session.getId() == currentSessionId;
        if (isCurrent) {
            holder.tvTitle.setTextColor(0xFF4F46E5);
            holder.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_check_mark, 0);
            holder.itemView.setBackgroundColor(0xFFEEF2FF);
        } else {
            holder.tvTitle.setTextColor(0xFF1E293B);
            holder.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            holder.itemView.setBackgroundColor(0x00000000);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSessionClick(session);
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = (TextView) itemView;
            tvTitle.setPadding(32, 16, 16, 16);
        }
    }
}

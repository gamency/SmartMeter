package com.example.smartmeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
                .inflate(R.layout.item_session, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatSession session = sessions.get(position);
        holder.tvTitle.setText(session.getTitle());

        boolean isCurrent = session.getId() == currentSessionId;
        if (isCurrent) {
            holder.tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_blue));
            holder.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_check_mark, 0);
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_blue_light));
        } else {
            holder.tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.black));
            holder.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
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
            tvTitle = itemView.findViewById(R.id.tv_session_title);
        }
    }
}
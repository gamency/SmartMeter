package com.example.smartmeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RoomSelectorAdapter extends RecyclerView.Adapter<RoomSelectorAdapter.ViewHolder> {

    private List<RoomItem> rooms;
    private OnRoomSelectedListener listener;

    public interface OnRoomSelectedListener {
        void onRoomSelected(RoomItem room);
    }

    public RoomSelectorAdapter(List<RoomItem> rooms, OnRoomSelectedListener listener) {
        this.rooms = rooms;
        this.listener = listener;
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
        RoomItem room = rooms.get(position);
        // 使用 getter 方法
        holder.tvName.setText(room.getName() + " (" + room.getFloor() + "层)");
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRoomSelected(room);
        });
    }

    @Override
    public int getItemCount() {
        return rooms.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = (TextView) itemView;
            tvName.setPadding(32, 16, 16, 16);
        }
    }
}

package com.example.smartmeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.ViewHolder> {

    private List<RoomItem> roomList;

    public RoomAdapter(List<RoomItem> roomList) {
        this.roomList = roomList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_room_ranked, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RoomItem item = roomList.get(position);
        holder.rank.setText(String.valueOf(position + 1));
        holder.roomName.setText(item.getName());
        holder.roomInfo.setText(item.getFloor() + "层 · " + item.getRoomType());

        if (position == 0) {
            holder.roomKwh.setTextColor(0xFFF59E0B);
        } else if (position == 1 || position == 2) {
            holder.roomKwh.setTextColor(0xFF64748B);
        } else {
            holder.roomKwh.setTextColor(0xFF4A6CF7);
        }
        holder.roomKwh.setText(String.format("%.1f 度", item.getTotalKwh()));
    }

    @Override
    public int getItemCount() {
        return roomList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView rank, roomName, roomInfo, roomKwh;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            rank = itemView.findViewById(R.id.rank);
            roomName = itemView.findViewById(R.id.room_name);
            roomInfo = itemView.findViewById(R.id.room_info);
            roomKwh = itemView.findViewById(R.id.room_kwh);
        }
    }
}
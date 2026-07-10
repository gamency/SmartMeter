package com.example.smartmeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class PendingAdapter extends RecyclerView.Adapter<PendingAdapter.ViewHolder> {

    private List<SmartReadingEntity> list;
    private OnDeleteListener deleteListener;

    public interface OnDeleteListener {
        void onDelete(SmartReadingEntity entity);
    }

    public PendingAdapter(List<SmartReadingEntity> list, OnDeleteListener deleteListener) {
        this.list = list;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SmartReadingEntity entity = list.get(position);
        holder.roomName.setText(entity.roomName);
        holder.dateTime.setText(entity.readDate + " " + entity.readTime);
        String statusText = entity.isManual ? "手动输入" : "待识别";
        holder.status.setText(statusText);
        holder.status.setTextColor(entity.isManual ? 0xFF22C55E : 0xFFF59E0B);

        // 显示缩略图
        File file = new File(entity.photoPath);
        if (file.exists()) {
            Glide.with(holder.itemView.getContext())
                    .load(file)
                    .into(holder.thumbnail);
        } else {
            holder.thumbnail.setImageResource(android.R.drawable.ic_menu_camera);
        }

        holder.deleteBtn.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(entity);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView roomName, dateTime, status, deleteBtn;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            roomName = itemView.findViewById(R.id.roomName);
            dateTime = itemView.findViewById(R.id.dateTime);
            status = itemView.findViewById(R.id.status);
            deleteBtn = itemView.findViewById(R.id.deleteBtn);
        }
    }
}
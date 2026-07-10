package com.example.smartmeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ViewHolder> {

    private List<SmartReviewActivity.ReviewItem> list;
    private OnConfirmListener confirmListener;
    private OnEditListener editListener;

    public interface OnConfirmListener {
        void onConfirm(int position);
    }
    public interface OnEditListener {
        void onEdit(int position, double newReading);
    }

    public ReviewAdapter(List<SmartReviewActivity.ReviewItem> list,
                         OnConfirmListener confirmListener,
                         OnEditListener editListener) {
        this.list = list;
        this.confirmListener = confirmListener;
        this.editListener = editListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SmartReviewActivity.ReviewItem item = list.get(position);
        holder.roomName.setText(item.roomName);
        holder.dateTime.setText(item.readDate + " " + item.readTime);
        holder.reading.setText(String.format("%.2f", item.reading));
        holder.status.setText(item.confirmed ? "✅ 已确认" : "⏳ 待确认");

        File file = new File(item.photoPath);
        if (file.exists()) {
            Glide.with(holder.itemView.getContext())
                    .load(file)
                    .into(holder.thumbnail);
        }

        holder.confirmBtn.setOnClickListener(v -> {
            if (confirmListener != null) confirmListener.onConfirm(position);
        });
        holder.editBtn.setOnClickListener(v -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(holder.itemView.getContext());
            builder.setTitle("修改读数");
            final android.widget.EditText input = new android.widget.EditText(holder.itemView.getContext());
            input.setInputType(android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setText(String.valueOf(item.reading));
            builder.setView(input);
            builder.setPositiveButton("确定", (dialog, which) -> {
                try {
                    double newVal = Double.parseDouble(input.getText().toString());
                    if (editListener != null) editListener.onEdit(position, newVal);
                } catch (NumberFormatException e) {}
            });
            builder.setNegativeButton("取消", null);
            builder.show();
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView roomName, dateTime, reading, status;
        Button confirmBtn, editBtn;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            roomName = itemView.findViewById(R.id.roomName);
            dateTime = itemView.findViewById(R.id.dateTime);
            reading = itemView.findViewById(R.id.reading);
            status = itemView.findViewById(R.id.status);
            confirmBtn = itemView.findViewById(R.id.confirmBtn);
            editBtn = itemView.findViewById(R.id.editBtn);
        }
    }
}
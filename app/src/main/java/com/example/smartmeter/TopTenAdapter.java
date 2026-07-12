package com.example.smartmeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TopTenAdapter extends RecyclerView.Adapter<TopTenAdapter.ViewHolder> {

    private List<OverviewFragment.TopTenItem> list;

    public TopTenAdapter(List<OverviewFragment.TopTenItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_top_tenant, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OverviewFragment.TopTenItem item = list.get(position);
        holder.tvRoomName.setText(item.room);
        holder.tvTenantName.setText(item.floor);
        holder.tvUsage.setText(String.format("%.1f 度", item.usage));
        if (item.isWarning) {
            holder.tvWarning.setVisibility(View.VISIBLE);
            holder.tvWarning.setText("异常高耗电");
        } else {
            holder.tvWarning.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRoomName, tvTenantName, tvUsage, tvWarning;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRoomName = itemView.findViewById(R.id.tv_room_name);
            tvTenantName = itemView.findViewById(R.id.tv_tenant_name);
            tvUsage = itemView.findViewById(R.id.tv_usage);
            tvWarning = itemView.findViewById(R.id.tv_warning);
        }
    }
}
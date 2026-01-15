package com.gitgud.citywatch.ui.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gitgud.citywatch.R;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * Adapter for displaying status options as chips (admin-only)
 */
public class StatusChipAdapter extends RecyclerView.Adapter<StatusChipAdapter.StatusViewHolder> {

    private final List<String> statusOptions;
    private String selectedStatus;
    private OnStatusSelectedListener listener;

    public interface OnStatusSelectedListener {
        void onStatusSelected(String status);
    }

    public StatusChipAdapter(List<String> statusOptions, String initialStatus) {
        this.statusOptions = statusOptions;
        this.selectedStatus = initialStatus;
    }

    public void setOnStatusSelectedListener(OnStatusSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public StatusViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_status_chip, parent, false);
        return new StatusViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StatusViewHolder holder, int position) {
        String status = statusOptions.get(position);
        holder.bind(status, status.equals(selectedStatus), listener);
    }

    @Override
    public int getItemCount() {
        return statusOptions.size();
    }

    public void setSelectedStatus(String status) {
        this.selectedStatus = status;
        notifyDataSetChanged();
    }

    static class StatusViewHolder extends RecyclerView.ViewHolder {
        private final Chip chipStatus;

        StatusViewHolder(@NonNull View itemView) {
            super(itemView);
            chipStatus = itemView.findViewById(R.id.chipStatus);
        }

        void bind(String status, boolean isSelected, OnStatusSelectedListener listener) {
            chipStatus.setText(status);
            chipStatus.setChecked(isSelected);

            chipStatus.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onStatusSelected(status);
                }
            });
        }
    }
}

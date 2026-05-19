package com.p2pchat.app.ui.adapter;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.p2pchat.app.R;
import com.p2pchat.app.model.Message;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 消息列表适配器（区分自己/对方消息气泡）
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {

    private static final int TYPE_MINE   = 1;
    private static final int TYPE_THEIRS = 2;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private final List<Message> messages;
    private final String selfId;

    public MessageAdapter(List<Message> messages, String selfId) {
        this.messages = messages;
        this.selfId   = selfId;
    }

    @Override public int getItemViewType(int pos) {
        Message m = messages.get(pos);
        return selfId.equals(m.fromPeerId) ? TYPE_MINE : TYPE_THEIRS;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_MINE ? R.layout.item_msg_mine : R.layout.item_msg_theirs;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Message m = messages.get(pos);
        h.tvContent.setText(m.content != null ? m.content : "[文件]");
        h.tvTime.setText(sdf.format(new Date(m.timestamp)));
    }

    @Override public int getItemCount() { return messages.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvContent, tvTime;
        VH(View v) {
            super(v);
            tvContent = v.findViewById(R.id.tvMsgContent);
            tvTime    = v.findViewById(R.id.tvMsgTime);
        }
    }
}

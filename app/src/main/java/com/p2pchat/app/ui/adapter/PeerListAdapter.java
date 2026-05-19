package com.p2pchat.app.ui.adapter;

import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.p2pchat.app.R;
import com.p2pchat.app.model.PeerInfo;
import java.util.List;

/**
 * 联系人列表适配器
 */
public class PeerListAdapter extends RecyclerView.Adapter<PeerListAdapter.VH> {

    public interface OnPeerClickListener { void onClick(PeerInfo peer); }

    private final List<PeerInfo> peers;
    private final OnPeerClickListener listener;

    public PeerListAdapter(List<PeerInfo> peers, OnPeerClickListener l) {
        this.peers = peers;
        this.listener = l;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_peer, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        PeerInfo peer = peers.get(pos);
        h.tvNick.setText(peer.nickname != null ? peer.nickname : "未知");
        h.tvId.setText("ID: " + peer.peerId.substring(0, 8) + "...");
        h.tvOnline.setText(peer.online ? "在线" : "离线");
        h.tvOnline.setTextColor(peer.online ? Color.parseColor("#4CAF50") : Color.GRAY);
        // 头像：彩色圆形 + 首字母
        try {
            h.ivAvatar.setBackgroundColor(Color.parseColor(peer.avatarColor));
        } catch (Exception ignored) {
            h.ivAvatar.setBackgroundColor(Color.BLUE);
        }
        h.tvInitial.setText(peer.getInitials());
        h.itemView.setOnClickListener(v -> listener.onClick(peer));
    }

    @Override public int getItemCount() { return peers.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvNick, tvId, tvOnline, tvInitial;
        View ivAvatar;
        VH(View v) {
            super(v);
            tvNick   = v.findViewById(R.id.tvPeerNick);
            tvId     = v.findViewById(R.id.tvPeerId);
            tvOnline = v.findViewById(R.id.tvOnlineStatus);
            tvInitial= v.findViewById(R.id.tvAvatarInitial);
            ivAvatar = v.findViewById(R.id.vAvatar);
        }
    }
}

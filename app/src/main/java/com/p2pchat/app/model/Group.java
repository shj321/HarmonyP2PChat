package com.p2pchat.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 群组实体
 */
@Entity(tableName = "groups")
public class Group {

    @PrimaryKey
    @NonNull
    public String groupId;

    public String groupName;
    public String ownerId;
    public String memberIds;    // JSON 序列化的 List<String>
    public long   createdAt;
    public String avatarColor;

    public Group() {
        this.groupId = "g_" + UUID.randomUUID().toString().replace("-","").substring(0,12);
        this.createdAt = System.currentTimeMillis();
        this.avatarColor = "#1565C0";
    }

    public String getInitials() {
        if (groupName == null || groupName.isEmpty()) return "G";
        return groupName.substring(0, Math.min(2, groupName.length())).toUpperCase();
    }
}

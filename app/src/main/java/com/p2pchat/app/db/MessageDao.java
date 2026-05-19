package com.p2pchat.app.db;

import androidx.room.*;
import com.p2pchat.app.model.Message;
import java.util.List;

@Dao
public interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Message message);

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    List<Message> getBySession(String sessionId);

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    Message getLatestBySession(String sessionId);

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId AND state = 0")
    int getUnreadCount(String sessionId);

    @Query("UPDATE messages SET state = 2 WHERE sessionId = :sessionId")
    void markAllRead(String sessionId);

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    void deleteBySession(String sessionId);

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    List<Message> getAll();
}

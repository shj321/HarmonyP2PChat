package com.p2pchat.app.db;

import androidx.room.*;
import com.p2pchat.app.model.Group;
import java.util.List;

@Dao
public interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Group group);

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    List<Group> getAll();

    @Query("SELECT * FROM groups WHERE groupId = :id")
    Group getById(String id);

    @Delete
    void delete(Group group);
}

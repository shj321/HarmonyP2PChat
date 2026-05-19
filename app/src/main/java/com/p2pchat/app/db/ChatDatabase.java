package com.p2pchat.app.db;

import android.content.Context;
import androidx.room.*;
import com.p2pchat.app.model.Group;
import com.p2pchat.app.model.Message;

@Database(entities = {Message.class, Group.class}, version = 1, exportSchema = false)
public abstract class ChatDatabase extends RoomDatabase {

    private static volatile ChatDatabase INSTANCE;

    public abstract MessageDao messageDao();
    public abstract GroupDao   groupDao();

    public static ChatDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (ChatDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            ctx.getApplicationContext(),
                            ChatDatabase.class,
                            "p2pchat.db"
                    ).allowMainThreadQueries()
                     .fallbackToDestructiveMigration()
                     .build();
                }
            }
        }
        return INSTANCE;
    }
}

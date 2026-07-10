package com.example.smartmeter;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

@Database(entities = {SmartReadingEntity.class}, version = 1, exportSchema = false)
public abstract class SmartReadingDatabase extends RoomDatabase {
    public abstract SmartReadingDao smartReadingDao();

    private static volatile SmartReadingDatabase INSTANCE;

    public static SmartReadingDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SmartReadingDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    SmartReadingDatabase.class, "smart_readings.db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
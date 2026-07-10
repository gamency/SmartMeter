package com.example.smartmeter;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SmartReadingDao {
    @Insert
    long insert(SmartReadingEntity entity);

    @Update
    void update(SmartReadingEntity entity);

    @Delete
    void delete(SmartReadingEntity entity);

    @Query("SELECT * FROM smart_readings WHERE status = 'pending' ORDER BY createdAt ASC")
    List<SmartReadingEntity> getPendingReadings();

    @Query("SELECT * FROM smart_readings WHERE status = 'synced' ORDER BY createdAt DESC")
    List<SmartReadingEntity> getSyncedReadings();

    @Query("SELECT * FROM smart_readings WHERE batchId = :batchId")
    List<SmartReadingEntity> getByBatchId(String batchId);

    @Query("DELETE FROM smart_readings WHERE status = 'synced' AND batchId IS NOT NULL")
    void deleteSynced();
}
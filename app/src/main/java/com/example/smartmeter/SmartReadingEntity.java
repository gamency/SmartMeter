package com.example.smartmeter;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "smart_readings")
public class SmartReadingEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public int roomId;
    public String roomName;
    public String readDate;
    public String readTime;
    public int pointId;
    public String timeLabel;
    public String photoPath;
    public double manualReading;
    public String status;           // 'pending', 'synced', 'confirmed'
    public boolean isManual;
    public long createdAt;
    public String batchId;
    public String resourceType;     // 新增：'electric', 'cold_water', 'hot_water'

    public SmartReadingEntity() {}

    public SmartReadingEntity(int roomId, String roomName, String readDate, String readTime,
                              int pointId, String timeLabel, String photoPath,
                              double manualReading, String status, boolean isManual,
                              String resourceType) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.readDate = readDate;
        this.readTime = readTime;
        this.pointId = pointId;
        this.timeLabel = timeLabel;
        this.photoPath = photoPath;
        this.manualReading = manualReading;
        this.status = status;
        this.isManual = isManual;
        this.resourceType = resourceType;
        this.createdAt = System.currentTimeMillis();
    }
}
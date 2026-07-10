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
    public String photoPath;        // 本地文件路径
    public double manualReading;    // 手动输入值（如果手动输入）
    public String status;           // 'pending', 'synced', 'confirmed'
    public boolean isManual;        // true 表示手动输入，false 表示待AI识别
    public long createdAt;
    public String batchId;          // 同步后由后端返回

    // 默认构造函数（Room 需要）
    public SmartReadingEntity() {}

    // 带参构造
    public SmartReadingEntity(int roomId, String roomName, String readDate, String readTime,
                              int pointId, String timeLabel, String photoPath,
                              double manualReading, String status, boolean isManual) {
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
        this.createdAt = System.currentTimeMillis();
    }
}
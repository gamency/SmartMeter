package com.example.smartmeter;

public class RoomItem {
    private int id;
    private String name;
    private int floor;
    private String roomType;
    private double totalKwh;
    private double price;

    public RoomItem(int id, String name, int floor, String roomType, double totalKwh, double price) {
        this.id = id;
        this.name = name;
        this.floor = floor;
        this.roomType = roomType;
        this.totalKwh = totalKwh;
        this.price = price;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getFloor() { return floor; }
    public String getRoomType() { return roomType; }
    public double getTotalKwh() { return totalKwh; }
    public double getPrice() { return price; }
}
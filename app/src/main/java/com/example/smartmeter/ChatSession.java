package com.example.smartmeter;

public class ChatSession {
    private int id;
    private String title;
    private String createdAt;
    private String updatedAt;
    private int messageCount;

    public ChatSession(int id, String title, String createdAt, String updatedAt, int messageCount) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messageCount = messageCount;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public int getMessageCount() { return messageCount; }
}
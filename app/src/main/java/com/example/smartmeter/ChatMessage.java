package com.example.smartmeter;

import java.util.List;
import java.util.Map;

public class ChatMessage {
    private int id;
    private String role;
    private String content;
    private String createdAt;
    private List<Map<String, Object>> steps;  // 必须是 List<Map<String, Object>>

    public ChatMessage(int id, String role, String content, String createdAt, List<Map<String, Object>> steps) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
        this.steps = steps;
    }

    public int getId() { return id; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getCreatedAt() { return createdAt; }
    public List<Map<String, Object>> getSteps() { return steps; }
    public boolean hasSteps() { return steps != null && !steps.isEmpty(); }
}
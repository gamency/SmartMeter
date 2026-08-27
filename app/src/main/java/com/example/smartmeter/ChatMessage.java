package com.example.smartmeter;

import java.util.List;
import java.util.Map;

public class ChatMessage {
    private int id;
    private String role;      // "user" 或 "assistant"
    private String content;
    private String createdAt;
    private List<Map<String, Object>> steps;  // 思考过程

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
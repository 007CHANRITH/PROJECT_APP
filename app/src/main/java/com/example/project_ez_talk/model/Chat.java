package com.example.project_ez_talk.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Chat {
    private String id;                    // chatId e.g. "user1_user2"
    private String name;                  // Other user's name
    private String avatarUrl;
    private String lastMessage;
    private String lastMessageType;       // TEXT, VOICE, IMAGE, LOCATION, FILE
    private long lastMessageTimestamp;
    private int unreadCount = 0;
    private List<String> participants;

    public Chat() {}

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getLastMessage() { return lastMessage; }
    public String getLastMessageType() { return lastMessageType; }
    public long getLastMessageTimestamp() { return lastMessageTimestamp; }
    public int getUnreadCount() { return unreadCount; }
    public List<String> getParticipants() { return participants; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public void setLastMessageType(String lastMessageType) { this.lastMessageType = lastMessageType; }
    public void setLastMessageTimestamp(long lastMessageTimestamp) { this.lastMessageTimestamp = lastMessageTimestamp; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
    public void setParticipants(List<String> participants) { this.participants = participants; }

    // Improved time formatting (just now, Yesterday, date)
    public String getFormattedTime() {
        if (lastMessageTimestamp == 0) return "";

        long now = System.currentTimeMillis();
        long diff = now - lastMessageTimestamp;

        if (diff < 60_000) { // less than 1 minute
            return "just now";
        } else if (diff < 3_600_000) { // less than 1 hour
            int minutes = (int) (diff / 60_000);
            return minutes + "m ago";
        } else if (diff < 86_400_000) { // today
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            return sdf.format(new Date(lastMessageTimestamp));
        } else if (diff < 172_800_000) { // yesterday
            return "Yesterday";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
            return sdf.format(new Date(lastMessageTimestamp));
        }
    }
}
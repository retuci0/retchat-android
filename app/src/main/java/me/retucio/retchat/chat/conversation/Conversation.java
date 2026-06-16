package me.retucio.retchat.chat.conversation;

import java.util.ArrayList;
import java.util.List;

import me.retucio.retchat.chat.ChatMessage;


public class Conversation {

    public enum Type {
        ROOM,
        DM;
    }

    public final Type type;
    public final String id;
    public final String displayName;
    public int unreadCount;
    public final List<ChatMessage> messages = new ArrayList<>();
    public long lastActive;

    public Conversation(Type type, String id, String displayName) {
        this.type = type;
        this.id = id;
        this.displayName = displayName;
        this.lastActive = System.currentTimeMillis();
    }

    public void addMessage(ChatMessage msg) {
        messages.add(msg);
        lastActive = msg.timestamp;
    }

    public void markRead() {
        unreadCount = 0;
    }
}
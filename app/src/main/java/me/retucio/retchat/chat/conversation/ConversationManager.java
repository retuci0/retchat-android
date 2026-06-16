package me.retucio.retchat.chat.conversation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import me.retucio.retchat.chat.ChatMessage;

public class ConversationManager {

    private final Map<String, Conversation> conversations = new HashMap<>();
    private Conversation activeConversation;

    public Conversation getOrCreateRoom(String roomName) {
        String key = "room:" + roomName;
        if (!conversations.containsKey(key)) {
            Conversation conv = new Conversation(Conversation.Type.ROOM, roomName, roomName);
            conversations.put(key, conv);
        }
        return conversations.get(key);
    }

    public Conversation getOrCreateDM(String targetNick) {
        String key = "dm:" + targetNick;
        if (!conversations.containsKey(key)) {
            Conversation conv = new Conversation(Conversation.Type.DM, targetNick, targetNick);
            conversations.put(key, conv);
        }
        return conversations.get(key);
    }

    public void setActiveConversation(Conversation conv) {
        if (activeConversation != null) {
            // mark previous as read?
        }
        activeConversation = conv;
        activeConversation.markRead();
    }

    public Conversation getActiveConversation() {
        return activeConversation;
    }

    public void addMessageToConversation(Conversation conv, ChatMessage msg) {
        conv.addMessage(msg);
        if (conv != activeConversation) {
            conv.unreadCount++;
        }
    }

    public List<Conversation> getRecentRooms() {
        return conversations.values().stream()
                .filter(c -> c.type == Conversation.Type.ROOM)
                .sorted(Comparator.comparingLong((Conversation c) -> c.lastActive).reversed())
                .collect(Collectors.toList());
    }

    public List<Conversation> getRecentDMs() {
        return conversations.values().stream()
                .filter(c -> c.type == Conversation.Type.DM)
                .sorted(Comparator.comparingLong((Conversation c) -> c.lastActive).reversed())
                .collect(Collectors.toList());
    }

    public Conversation getConversationByKey(String key) {
        return conversations.get(key);
    }
}
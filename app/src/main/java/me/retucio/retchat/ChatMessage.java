package me.retucio.retchat;

public class ChatMessage {
    public enum Type {
        SELF,       // own message (right, green bubble)
        OTHER,      // message from another user (left, grey bubble)
        SYSTEM      // system info/error (centered, italic, no bubble)
    }

    public final String text;
    public final Type type;
    public final String sender;
    public final long timestamp;
    public final boolean isError;    // true = red, false = gray
    public final boolean isDateHeader;

    // constructor for SELF / OTHER
    public ChatMessage(String text, Type type, String sender, long timestamp) {
        this.text = text;
        this.type = type;
        this.sender = sender;
        this.timestamp = timestamp;
        this.isError = false;
        this.isDateHeader = false;
    }

    // constructor for SYSTEM
    public ChatMessage(String text, boolean isError, long timestamp) {
        this.text = text;
        this.type = Type.SYSTEM;
        this.sender = null;
        this.timestamp = timestamp;
        this.isError = isError;
        this.isDateHeader = false;
    }

    // constructor for date header
    private ChatMessage(String dateText, long timestamp) {
        this.text = dateText;
        this.type = Type.SYSTEM;
        this.sender = null;
        this.timestamp = timestamp;
        this.isError = false;
        this.isDateHeader = true;
    }

    // factory method for date headers
    public static ChatMessage createDateHeader(String dateText, long timestamp) {
        return new ChatMessage(dateText, timestamp);
    }
}
package me.retucio.retchat.chat;

public class ChatMessage {
    public enum Type {
        SELF,       // own message (right bubble)
        OTHER,      // message from another user (left bubble)
        SYSTEM      // system info/error (centered, no bubble)
    }

    public final String  text;
    public final Type    type;
    public final String  sender;
    public final long    timestamp;
    public final boolean isError;
    public final boolean isDateHeader;
    public final byte[]  imageData;
    public final String  mimeType;
    public final boolean hasImage;

    // SELF / OTHER
    public ChatMessage(String text, Type type, String sender, long timestamp) {
        this.text = text;
        this.type = type;
        this.sender = sender;
        this.timestamp = timestamp;
        this.isError = false;
        this.isDateHeader = false;
        this.imageData = null;
        this.mimeType = null;
        this.hasImage = false;
    }

    // SYSTEM
    public ChatMessage(String text, boolean isError, long timestamp) {
        this.text = text;
        this.type = Type.SYSTEM;
        this.sender = null;
        this.timestamp = timestamp;
        this.isError = isError;
        this.isDateHeader = false;
        this.imageData = null;
        this.mimeType = null;
        this.hasImage = false;
    }

    // IMAGE
    public ChatMessage(String text, Type type, String sender, long timestamp, byte[] imageData, String mimeType) {
        this.text = (text != null) ? text : "";
        this.type = type;
        this.sender = sender;
        this.timestamp = timestamp;
        this.isError = false;
        this.isDateHeader = false;
        this.imageData = imageData;
        this.mimeType = mimeType;
        this.hasImage = (imageData != null && imageData.length > 0);
    }

    private ChatMessage(String dateText, long timestamp) {
        this.text = dateText;
        this.type = Type.SYSTEM;
        this.sender = null;
        this.timestamp = timestamp;
        this.isError = false;
        this.isDateHeader = true;
        this.imageData = null;
        this.mimeType = null;
        this.hasImage = false;
    }

    public static ChatMessage createDateHeader(String dateText, long timestamp) {
        return new ChatMessage(dateText, timestamp);
    }
}
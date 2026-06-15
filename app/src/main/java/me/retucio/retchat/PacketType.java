package me.retucio.retchat;

public class PacketType {

    public static final byte HANDSHAKE      = 0x01;
    public static final byte KEEPALIVE      = 0x02;
    public static final byte KEEPALIVE_ACK  = 0x03;
    public static final byte NICK_REQUEST   = 0x10;
    public static final byte NICK_ACK       = 0x11;
    public static final byte NICK_NOTIFY    = 0x12;
    public static final byte JOIN_REQUEST   = 0x13;
    public static final byte JOIN_ACK       = 0x14;
    public static final byte JOIN_NOTIFY    = 0x15;
    public static final byte LEAVE_NOTIFY   = 0x16;
    public static final byte ROOM_LIST      = 0x17;
    public static final byte USER_LIST      = 0x18;
    public static final byte CHAT_MSG       = 0x20;
    public static final byte SYSTEM_MSG     = 0x21;
    public static final byte DISCONNECT     = 0x30;
}
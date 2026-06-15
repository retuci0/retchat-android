package me.retucio.retchat;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ChatConnection {

    public interface MessageListener {
        void onConnected();
        void onDisconnected();
        void onSystemMessage(String text, boolean isError);
        void onChatMessage(String sender, String text);
        void onNickChanged(String newNick);
        void onNickNotify(String oldNick, String newNick);
        void onRoomJoined(String roomName);
        void onUserJoined(String nick);
        void onUserLeft(String nick);
    }

    private static final String DH_PRIME_HEX =
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
            "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
            "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
            "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
            "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
            "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
            "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
            "15728E5A8AACAA68FFFFFFFFFFFFFFFF";

    private static final BigInteger DH_PRIME = new BigInteger(DH_PRIME_HEX, 16);
    private static final BigInteger DH_GENERATOR = BigInteger.valueOf(2);
    private static final int KEY_LENGTH = 32;
    private static final int MAX_MSG_LEN = 4096;

    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private final byte[] encKey = new byte[KEY_LENGTH];
    private long sendCounter = 0;
    private long recvCounter = 0;
    private final MessageListener listener;
    private volatile boolean running;

    public ChatConnection(MessageListener listener) {
        this.listener = listener;
    }

    // --- DH (same as before) ---
    private BigInteger generatePrivateKey() {
        SecureRandom rng = new SecureRandom();
        byte[] privBytes = new byte[32];
        rng.nextBytes(privBytes);
        return new BigInteger(1, privBytes);
    }
    private BigInteger computePublicKey(BigInteger priv) {
        return DH_GENERATOR.modPow(priv, DH_PRIME);
    }
    private BigInteger computeSharedSecret(BigInteger peerPub, BigInteger priv) {
        return peerPub.modPow(priv, DH_PRIME);
    }
    private byte[] deriveBaseKey(BigInteger sharedSecret) throws Exception {
        byte[] secretBytes = sharedSecret.toByteArray();
        if (secretBytes.length > 1 && secretBytes[0] == 0)
            secretBytes = java.util.Arrays.copyOfRange(secretBytes, 1, secretBytes.length);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return sha256.digest(secretBytes);
    }

    private byte[] deriveKeystream(long counter, int neededLen) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(encKey, "HmacSHA256");
        hmac.init(keySpec);
        byte[] counterBytes = new byte[8];
        for (int i = 0; i < 8; i++) counterBytes[i] = (byte)((counter >> (i * 8)) & 0xFF);
        byte[] digest = hmac.doFinal(counterBytes);
        byte[] keystream = new byte[neededLen];
        int copied = 0;
        while (copied < neededLen) {
            int chunk = Math.min(neededLen - copied, digest.length);
            System.arraycopy(digest, 0, keystream, copied, chunk);
            copied += chunk;
            if (copied < neededLen) digest = hmac.doFinal(digest);
        }
        return keystream;
    }
    private void xorCrypt(byte[] data, long counter) throws Exception {
        byte[] keystream = deriveKeystream(counter, data.length);
        for (int i = 0; i < data.length; i++) data[i] ^= keystream[i];
    }

    // --- public API ---
    public void connect(String ip, int port) throws Exception {
        socket = new Socket(ip, port);
        out = socket.getOutputStream();
        in = new DataInputStream(socket.getInputStream());

        BigInteger clientPriv = generatePrivateKey();
        BigInteger clientPub = computePublicKey(clientPriv);

        int serverPubLen = in.readInt();
        byte[] serverPubBytes = new byte[serverPubLen];
        in.readFully(serverPubBytes);
        BigInteger serverPub = new BigInteger(1, serverPubBytes);

        byte[] clientPubBytes = clientPub.toByteArray();
        out.write(ByteBuffer.allocate(4).putInt(clientPubBytes.length).array());
        out.write(clientPubBytes);
        out.flush();

        BigInteger sharedSecret = computeSharedSecret(serverPub, clientPriv);
        byte[] baseKey = deriveBaseKey(sharedSecret);
        System.arraycopy(baseKey, 0, encKey, 0, KEY_LENGTH);

        sendCounter = 0;
        recvCounter = 0;
        running = true;
        new Thread(this::receiveLoop).start();
        listener.onConnected();
    }

    public void disconnect() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public boolean isRunning() { return running; }

    // --- send packets ---
    private void sendPacket(byte type, byte[] payload) throws Exception {
        if (!running) throw new IOException("not connected");
        byte[] full = new byte[1 + payload.length];
        full[0] = type;
        System.arraycopy(payload, 0, full, 1, payload.length);
        byte[] ciphertext = full.clone();
        xorCrypt(ciphertext, sendCounter);
        sendCounter++;
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(encKey, "HmacSHA256");
        hmac.init(keySpec);
        byte[] hmacBytes = hmac.doFinal(ciphertext);
        synchronized (out) {
            out.write(hmacBytes);
            out.write((ciphertext.length >> 8) & 0xFF);
            out.write(ciphertext.length & 0xFF);
            out.write(ciphertext);
            out.flush();
        }
    }

    public void sendNick(String newNick) throws Exception {
        byte[] payload = (newNick + "\0").getBytes(StandardCharsets.UTF_8);
        sendPacket(PacketType.NICK_REQUEST, payload);
    }
    public void sendJoin(String room) throws Exception {
        byte[] payload = (room + "\0").getBytes(StandardCharsets.UTF_8);
        sendPacket(PacketType.JOIN_REQUEST, payload);
    }
    public void sendChat(String text) throws Exception {
        byte[] payload = (text + "\0").getBytes(StandardCharsets.UTF_8);
        sendPacket(PacketType.CHAT_MSG, payload);
    }

    // --- receive loop ---
    private void receiveLoop() {
        try {
            while (running) {
                byte[] recvHmac = new byte[32];
                int hmacRead = 0;
                while (hmacRead < 32) {
                    int r = in.read(recvHmac, hmacRead, 32 - hmacRead);
                    if (r < 0) break;
                    hmacRead += r;
                }
                if (hmacRead != 32) break;
                int msgLen = in.readUnsignedShort();
                if (msgLen > MAX_MSG_LEN) break;
                byte[] ciphertext = new byte[msgLen];
                int total = 0;
                while (total < msgLen) {
                    int r = in.read(ciphertext, total, msgLen - total);
                    if (r < 0) break;
                    total += r;
                }
                if (total != msgLen) break;
                Mac hmac = Mac.getInstance("HmacSHA256");
                SecretKeySpec keySpec = new SecretKeySpec(encKey, "HmacSHA256");
                hmac.init(keySpec);
                byte[] expected = hmac.doFinal(ciphertext);
                if (!MessageDigest.isEqual(recvHmac, expected)) continue;
                xorCrypt(ciphertext, recvCounter);
                recvCounter++;
                // parse packet
                byte type = ciphertext[0];
                byte[] payload = new byte[ciphertext.length - 1];
                System.arraycopy(ciphertext, 1, payload, 0, payload.length);
                handlePacket(type, payload);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            running = false;
            listener.onDisconnected();
        }
    }

    private void handlePacket(byte type, byte[] payload) {
        int[] offset = { 0 };
        switch (type) {
            case PacketType.NICK_ACK: {
                String nick = readString(payload, offset);
                listener.onNickChanged(nick);
                break;
            }
            case PacketType.NICK_NOTIFY: {
                String oldNick = readString(payload, offset);
                String newNick = readString(payload, offset);
                listener.onNickNotify(oldNick, newNick);
                break;
            }
            case PacketType.JOIN_ACK: {
                String room = readString(payload, offset);
                listener.onRoomJoined(room);
                break;
            }
            case PacketType.JOIN_NOTIFY: {
                String nick = readString(payload, offset);
                listener.onUserJoined(nick);
                break;
            }
            case PacketType.LEAVE_NOTIFY: {
                String nick = readString(payload, offset);
                listener.onUserLeft(nick);
                break;
            }
            case PacketType.CHAT_MSG: {
                String sender = readString(payload, offset);
                String text   = readString(payload, offset);
                listener.onChatMessage(sender, text);
                break;
            }
            case PacketType.SYSTEM_MSG: {
                boolean isError = payload[offset[0]++] != 0;
                String text = readString(payload, offset);
                listener.onSystemMessage(text, isError);
                break;
            }
            case PacketType.DISCONNECT: {
                disconnect();
                break;
            }
        }
    }

    private String readString(byte[] data, int start) {
        int end = start;
        while (end < data.length && data[end] != 0) end++;
        return new String(data, start, end - start, StandardCharsets.UTF_8);
    }

    private String readString(byte[] data, int[] offset) {
        int start = offset[0];
        while (offset[0] < data.length && data[offset[0]] != 0) offset[0]++;
        String s = new String(data, start, offset[0] - start, StandardCharsets.UTF_8);
        offset[0]++;
        return s;
    }
}
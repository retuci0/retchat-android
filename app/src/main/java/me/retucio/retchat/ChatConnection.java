package me.retucio.retchat;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


public class ChatConnection {

    public interface MessageListener {
        void onMessage(String msg);
        void onDisconnected();
    }

    // RFC 3526 2048bit MODP group (14)
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

    // --- DH key exchange (2048-bit) ---
    private BigInteger generatePrivateKey() {
        SecureRandom rng = new SecureRandom();
        byte[] privBytes = new byte[32];  // 256 bits
        rng.nextBytes(privBytes);
        return new BigInteger(1, privBytes);
    }

    private BigInteger computePublicKey(BigInteger priv) {
        return DH_GENERATOR.modPow(priv, DH_PRIME);
    }

    private BigInteger computeSharedSecret(BigInteger peerPub, BigInteger priv) {
        return peerPub.modPow(priv, DH_PRIME);
    }

    // derive base key from shared secret (SHA-256)
    private byte[] deriveBaseKey(BigInteger sharedSecret) throws Exception {
        byte[] secretBytes = sharedSecret.toByteArray();
        // remove leading zero byte if present (match BN_bn2bin behaviour)
        if (secretBytes.length > 1 && secretBytes[0] == 0) {
            secretBytes = java.util.Arrays.copyOfRange(secretBytes, 1, secretBytes.length);
        }
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return sha256.digest(secretBytes);
    }

    // --- per‑message keystream (HMAC‑SHA256 of counter) ---
    private byte[] deriveKeystream(long counter, int neededLen) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(encKey, "HmacSHA256");
        hmac.init(keySpec);

        // little-endian
        byte[] counterBytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            counterBytes[i] = (byte) ((counter >> (i * 8)) & 0xFF);
        }
        byte[] digest = hmac.doFinal(counterBytes);

        byte[] keystream = new byte[neededLen];
        int copied = 0;
        while (copied < neededLen) {
            int chunk = Math.min(neededLen - copied, digest.length);
            System.arraycopy(digest, 0, keystream, copied, chunk);
            copied += chunk;
            if (copied < neededLen) {
                digest = hmac.doFinal(digest);
            }
        }
        return keystream;
    }

    private void xorCrypt(byte[] data, long counter) throws Exception {
        byte[] keystream = deriveKeystream(counter, data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] ^= keystream[i];
        }
    }

    // --- net IO ---
    public void connect(String ip, int port) throws Exception {
        socket = new Socket(ip, port);
        out = socket.getOutputStream();
        in = new DataInputStream(socket.getInputStream());

        // ---- Diffie‑Hellman exchange ----
        BigInteger clientPriv = generatePrivateKey();
        BigInteger clientPub = computePublicKey(clientPriv);

        // receive server public key (length + data)
        int serverPubLen = in.readInt();
        byte[] serverPubBytes = new byte[serverPubLen];
        in.readFully(serverPubBytes);
        BigInteger serverPub = new BigInteger(1, serverPubBytes);

        // send client public key (length + data)
        byte[] clientPubBytes = clientPub.toByteArray();
        out.write(ByteBuffer.allocate(4).putInt(clientPubBytes.length).array());
        out.write(clientPubBytes);
        out.flush();

        // compute shared secret and derive base key
        BigInteger sharedSecret = computeSharedSecret(serverPub, clientPriv);
        byte[] baseKey = deriveBaseKey(sharedSecret);
        System.arraycopy(baseKey, 0, encKey, 0, KEY_LENGTH);

        // reset counters
        sendCounter = 0;
        recvCounter = 0;

        running = true;
        new Thread(this::receiveLoop).start();
    }

    private void receiveLoop() {
        try {
            while (running) {
                // read HMAC (32 bytes)
                byte[] receivedHmac = new byte[32];
                int hmacRead = 0;
                while (hmacRead < 32) {
                    int r = in.read(receivedHmac, hmacRead, 32 - hmacRead);
                    if (r < 0) break;
                    hmacRead += r;
                }
                if (hmacRead != 32) break;

                // read message length (uint16, big-endian)
                int msgLen = in.readUnsignedShort();
                if (msgLen > MAX_MSG_LEN) break;

                // read ciphertext
                byte[] ciphertext = new byte[msgLen];
                int total = 0;
                while (total < msgLen) {
                    int r = in.read(ciphertext, total, msgLen - total);
                    if (r < 0) break;
                    total += r;
                }
                if (total != msgLen) break;

                // verify HMAC
                Mac hmac = Mac.getInstance("HmacSHA256");
                SecretKeySpec keySpec = new SecretKeySpec(encKey, "HmacSHA256");
                hmac.init(keySpec);
                byte[] expectedHmac = hmac.doFinal(ciphertext);
                if (!MessageDigest.isEqual(receivedHmac, expectedHmac)) {
                    continue;  // tampered, ignore
                }

                // decrypt
                xorCrypt(ciphertext, recvCounter);
                recvCounter++;

                int actualLen = ciphertext.length;
                if (actualLen > 0 && ciphertext[actualLen-1] == 0) actualLen--;
                String msg = new String(ciphertext, 0, actualLen, StandardCharsets.UTF_8);
                listener.onMessage(msg);
            }
        } catch (Exception e) {
            // connection lost
        } finally {
            running = false;
            listener.onDisconnected();
        }
    }

    public void send(String message) throws Exception {
        if (!running) throw new IOException("not connected");
        String line = message.endsWith("\n") ? message : message + "\n";
        byte[] data = (line + "\0").getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_MSG_LEN) throw new IOException("Message too long");

        // encrypt
        xorCrypt(data, sendCounter);
        sendCounter++;

        // compute HMAC
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(encKey, "HmacSHA256");
        hmac.init(keySpec);
        byte[] hmacBytes = hmac.doFinal(data);

        // Ssnd HMAC (32 bytes) + length (uint16) + ciphertext
        synchronized (out) {
            out.write(hmacBytes);
            out.write((data.length >> 8) & 0xFF);
            out.write(data.length & 0xFF);
            out.write(data);
            out.flush();
        }
    }

    public void disconnect() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }
}
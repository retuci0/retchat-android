package me.retucio.retchat.chat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.util.List;

import me.retucio.retchat.MainActivity;
import me.retucio.retchat.net.ChatConnection;


public class ChatService extends Service {

    public class LocalBinder extends Binder {
        public ChatService getService() { return ChatService.this; }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    private static final String CHANNEL_ID = "retchat_conn";
    private static final int    NOTIF_ID   = 1;

    private ChatConnection conn;
    private ChatConnection.MessageListener listener;
    private String currentIp   = "";
    private int    currentPort = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        disconnectInternal();
        super.onDestroy();
    }

    public void setListener(ChatConnection.MessageListener l)  { this.listener = l; }
    public void clearListener()                                { this.listener = null; }

    public boolean isConnected()   { return conn != null && conn.isRunning(); }
    public String  getCurrentIp()  { return currentIp; }
    public int     getCurrentPort(){ return currentPort; }

    public void connect(String ip, int port) {
        if (conn != null && conn.isRunning()) conn.disconnect();
        currentIp   = ip;
        currentPort = port;

        startForeground(NOTIF_ID, buildNotification("connecting to " + ip + "…"));

        conn = new ChatConnection(new ChatConnection.MessageListener() {
            @Override public void onConnected() {
                updateNotification(currentIp + ":" + currentPort);
                if (listener != null) listener.onConnected();
            }
            @Override public void onDisconnected() {
                updateNotification("disconnected");
                if (listener != null) listener.onDisconnected();
            }
            @Override public void onSystemMessage(int code, List<String> params, boolean isError) {
                if (listener != null) listener.onSystemMessage(code, params, isError);
            }
            @Override public void onChatMessage(String sender, String text) {
                if (listener != null) listener.onChatMessage(sender, text);
            }
            @Override public void onNickChanged(String newNick) {
                if (listener != null) listener.onNickChanged(newNick);
            }
            @Override public void onNickNotify(String oldNick, String newNick) {
                if (listener != null) listener.onNickNotify(oldNick, newNick);
            }
            @Override public void onRoomJoined(String roomName) {
                if (listener != null) listener.onRoomJoined(roomName);
            }
            @Override public void onUserJoined(String nick) {
                if (listener != null) listener.onUserJoined(nick);
            }
            @Override public void onUserLeft(String nick) {
                if (listener != null) listener.onUserLeft(nick);
            }
            @Override public void onDirectMessage(String sender, String text) {
                if (listener != null) listener.onDirectMessage(sender, text);
            }
            @Override public void onImageMessage(String sender, String mimeType, String fileName, byte[] imageData) {
                if (listener != null) listener.onImageMessage(sender, mimeType, fileName, imageData);
            }
            @Override public void onKicked(String reason) {
                updateNotification("disconnected");
                if (listener != null) listener.onKicked(reason);
            }
            @Override public void onBanned(String reason) {
                updateNotification("disconnected");
                if (listener != null) listener.onBanned(reason);
            }
        });

        new Thread(() -> {
            try {
                conn.connect(ip, port);
            } catch (Exception e) {
                updateNotification("connection failed");
                if (listener != null) listener.onDisconnected();
            }
        }).start();
    }


    public void disconnect() {
        disconnectInternal();
        stopSelf();
    }

    public void sendNick(String nick) throws Exception {
        requireConn().sendNick(nick);
    }
    public void sendJoin(String room) throws Exception {
        requireConn().sendJoin(room);
    }
    public void sendChat(String text) throws Exception {
        requireConn().sendChat(text);
    }
    public void sendDm(String targetNick, String text) throws Exception {
        requireConn().sendDm(targetNick, text);
    }
    public void sendImage(String target, String mimeType, String fileName, byte[] data) throws Exception {
        requireConn().sendImage(target, mimeType, fileName, data);
    }


    private void disconnectInternal() {
        if (conn != null) { conn.disconnect(); conn = null; }
    }

    private ChatConnection requireConn() {
        if (conn == null || !conn.isRunning()) throw new IllegalStateException("not connected");
        return conn;
    }


    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "retchat connection", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("keeps your chat connection alive in the background");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Intent tap = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, tap, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("retchat")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        getSystemService(NotificationManager.class)
                .notify(NOTIF_ID, buildNotification(text));
    }
}
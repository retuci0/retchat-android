package me.retucio.retchat;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends AppCompatActivity {

    private ChatConnection conn;
    private ChatAdapter adapter;
    private RecyclerView recycler;
    private EditText msgField;

    private SharedPreferences prefs;
    private static final String PREF_NAME = "retchat_prefs";
    private static final String KEY_NICKNAME = "usuario";
    private static final String KEY_ROOM = "lobby";

    private static final Pattern USER_MSG_PATTERN = Pattern.compile("^\\[([^]]+)]\\s+(.*)$");

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topRow = new LinearLayout(this);
        EditText ipField = new EditText(this);
        ipField.setHint("ip address");
        ipField.setText("retucio.me");
        EditText portField = new EditText(this);
        portField.setHint("port");
        portField.setText("6677");
        Button connectBtn = new Button(this);
        connectBtn.setText("connect");
        topRow.addView(ipField, new LinearLayout.LayoutParams(0, -2, 2));
        topRow.addView(portField, new LinearLayout.LayoutParams(0, -2, 1));
        topRow.addView(connectBtn, new LinearLayout.LayoutParams(-2, -2));

        recycler = new RecyclerView(this);
        adapter = new ChatAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        LinearLayout bottomRow = new LinearLayout(this);
        msgField = new EditText(this);
        msgField.setHint("message or /command");
        Button sendBtn = new Button(this);
        sendBtn.setText("send");
        bottomRow.addView(msgField, new LinearLayout.LayoutParams(0, -2, 1));
        bottomRow.addView(sendBtn, new LinearLayout.LayoutParams(-2, -2));

        root.addView(topRow, new LinearLayout.LayoutParams(-1, -2));
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottomRow, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        connectBtn.setOnClickListener(v -> {
            String ip = ipField.getText().toString().trim();
            int port;
            try {
                port = Integer.parseInt(portField.getText().toString().trim());
            } catch (NumberFormatException e) {
                addSystemMessage("invalid port number", true);
                return;
            }

            conn = new ChatConnection(new ChatConnection.MessageListener() {
                @Override
                public void onMessage(String rawMsg) {
                    runOnUiThread(() -> {
                        String msg = rawMsg.trim();
                        if (msg.startsWith("[SERVER]")) {
                            String serverMsg = msg.substring(8).trim();
                            if (serverMsg.startsWith("ahora eres ")) {
                                String newNick = serverMsg.substring(11).replace(".", "").trim();
                                if (!newNick.isEmpty()) {
                                    saveNickname(newNick);
                                }
                            }
                            else if (serverMsg.startsWith("ahora estás en la sala \"")) {
                                String newRoom = serverMsg.substring(25);
                                int endIdx = newRoom.indexOf("\"");
                                if (endIdx > 0) {
                                    newRoom = newRoom.substring(0, endIdx);
                                    saveRoom(newRoom);
                                }
                            }
                            addSystemMessage(serverMsg, false);
                            return;
                        }
                        Matcher m = USER_MSG_PATTERN.matcher(msg);
                        if (m.matches()) {
                            String nickname = m.group(1);
                            String text = m.group(2);
                            ChatMessage chatMsg = new ChatMessage(text, ChatMessage.Type.OTHER, nickname, System.currentTimeMillis());
                            adapter.addMessage(chatMsg);
                            recycler.scrollToPosition(adapter.getItemCount() - 1);
                            return;
                        }
                        addSystemMessage(msg, false);
                    });
                }

                @Override
                public void onDisconnected() {
                    runOnUiThread(() -> addSystemMessage("disconnected from server", false));
                }
            });

            new Thread(() -> {
                try {
                    conn.connect(ip, port);
                    runOnUiThread(() -> addSystemMessage("connected to " + ip + ":" + port, false));

                    String savedNick = prefs.getString(KEY_NICKNAME, null);
                    String savedRoom = prefs.getString(KEY_ROOM, null);

                    if (savedNick != null && !savedNick.isEmpty()) {
                        conn.send("/nick " + savedNick);
                    }
                    if (savedRoom != null && !savedRoom.isEmpty() && !savedRoom.equals("lobby")) {
                        Thread.sleep(200);
                        conn.send("/join " + savedRoom);
                    }
                } catch (UnknownHostException e) {
                    runOnUiThread(() -> addSystemMessage("cannot resolve host: " + ip, true));
                } catch (Exception e) {
                    runOnUiThread(() -> addSystemMessage("connection error: " + e.getMessage(), true));
                }
            }).start();
        });

        Runnable sendAction = () -> {
            if (conn == null || !conn.isRunning()) {
                addSystemMessage("not connected to any server", true);
                return;
            }
            String msg = msgField.getText().toString().trim();
            if (msg.isEmpty()) return;
            msgField.setText("");

            ChatMessage selfMsg = new ChatMessage(msg, ChatMessage.Type.SELF, null, System.currentTimeMillis());
            adapter.addMessage(selfMsg);
            recycler.scrollToPosition(adapter.getItemCount() - 1);

            new Thread(() -> {
                try {
                    conn.send(msg);
                } catch (Exception e) {
                    runOnUiThread(() -> addSystemMessage("failed to send message", true));
                }
            }).start();
        };

        sendBtn.setOnClickListener(v -> sendAction.run());
        msgField.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEND) {
                sendAction.run();
                return true;
            }
            return false;
        });
    }

    private void addSystemMessage(String text, boolean isError) {
        ChatMessage sysMsg = new ChatMessage(text, isError, System.currentTimeMillis());
        adapter.addMessage(sysMsg);
        recycler.scrollToPosition(adapter.getItemCount() - 1);
    }

    private void saveNickname(String nick) {
        prefs.edit().putString(KEY_NICKNAME, nick).apply();
    }

    private void saveRoom(String room) {
        prefs.edit().putString(KEY_ROOM, room).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (conn != null) conn.disconnect();
    }
}
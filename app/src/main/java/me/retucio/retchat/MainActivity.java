package me.retucio.retchat;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity implements ChatConnection.MessageListener {

    private ChatConnection conn;
    private ChatAdapter adapter;
    private RecyclerView recycler;

    private SharedPreferences prefs;
    private static final String PREF_NAME = "retchat_prefs";
    private static final String KEY_IP = "last_ip";
    private static final String KEY_PORT = "last_port";
    private static final String KEY_NICKNAME = "last_nickname";
    private static final String KEY_ROOM = "last_room";
    private static final String KEY_PANEL_EXPANDED = "panel_expanded";

    private LinearLayout connectPanel;
    private boolean connectPanelExpanded = true;

    private EditText msgField;
    private EditText ipField, portField;
    private Button connectBtn;
    private TextView statusNick, statusRoom;
    private String currentNick = "", currentRoom = "lobby";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        buildUI();

        String savedIp = prefs.getString(KEY_IP, "retucio.me");
        connectPanelExpanded = prefs.getBoolean(KEY_PANEL_EXPANDED, true);
        int savedPort = prefs.getInt(KEY_PORT, 6677);
        ipField.setText(savedIp);
        portField.setText(String.valueOf(savedPort));
        doConnect(savedIp, savedPort);
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A1A);

        // --- header bar ---
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setPadding(dp(12), dp(8), dp(8), dp(8));

        statusNick = new TextView(this);
        statusNick.setText("nick: ?");
        statusNick.setTextColor(0xFF9E9E9E);
        statusRoom = new TextView(this);
        statusRoom.setText("sala: lobby");
        statusRoom.setTextColor(0xFF9E9E9E);

        // connect panel toggle button
        TextView toggleButton = new TextView(this);
        toggleButton.setText("▼");
        toggleButton.setTextSize(18);
        toggleButton.setTextColor(0xFFBBBBBB);
        toggleButton.setPadding(dp(8), 0, dp(8), 0);
        toggleButton.setClickable(true);

        headerBar.addView(statusNick, new LinearLayout.LayoutParams(0, -2, 1));
        headerBar.addView(statusRoom, new LinearLayout.LayoutParams(0, -2, 1));
        headerBar.addView(toggleButton, new LinearLayout.LayoutParams(-2, -2));

        // --- connect panel ---
        connectPanel = new LinearLayout(this);
        connectPanel.setOrientation(LinearLayout.HORIZONTAL);
        connectPanel.setPadding(dp(12), dp(8), dp(12), dp(8));
        connectPanel.setVisibility(connectPanelExpanded ? View.VISIBLE : View.GONE);

        ipField = new EditText(this);
        ipField.setHint("ip");
        portField = new EditText(this);
        portField.setHint("puerto");
        connectBtn = new Button(this);
        connectBtn.setText("conectar");
        connectBtn.setOnClickListener(v -> {
            String ip = ipField.getText().toString().trim();
            int port = Integer.parseInt(portField.getText().toString().trim());
            prefs.edit().putString(KEY_IP, ip).putInt(KEY_PORT, port).apply();
            doConnect(ip, port);
        });
        connectPanel.addView(ipField, new LinearLayout.LayoutParams(0, -2, 2));
        connectPanel.addView(portField, new LinearLayout.LayoutParams(0, -2, 1));
        connectPanel.addView(connectBtn, new LinearLayout.LayoutParams(-2, -2));

        // --- input row ---
        recycler = new RecyclerView(this);
        adapter = new ChatAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        recycler.setBackgroundColor(0xFF1A1A1A);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(dp(8), dp(4), dp(8), dp(4));
        msgField = new EditText(this);
        msgField.setHint("mensaje o /comando");
        msgField.setSingleLine(true);
        msgField.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEND) sendMessage();
            return true;
        });
        Button sendBtn = new Button(this);
        sendBtn.setText("enviar");
        sendBtn.setOnClickListener(v -> sendMessage());
        inputRow.addView(msgField, new LinearLayout.LayoutParams(0, -2, 1));
        inputRow.addView(sendBtn, new LinearLayout.LayoutParams(-2, -2));


        root.addView(headerBar, new LinearLayout.LayoutParams(-1, -2));
        root.addView(connectPanel, new LinearLayout.LayoutParams(-1, -2));
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);

        toggleButton.setOnClickListener(v -> {
            if (connectPanelExpanded) {
                TranslateAnimation slideUp = new TranslateAnimation(0, 0, 0, -connectPanel.getHeight());
                slideUp.setDuration(200);
                slideUp.setAnimationListener(new Animation.AnimationListener() {
                    @Override public void onAnimationStart(Animation a) {}
                    @Override public void onAnimationEnd(Animation a) {
                        connectPanel.setVisibility(View.GONE);
                    }
                    @Override public void onAnimationRepeat(Animation a) {}
                });
                connectPanel.startAnimation(slideUp);
                toggleButton.setText("▼");
            } else {
                connectPanel.setVisibility(View.VISIBLE);
                TranslateAnimation slideDown = new TranslateAnimation(0, 0, -connectPanel.getHeight(), 0);
                slideDown.setDuration(200);
                connectPanel.startAnimation(slideDown);
                toggleButton.setText("▲");
            }
            connectPanelExpanded = !connectPanelExpanded;
            prefs.edit().putBoolean(KEY_PANEL_EXPANDED, connectPanelExpanded).apply();
        });

        // no animation initially
        if (connectPanelExpanded) {
            toggleButton.setText("▲");
            connectPanel.setVisibility(View.VISIBLE);
        } else {
            toggleButton.setText("▼");
            connectPanel.setVisibility(View.GONE);
        }
    }

    private void doConnect(String ip, int port) {
        if (conn != null) conn.disconnect();
        conn = new ChatConnection(this);
        new Thread(() -> {
            try {
                conn.connect(ip, port);
            } catch (Exception e) {
                runOnUiThread(() -> addSystemMessage("error de conexión: " + e.getMessage(), true));
            }
        }).start();
    }

    private void sendMessage() {
        if (conn == null || !conn.isRunning()) {
            addSystemMessage("no conectado", true);
            return;
        }
        String text = msgField.getText().toString().trim();
        if (text.isEmpty()) return;
        msgField.setText("");

        if (text.startsWith("/nick ")) {
            String nick = text.substring(6).trim();
            new Thread(() -> {
                try {
                    conn.sendNick(nick);
                } catch (Exception e) {
                    runOnUiThread(() -> addSystemMessage("error al cambiar nick", true));
                }
            }).start();
        }
        else if (text.startsWith("/join ")) {
            String room = text.substring(6).trim();
            new Thread(() -> {
                try {
                    conn.sendJoin(room);
                } catch (Exception e) {
                    runOnUiThread(() -> addSystemMessage("error al unirse a la sala", true));
                }
            }).start();
        }
        else {
            // normal chat message
            adapter.addMessage(new ChatMessage(text, ChatMessage.Type.SELF, null, System.currentTimeMillis()));
            scrollToBottom();
            new Thread(() -> {
                try {
                    conn.sendChat(text);
                } catch (Exception e) {
                    runOnUiThread(() -> addSystemMessage("no se pudo enviar", true));
                }
            }).start();
        }
    }


    // --- callbacks ---

    @Override
    public void onConnected() {
        runOnUiThread(() -> addSystemMessage("conectado", false));

        String savedNick = prefs.getString(KEY_NICKNAME, null);
        String savedRoom = prefs.getString(KEY_ROOM, null);

        // Send nickname and join room in background
        new Thread(() -> {
            try {
                if (savedNick != null && !savedNick.isEmpty()) {
                    conn.sendNick(savedNick);
                }
                if (savedRoom != null && !savedRoom.isEmpty() && !savedRoom.equals("lobby")) {
                    Thread.sleep(200);  // now safe (background thread)
                    conn.sendJoin(savedRoom);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    @Override public void onDisconnected() {
        runOnUiThread(() -> addSystemMessage("desconectado", false));
    }

    @Override public void onSystemMessage(String text, boolean isError) {
        runOnUiThread(() -> addSystemMessage(text, isError));
    }

    @Override public void onChatMessage(String sender, String text) {
        runOnUiThread(() -> {
            adapter.addMessage(new ChatMessage(text, ChatMessage.Type.OTHER, sender, System.currentTimeMillis()));
            scrollToBottom();
        });
    }

    @Override public void onNickChanged(String newNick) {
        currentNick = newNick;
        prefs.edit().putString(KEY_NICKNAME, newNick).apply();
        runOnUiThread(() -> {
            statusNick.setText("nick: " + newNick);
            addSystemMessage("ahora eres " + newNick, false);
        });
    }

    @Override public void onNickNotify(String oldNick, String newNick) {
        runOnUiThread(() -> addSystemMessage(oldNick + " ahora es " + newNick, false));
    }

    @Override public void onRoomJoined(String roomName) {
        currentRoom = roomName;
        prefs.edit().putString(KEY_ROOM, roomName).apply();
        runOnUiThread(() -> {
            statusRoom.setText("sala: " + roomName);
            addSystemMessage("te has unido a " + roomName, false);
        });
    }

    @Override public void onUserJoined(String nick) {
        runOnUiThread(() -> addSystemMessage(nick + " se ha unido", false));
    }

    @Override public void onUserLeft(String nick) {
        runOnUiThread(() -> addSystemMessage(nick + " se ha ido", false));
    }


    // --- helpers ---

    private void addSystemMessage(String text, boolean isError) {
        adapter.addMessage(new ChatMessage(text, isError, System.currentTimeMillis()));
        scrollToBottom();
    }

    private void scrollToBottom() { recycler.scrollToPosition(adapter.getItemCount() - 1); }
    private int dp(int dp) { return (int) (dp * getResources().getDisplayMetrics().density); }
}
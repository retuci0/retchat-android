package me.retucio.retchat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.List;

import me.retucio.retchat.chat.ChatAdapter;
import me.retucio.retchat.chat.ChatMessage;
import me.retucio.retchat.chat.conversation.Conversation;
import me.retucio.retchat.chat.conversation.ConversationManager;
import me.retucio.retchat.net.ChatConnection;
import me.retucio.retchat.net.SystemMessageCode;


public class MainActivity extends AppCompatActivity implements ChatConnection.MessageListener {

    private ChatConnection conn;
    private ChatAdapter adapter;
    private ConversationManager convs;
    private RecyclerView recycler;

    private SharedPreferences prefs;
    private static final String PREF_NAME = "retchat_prefs";
    private static final String KEY_IP = "last_ip";
    private static final String KEY_PORT = "last_port";
    private static final String KEY_NICKNAME = "last_nickname";
    private static final String KEY_ROOM = "last_room";
    private static final String KEY_PANEL_EXPANDED = "panel_expanded";

    private static final int REQUEST_IMAGE_PICK = 1;

    private LinearLayout connectPanel;
    private boolean connectPanelExpanded = true;

    // widgets
    private EditText msgField;
    private EditText ipField, portField;
    private Button connectBtn;
    private TextView statusNick, statusRoom;
    private TextView toggleButton, menuButton;
    private String currentNick = "", currentRoom = "lobby";
    private DrawerLayout drawerLayout;
    private LinearLayout sidePanel;
    private TextView currentRoomHeader;
    private RecyclerView dmRecycler;
    private DmListAdapter dmAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        connectPanelExpanded = prefs.getBoolean(KEY_PANEL_EXPANDED, true);
        buildUI();

        String savedIp = prefs.getString(KEY_IP, "retucio.me");
        int savedPort = prefs.getInt(KEY_PORT, 6677);
        ipField.setText(savedIp);
        portField.setText(String.valueOf(savedPort));
        new Thread(() -> doConnect(savedIp, savedPort)).start();
    }

    private void buildUI() {
        drawerLayout = new DrawerLayout(this);
        drawerLayout.setBackgroundColor(0xFF1A1A1A);
        LinearLayout mainContent = new LinearLayout(this);
        mainContent.setOrientation(LinearLayout.VERTICAL);

        // --- header bar ---
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setPadding(dp(12), dp(8), dp(8), dp(8));

        menuButton = new TextView(this);
        menuButton.setText("☰");
        menuButton.setTextSize(20);
        menuButton.setTextColor(0xFFBBBBBB);
        menuButton.setPadding(dp(8), 0, dp(8), 0);
        menuButton.setClickable(true);
        menuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        statusNick = new TextView(this);
        statusNick.setText(getString(R.string.nick, "?"));
        statusNick.setTextColor(0xFF9E9E9E);
        statusRoom = new TextView(this);
        statusRoom.setText(getString(R.string.room, "lobby"));
        statusRoom.setTextColor(0xFF9E9E9E);

        toggleButton = new TextView(this);
        toggleButton.setTextSize(18);
        toggleButton.setTextColor(0xFFBBBBBB);
        toggleButton.setPadding(dp(8), 0, dp(8), 0);
        toggleButton.setClickable(true);

        headerBar.addView(menuButton, new LinearLayout.LayoutParams(-2, -2));
        headerBar.addView(statusNick, new LinearLayout.LayoutParams(0, -2, 1));
        headerBar.addView(statusRoom, new LinearLayout.LayoutParams(0, -2, 1));
        headerBar.addView(toggleButton, new LinearLayout.LayoutParams(-2, -2));

        // --- connect panel ---
        connectPanel = new LinearLayout(this);
        connectPanel.setOrientation(LinearLayout.HORIZONTAL);
        connectPanel.setPadding(dp(12), dp(8), dp(12), dp(8));
        connectPanel.setVisibility(connectPanelExpanded ? View.VISIBLE : View.GONE);

        ipField = new EditText(this);
        ipField.setHint(getString(R.string.ip));
        portField = new EditText(this);
        portField.setHint(getString(R.string.port));
        connectBtn = new Button(this);
        connectBtn.setText(getString(R.string.connect));
        connectBtn.setOnClickListener(v -> {
            String ip = ipField.getText().toString().trim();
            String portStr = portField.getText().toString().trim();
            int port;
            try {
                port = Integer.parseInt(portStr);
                if (port < 0 || port > 65535) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                addSystemMessage(getString(R.string.error_invalid_port), true);
                return;
            }
            new Thread(() -> {
                if (conn != null && conn.isRunning() && ip.equals(conn.getIp()) && port == conn.getPort()) {
                    runOnUiThread(() -> addSystemMessage(getString(R.string.error_already_connected), true));
                    return;
                }
                prefs.edit().putString(KEY_IP, ip).putInt(KEY_PORT, port).apply();
                doConnect(ip, port);
            }).start();
        });
        connectPanel.addView(ipField, new LinearLayout.LayoutParams(0, -2, 2));
        connectPanel.addView(portField, new LinearLayout.LayoutParams(0, -2, 1));
        connectPanel.addView(connectBtn, new LinearLayout.LayoutParams(-2, -2));

        // --- chat list ---
        recycler = new RecyclerView(this);
        adapter = new ChatAdapter(this);
        adapter.setOnAfterInsert(this::scrollToBottom);
        convs = new ConversationManager();
        convs.setActiveConversation(convs.getOrCreateRoom("lobby"));
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        recycler.setBackgroundColor(0xFF1A1A1A);

        // --- input row ---
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(dp(8), dp(4), dp(8), dp(4));

        msgField = new EditText(this);
        msgField.setHint(getString(R.string.message_placeholder));
        msgField.setSingleLine(true);
        msgField.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEND) sendMessage();
            return true;
        });

        ImageButton attachButton = new ImageButton(this);
        attachButton.setImageResource(android.R.drawable.ic_menu_gallery);
        attachButton.setBackground(null);
        attachButton.setOnClickListener(v -> openImagePicker());

        Button sendBtn = new Button(this);
        sendBtn.setText(getString(R.string.send));
        sendBtn.setOnClickListener(v -> sendMessage());

        inputRow.addView(msgField, new LinearLayout.LayoutParams(0, -2, 1));
        inputRow.addView(attachButton, new LinearLayout.LayoutParams(-2, -2));
        inputRow.addView(sendBtn, new LinearLayout.LayoutParams(-2, -2));

        mainContent.addView(headerBar, new LinearLayout.LayoutParams(-1, -2));
        mainContent.addView(connectPanel, new LinearLayout.LayoutParams(-1, -2));
        mainContent.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1));
        mainContent.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));

        // --- side panel ---
        sidePanel = new LinearLayout(this);
        sidePanel.setOrientation(LinearLayout.VERTICAL);
        sidePanel.setBackgroundColor(0xFF2A2A2A);
        sidePanel.setPadding(dp(16), dp(16), dp(16), dp(16));
        sidePanel.setLayoutParams(new DrawerLayout.LayoutParams(dp(280), -1, Gravity.START));

        // current room header
        currentRoomHeader = new TextView(this);
        currentRoomHeader.setText(getString(R.string.current_room, currentRoom));
        currentRoomHeader.setTextColor(0xFFFFFFFF);
        currentRoomHeader.setTextSize(16);
        currentRoomHeader.setPadding(0, dp(8), 0, dp(8));
        currentRoomHeader.setBackgroundResource(android.R.drawable.list_selector_background);
        currentRoomHeader.setClickable(true);
        currentRoomHeader.setOnClickListener(v -> {
            Conversation roomConv = convs.getOrCreateRoom(currentRoom);
            switchToConversation(roomConv);
        });

        View divider = new View(this);
        divider.setBackgroundColor(0xFF444444);
        divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        divider.setPadding(0, dp(8), 0, dp(8));

        TextView dmsHeader = new TextView(this);
        dmsHeader.setText(getString(R.string.dms));
        dmsHeader.setTextColor(0xFFBBBBBB);
        dmsHeader.setTextSize(14);
        dmsHeader.setPadding(0, dp(16), 0, dp(8));

        dmRecycler = new RecyclerView(this);
        dmRecycler.setLayoutManager(new LinearLayoutManager(this));
        dmAdapter = new DmListAdapter(convs, this::switchToConversation);
        dmRecycler.setAdapter(dmAdapter);

        sidePanel.addView(currentRoomHeader);
        sidePanel.addView(divider);
        sidePanel.addView(dmsHeader);
        sidePanel.addView(dmRecycler, new LinearLayout.LayoutParams(-1, 0, 1));

        drawerLayout.addView(mainContent, new DrawerLayout.LayoutParams(-1, -1));
        drawerLayout.addView(sidePanel);

        setContentView(drawerLayout);

        if (connectPanelExpanded) {
            toggleButton.setText("▲");
        } else {
            toggleButton.setText("▼");
        }
        toggleButton.setOnClickListener(v -> {
            if (connectPanelExpanded) {
                TranslateAnimation slideUp = new TranslateAnimation(0, 0, 0, -connectPanel.getHeight());
                slideUp.setDuration(200);
                slideUp.setAnimationListener(new Animation.AnimationListener() {
                    @Override public void onAnimationStart(Animation a) {}
                    @Override public void onAnimationEnd(Animation a) { connectPanel.setVisibility(View.GONE); }
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
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(intent, "Select Image"), REQUEST_IMAGE_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri == null) return;
            try {
                InputStream is = getContentResolver().openInputStream(imageUri);
                byte[] imageBytes = readBytes(is);
                if (is != null) is.close();
                String mimeType = getContentResolver().getType(imageUri);
                String fileName = getFileName(imageUri);

                // compress of too large
                if (imageBytes.length > 2 * 1024 * 1024) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    imageBytes = baos.toByteArray();
                }

                Conversation active = convs.getActiveConversation();
                String target;
                if (active != null && active.type == Conversation.Type.DM) {
                    target = active.id;
                } else {
                    target = "";
                }

                byte[] finalImageBytes = imageBytes;
                new Thread(() -> {
                    try {
                        conn.sendImage(target, mimeType, fileName, finalImageBytes);
                    } catch (Exception e) {
                        runOnUiThread(() -> addSystemMessage(getString(R.string.error_send_image), true));
                    }
                }).start();

                // Add local self-message with image
                ChatMessage selfMsg = new ChatMessage("", ChatMessage.Type.SELF, null,
                        System.currentTimeMillis(), imageBytes, mimeType);
                if (active != null) {
                    convs.addMessageToConversation(active, selfMsg);
                    adapter.addMessage(selfMsg);
                    scrollToBottom();
                }
            } catch (Exception e) {
                addSystemMessage(getString(R.string.error_reading_image), true);
            }
        }
    }

    private byte[] readBytes(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private String getFileName(Uri uri) {
        String fileName = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex);
                }
            }
        }
        if (fileName == null) fileName = uri.getLastPathSegment();
        return fileName;
    }

    // --- rest of MainActivity (unchanged except add onImageMessage) ---

    private void switchToConversation(Conversation conv) {
        convs.setActiveConversation(conv);
        adapter.setMessages(conv.messages);
        scrollToBottom();
        refreshSidePanel();
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void refreshSidePanel() {
        currentRoomHeader.setText(getString(R.string.current_room, currentRoom));
        List<Conversation> dms = convs.getRecentDMs();
        dmAdapter.setConversations(dms);
    }

    private void doConnect(String ip, int port) {
        if (conn != null) conn.disconnect();
        conn = new ChatConnection(this);
        try {
            conn.connect(ip, port);
        } catch (EOFException e) {
            runOnUiThread(() -> addSystemMessage(getString(R.string.error_EOF), true));
        } catch (Exception e) {
            runOnUiThread(() -> addSystemMessage(getString(R.string.error_connection, e.getMessage()), true));
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        if (conn == null || !conn.isRunning()) {
            addSystemMessage(getString(R.string.error_not_connected), true);
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
                    runOnUiThread(() -> addSystemMessage(getString(R.string.error_nick), true));
                }
            }).start();
        } else if (text.startsWith("/join ")) {
            String room = text.substring(6).trim();
            new Thread(() -> {
                try {
                    conn.sendJoin(room);
                } catch (Exception e) {
                    runOnUiThread(() -> addSystemMessage(getString(R.string.error_join), true));
                }
            }).start();
        } else if (text.startsWith("/dm ")) {
            String[] parts = text.substring(4).trim().split(" ", 2);
            if (parts.length < 2) {
                addSystemMessage(getString(R.string.dm_usage), true);
                return;
            }
            String target = parts[0];
            String dmText = parts[1];
            Conversation dmConv = convs.getOrCreateDM(target);
            ChatMessage selfMsg = new ChatMessage(getString(R.string.dm_to, target, dmText),
                    ChatMessage.Type.SELF, null, System.currentTimeMillis());
            convs.addMessageToConversation(dmConv, selfMsg);
            convs.setActiveConversation(dmConv);
            adapter.setMessages(dmConv.messages);
            refreshSidePanel();
            scrollToBottom();
            new Thread(() -> {
                try {
                    conn.sendDm(target, dmText);
                } catch (Exception e) {
                    runOnUiThread(() -> addSystemMessage(getString(R.string.error_dm), true));
                }
            }).start();
        } else {
            Conversation active = convs.getActiveConversation();
            if (active == null) {
                addSystemMessage(getString(R.string.error_no_active_conv), true);
                return;
            }
            if (active.type == Conversation.Type.ROOM) {
                ChatMessage selfMsg = new ChatMessage(text, ChatMessage.Type.SELF, null, System.currentTimeMillis());
                convs.addMessageToConversation(active, selfMsg);
                adapter.addMessage(selfMsg);
                scrollToBottom();
                new Thread(() -> {
                    try {
                        conn.sendChat(text);
                    } catch (Exception e) {
                        runOnUiThread(() -> addSystemMessage(getString(R.string.error_send), true));
                        e.printStackTrace();
                    }
                }).start();
            } else if (active.type == Conversation.Type.DM) {
                ChatMessage selfMsg = new ChatMessage(getString(R.string.dm, text), ChatMessage.Type.SELF, null, System.currentTimeMillis());
                convs.addMessageToConversation(active, selfMsg);
                adapter.addMessage(selfMsg);
                scrollToBottom();
                new Thread(() -> {
                    try {
                        conn.sendDm(active.id, text);
                    } catch (Exception e) {
                        runOnUiThread(() -> addSystemMessage(getString(R.string.error_dm), true));
                    }
                }).start();
            }
        }
    }

    // --- MessageListener callbacks ---

    @Override
    public void onConnected() {
        runOnUiThread(() -> addSystemMessage(getString(R.string.connected), false));
        String savedNick = prefs.getString(KEY_NICKNAME, null);
        String savedRoom = prefs.getString(KEY_ROOM, null);
        new Thread(() -> {
            try {
                if (savedNick != null && !savedNick.isEmpty()) {
                    conn.sendNick(savedNick);
                }
                if (savedRoom != null && !savedRoom.isEmpty() && !savedRoom.equals("lobby")) {
                    Thread.sleep(200);
                    conn.sendJoin(savedRoom);
                }
            } catch (Exception ignored) {
                // empty catch block
            }
        }).start();
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> addSystemMessage(getString(R.string.disconnected), false));
    }

    @Override
    public void onSystemMessage(int code, List<String> params, boolean isError) {
        int resId = getResourceIdForCode(code);
        String formatted;
        if (resId != 0) {
            formatted = getString(resId, params.toArray());
        } else {
            formatted = "unknown message (code " + code + ")";
        }
        addSystemMessage(formatted, isError);
    }

    @Override
    public void onChatMessage(String sender, String text) {
        runOnUiThread(() -> {
            Conversation roomConv = convs.getOrCreateRoom(currentRoom);
            ChatMessage msg = new ChatMessage(text, ChatMessage.Type.OTHER, sender, System.currentTimeMillis());
            convs.addMessageToConversation(roomConv, msg);
            if (convs.getActiveConversation() == roomConv) {
                adapter.addMessage(msg);
                scrollToBottom();
            } else {
                refreshSidePanel();
            }
        });
    }

    @Override
    public void onImageMessage(String sender, String mimeType, String fileName, byte[] imageData) {
        runOnUiThread(() -> {
            // Determine target conversation: if sender is in a DM with us, route there; else room.
            Conversation active = convs.getActiveConversation();
            Conversation targetConv = null;
            // Check if we have a DM with this sender
            List<Conversation> dms = convs.getRecentDMs();
            for (Conversation c : dms) {
                if (c.id.equals(sender)) {
                    targetConv = c;
                    break;
                }
            }
            if (targetConv == null) {
                // treat as room message
                targetConv = convs.getOrCreateRoom(currentRoom);
            }
            ChatMessage msg = new ChatMessage("", ChatMessage.Type.OTHER, sender,
                    System.currentTimeMillis(), imageData, mimeType);
            convs.addMessageToConversation(targetConv, msg);
            if (convs.getActiveConversation() == targetConv) {
                adapter.addMessage(msg);
                scrollToBottom();
            } else {
                refreshSidePanel();
            }
        });
    }

    @Override
    public void onNickChanged(String newNick) {
        currentNick = newNick;
        prefs.edit().putString(KEY_NICKNAME, newNick).apply();
        runOnUiThread(() -> {
            statusNick.setText(getString(R.string.nick, newNick));
            addSystemMessage(getString(R.string.youre_now, newNick), false);
        });
    }

    @Override
    public void onNickNotify(String oldNick, String newNick) {
        runOnUiThread(() -> addSystemMessage(getString(R.string.is_now, oldNick, newNick), false));
    }

    @Override
    public void onRoomJoined(String roomName) {
        currentRoom = roomName;
        prefs.edit().putString(KEY_ROOM, roomName).apply();
        runOnUiThread(() -> {
            statusRoom.setText(getString(R.string.room, roomName));
            addSystemMessage(getString(R.string.youve_joined, roomName), false);
            Conversation roomConv = convs.getOrCreateRoom(roomName);
            convs.setActiveConversation(roomConv);
            adapter.setMessages(roomConv.messages);
            refreshSidePanel();
            scrollToBottom();
        });
    }

    @Override
    public void onUserJoined(String nick) {
        runOnUiThread(() -> addSystemMessage(getString(R.string.joined, nick), false));
    }

    @Override
    public void onUserLeft(String nick) {
        runOnUiThread(() -> addSystemMessage(getString(R.string.left, nick), false));
    }

    @Override
    public void onDirectMessage(String sender, String text) {
        runOnUiThread(() -> {
            Conversation dmConv = convs.getOrCreateDM(sender);
            ChatMessage msg = new ChatMessage(getString(R.string.dm_from, sender, text),
                    ChatMessage.Type.OTHER, sender, System.currentTimeMillis());
            convs.addMessageToConversation(dmConv, msg);
            if (convs.getActiveConversation() == dmConv) {
                adapter.addMessage(msg);
                scrollToBottom();
            } else {
                refreshSidePanel();
            }
        });
    }

    @Override
    public void onKicked(String reason) {
        runOnUiThread(() -> {
            addSystemMessage(getString(R.string.error_kicked, reason), true);
            if (conn != null) conn.disconnect();
        });
    }

    @Override
    public void onBanned(String reason) {
        runOnUiThread(() -> {
            addSystemMessage(getString(R.string.error_banned, reason), true);
            if (conn != null) conn.disconnect();
        });
    }


    // --- helper methods ---

    private void addSystemMessage(String text, boolean isError) {
        Conversation active = convs.getActiveConversation();
        if (active == null) return;
        ChatMessage sysMsg = new ChatMessage(text, isError, System.currentTimeMillis());
        convs.addMessageToConversation(active, sysMsg);
        adapter.addMessage(sysMsg);
        scrollToBottom();
    }

    private int getResourceIdForCode(int code) {
        switch (code) {
            case SystemMessageCode.MSG_WELCOME:                return R.string.msg_1;
            case SystemMessageCode.MSG_NICK_EMPTY:             return R.string.msg_2;
            case SystemMessageCode.MSG_NICK_TOO_LONG:          return R.string.msg_3;
            case SystemMessageCode.MSG_NICK_INVALID_CHARS:     return R.string.msg_4;
            case SystemMessageCode.MSG_NICK_SAME:              return R.string.msg_5;
            case SystemMessageCode.MSG_NICK_BANNED:            return R.string.msg_6;
            case SystemMessageCode.MSG_NICK_TAKEN:             return R.string.msg_7;
            case SystemMessageCode.MSG_JOIN_ALREADY:           return R.string.msg_8;
            case SystemMessageCode.MSG_JOIN_NAME_TAKEN:        return R.string.msg_9;
            case SystemMessageCode.MSG_DM_TARGET_NOT_FOUND:    return R.string.msg_10;
            case SystemMessageCode.MSG_IMAGE_UNSUPPORTED:      return R.string.msg_11;
            default: return 0;
        }
    }

    private int dp(int dp) { return (int) (dp * getResources().getDisplayMetrics().density); }

    private void scrollToBottom() {
        recycler.post(() -> {
            int lastPos = adapter.getItemCount() - 1;
            if (lastPos >= 0) {
                recycler.smoothScrollToPosition(lastPos);
            }
        });
    }

    public RecyclerView getRecycler() {
        return recycler;
    }

    class DmListAdapter extends RecyclerView.Adapter<DmListAdapter.ViewHolder> {
        private List<Conversation> conversations;
        private final ConversationManager manager;
        private final OnConversationClickListener listener;
        DmListAdapter(ConversationManager m, OnConversationClickListener l) { manager = m; listener = l; }
        void setConversations(List<Conversation> list) { conversations = list; notifyDataSetChanged(); }
        @Override public int getItemCount() { return conversations == null ? 0 : conversations.size(); }
        @Override @NonNull public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            tv.setPadding(dp(8), dp(8), dp(8), dp(8));
            tv.setTextColor(0xFFFFFFFF);
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            tv.setClickable(true);
            tv.setFocusable(true);
            return new ViewHolder(tv);
        }
        @Override public void onBindViewHolder(ViewHolder holder, int pos) {
            Conversation c = conversations.get(pos);
            String display = c.displayName + (c.unreadCount > 0 ? " (" + c.unreadCount + ")" : "");
            holder.textView.setText(display);
            holder.textView.setOnClickListener(v -> listener.onClick(c));
        }
        static class ViewHolder extends RecyclerView.ViewHolder { TextView textView; ViewHolder(TextView v) { super(v); textView = v; } }
    }

    interface OnConversationClickListener { void onClick(Conversation conv); }
}
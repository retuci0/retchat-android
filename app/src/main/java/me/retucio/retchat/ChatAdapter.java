package me.retucio.retchat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SELF = 1;
    private static final int TYPE_OTHER = 2;
    private static final int TYPE_SYSTEM = 3;

    private final List<ChatMessage> messages = new ArrayList<>();

    public void addMessage(ChatMessage msg) {
        maybeAddDateHeader(msg);
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    private void maybeAddDateHeader(ChatMessage newMsg) {
        if (messages.isEmpty()) {
            String header = formatDateHeader(newMsg.timestamp);
            messages.add(ChatMessage.createDateHeader(header, newMsg.timestamp));
            return;
        }
        long lastTimestamp = messages.get(messages.size() - 1).timestamp;
        if (!isSameDay(lastTimestamp, newMsg.timestamp)) {
            String header = formatDateHeader(newMsg.timestamp);
            messages.add(ChatMessage.createDateHeader(header, newMsg.timestamp));
        }
    }

    private boolean isSameDay(long t1, long t2) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return sdf.format(new Date(t1)).equals(sdf.format(new Date(t2)));
    }

    private String formatDateHeader(long timestamp) {
        long now = System.currentTimeMillis();
        SimpleDateFormat sdf;
        if (isSameDay(now, timestamp)) {
            return "today";
        } else if (isSameDay(now - 24 * 60 * 60 * 1000, timestamp)) {
            return "yesterday";
        } else {
            sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        if (msg.isDateHeader) return TYPE_SYSTEM;
        return msg.type == ChatMessage.Type.SELF ? TYPE_SELF :
                msg.type == ChatMessage.Type.OTHER ? TYPE_OTHER : TYPE_SYSTEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SELF) {
            View v = inflater.inflate(R.layout.item_message_self, parent, false);
            return new SelfViewHolder(v);
        } else if (viewType == TYPE_OTHER) {
            View v = inflater.inflate(R.layout.item_message_other, parent, false);
            return new OtherViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_message_system, parent, false);
            return new SystemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = timeFormat.format(new Date(msg.timestamp));

        if (holder instanceof SelfViewHolder vh) {
            vh.messageText.setText(msg.text);
            vh.timeText.setText(timeStr);
        } else if (holder instanceof OtherViewHolder vh) {
            vh.messageText.setText(msg.text);
            vh.timeText.setText(timeStr);
            if (msg.sender != null && !msg.sender.isEmpty()) {
                vh.senderText.setText(msg.sender);
                vh.senderText.setVisibility(View.VISIBLE);
            } else {
                vh.senderText.setVisibility(View.GONE);
            }
        } else if (holder instanceof SystemViewHolder) {
            SystemViewHolder vh = (SystemViewHolder) holder;
            vh.messageText.setText(msg.text);
            if (msg.isDateHeader) {
                // date headers: bold, no error color
                vh.messageText.setTypeface(null, android.graphics.Typeface.BOLD);
                vh.messageText.setTextColor(0xFF666666);
            } else {
                vh.messageText.setTypeface(null, android.graphics.Typeface.ITALIC);
                if (msg.isError) {
                    vh.messageText.setTextColor(0xFFD32F2F);  // red
                } else {
                    vh.messageText.setTextColor(0xFF757575);  // gray
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SelfViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText;
        SelfViewHolder(View v) {
            super(v);
            messageText = v.findViewById(R.id.text_message);
            timeText = v.findViewById(R.id.text_time);
        }
    }

    static class OtherViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText, senderText;
        OtherViewHolder(View v) {
            super(v);
            messageText = v.findViewById(R.id.text_message);
            timeText = v.findViewById(R.id.text_time);
            senderText = v.findViewById(R.id.text_sender);
        }
    }

    static class SystemViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        SystemViewHolder(View v) {
            super(v);
            messageText = v.findViewById(R.id.text_system_message);
        }
    }
}
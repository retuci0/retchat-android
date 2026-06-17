package me.retucio.retchat.chat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.retucio.retchat.MainActivity;
import me.retucio.retchat.R;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final MainActivity activity;
    private final List<ChatMessage> messages = new ArrayList<>();
    private static final int TYPE_SELF = 1;
    private static final int TYPE_OTHER = 2;
    private static final int TYPE_SYSTEM = 3;

    private Runnable onAfterInsert;

    public ChatAdapter(MainActivity act) {
        this.activity = act;
    }

    public void setOnAfterInsert(Runnable r) {
        this.onAfterInsert = r;
    }

    public void addMessage(ChatMessage msg) {
        activity.runOnUiThread(() -> {
            int start = messages.size();
            boolean addDateHeader = messages.isEmpty() ||
                    !isSameDay(messages.get(messages.size() - 1).timestamp, msg.timestamp);

            if (addDateHeader) {
                String header = formatDateHeader(msg.timestamp);
                messages.add(ChatMessage.createDateHeader(header, msg.timestamp));
            }
            messages.add(msg);
            int count = addDateHeader ? 2 : 1;
            notifyItemRangeInserted(start, count);

            if (onAfterInsert != null) {
                activity.runOnUiThread(() -> {
                    activity.getRecycler().post(() -> onAfterInsert.run());
                });
            }
        });
    }

    public void setMessages(List<ChatMessage> newMessages) {
        activity.runOnUiThread(() -> {
            messages.clear();
            messages.addAll(newMessages);
            notifyDataSetChanged();
            // Scroll after layout
            if (onAfterInsert != null) {
                activity.getRecycler().post(() -> onAfterInsert.run());
            }
        });
    }

    private boolean isSameDay(long t1, long t2) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return sdf.format(new Date(t1)).equals(sdf.format(new Date(t2)));
    }

    private String formatDateHeader(long timestamp) {
        long now = System.currentTimeMillis();
        if (isSameDay(now, timestamp)) {
            return activity.getString(R.string.today);
        } else if (isSameDay(now - 24 * 60 * 60 * 1000, timestamp)) {
            return activity.getString(R.string.yesterday);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
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
            vh.timeText.setText(timeStr);
            if (msg.hasImage) {
                vh.messageText.setVisibility(View.GONE);
                vh.imageView.setVisibility(View.VISIBLE);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = calculateInSampleSize(msg.imageData, 400, 400);
                Bitmap bitmap = BitmapFactory.decodeByteArray(msg.imageData, 0, msg.imageData.length, options);
                vh.imageView.setImageBitmap(bitmap);
            } else {
                vh.messageText.setVisibility(View.VISIBLE);
                vh.imageView.setVisibility(View.GONE);
                vh.messageText.setText(msg.text);
                Linkify.addLinks(vh.messageText, Linkify.WEB_URLS);
            }
        } else if (holder instanceof OtherViewHolder vh) {
            vh.timeText.setText(timeStr);
            if (msg.sender != null && !msg.sender.isEmpty()) {
                vh.senderText.setText(msg.sender);
                vh.senderText.setVisibility(View.VISIBLE);
            } else {
                vh.senderText.setVisibility(View.GONE);
            }
            if (msg.hasImage) {
                vh.messageText.setVisibility(View.GONE);
                vh.imageView.setVisibility(View.VISIBLE);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = calculateInSampleSize(msg.imageData, 400, 400);
                Bitmap bitmap = BitmapFactory.decodeByteArray(msg.imageData, 0, msg.imageData.length, options);
                vh.imageView.setImageBitmap(bitmap);
            } else {
                vh.messageText.setVisibility(View.VISIBLE);
                vh.imageView.setVisibility(View.GONE);
                vh.messageText.setText(msg.text);
                Linkify.addLinks(vh.messageText, Linkify.WEB_URLS);
            }
        } else if (holder instanceof SystemViewHolder vh) {
            vh.messageText.setText(msg.text);
            if (msg.isDateHeader) {
                vh.messageText.setTypeface(null, android.graphics.Typeface.BOLD);
                vh.messageText.setTextColor(0xFF666666);
            } else {
                vh.messageText.setTypeface(null, android.graphics.Typeface.ITALIC);
                vh.messageText.setTextColor(msg.isError ? 0xFFD32F2F : 0xFF757575);
            }
        }
    }

    private int calculateInSampleSize(byte[] data, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        int height = options.outHeight;
        int width = options.outWidth;
        int sampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / sampleSize) >= reqHeight
                    && (halfWidth / sampleSize) >= reqWidth) {
                sampleSize *= 2;
            }
        }
        return sampleSize;
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SelfViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText;
        ImageView imageView;
        SelfViewHolder(View v) {
            super(v);
            messageText = v.findViewById(R.id.text_message);
            timeText = v.findViewById(R.id.text_time);
            imageView = v.findViewById(R.id.image_message);
        }
    }

    static class OtherViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText, senderText;
        ImageView imageView;
        OtherViewHolder(View v) {
            super(v);
            messageText = v.findViewById(R.id.text_message);
            timeText = v.findViewById(R.id.text_time);
            senderText = v.findViewById(R.id.text_sender);
            imageView = v.findViewById(R.id.image_message);
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
package com.zulip.android.activities;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.zulip.android.R;
import com.zulip.android.ZulipApp;
import com.zulip.android.filters.NarrowFilterPM;
import com.zulip.android.filters.NarrowFilterStream;
import com.zulip.android.filters.NarrowListener;
import com.zulip.android.models.Message;
import com.zulip.android.models.MessageType;
import com.zulip.android.models.Stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEWTYPE_MESSAGEHEADER = 1;
    private static final int VIEWTYPE_MESSAGE = 2;
    private static final int VIEWTYPE_HEADER = 3; //At position 0
    private static final int VIEWTYPE_FOOTER = 4; //At end position

    private static String privateHuddleText;
    private List<Object> items;
    private ZulipApp zulipApp;
    private Context context;
    private NarrowListener narrowListener;
    private static final float HEIGHT_IN_DP = 48;
    private
    @ColorInt
    int mDefaultStreamHeaderColor;

    @ColorInt
    private int mDefaultPrivateMessageColor;
    private OnItemClickListener onItemClickListener;
    private int contextMenuItemSelectedPosition;
    private boolean startedFromFilter;
    private View footerView;
    private View headerView;

    int getContextMenuItemSelectedPosition() {
        return contextMenuItemSelectedPosition;
    }

    MessageAdapter(List<Message> messageList, final Context context, boolean startedFromFilter) {
        super();
        items = new ArrayList<>();
        setupHeaderAndFooterViews();
        zulipApp = ZulipApp.get();
        this.context = context;
        narrowListener = ((NarrowListener) context);
        this.startedFromFilter = startedFromFilter;
        mDefaultStreamHeaderColor = ContextCompat.getColor(context, R.color.stream_header);
        mDefaultPrivateMessageColor = ContextCompat.getColor(context, R.color.huddle_body);
        privateHuddleText = context.getResources().getString(R.string.huddle_text);
        setupLists(messageList);
    }

    public View getView(int position, View convertView, ViewGroup group) {

        final ZulipActivity context = (ZulipActivity) this.getContext();
        final Message message = getItem(position);
        LinearLayout tile;

        if (convertView == null || !(convertView.getClass().equals(LinearLayout.class))) {
            // We didn't get passed a tile, so construct a new one.
            // In the future, we should inflate from a layout here.
            LayoutInflater inflater = ((Activity) this.getContext()).getLayoutInflater();
            tile = (LinearLayout) inflater.inflate(R.layout.message_tile, group, false);
        } else {
            tile = (LinearLayout) convertView;
        }

        LinearLayout envelopeTile = (LinearLayout) tile.findViewById(R.id.envelopeTile);
        TextView displayRecipient = (TextView) tile.findViewById(R.id.displayRecipient);
        ImageView muteImageView = (ImageView) tile.findViewById(R.id.muteMessageImage);

        if (message.getType() != MessageType.STREAM_MESSAGE) {
            envelopeTile.setBackgroundColor(mDefaultHuddleHeaderColor);
        } else {
            Stream stream = message.getStream();
            @ColorInt int color = stream == null ? mDefaultStreamHeaderColor : stream.getColor();
            envelopeTile.setBackgroundColor(color);
        }

        if (message.getType() != MessageType.STREAM_MESSAGE) {
            displayRecipient.setText(context.getString(R.string.huddle_text, message.getDisplayRecipient(context.app)));
            displayRecipient.setTextColor(Color.WHITE);
            displayRecipient.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getContext() instanceof NarrowListener) {
                        ((NarrowListener) getContext()).onNarrow(new NarrowFilterPM(
                                Arrays.asList(message.getRecipients(ZulipApp.get()))));
                    }
                }
            });
        } else {
            displayRecipient.setText(message.getDisplayRecipient(context.app));
            displayRecipient.setTextColor(Color.BLACK);
            displayRecipient.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getContext() instanceof NarrowListener) {
                        ((NarrowListener) getContext()).onNarrow(new NarrowFilterStream(message.getStream(), null));
                    }
                }
            });
            if (getContext() instanceof NarrowListener) {
                if (context.app.isTopicMute(message)) {
                    muteImageView.setVisibility(View.VISIBLE);
                } else {
                    muteImageView.setVisibility(View.GONE);
                }
            }
        }

        TextView sep = (TextView) tile.findViewById(R.id.sep);
        TextView instance = (TextView) tile.findViewById(R.id.instance);

        if (message.getType() != MessageType.STREAM_MESSAGE) {
            instance.setVisibility(View.GONE);
            sep.setVisibility(View.GONE);
            instance.setOnClickListener(null);
        } else {
            instance.setVisibility(View.VISIBLE);
            sep.setVisibility(View.VISIBLE);
            instance.setText(message.getSubject());
            instance.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getContext() instanceof NarrowListener) {
                        ((NarrowListener) getContext()).onNarrow(new NarrowFilterStream(message.getStream(), message.getSubject()));
                        ((NarrowListener) getContext()).onNarrowFillSendBox(message);
                    }
                }
            });
        }

        LinearLayout messageTile = (LinearLayout) tile.findViewById(R.id.messageTile);
        if (message.getType() != MessageType.STREAM_MESSAGE) {
            messageTile.setBackgroundColor(mDefaultHuddleMessageColor);
        } else {
            messageTile.setBackgroundColor(mDefaultStreamMessageColor);
        }

        TextView senderName = (TextView) tile.findViewById(R.id.senderName);
        senderName.setText(message.getSender().getName());

        TextView contentView = (TextView) tile.findViewById(R.id.contentView);

        Spanned formattedMessage = formatContent(message.getFormattedContent(),
                context.app);
        while (formattedMessage.length() != 0
                && formattedMessage.charAt(formattedMessage.length() - 1) == '\n') {
            formattedMessage = (Spanned) formattedMessage.subSequence(0,
                    formattedMessage.length() - 2);
        }
        contentView.setText(formattedMessage);

        contentView.setMovementMethod(LinkMovementMethod.getInstance());
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((NarrowListener) getContext()).onNarrowFillSendBox(message);
            }
        });

        TextView timestamp = (TextView) tile.findViewById(R.id.timestamp);

        if (DateUtils.isToday(message.getTimestamp().getTime())) {
            timestamp.setText(DateUtils.formatDateTime(context, message
                    .getTimestamp().getTime(), DateUtils.FORMAT_SHOW_TIME));
        } else {
            timestamp.setText(DateUtils.formatDateTime(context, message
                    .getTimestamp().getTime(), DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_ABBREV_MONTH
                    | DateUtils.FORMAT_SHOW_TIME));
        }

        ImageView gravatar = (ImageView) tile.findViewById(R.id.gravatar);
        Bitmap gravatarImg = context.getGravatars().get(message.getSender().getEmail());
        if (gravatarImg != null) {
            // Gravatar already exists for this image, set the ImageView to it
            gravatar.setImageBitmap(gravatarImg);
        } else {
            // Go get the Bitmap
            URL url = GravatarAsyncFetchTask.sizedURL(context, message.getSender().getAvatarURL(), 35);
            GravatarAsyncFetchTask task = new GravatarAsyncFetchTask(context, gravatar, message.getSender());
            task.loadBitmap(context, url, gravatar, message.getSender());
        }

        tile.setTag(R.id.messageID, message.getID());

        return tile;

    }

    /**
     * Copied from Html.fromHtml
     *
     * @param source HTML to be formatted
     * @param app
     * @return Span
     */
    public static Spanned formatContent(String source, ZulipApp app) {
        final Context context = app.getApplicationContext();
        final float density = context.getResources().getDisplayMetrics().density;
        Parser parser = new Parser();
        try {
            parser.setProperty(Parser.schemaProperty, schema);
        } catch (org.xml.sax.SAXNotRecognizedException e) {
            // Should not happen.
            throw new RuntimeException(e);
        } catch (org.xml.sax.SAXNotSupportedException e) {
            // Should not happen.
            throw new RuntimeException(e);
        }

        Html.ImageGetter emojiGetter = new Html.ImageGetter() {
            @Override
            public Drawable getDrawable(String source) {
                int lastIndex = -1;
                if (source != null) {
                    lastIndex = source.lastIndexOf('/');
                }
                if (lastIndex != -1) {
                    String filename = source.substring(lastIndex + 1);
                    try {
                        Drawable drawable = Drawable.createFromStream(context
                                        .getAssets().open("emoji/" + filename),
                                "emoji/" + filename);
                        // scaling down by half to fit well in message
                        double scaleFactor = 0.5;
                        drawable.setBounds(0, 0,
                                (int) (drawable.getIntrinsicWidth()
                                        * scaleFactor * density),
                                (int) (drawable.getIntrinsicHeight()
                                        * scaleFactor * density));
                        return drawable;
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
                return null;
            }
        };

        CustomHtmlToSpannedConverter converter = new CustomHtmlToSpannedConverter(
                source, null, null, parser, emojiGetter, app.getServerURI());
        return converter.convert();
    }

    public long getItemId(int position) {
        return this.getItem(position).getID();
    }

}

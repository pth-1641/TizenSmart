package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.LongClickPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview.ComplexImageCardView;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/*
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
public class VideoCardPresenter extends LongClickPresenter {
    private static final String TAG = VideoCardPresenter.class.getSimpleName();
    private int mDefaultBackgroundColor = -1;
    private int mDefaultTextColor = -1;
    private int mSelectedBackgroundColor = -1;
    private int mSelectedTextColor = -1;
    private int mCardPreviewType;
    private int mThumbQuality;
    private int mWidth;
    private int mHeight;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();

        mDefaultBackgroundColor =
            ContextCompat.getColor(context, Helpers.getThemeAttr(context, R.attr.cardDefaultBackground));
        mDefaultTextColor =
                ContextCompat.getColor(context, R.color.card_default_text);
        mSelectedBackgroundColor =
                ContextCompat.getColor(context, Helpers.getThemeAttr(context, R.attr.cardSelectedBackground));
        mSelectedTextColor =
                ContextCompat.getColor(context, R.color.card_selected_text_grey);

        mCardPreviewType = getCardPreviewType(context);
        mThumbQuality = getThumbQuality(context);

        boolean isCardMultilineTitleEnabled = isCardMultilineTitleEnabled(context);
        boolean isCardMultilineSubtitleEnabled = isCardMultilineSubtitleEnabled(context);
        boolean isCardTextAutoScrollEnabled = isCardTextAutoScrollEnabled(context);
        float cardTextScrollSpeed = getCardTextScrollSpeed(context);

        updateDimensions(context);

        ComplexImageCardView cardView = new ComplexImageCardView(context) {
            @Override
            public void setSelected(boolean selected) {
                updateCardBackgroundColor(this, selected);
                super.setSelected(selected);
            }
        };

        cardView.setTitleLinesNum(isCardMultilineTitleEnabled ? 2 : 1);
        cardView.setContentLinesNum(isCardMultilineSubtitleEnabled ? 2 : 1);
        cardView.enableTextAutoScroll(isCardTextAutoScrollEnabled);
        if (!isCardTextAutoScrollEnabled) {
            // The theme enables marquee on selection: use a static ellipsis instead
            TextView t = cardView.findViewById(R.id.title_text);
            if (t != null) t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView c = cardView.findViewById(R.id.content_text);
            if (c != null) c.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (t instanceof com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextviewcompat.MarqueeTextViewCompat) {
                ((com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextviewcompat.MarqueeTextViewCompat) t).setForceStatic(true);
            }
            if (c instanceof com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextviewcompat.MarqueeTextViewCompat) {
                ((com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextviewcompat.MarqueeTextViewCompat) c).setForceStatic(true);
            }
        }
        cardView.setTextScrollSpeed(cardTextScrollSpeed);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.enableBadge(isBadgeEnabled());
        cardView.enableTitle(isTitleEnabled());
        cardView.enableContent(isContentEnabled());
        cardView.setBackgroundColor(mDefaultBackgroundColor); // background is temporarily visible during animations
        //if (VERSION.SDK_INT >= 23 && MainUIData.instance(context).isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS)) {
        //    cardView.setForeground(ContextCompat.getDrawable(context, R.drawable.lb_card_outline));
        //}
        applyTizenTubeTextStyle(cardView);
        updateCardBackgroundColor(cardView, false);
        return new ViewHolder(cardView);
    }

    /** TizenTube-like card text: flush with the thumbnail edge, bold title, muted two-line subtitle */
    private void applyTizenTubeTextStyle(ComplexImageCardView cardView) {
        float d = cardView.getResources().getDisplayMetrics().density;

        View infoField = cardView.findViewById(R.id.info_field);
        if (infoField != null) {
            infoField.setPadding(0, (int) (8 * d), 0, 0);
        }

        TextView title = cardView.findViewById(R.id.title_text);
        if (title != null) {
            title.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            clearTextMargins(title);
            title.setMinLines(0); // wrap to content, so the subtitle sits right under a one-line title
        }

        TextView content = cardView.findViewById(R.id.content_text);
        if (content != null) {
            clearTextMargins(content);
            content.setMinLines(0);
            if (title != null && content.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams) {
                android.widget.RelativeLayout.LayoutParams lp = (android.widget.RelativeLayout.LayoutParams) content.getLayoutParams();
                lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
                lp.addRule(android.widget.RelativeLayout.BELOW, R.id.title_text);
                lp.topMargin = (int) (4 * d);
                content.setLayoutParams(lp);
            }
        }

        // Same height for every card (2-line title + 2-line subtitle): otherwise the grid places the next card under the
        // shortest column and the layout gets staggered
        if (infoField != null && title != null && content != null) {
            int h = (int) (8 * d) + title.getLineHeight() * 2 + (int) (4 * d) + content.getLineHeight() * 2 + (int) (12 * d);
            infoField.setMinimumHeight(h);
        }
    }

    private static void clearTextMargins(TextView view) {
        if (view.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) view.getLayoutParams();
            lp.leftMargin = 0;
            lp.setMarginStart(0);
            lp.bottomMargin = 0;
            view.setLayoutParams(lp);
        }
    }

    private void updateCardBackgroundColor(ComplexImageCardView view, boolean selected) {
        // TizenTube-like: selection is a white outline around the thumbnail, text stays light on dark
        int backgroundColor = mDefaultBackgroundColor;
        int textColor = mDefaultTextColor;

        View preview = view.findViewById(R.id.main_image_wrapper);
        if (preview != null && android.os.Build.VERSION.SDK_INT >= 23) {
            // Radius and border scale with the card width (grid scale setting): ~3.5% and ~1% of the width
            float d = view.getResources().getDisplayMetrics().density;
            final float radius = Math.max(3 * d, mWidth * 0.035f);
            float stroke = Math.max(2f, mWidth * 0.01f);

            android.graphics.drawable.Drawable outline = null;
            if (selected) {
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setColor(android.graphics.Color.TRANSPARENT);
                g.setCornerRadius(Math.max(0f, radius - stroke / 2f)); // stroke is centered: its outer edge matches the clip radius
                g.setStroke(Math.round(stroke), 0xFFFFFFFF);
                outline = g;
            }
            preview.setForeground(outline);
            // Clip thumbnail and badge to the rounded shape (all four corners), selected or not
            preview.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
            preview.setClipToOutline(true);
        }

        // Both background colors should be set because the view's
        // background is temporarily visible during animations.
        // NOTE: has visual bug with rounded corners
        //view.setBackgroundColor(backgroundColor);

        View infoField = view.findViewById(R.id.info_field);
        if (infoField != null) {
            infoField.setBackgroundColor(backgroundColor);
        }

        TextView titleText = view.findViewById(R.id.title_text);
        if (titleText != null) {
            titleText.setTextColor(selected ? 0xFFFFFFFF : 0xFFB3B3B3); // TizenTube: only the selected title is bright
        }
        TextView contentText = view.findViewById(R.id.content_text);
        if (contentText != null) {
            contentText.setTextColor(selected ? 0xFFAAAAAA : 0xFF8C8C8C); // TizenTube-like muted subtitle
        }
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        super.onBindViewHolder(viewHolder, item);

        Video video = (Video) item;

        ComplexImageCardView cardView = (ComplexImageCardView) viewHolder.view;
        Context context = cardView.getContext();

        cardView.setTitleText(video.getTitle());
        String second = video.getSecondTitle() != null ? video.getSecondTitle().toString() : null;
        if (second != null) {
            second = second.replaceFirst(" • ", "\n"); // channel on the first line, views • age on the second (TizenTube)
        }
        cardView.setContentText(second);
        // Count progress that very close to zero. E.g. when user closed video immediately.
        cardView.setProgress(video.percentWatched > 0 && video.percentWatched < 1 ? 1 : Math.round(video.percentWatched));
        cardView.setBadgeText(
                video.hasNewContent ? context.getString(R.string.badge_new_content) :
                video.isLive ? context.getString(R.string.badge_live) :
                video.isShorts ? context.getString(R.string.header_shorts).toUpperCase() :
                video.badge
        );
        cardView.setBadgeColor(video.hasNewContent || video.isLive || video.isUpcoming ?
                ContextCompat.getColor(context, R.color.dark_red) : ContextCompat.getColor(context, R.color.black));

        if (mCardPreviewType != MainUIData.CARD_PREVIEW_DISABLED) {
            cardView.setPreview(video);
            cardView.setMute(mCardPreviewType == MainUIData.CARD_PREVIEW_MUTED);
        }

        cardView.setMainImageDimensions(mWidth, mHeight);

        if (context instanceof Activity && ((Activity) context).isDestroyed()) {
            // Glide.with(context): IllegalArgumentException: You cannot start a load for a destroyed activity
            return;
        }

        android.widget.ImageView mainImage = cardView.getMainImageView();

        if (video.cardImageUrl != null && video.cardImageUrl.startsWith("icon://")) {
            // Tile without thumbnail (channel tabs): draw a centered icon on a dark tile
            Glide.with(context.getApplicationContext()).clear(mainImage);
            String type = video.cardImageUrl.substring(7).toUpperCase();
            int res = type.contains("PLAYLIST") ? R.drawable.ic_tab_playlist :
                    type.contains("SHORT") ? R.drawable.ic_tab_shorts : R.drawable.ic_tab_video;
            mainImage.setBackgroundColor(0xFF272727);
            mainImage.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            mainImage.setImageResource(res);
            return;
        }

        mainImage.setBackground(null);
        mainImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);

        Glide.with(context)
                //.asBitmap() // disable animation (webp, gif)
                .load(ClickbaitRemover.updateThumbnail(video, mThumbQuality))
                //.placeholder(mDefaultCardImage)
                .apply(ViewUtil.glideOptions())
                // improve image compression on low end devices
                .override(mWidth, mHeight)
                // com.liskovsoft.smartyoutubetv2.tv.util.CacheGlideModule
                // Cache makes app crashing on old android versions
                .diskCacheStrategy(VERSION.SDK_INT > 21 ? DiskCacheStrategy.ALL : DiskCacheStrategy.NONE)
                .listener(mErrorListener)
                .error(
                    // Updated thumbnail url not found
                    Glide.with(context)
                        .load(video.cardImageUrl) // always working
                        //.placeholder(mDefaultCardImage)
                        .apply(ViewUtil.glideOptions())
                        .listener(mErrorListener)
                        .error(R.drawable.card_placeholder) // R.color.lb_grey
                )
                .into(cardView.getMainImageView());
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        super.onUnbindViewHolder(viewHolder);

        ComplexImageCardView cardView = (ComplexImageCardView) viewHolder.view;

        // Remove references to images so that the garbage collector can free up memory.
        cardView.setBadgeImage(null);
        cardView.setMainImage(null);

        // Cleanup Glide resources. https://chatgpt.com/share/682120c5-e428-8010-b848-371b2dec0cd5
        Glide.with(cardView.getContext().getApplicationContext()).clear(cardView.getMainImageView());
    }

    private void updateDimensions(Context context) {
        Pair<Integer, Integer> dimens = getCardDimensPx(context);

        mWidth = dimens.first;
        mHeight = dimens.second;
    }
    
    protected Pair<Integer, Integer> getCardDimensPx(Context context) {
        return GridFragmentHelper.getCardDimensPx(context, R.dimen.card_width, R.dimen.card_height, MainUIData.instance(context).getVideoGridScale());
    }

    protected boolean isCardTextAutoScrollEnabled(Context context) {
        return MainUIData.instance(context).isCardTextAutoScrollEnabled();
    }

    protected int getCardPreviewType(Context context) {
        return MainUIData.instance(context).getCardPreviewType();
    }

    protected boolean isCardMultilineTitleEnabled(Context context) {
        return MainUIData.instance(context).isCardMultilineTitleEnabled();
    }

    protected boolean isCardMultilineSubtitleEnabled(Context context) {
        return MainUIData.instance(context).isCardMultilineSubtitleEnabled();
    }

    protected float getCardTextScrollSpeed(Context context) {
        return MainUIData.instance(context).getCardTextScrollSpeed();
    }

    protected int getThumbQuality(Context context) {
        return MainUIData.instance(context).getThumbQuality();
    }

    protected boolean isContentEnabled() {
        return true;
    }

    protected boolean isTitleEnabled() {
        return true;
    }

    protected boolean isBadgeEnabled() {
        return true;
    }

    private final RequestListener<Drawable> mErrorListener = new RequestListener<Drawable>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            Log.e(TAG, "Glide load failed: " + e);
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            return false;
        }
    };

    private final RequestListener<Bitmap> mErrorListener2 = new RequestListener<Bitmap>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
            Log.e(TAG, "Glide load failed: " + e);
            return false;
        }

        @Override
        public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
            return false;
        }
    };
}

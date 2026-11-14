package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowHeaderPresenter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class IconHeaderItemPresenter extends RowHeaderPresenter {
    private static final String TAG = IconHeaderItemPresenter.class.getSimpleName();
    private static boolean sCollapsed = true; // the app starts with the sidebar collapsed
    private static View sExtraItem; // e.g. the account item above the sections
    private float mUnselectedAlpha;
    private final int mResId;
    private final String mIconUrl;
    private Drawable mDefaultIcon;

    public IconHeaderItemPresenter(int resId, String iconUrl) {
        mResId = resId;
        mIconUrl = iconUrl;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup) {
        mUnselectedAlpha = viewGroup.getResources()
                .getFraction(R.fraction.lb_browse_header_unselect_alpha, 1, 1);
        LayoutInflater inflater = (LayoutInflater) viewGroup.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDefaultIcon = new ColorDrawable(ContextCompat.getColor(viewGroup.getContext(), R.color.lb_grey));

        // Inflate with the parent so that match_parent width is honored (full width highlight)
        View view = inflater.inflate(R.layout.icon_header_item, viewGroup, false);
        // Icons are shifted left to center them in the mini strip: don't clip them at the list padding
        viewGroup.setClipToPadding(false);
        viewGroup.setClipChildren(false);
        // Full width highlight (the list wraps items otherwise)
        view.setMinimumWidth((int) (215 * viewGroup.getResources().getDisplayMetrics().density));
        view.setAlpha(mUnselectedAlpha); // Initialize icons to be at half-opacity.

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        HeaderItem headerItem;

        if (item instanceof PageRow) {
            headerItem = ((PageRow) item).getHeaderItem();
        } else {
            headerItem = ((ListRow) item).getHeaderItem();
        }

        View rootView = viewHolder.view;
        rootView.setFocusable(true);

        ImageView iconView = rootView.findViewById(R.id.header_icon);
        if (iconView != null) {
            if (mIconUrl != null) {
                Glide.with(rootView.getContext())
                        .load(mIconUrl)
                        .apply(ViewUtil.glideOptions().error(mDefaultIcon))
                        .listener(mErrorListener)
                        .into(iconView);

                //ViewUtil.makeMonochrome(iconView);
            } else {
                Drawable icon = mResId > 0 ? ContextCompat.getDrawable(rootView.getContext(), mResId) : mDefaultIcon;
                iconView.setImageDrawable(icon);
            }
        }

        TextView label = rootView.findViewById(R.id.header_label);
        if (label != null) {
            label.setText(headerItem.getName());
        }

        // TizenTube-like: focused header is a white pill with dark text/icon
        final TextView labelView = label;
        final ImageView icon = iconView;
        applyCollapsed(rootView);
        if (rootView.getTag(R.id.header_sync_listener) == null) {
            // Items scrolled out of view miss the collapse/expand switch: resync whenever they come back
            rootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    applyCollapsed(v);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            });
            rootView.setTag(R.id.header_sync_listener, Boolean.TRUE);
        }
        rootView.setOnFocusChangeListener((v, focused) -> updateHeaderStyle(v, labelView, icon, focused && !sCollapsed));
    }

    /** Collapsed sidebar shows icons only (TizenTube mini sidebar) */
    public static void setCollapsed(boolean collapsed, androidx.recyclerview.widget.RecyclerView headersList, int selectedPosition) {
        sCollapsed = collapsed;

        if (sExtraItem != null) {
            animateCollapsed(sExtraItem);
        }

        if (headersList == null) {
            return;
        }

        for (int i = 0; i < headersList.getChildCount(); i++) {
            View wrapper = headersList.getChildAt(i);
            View item = findItem(wrapper);
            if (item == null) {
                continue;
            }
            animateCollapsed(item);
            if (sCollapsed) {
                highlightIfSelected(item, headersList.getChildAdapterPosition(wrapper) == selectedPosition);
            }
        }
    }

    /**
     * An item that lives outside of the sections list (e.g. the account) but follows the same collapse/focus style.
     * Set the icon/label by yourself. Photos (avatar) shouldn't be tinted: tag the icon with R.id.header_icon = TRUE.
     */
    public static void attachExtraItem(View item) {
        sExtraItem = item;
        final TextView label = item.findViewById(R.id.header_label);
        final ImageView icon = item.findViewById(R.id.header_icon);
        item.setFocusable(true);
        applyCollapsed(item);
        item.setOnFocusChangeListener((v, focused) -> updateHeaderStyle(v, label, icon, focused && !sCollapsed));
    }

    public static void detachExtraItem(View item) {
        if (sExtraItem == item) {
            sExtraItem = null;
        }
    }

    /** The header item itself (the child of the adapter wrapper) */
    private static View findItem(View wrapper) {
        View icon = wrapper.findViewById(R.id.header_icon);
        return icon != null && icon.getParent() instanceof View ? (View) icon.getParent() : null;
    }

    private static void highlightIfSelected(View item, boolean selected) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(10f * item.getResources().getDisplayMetrics().density);
        bg.setColor(selected ? 0x33FFFFFF : android.graphics.Color.TRANSPARENT);
        item.setBackground(bg);
        item.setAlpha(selected ? 1f : 0.55f);
    }

    /** Smooth version of applyCollapsed: label fades and padding eases together with the width transition */
    private static void animateCollapsed(final View root) {
        Object runningFade = root.getTag(R.id.header_fade);
        if (runningFade instanceof android.animation.ValueAnimator) {
            root.setTag(R.id.header_fade, null);
            ((android.animation.ValueAnimator) runningFade).cancel(); // its end handler finishes the previous state
        }

        TextView lbl = root.findViewById(R.id.header_label);
        if (sCollapsed && lbl != null && lbl.getVisibility() == View.VISIBLE && root.getWidth() > 0) {
            // Collapsing: fade label and pill out first instead of snapping them away (the snap is seen as a flash)
            final float density = root.getResources().getDisplayMetrics().density;
            root.animate().translationX(-14 * density).setDuration(140).start();
            android.animation.ValueAnimator fade = android.animation.ValueAnimator.ofFloat(1f, 0f);
            fade.setDuration(90); // short: it must be gone before the panel gets narrow (a squeezed label shows as "...")
            fade.addUpdateListener(a -> {
                float v = (float) a.getAnimatedValue();
                lbl.setAlpha(v);
                if (root.getBackground() != null) {
                    root.getBackground().setAlpha((int) (255 * v));
                }
            });
            fade.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean done;

                private void finish() {
                    if (done) return;
                    done = true;
                    root.setTag(R.id.header_fade, null);
                    animateCollapsedNow(root);
                }

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    finish();
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    finish();
                }
            });
            root.setTag(R.id.header_fade, fade);
            fade.start();
            return;
        }

        animateCollapsedNow(root);
    }

    private static void animateCollapsedNow(final View root) {
        final TextView label = root.findViewById(R.id.header_label);
        ImageView icon = root.findViewById(R.id.header_icon);
        final float density = root.getResources().getDisplayMetrics().density;
        root.setPadding((int) (8 * density), 0, (int) (8 * density), 0); // constant: icon never gets squeezed
        final boolean collapsing = sCollapsed;

        Object running = root.getTag(R.id.header_label);
        if (running instanceof android.animation.ValueAnimator) {
            ((android.animation.ValueAnimator) running).cancel();
        }

        if (label != null && !collapsing) {
            label.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            label.setAlpha(0f);
            label.setVisibility(View.VISIBLE);
        }
        if (label != null && collapsing) {
            label.setAlpha(0f); // hide at once: a shrinking label would show as "..." for a frame
            label.setVisibility(View.GONE);
        }
        root.setMinimumWidth((int) ((collapsing ? 36 : 215) * density)); // collapsed: wide enough for padding + 20dp icon, else the icon gets squeezed and clipped
        updateHeaderStyle(root, label, icon, !collapsing && root.hasFocus());

        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        anim.setStartDelay(collapsing ? 0 : 110); // expanding: show labels only once the panel is wide enough (else "Kênh đ..." flashes)
        anim.setDuration(collapsing ? 1 : 150); // collapsing: hide the label at once, otherwise it shows as "..." while the width shrinks
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            if (label != null) {
                label.setAlpha(collapsing ? 1f - t : t);
            }
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            private void finish() {
                // Also on cancel: otherwise the label stays stuck at alpha 0 (item looks empty)
                if (label != null) {
                    label.setAlpha(1f);
                    label.setVisibility(collapsing ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                finish();
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                finish();
            }
        });
        root.setTag(R.id.header_label, anim);
        root.animate().cancel();
        root.animate().translationX(collapsing ? -14 * density : 0f).setDuration(200).start(); // center icons in the mini strip
        anim.start();
    }

    private static void applyCollapsed(View root) {
        TextView label = root.findViewById(R.id.header_label);
        ImageView icon = root.findViewById(R.id.header_icon);
        if (label != null) {
            label.setVisibility(sCollapsed ? View.GONE : View.VISIBLE);
            label.setAlpha(1f);
        }
        float density = root.getResources().getDisplayMetrics().density;
        root.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
        root.setTranslationX(sCollapsed ? -14 * density : 0f);
        root.setMinimumWidth((int) ((sCollapsed ? 36 : 215) * density));
        root.setAlpha(sCollapsed ? 0.55f : 1f);
        updateHeaderStyle(root, label, icon, !sCollapsed && root.hasFocus());
    }

    private static void updateHeaderStyle(final View root, final TextView label, final ImageView icon, final boolean focused) {
        final float radius = 10f * root.getResources().getDisplayMetrics().density;

        Object running = root.getTag(R.id.header_icon);
        if (running instanceof android.animation.ValueAnimator) {
            ((android.animation.ValueAnimator) running).cancel();
        }

        if (!sCollapsed) {
            root.setAlpha(focused ? 1f : 0.9f); // drop the dim left over from the collapsed state
        }

        if (!focused) {
            applyHeaderColors(root, label, icon, 0f, radius);
            return;
        }

        // Focused pill fades in: while the sidebar is still growing it would otherwise flash as a small white square
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        anim.setStartDelay(120); // wait until the sidebar is wide enough, a small white square would flash otherwise
        anim.setDuration(160);
        anim.addUpdateListener(a -> applyHeaderColors(root, label, icon, (float) a.getAnimatedValue(), radius));
        root.setTag(R.id.header_icon, anim);
        applyHeaderColors(root, label, icon, 0f, radius);
        anim.start();
    }

    /** t = 0: idle (transparent, white content); t = 1: focused (white pill, dark content) */
    private static void applyHeaderColors(View root, TextView label, ImageView icon, float t, float radius) {
        android.animation.ArgbEvaluator ev = new android.animation.ArgbEvaluator();

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(radius);
        bg.setColor((Integer) ev.evaluate(t, 0x00F1F1F1, 0xFFF1F1F1));
        root.setBackground(bg);

        int color = (Integer) ev.evaluate(t, 0xFFFFFFFF, 0xFF0F0F0F);
        if (label != null) {
            label.setTextColor(color);
        }
        if (icon != null) {
            if (icon.getTag(R.id.header_icon) == null) {
                icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                icon.clearColorFilter(); // photo
            }
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        // NOP
    }

    // TODO: This is a temporary fix. Remove me when leanback onCreateViewHolder no longer sets the
    // mUnselectAlpha, and also assumes the xml inflation will return a RowHeaderView.
    @Override
    protected void onSelectLevelChanged(RowHeaderPresenter.ViewHolder holder) {
        if (sCollapsed) {
            boolean selected = holder.getSelectLevel() > 0.5f;
            holder.view.setAlpha(selected ? 1f : 0.55f);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(10f * holder.view.getResources().getDisplayMetrics().density);
            bg.setColor(selected ? 0x33FFFFFF : android.graphics.Color.TRANSPARENT);
            holder.view.setBackground(bg);
            return;
        }

        holder.view.setAlpha(mUnselectedAlpha + holder.getSelectLevel() *
                (1.0f - mUnselectedAlpha));
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
}

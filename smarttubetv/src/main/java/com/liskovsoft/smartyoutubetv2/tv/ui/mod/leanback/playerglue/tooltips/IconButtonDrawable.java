package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

/** TizenTube-like round button: translucent circle, solid white circle with dark icon when focused */
public class IconButtonDrawable extends Drawable {
    private final Paint mBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable mIcon;
    private final int mIconSize;
    private boolean mFocused;
    private boolean mTintOnFocus = true;

    /** false for photos (channel avatar): the white circle only, the picture keeps its colors */
    public IconButtonDrawable setTintOnFocus(boolean tint) {
        mTintOnFocus = tint;
        update();
        return this;
    }

    public IconButtonDrawable(Drawable icon, int iconSize) {
        mIcon = icon != null ? icon.mutate() : null;
        mIconSize = iconSize;
        update();
    }

    private void update() {
        mBg.setColor(mFocused ? 0xFFF1F1F1 : 0x1AFFFFFF);
        if (mIcon != null) {
            mIcon.setColorFilter(mFocused && mTintOnFocus ? new PorterDuffColorFilter(0xFF0F0F0F, PorterDuff.Mode.SRC_IN) : null);
        }
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean focused = false;
        for (int s : state) {
            if (s == android.R.attr.state_focused) focused = true;
        }
        if (focused != mFocused) {
            mFocused = focused;
            update();
            invalidateSelf();
            return true;
        }
        return false;
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect b = getBounds();
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), Math.min(b.width(), b.height()) / 2f, mBg);
        if (mIcon != null) {
            int half = mIconSize / 2;
            mIcon.setBounds((int) b.exactCenterX() - half, (int) b.exactCenterY() - half,
                    (int) b.exactCenterX() + half, (int) b.exactCenterY() + half);
            mIcon.draw(canvas);
        }
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.search;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.leanback.widget.SearchOrbView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Orb that looks like a sidebar item (TizenTube style): icon + label, white pill when focused.
 */
public class PillSearchOrbView extends SearchOrbView {
    private static final int TEXT_NORMAL = Color.WHITE;
    private static final int TEXT_FOCUSED = 0xFF0F0F0F;

    public PillSearchOrbView(Context context) {
        this(context, null);
    }

    public PillSearchOrbView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PillSearchOrbView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // The pill is lifted on focus: keep the label and the icon above it
        float z = 16 * getResources().getDisplayMetrics().density;
        View label = findViewById(R.id.pill_label);
        if (label != null) {
            androidx.core.view.ViewCompat.setZ(label, z);
        }
        View icon = findViewById(R.id.icon);
        if (icon != null) {
            androidx.core.view.ViewCompat.setZ(icon, z);
        }
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.lb_pill_orb;
    }

    /** Label color when the view is not focused */
    protected int getIdleTextColor() {
        return TEXT_NORMAL;
    }

    /** Background color when the view is not focused */
    protected int getIdleColor() {
        return Color.TRANSPARENT;
    }

    /** Background is transparent, focus color is light: ignore colors that are set from outside */
    @Override
    public void setOrbColors(Colors colors) {
        super.setOrbColors(new Colors(getIdleColor(), 0xFFF1F1F1, colors.iconColor));
        applyFocusStyle(hasFocus());
    }

    /** No pulsing color animation on focus: the pill is simply white when focused */
    @Override
    public void enableOrbColorAnimation(boolean enable) {
        super.enableOrbColorAnimation(false);
    }

    public void setLabel(CharSequence label) {
        TextView view = findViewById(R.id.pill_label);
        if (view != null) {
            view.setText(label);
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        applyFocusStyle(gainFocus);
    }

    private void applyFocusStyle(boolean gainFocus) {
        // Leanback doesn't change the orb color on focus: do it by hand
        View pill = findViewById(R.id.search_orb);
        if (pill != null && pill.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) pill.getBackground()).setColor(gainFocus ? 0xFFF1F1F1 : getIdleColor());
        }

        TextView label = findViewById(R.id.pill_label);
        if (label != null) {
            label.setTextColor(gainFocus ? TEXT_FOCUSED : getIdleTextColor());
        }

        // Avatars have transparent filter: don't tint them
        ImageView icon = findViewById(R.id.icon);
        Colors colors = getOrbColors();
        // The search orb always has a plain icon; the account orb may show an avatar (transparent filter)
        boolean plainIcon = !(this instanceof LongClickSearchOrbView)
                || (colors != null && colors.iconColor != Color.TRANSPARENT);
        if (icon != null && plainIcon) {
            icon.setColorFilter(gainFocus ? TEXT_FOCUSED : TEXT_NORMAL);
        }
    }
}

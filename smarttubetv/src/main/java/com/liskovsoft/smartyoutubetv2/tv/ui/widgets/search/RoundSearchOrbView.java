package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.search;

import android.content.Context;
import android.util.AttributeSet;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Small round search button (TizenTube style): dark circle, white when focused.
 */
public class RoundSearchOrbView extends PillSearchOrbView {
    public RoundSearchOrbView(Context context) {
        this(context, null);
    }

    public RoundSearchOrbView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RoundSearchOrbView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setOrbIcon(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.lb_ic_in_app_search));
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.lb_mic_orb; // 36dp circle with a centered icon
    }

    @Override
    protected int getIdleColor() {
        return 0xFF272727;
    }
}
